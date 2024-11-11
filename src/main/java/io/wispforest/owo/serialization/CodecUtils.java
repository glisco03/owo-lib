package io.wispforest.owo.serialization;

import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.google.gson.JsonPrimitive;
import com.mojang.datafixers.util.Either;
import com.mojang.datafixers.util.Pair;
import com.mojang.serialization.*;
import io.netty.buffer.ByteBuf;
import io.netty.buffer.Unpooled;
import io.wispforest.endec.*;
import io.wispforest.endec.format.bytebuf.ByteBufDeserializer;
import io.wispforest.endec.format.bytebuf.ByteBufSerializer;
import io.wispforest.endec.format.edm.*;
import io.wispforest.endec.format.forwarding.ForwardingDeserializer;
import io.wispforest.endec.format.forwarding.ForwardingSerializer;
import io.wispforest.endec.format.gson.GsonDeserializer;
import io.wispforest.endec.format.gson.GsonEndec;
import io.wispforest.endec.format.gson.GsonSerializer;
import io.wispforest.owo.mixin.ForwardingDynamicOpsAccessor;
import io.wispforest.owo.mixin.RegistryOpsAccessor;
import io.wispforest.owo.serialization.endec.EitherEndec;
import io.wispforest.owo.serialization.endec.MinecraftEndecs;
import io.wispforest.owo.serialization.endec.StructEitherEndec;
import io.wispforest.owo.serialization.format.ContextHolder;
import io.wispforest.owo.serialization.format.DynamicOpsWithContext;
import io.wispforest.owo.serialization.format.edm.EdmOps;
import io.wispforest.owo.serialization.format.nbt.NbtDeserializer;
import io.wispforest.owo.serialization.format.nbt.NbtEndec;
import io.wispforest.owo.serialization.format.nbt.NbtSerializer;
import io.wispforest.owo.util.Scary;
import io.wispforest.owo.util.StackTraceSupplier;
import net.minecraft.nbt.NbtCompound;
import net.minecraft.nbt.NbtElement;
import net.minecraft.nbt.NbtOps;
import net.minecraft.nbt.NbtString;
import net.minecraft.network.PacketByteBuf;
import net.minecraft.network.RegistryByteBuf;
import net.minecraft.network.codec.PacketCodec;
import net.minecraft.registry.RegistryOps;
import net.minecraft.util.dynamic.ForwardingDynamicOps;
import org.jetbrains.annotations.ApiStatus;
import org.jetbrains.annotations.Nullable;

import java.util.HashMap;
import java.util.Map;
import java.util.function.Function;
import java.util.function.Supplier;
import java.util.stream.Stream;

public class CodecUtils {

    /**
     * Create a new endec serializing the same data as {@code codec}
     * <p>
     * This method is implemented by converting all data to be (de-)serialized
     * to the Endec Data Model data format (hereto-forth to be referred to as EDM)
     * which has both an endec ({@link EdmEndec}) and DynamicOps implementation ({@link EdmOps}).
     * Since EDM encodes structure using a self-described format's native structural types,
     * <b>this means that for JSON and NBT, the created endec's serialized representation is identical
     * to that of {@code codec}</b>. In general, for non-self-described formats, the serialized
     * representation is a byte array
     * <p>
     * When decoding, an EDM element is read from the deserializer and then parsed using {@code codec}
     * <p>
     * When encoding, the value is encoded using {@code codec} to an EDM element which is then
     * written into the serializer
     */
    public static <T> Endec<T> toEndec(Codec<T> codec) {
        return Endec.of(encoderOfCodec(codec), decoderOfCodec(codec));
    }

    private static <T> Endec.Encoder<T> encoderOfCodec(Codec<T> codec) {
        return (ctx, serializer, value) -> encodeWithCodecIntoSerializer(codec, value, serializer, ctx);
    }

    private static <T, S> void encodeWithCodecIntoSerializer(Codec<T> codec, T value, Serializer<S> serializer, SerializationContext ctx) {
        var unpackedSerializer = unpackSerializer(serializer);
        var pair = getOpsAndAdapter(unpackedSerializer, ctx);

        if (pair == null || !(unpackedSerializer instanceof SelfDescribedSerializer<S> selfDescribedSerializer)) {
            EdmEndec.INSTANCE.encode(ctx, serializer, codec.encodeStart(createEdmOps(ctx), value).getOrThrow());
        } else {
            var ops = pair.getFirst();
            var adapter = pair.getSecond();

            encodeValue(adapter, selfDescribedSerializer, codec.encodeStart(ops, value).getOrThrow());
        }
    }

    private static <T> Endec.Decoder<T> decoderOfCodec(Codec<T> codec) {
        return (ctx, deserializer) -> decodeWithCodecFromDeserializer(codec, deserializer, ctx);
    }

    private static <T, S> T decodeWithCodecFromDeserializer(Codec<T> codec, Deserializer<S> deserializer, SerializationContext ctx) {
        var unpackedDeserializer = unpackDeserializer(deserializer);
        var pair = CodecUtils.getOpsAndAdapter(unpackedDeserializer, ctx);

        return pair == null || !(unpackedDeserializer instanceof SelfDescribedDeserializer<S> selfDescribedDeserializer)
            ? codec.parse(createEdmOps(ctx), EdmEndec.INSTANCE.decode(ctx, deserializer)).getOrThrow()
            : codec.parse(pair.getFirst(), copyDecodedValue(pair.getSecond(), selfDescribedDeserializer)).getOrThrow();
    }

    public static <T> Endec<T> toEndec(Codec<T> codec, PacketCodec<ByteBuf, T> packetCodec) {
        var encoder = encoderOfCodec(codec);
        var decoder = decoderOfCodec(codec);

        return Endec.of(
            (ctx, serializer, value) -> {
                if (serializer instanceof ByteBufSerializer<?>) {
                    var buffer = new PacketByteBuf(Unpooled.buffer());
                    packetCodec.encode(buffer, value);
                    MinecraftEndecs.PACKET_BYTE_BUF.encode(ctx, serializer, buffer);
                } else {
                    encoder.encode(ctx, serializer, value);
                }
            },
            (ctx, deserializer) -> {
                if (deserializer instanceof ByteBufDeserializer) {
                    return packetCodec.decode(MinecraftEndecs.PACKET_BYTE_BUF.decode(ctx, deserializer));
                } else {
                    return decoder.decode(ctx, deserializer);
                }
            }
        );
    }

    public static <T> Endec<T> toEndecWithRegistries(Codec<T> codec, PacketCodec<RegistryByteBuf, T> packetCodec) {
        var encoder = encoderOfCodec(codec);
        var decoder = decoderOfCodec(codec);

        return Endec.of(
            (ctx, serializer, value) -> {
                if (serializer instanceof ByteBufSerializer<?>) {
                    var buffer = new RegistryByteBuf(new PacketByteBuf(Unpooled.buffer()), ctx.requireAttributeValue(RegistriesAttribute.REGISTRIES).registryManager());

                    packetCodec.encode(buffer, value);

                    MinecraftEndecs.PACKET_BYTE_BUF.encode(ctx, serializer, buffer);
                } else {
                    encoder.encode(ctx, serializer, value);
                }
            },
            (ctx, deserializer) -> {
                if (deserializer instanceof ByteBufDeserializer) {
                    return packetCodec.decode(
                        new RegistryByteBuf(
                            MinecraftEndecs.PACKET_BYTE_BUF.decode(ctx, deserializer),
                            ctx.requireAttributeValue(RegistriesAttribute.REGISTRIES).registryManager()
                        ));
                } else {
                    return decoder.decode(ctx, deserializer);
                }
            }
        );
    }

    /**
     * Create an endec which serializes an instance of {@link Either}, using {@code first}
     * for the left and {@code second} for the right variant
     * <p>
     * In a self-describing format, the serialized representation is simply that of the endec of
     * whichever variant is represented. In the general for non-self-described formats, the
     * which variant is represented must also be stored
     */
    public static <F, S> Endec<Either<F, S>> eitherEndec(Endec<F> first, Endec<S> second) {
        return new EitherEndec<>(first, second, false);
    }

    /**
     * Like {@link #eitherEndec(Endec, Endec)}, but ensures when decoding from a self-described format
     * that only {@code first} or {@code second}, but not both, succeed
     */
    public static <F, S> Endec<Either<F, S>> xorEndec(Endec<F> first, Endec<S> second) {
        return new EitherEndec<>(first, second, true);
    }

    /**
     * Create a structured endec which serializes an instance of {@link Either}, using {@code first}
     * for the left and {@code second} for the right variant
     * <p>
     * In a self-describing format, the serialized representation is simply that of the endec of
     * whichever variant is represented. In the general for non-self-described formats, the
     * which variant is represented must also be stored
     */
    public static <F, S> StructEndec<Either<F, S>> eitherStructEndec(StructEndec<F> first, StructEndec<S> second) {
        return new StructEitherEndec<>(first, second, false);
    }

    /**
     * Like {@link #eitherStructEndec(StructEndec, StructEndec)}, but ensures when decoding from a self-described format
     * that only {@code first} or {@code second}, but not both, succeed
     */
    public static <F, S> StructEndec<Either<F, S>> xorStructEndec(StructEndec<F> first, StructEndec<S> second) {
        return new StructEitherEndec<>(first, second, true);
    }

    //--

    /**
     * Create a codec serializing the same data as this endec, assuming
     * that the serialized format posses the {@code assumedAttributes}
     * <p>
     * This method is implemented by converting between a given DynamicOps'
     * datatype and EDM (see {@link #toEndec(Codec)}) and then encoding/decoding
     * from/to an EDM element using the {@link EdmSerializer} and {@link EdmDeserializer}
     * <p>
     * The serialized representation of a codec created through this method is generally
     * identical to that of a codec manually created to describe the same data
     */
    public static <T> Codec<T> toCodec(Endec<T> endec, SerializationContext assumedContext) {
        return new Codec<>() {
            @Override
            public <D> DataResult<Pair<T, D>> decode(DynamicOps<D> ops, D input) {
                return captureThrows(() -> {
                    var deserializer = deserializerForValue(ops, input);
                    var context = createContext(ops, assumedContext);

                    var decodedValue = (deserializer != null)
                        ? endec.decode(deserializer.setupContext(context), deserializer)
                        : endec.decode(context, LenientEdmDeserializer.of(ops.convertTo(EdmOps.withoutContext(), input)));

                    return new Pair<>(decodedValue, input);
                });
            }

            @Override
            public <D> DataResult<D> encode(T input, DynamicOps<D> ops, D prefix) {
                return captureThrows(() -> {
                    var serializer = serializerForOps(ops);
                    var context = createContext(ops, assumedContext);

                    return (serializer != null)
                        ? endec.encodeFully(context, () -> serializer, input)
                        : EdmOps.withoutContext().convertTo(ops, endec.encodeFully(context, EdmSerializer::of, input));
                });
            }
        };
    }

    @Deprecated
    public static <T> Codec<T> ofEndec(Endec<T> endec) {
        return toCodec(endec);
    }

    public static <T> Codec<T> toCodec(Endec<T> endec) {
        return toCodec(endec, SerializationContext.empty());
    }

    public static <T> MapCodec<T> toMapCodec(StructEndec<T> structEndec, SerializationContext assumedContext) {
        return new MapCodec<>() {
            @Override
            public <T1> Stream<T1> keys(DynamicOps<T1> ops) {
                throw new UnsupportedOperationException("MapCodec generated from StructEndec cannot report keys");
            }

            @Override
            public <T1> DataResult<T> decode(DynamicOps<T1> ops, MapLike<T1> input) {
                return captureThrows(() -> {
                    var deserializer = deserializerForMapLike(ops, input);
                    var context = createContext(ops, assumedContext);

                    if (deserializer != null) {
                        return structEndec.decode(deserializer.setupContext(context), deserializer);
                    } else {
                        var map = new HashMap<String, EdmElement<?>>();

                        input.entries().forEach(pair -> {
                            map.put(
                                ops.getStringValue(pair.getFirst()).getOrThrow(s -> new IllegalStateException("Unable to parse key: " + s)),
                                ops.convertTo(EdmOps.withoutContext(), pair.getSecond())
                            );
                        });

                        return structEndec.decode(context, LenientEdmDeserializer.of(EdmElement.consumeMap(map)));
                    }
                });
            }

            @Override
            public <T1> RecordBuilder<T1> encode(T input, DynamicOps<T1> ops, RecordBuilder<T1> prefix) {
                try {
                    var context = createContext(ops, assumedContext);
                    var pair = serializerForRecordBuilder(ops, prefix);

                    if (pair != null) {
                        var serializer = pair.getFirst();
                        return pair.getSecond().apply(structEndec.encodeFully(serializer.setupContext(context), () -> serializer, input));
                    } else {
                        var element = structEndec.encodeFully(context, EdmSerializer::of, input).<Map<String, EdmElement<?>>>cast();

                        var result = prefix;
                        for (var entry : element.entrySet()) {
                            result = result.add(entry.getKey(), EdmOps.withoutContext().convertTo(ops, entry.getValue()));
                        }

                        return result;
                    }
                } catch (Exception e) {
                    return prefix.withErrorsFrom(DataResult.error(e::getMessage, input));
                }
            }
        };
    }

    public static <T> MapCodec<T> toMapCodec(StructEndec<T> structEndec) {
        return toMapCodec(structEndec, SerializationContext.empty());
    }

    /*
     * This method overall should be fine but do not expect such to work always as it could be a problem as
     * it bypasses certain features about Deserializer API that may be an issue but is low chance for general
     * cases within Minecraft.
     *
     * blodhgarm: 21.07.2024
     */
    @Scary
    @ApiStatus.Experimental
    public static <T> StructEndec<T> toStructEndec(MapCodec<T> mapCodec) {
        return new StructEndec<T>() {
            @Override
            public void encodeStruct(SerializationContext ctx, Serializer<?> serializer, Serializer.Struct struct, T value) {
                this.doStructEncode(ctx, serializer, struct, value);
            }

            private <S> void doStructEncode(SerializationContext ctx, Serializer<S> serializer, Serializer.Struct struct, T value) {
                var unpackedSerializer = unpackSerializer(serializer);
                var pair = getOpsAndAdapter(unpackedSerializer, ctx);

                if (pair == null || !(unpackedSerializer instanceof SelfDescribedSerializer<S> selfDescribedSerializer)) {
                    var edmOps = createEdmOps(ctx);

                    var edmMap = mapCodec.encode(value, edmOps, edmOps.mapBuilder()).build(edmOps.emptyMap())
                        .getOrThrow()
                        .asMap();

                    if (serializer instanceof SelfDescribedSerializer<?>) {
                        edmMap.value().forEach((s, element) -> struct.field(s, ctx, EdmEndec.INSTANCE, element));
                    } else {
                        struct.field("element", ctx, EdmEndec.MAP, edmMap);
                    }
                } else {
                    CodecUtils.encodeStruct(pair.getSecond(), pair.getFirst(), selfDescribedSerializer, struct, mapCodec, value);
                }
            }

            @Override
            public T decodeStruct(SerializationContext ctx, Deserializer<?> deserializer, Deserializer.Struct struct) {
                return this.doStructDecode(ctx, deserializer, struct);
            }

            private <S> T doStructDecode(SerializationContext ctx, Deserializer<S> deserializer, Deserializer.Struct struct) {
                var unpackedDeserializer = unpackDeserializer(deserializer);
                var pair = getOpsAndAdapter(unpackedDeserializer, ctx);

                if (pair == null || !(unpackedDeserializer instanceof SelfDescribedDeserializer<S> selfDescribedDeserializer)) {
                    var edmMap = ((deserializer instanceof SelfDescribedDeserializer<?>)
                        ? EdmEndec.MAP.decode(ctx, deserializer)
                        : struct.field("element", ctx, EdmEndec.MAP));

                    var ops = createEdmOps(ctx);
                    return mapCodec.decode(ops, ops.getMap(edmMap).getOrThrow()).getOrThrow();
                } else {
                    return CodecUtils.decodeStruct(pair.getSecond(), pair.getFirst(), selfDescribedDeserializer, struct, mapCodec);
                }
            }
        };
    }

    // the fact that we lose context here is certainly far from ideal,
    // but for the most part *shouldn't* matter. after all, ideally nobody
    // should ever be nesting packet codecs into endecs - there's little
    // point to doing that and transferring any kind of context data becomes
    // mostly impossible because the system turns into one opaque spaghetti mess
    //
    // glisco, 28.04.2024
    public static <B extends PacketByteBuf, T> PacketCodec<B, T> toPacketCodec(Endec<T> endec) {
        return new PacketCodec<>() {
            @Override
            public T decode(B buf) {
                var ctx = buf instanceof RegistryByteBuf registryByteBuf
                    ? SerializationContext.attributes(RegistriesAttribute.of(registryByteBuf.getRegistryManager()))
                    : SerializationContext.empty();

                return endec.decode(ctx, ByteBufDeserializer.of(buf));
            }

            @Override
            public void encode(B buf, T value) {
                var ctx = buf instanceof RegistryByteBuf registryByteBuf
                    ? SerializationContext.attributes(RegistriesAttribute.of(registryByteBuf.getRegistryManager()))
                    : SerializationContext.empty();

                endec.encode(ctx, ByteBufSerializer.of(buf), value);
            }
        };
    }

    // ---

    private static SerializationContext createContext(DynamicOps<?> ops, SerializationContext assumedContext) {
        var rootOps = ops;
        var context = rootOps instanceof ContextHolder holder
            ? holder.capturedContext().and(assumedContext)
            : null;

        while (rootOps instanceof ForwardingDynamicOps<?>) {
            rootOps = ((ForwardingDynamicOpsAccessor<?>) rootOps).owo$delegate();

            if (context == null && rootOps instanceof ContextHolder holder) {
                context = holder.capturedContext().and(assumedContext);
            }
        }

        if (context == null) context = assumedContext;

        if (ops instanceof RegistryOps<?> registryOps) {
            context = context.withAttributes(RegistriesAttribute.tryFromCachedInfoGetter(((RegistryOpsAccessor) registryOps).owo$infoGetter()));
        }

        return context;
    }

    private static DynamicOps<EdmElement<?>> createEdmOps(SerializationContext ctx) {
        DynamicOps<EdmElement<?>> ops = EdmOps.withContext(ctx);

        if (ctx.hasAttribute(RegistriesAttribute.REGISTRIES)) {
            ops = RegistryOps.of(ops, ctx.getAttributeValue(RegistriesAttribute.REGISTRIES).infoGetter());
        }

        return ops;
    }

    private static <T> DataResult<T> captureThrows(Supplier<T> action) {
        try {
            return DataResult.success(action.get());
        } catch (Exception e) {
            return DataResult.error(StackTraceSupplier.of(e));
        }
    }

    // --- codec adapter shenanigans ensue ---

    private static final Map<Class<? extends Serializer<?>>, CodecAdapter<?, ?, ?>> serializerToAdapter = new HashMap<>();
    private static final Map<Class<? extends Deserializer<?>>, CodecAdapter<?, ?, ?>> deserializerToAdapter = new HashMap<>();
    private static final Map<Class<? extends DynamicOps<?>>, CodecAdapter<?, ?, ?>> opsToAdapter = new HashMap<>();

    @ApiStatus.Experimental
    public static void registerCodecAdapter(CodecAdapter<?, ?, ?> adapter) {
        if (serializerToAdapter.containsKey(adapter.serializerClass())) {
            throw new IllegalStateException("Serializer class " + adapter.serializerClass().getSimpleName() + " is already managed by a different codec adapter");
        }
        if (deserializerToAdapter.containsKey(adapter.deserializerClass())) {
            throw new IllegalStateException("Deserializer class " + adapter.deserializerClass().getSimpleName() + " is already managed by a different codec adapter");
        }
        if (opsToAdapter.containsKey(adapter.opsClass())) {
            throw new IllegalStateException("DynamicOps class " + adapter.opsClass().getSimpleName() + " is already managed by a different codec adapter");
        }

        serializerToAdapter.put(adapter.serializerClass(), adapter);
        deserializerToAdapter.put(adapter.deserializerClass(), adapter);
        opsToAdapter.put(adapter.opsClass(), adapter);
    }

    private static <T> DynamicOps<T> unpackOps(DynamicOps<T> ops) {
        var rootOps = ops;
        while (rootOps instanceof ForwardingDynamicOps<T>) rootOps = ((ForwardingDynamicOpsAccessor<T>) rootOps).owo$delegate();
        return rootOps;
    }

    private static <T> Serializer<T> unpackSerializer(Serializer<T> serializer) {
        var rootSerializer = serializer;
        while (rootSerializer instanceof ForwardingSerializer<T> forwardingSerializer) rootSerializer = forwardingSerializer.delegate();
        return rootSerializer;
    }

    private static <T> Deserializer<T> unpackDeserializer(Deserializer<T> deserializer) {
        var rootDeserializer = deserializer;
        while (rootDeserializer instanceof ForwardingDeserializer<T> forwardingDeserializer) rootDeserializer = forwardingDeserializer.delegate();
        return rootDeserializer;
    }

    @Nullable
    @SuppressWarnings("unchecked")
    private static <T, S extends SelfDescribedSerializer<T>> Pair<DynamicOps<T>, CodecAdapter<T, S, ?>> getOpsAndAdapter(Serializer<T> serializer, SerializationContext ctx) {
        var adapter = (CodecAdapter<T, S, ?>) serializerToAdapter.get(serializer.getClass());
        if (adapter == null) return null;

        DynamicOps<T> ops = DynamicOpsWithContext.of(ctx, adapter.getOps());
        if (ctx.hasAttribute(RegistriesAttribute.REGISTRIES)) {
            ops = RegistryOps.of(ops, ctx.getAttributeValue(RegistriesAttribute.REGISTRIES).infoGetter());
        }

        return new Pair<>(ops, adapter);
    }

    @Nullable
    @SuppressWarnings("unchecked")
    private static <T, D extends SelfDescribedDeserializer<T>> Pair<DynamicOps<T>, CodecAdapter<T, ?, D>> getOpsAndAdapter(Deserializer<T> deserializer, SerializationContext ctx) {
        var adapter = (CodecAdapter<T, ?, D>) deserializerToAdapter.get(deserializer.getClass());
        if (adapter == null) return null;

        DynamicOps<T> ops = DynamicOpsWithContext.of(ctx, adapter.getOps());
        if (ctx.hasAttribute(RegistriesAttribute.REGISTRIES)) {
            ops = RegistryOps.of(ops, ctx.getAttributeValue(RegistriesAttribute.REGISTRIES).infoGetter());
        }

        return new Pair<>(ops, adapter);
    }

    @Nullable
    @SuppressWarnings({"unchecked", "SuspiciousMethodCalls"})
    private static <T> Serializer<T> serializerForOps(DynamicOps<T> dynamicOps) {
        var adapter = (CodecAdapter<T, SelfDescribedSerializer<T>, ?>) opsToAdapter.get(unpackOps(dynamicOps).getClass());
        return adapter != null ? adapter.createSerializer() : null;
    }

    @Nullable
    @SuppressWarnings({"unchecked", "SuspiciousMethodCalls"})
    private static <T> Deserializer<T> deserializerForValue(DynamicOps<T> dynamicOps, T value) {
        var adapter = (CodecAdapter<T, ?, SelfDescribedDeserializer<T>>) opsToAdapter.get(unpackOps(dynamicOps).getClass());
        return (adapter != null) ? adapter.createDeserializer(value) : null;
    }

    @Nullable
    @SuppressWarnings({"unchecked", "SuspiciousMethodCalls"})
    private static <T> Pair<Serializer<T>, Function<T, RecordBuilder<T>>> serializerForRecordBuilder(DynamicOps<T> dynamicOps, RecordBuilder<T> builder) {
        var adapter = (CodecAdapter<T, SelfDescribedSerializer<T>, ?>) opsToAdapter.get(unpackOps(dynamicOps).getClass());

        return (adapter != null)
            ? new Pair<>(adapter.createSerializer(), t -> adapter.addToBuilder(t, builder))
            : null;
    }

    @Nullable
    @SuppressWarnings({"unchecked", "SuspiciousMethodCalls"})
    private static <T> Deserializer<T> deserializerForMapLike(DynamicOps<T> dynamicOps, MapLike<T> mapLike) {
        var adapter = (CodecAdapter<T, ?, SelfDescribedDeserializer<T>>) opsToAdapter.get(unpackOps(dynamicOps).getClass());

        return (adapter != null)
            ? adapter.createDeserializer(adapter.unpackMapLike(mapLike))
            : null;
    }

    //--

    private static <T, S extends SelfDescribedSerializer<T>> void encodeValue(CodecAdapter<T, S, ?> adapter, S serializer, T value) {
        adapter.createDeserializer(value).readAny(SerializationContext.empty(), serializer);
    }

    private static <T, D extends SelfDescribedDeserializer<T>> T copyDecodedValue(CodecAdapter<T, ?, D> adapter, D deserializer) {
        var serializer = adapter.createSerializer();
        deserializer.readAny(SerializationContext.empty(), serializer);
        return serializer.result();
    }

    private static <T, V, S extends SelfDescribedSerializer<T>> void encodeStruct(CodecAdapter<T, S, ?> adapter, DynamicOps<T> ops, S serializer, Serializer.Struct struct, MapCodec<V> mapCodec, V value) {
        var formatValue = mapCodec.encode(value, ops, ops.mapBuilder()).build(ops.emptyMap()).getOrThrow();
        adapter.encodeStruct(SerializationContext.empty(), serializer, struct, formatValue);
    }

    private static <T, V, D extends SelfDescribedDeserializer<T>> V decodeStruct(CodecAdapter<T, ?, D> adapter, DynamicOps<T> ops, D deserializer, Deserializer.Struct struct, MapCodec<V> mapCodec) {
        var formatValue = adapter.copyDecodedStruct(SerializationContext.empty(), deserializer, struct);
        return mapCodec.decode(ops, ops.getMap(formatValue).getOrThrow()).getOrThrow();
    }

    public interface CodecAdapter<T, S extends SelfDescribedSerializer<T>, D extends SelfDescribedDeserializer<T>> {
        Class<? extends Serializer<T>> serializerClass();
        Class<? extends Deserializer<T>> deserializerClass();
        Class<? extends DynamicOps<T>> opsClass();

        // ---

        S createSerializer();
        D createDeserializer(T value);
        DynamicOps<T> getOps();

        // ---

        T unpackMapLike(MapLike<T> mapLike);
        RecordBuilder<T> addToBuilder(T value, RecordBuilder<T> builder);

        // ---

        void encodeStruct(SerializationContext ctx, S serializer, Serializer.Struct struct, T value);
        T copyDecodedStruct(SerializationContext ctx, D serializer, Deserializer.Struct struct);
    }

    static {
        registerCodecAdapter(new CodecAdapter<NbtElement, NbtSerializer, NbtDeserializer>() {
            @Override
            public Class<? extends Serializer<NbtElement>> serializerClass() {
                return NbtSerializer.class;
            }

            @Override
            public Class<? extends Deserializer<NbtElement>> deserializerClass() {
                return NbtDeserializer.class;
            }

            @Override
            public Class<? extends DynamicOps<NbtElement>> opsClass() {
                return NbtOps.class;
            }

            @Override
            public NbtSerializer createSerializer() {
                return NbtSerializer.of();
            }

            @Override
            public NbtDeserializer createDeserializer(NbtElement value) {
                return NbtDeserializer.of(value);
            }

            @Override
            public DynamicOps<NbtElement> getOps() {
                return NbtOps.INSTANCE;
            }

            @Override
            public NbtElement unpackMapLike(MapLike<NbtElement> mapLike) {
                var compound = new NbtCompound();

                mapLike.entries().forEach(pairs -> {
                    var key = pairs.getFirst();
                    var value = pairs.getSecond();

                    if (!(key instanceof NbtString primitive)) {
                        throw new IllegalStateException("Unable to parse key: " + key);
                    }

                    compound.put(primitive.asString(), value);
                });

                return compound;
            }

            @Override
            public RecordBuilder<NbtElement> addToBuilder(NbtElement value, RecordBuilder<NbtElement> builder) {
                if (!(value instanceof NbtCompound compoundTag)) {
                    throw new IllegalStateException("Cannot add non-NbtCompound value into record builder: " + value);
                }

                var result = builder;
                for (var key : compoundTag.getKeys()) {
                    result = result.add(key, compoundTag.get(key));
                }

                return result;
            }

            @Override
            public void encodeStruct(SerializationContext ctx, NbtSerializer serializer, Serializer.Struct struct, NbtElement value) {
                if (!(value instanceof NbtCompound compoundTag)) {
                    throw new IllegalStateException("Cannot encode non-NbtCompound value as struct: " + value);
                }

                compoundTag.getKeys().forEach(key -> struct.field(key, ctx, NbtEndec.ELEMENT, compoundTag.get(key)));
            }

            @Override
            public NbtElement copyDecodedStruct(SerializationContext ctx, NbtDeserializer deserializer, Deserializer.Struct struct) {
                return NbtEndec.COMPOUND.decode(ctx, deserializer);
            }
        });

        registerCodecAdapter(new CodecAdapter<JsonElement, GsonSerializer, GsonDeserializer>() {
            @Override
            public Class<? extends Serializer<JsonElement>> serializerClass() {
                return GsonSerializer.class;
            }

            @Override
            public Class<? extends Deserializer<JsonElement>> deserializerClass() {
                return GsonDeserializer.class;
            }

            @Override
            public Class<? extends DynamicOps<JsonElement>> opsClass() {
                return JsonOps.class;
            }

            @Override
            public GsonSerializer createSerializer() {
                return GsonSerializer.of();
            }

            @Override
            public GsonDeserializer createDeserializer(JsonElement value) {
                return GsonDeserializer.of(value);
            }

            @Override
            public DynamicOps<JsonElement> getOps() {
                return JsonOps.INSTANCE;
            }

            @Override
            public JsonElement unpackMapLike(MapLike<JsonElement> mapLike) {
                var jsonObject = new JsonObject();

                mapLike.entries().forEach(pairs -> {
                    var key = pairs.getFirst();
                    var value = pairs.getSecond();

                    if (!(key instanceof JsonPrimitive primitive && primitive.isString())) {
                        throw new IllegalStateException("Unable to parse key: " + key);
                    }

                    jsonObject.add(primitive.getAsString(), value);
                });

                return jsonObject;
            }

            @Override
            public RecordBuilder<JsonElement> addToBuilder(JsonElement value, RecordBuilder<JsonElement> builder) {
                if (!(value instanceof JsonObject jsonObject)) {
                    throw new IllegalStateException("Cannot add non-JsonObject value into record builder: " + value);
                }

                var result = builder;
                for (var entry : jsonObject.asMap().entrySet()) {
                    result = result.add(entry.getKey(), entry.getValue());
                }

                return result;
            }

            @Override
            public void encodeStruct(SerializationContext ctx, GsonSerializer serializer, Serializer.Struct struct, JsonElement value) {
                if (!(value instanceof JsonObject jsonObject)) {
                    throw new IllegalStateException("Cannot encode non-JsonObject value as struct: " + value);
                }

                jsonObject.asMap().forEach((key, element) -> struct.field(key, ctx, GsonEndec.INSTANCE, element));
            }

            @Override
            public JsonElement copyDecodedStruct(SerializationContext ctx, GsonDeserializer serializer, Deserializer.Struct struct) {
                return GsonEndec.INSTANCE.decode(ctx, serializer);
            }
        });
    }
}