package io.wispforest.owo.serialization.format.nbt;

import io.wispforest.endec.*;
import io.wispforest.endec.util.RecursiveDeserializer;
import net.minecraft.nbt.*;
import org.jetbrains.annotations.Nullable;

import java.util.*;
import java.util.function.Supplier;

public class NbtDeserializer extends RecursiveDeserializer<NbtElement> implements SelfDescribedDeserializer<NbtElement> {

    protected NbtDeserializer(NbtElement element) {
        super(element);
    }

    public static NbtDeserializer of(NbtElement element) {
        return new NbtDeserializer(element);
    }

    private <N extends NbtElement> N getAs(NbtElement element, Class<N> clazz) {
        if (clazz.isInstance(element)) {
            return clazz.cast(element);
        } else {
            throw new IllegalStateException("Expected a " + clazz.getSimpleName() + ", found a " + element.getClass().getSimpleName());
        }
    }

    // ---

    @Override
    public byte readByte(SerializationContext ctx) {
        return this.getAs(this.getValue(), NbtByte.class).byteValue();
    }

    @Override
    public short readShort(SerializationContext ctx) {
        return this.getAs(this.getValue(), NbtShort.class).shortValue();
    }

    @Override
    public int readInt(SerializationContext ctx) {
        return this.getAs(this.getValue(), NbtInt.class).intValue();
    }

    @Override
    public long readLong(SerializationContext ctx) {
        return this.getAs(this.getValue(), NbtLong.class).longValue();
    }

    @Override
    public float readFloat(SerializationContext ctx) {
        return this.getAs(this.getValue(), NbtFloat.class).floatValue();
    }

    @Override
    public double readDouble(SerializationContext ctx) {
        return this.getAs(this.getValue(), NbtDouble.class).doubleValue();
    }

    // ---

    @Override
    public int readVarInt(SerializationContext ctx) {
        return this.getAs(this.getValue(), AbstractNbtNumber.class).intValue();
    }

    @Override
    public long readVarLong(SerializationContext ctx) {
        return this.getAs(this.getValue(), AbstractNbtNumber.class).longValue();
    }

    // ---

    @Override
    public boolean readBoolean(SerializationContext ctx) {
        return this.getAs(this.getValue(), NbtByte.class).byteValue() != 0;
    }

    @Override
    public String readString(SerializationContext ctx) {
        return this.getAs(this.getValue(), NbtString.class).asString();
    }

    @Override
    public byte[] readBytes(SerializationContext ctx) {
        return this.getAs(this.getValue(), NbtByteArray.class).getByteArray();
    }

    private final Set<IdentityHolder<NbtElement>> encodedOptionals = Collections.newSetFromMap(new WeakHashMap<>());

    @Override
    public <V> Optional<V> readOptional(SerializationContext ctx, Endec<V> endec) {
        var value = this.getValue();
        if (this.encodedOptionals.contains(new IdentityHolder<>(value))) {
            return Optional.of(endec.decode(ctx, this));
        }

        var struct = this.struct();
        return struct.field("present", ctx, Endec.BOOLEAN)
                ? Optional.of(struct.field("value", ctx, endec))
                : Optional.empty();
    }

    // ---

    @Override
    public <E> Deserializer.Sequence<E> sequence(SerializationContext ctx, Endec<E> elementEndec) {
        //noinspection unchecked
        return new Sequence<>(ctx, elementEndec, this.getAs(this.getValue(), AbstractNbtList.class));
    }

    @Override
    public <V> Deserializer.Map<V> map(SerializationContext ctx, Endec<V> valueEndec) {
        return new Map<>(ctx, valueEndec, this.getAs(this.getValue(), NbtCompound.class));
    }

    @Override
    public Deserializer.Struct struct() {
        return new Struct(this.getAs(this.getValue(), NbtCompound.class));
    }

    // ---

    @Override
    public <S> void readAny(SerializationContext ctx, Serializer<S> visitor) {
        this.decodeValue(ctx, visitor, this.getValue());
    }

    private <S> void decodeValue(SerializationContext ctx, Serializer<S> visitor, NbtElement value) {
        switch (value.getType()) {
            case NbtElement.BYTE_TYPE -> visitor.writeByte(ctx, ((NbtByte) value).byteValue());
            case NbtElement.SHORT_TYPE -> visitor.writeShort(ctx, ((NbtShort) value).shortValue());
            case NbtElement.INT_TYPE -> visitor.writeInt(ctx, ((NbtInt) value).intValue());
            case NbtElement.LONG_TYPE -> visitor.writeLong(ctx, ((NbtLong) value).longValue());
            case NbtElement.FLOAT_TYPE -> visitor.writeFloat(ctx, ((NbtFloat) value).floatValue());
            case NbtElement.DOUBLE_TYPE -> visitor.writeDouble(ctx, ((NbtDouble) value).doubleValue());
            case NbtElement.STRING_TYPE -> visitor.writeString(ctx, value.asString());
            case NbtElement.BYTE_ARRAY_TYPE -> visitor.writeBytes(ctx, ((NbtByteArray) value).getByteArray());
            case NbtElement.INT_ARRAY_TYPE, NbtElement.LONG_ARRAY_TYPE, NbtElement.LIST_TYPE -> {
                var list = (AbstractNbtList<?>) value;
                try (var sequence = visitor.sequence(ctx, Endec.<NbtElement>of(this::decodeValue, (ctx1, deserializer) -> null), list.size())) {
                    list.forEach(sequence::element);
                }
            }
            case NbtElement.COMPOUND_TYPE -> {
                var compound = (NbtCompound) value;
                try (var map = visitor.map(ctx, Endec.<NbtElement>of(this::decodeValue, (ctx1, deserializer) -> null), compound.getSize())) {
                    for (var key : compound.getKeys()) {
                        map.entry(key, compound.get(key));
                    }
                }
            }
            default ->
                    throw new IllegalArgumentException("Non-standard, unrecognized NbtElement implementation cannot be decoded");
        }
    }

    // ---

    private class Sequence<V> implements Deserializer.Sequence<V> {

        private final SerializationContext ctx;
        private final Endec<V> valueEndec;
        private final Iterator<NbtElement> elements;
        private final int size;

        private Sequence(SerializationContext ctx, Endec<V> valueEndec, List<NbtElement> elements) {
            this.ctx = ctx;
            this.valueEndec = valueEndec;

            this.elements = elements.iterator();
            this.size = elements.size();
        }

        @Override
        public int estimatedSize() {
            return this.size;
        }

        @Override
        public boolean hasNext() {
            return this.elements.hasNext();
        }

        @Override
        public V next() {
            return NbtDeserializer.this.frame(
                    this.elements::next,
                    () -> this.valueEndec.decode(this.ctx, NbtDeserializer.this)
            );
        }
    }

    private class Map<V> implements Deserializer.Map<V> {

        private final SerializationContext ctx;
        private final Endec<V> valueEndec;
        private final NbtCompound compound;
        private final Iterator<String> keys;
        private final int size;

        private Map(SerializationContext ctx, Endec<V> valueEndec, NbtCompound compound) {
            this.ctx = ctx;
            this.valueEndec = valueEndec;

            this.compound = compound;
            this.keys = compound.getKeys().iterator();
            this.size = compound.getSize();
        }

        @Override
        public int estimatedSize() {
            return this.size;
        }

        @Override
        public boolean hasNext() {
            return this.keys.hasNext();
        }

        @Override
        public java.util.Map.Entry<String, V> next() {
            var key = this.keys.next();
            return NbtDeserializer.this.frame(
                    () -> this.compound.get(key),
                    () -> java.util.Map.entry(key, this.valueEndec.decode(this.ctx, NbtDeserializer.this))
            );
        }
    }

    public class Struct implements Deserializer.Struct {

        private final NbtCompound compound;

        public Struct(NbtCompound compound) {
            this.compound = compound;
        }

        @Override
        public <F> @Nullable F field(String name, SerializationContext ctx, Endec<F> endec, @Nullable Supplier<F> defaultValueFactory) {
            if (!this.compound.contains(name)) {
                if (defaultValueFactory == null) {
                    throw new IllegalStateException("Field '" + name + "' was missing from serialized data, but no default value was provided");
                }

                return defaultValueFactory.get();
            }
            var element = this.compound.get(name);
            if (defaultValueFactory != null) NbtDeserializer.this.encodedOptionals.add(new IdentityHolder<>(element));
            return NbtDeserializer.this.frame(
                    () -> element,
                    () -> endec.decode(ctx, NbtDeserializer.this)
            );
        }
    }
}
