package io.wispforest.owo.serialization.format.edm;

import com.mojang.datafixers.DataFixUtils;
import com.mojang.datafixers.util.Pair;
import com.mojang.serialization.DataResult;
import com.mojang.serialization.DynamicOps;
import io.wispforest.endec.SerializationContext;
import io.wispforest.endec.format.edm.EdmElement;
import io.wispforest.owo.serialization.format.ContextHolder;

import java.nio.ByteBuffer;
import java.util.*;
import java.util.stream.Collectors;
import java.util.stream.Stream;

public class EdmOps implements DynamicOps<EdmElement<?>>, ContextHolder {

    private static final EdmOps NO_CONTEXT = new EdmOps(SerializationContext.empty());

    private final SerializationContext capturedContext;
    private EdmOps(SerializationContext capturedContext) {
        this.capturedContext = capturedContext;
    }

    public static EdmOps withContext(SerializationContext context) {
        return new EdmOps(context);
    }

    public static EdmOps withoutContext() {
        return NO_CONTEXT;
    }

    @Override
    public SerializationContext capturedContext() {
        return this.capturedContext;
    }

    // --- Serialization ---

    @Override
    public EdmElement<?> empty() {
        return EdmElement.EMPTY;
    }

    public EdmElement<?> createNumeric(Number number) {
        return EdmElement.f64(number.doubleValue());
    }

    public EdmElement<?> createByte(byte b) {
        return EdmElement.i8(b);
    }

    public EdmElement<?> createShort(short s) {
        return EdmElement.i16(s);
    }

    public EdmElement<?> createInt(int i) {
        return EdmElement.i32(i);
    }

    public EdmElement<?> createLong(long l) {
        return EdmElement.i64(l);
    }

    public EdmElement<?> createFloat(float f) {
        return EdmElement.f32(f);
    }

    public EdmElement<?> createDouble(double d) {
        return EdmElement.f64(d);
    }

    // ---

    public EdmElement<?> createBoolean(boolean bl) {
        return EdmElement.bool(bl);
    }

    @Override
    public EdmElement<?> createString(String value) {
        return EdmElement.string(value);
    }

    @Override
    public EdmElement<?> createByteList(ByteBuffer input) {
        return EdmElement.bytes(DataFixUtils.toArray(input));
    }

    // ---

    @Override
    public EdmElement<?> createList(Stream<EdmElement<?>> input) {
        return EdmElement.sequence(input.toList());
    }

    @Override
    public DataResult<EdmElement<?>> mergeToList(EdmElement<?> list, EdmElement<?> value) {
        if (list == empty()) {
            return DataResult.success(EdmElement.sequence(List.of(value)));
        } else if (list.value() instanceof List<?> properList) {
            var newList = new ArrayList<EdmElement<?>>((Collection<? extends EdmElement<?>>) properList);
            newList.add(value);

            return DataResult.success(EdmElement.sequence(newList));
        } else {
            return DataResult.error(() -> "Not a sequence: " + list);
        }
    }

    @Override
    public EdmElement<?> createMap(Stream<Pair<EdmElement<?>, EdmElement<?>>> map) {
        return EdmElement.consumeMap(map.collect(Collectors.toMap(pair -> pair.getFirst().cast(), Pair::getSecond)));
    }

    @Override
    public DataResult<EdmElement<?>> mergeToMap(EdmElement<?> map, EdmElement<?> key, EdmElement<?> value) {
        if (!(key.value() instanceof String)) {
            return DataResult.error(() -> "Key is not a string: " + key);
        }

        if (map == empty()) {
            return DataResult.success(EdmElement.consumeMap(Map.of(key.cast(), value)));
        } else if (map.value() instanceof Map<?, ?> properMap) {
            var newMap = new HashMap<String, EdmElement<?>>((Map<String, ? extends EdmElement<?>>) properMap);
            newMap.put(key.cast(), value);

            return DataResult.success(EdmElement.consumeMap(newMap));
        } else {
            return DataResult.error(() -> "Not a map: " + map);
        }
    }

    // --- Deserialization ---

    @Override
    public DataResult<Number> getNumberValue(EdmElement<?> input) {
        if (input.value() instanceof Number number) {
            return DataResult.success(number);
        } else {
            return DataResult.error(() -> "Not a number: " + input);
        }
    }

    @Override
    public DataResult<Boolean> getBooleanValue(EdmElement<?> input) {
        if (input.value() instanceof Boolean bl) {
            return DataResult.success(bl);
        } else if(input.value() instanceof Byte b) {
            return DataResult.success(b == 1);
        } else {
            return DataResult.error(() -> "Not a boolean: " + input);
        }
    }

    @Override
    public DataResult<String> getStringValue(EdmElement<?> input) {
        if (input.value() instanceof String string) {
            return DataResult.success(string);
        } else {
            return DataResult.error(() -> "Not a string: " + input);
        }
    }

    @Override
    public DataResult<ByteBuffer> getByteBuffer(EdmElement<?> input) {
        if (input.value() instanceof byte[] bytes) {
            return DataResult.success(ByteBuffer.wrap(bytes));
        } else {
            return DataResult.error(() -> "Not bytes: " + input);
        }
    }

    // ---

    @Override
    public DataResult<Stream<EdmElement<?>>> getStream(EdmElement<?> input) {
        if (input == this.empty()) {
            return DataResult.success(Stream.of());
        } else if (input.value() instanceof List<?> list) {
            return DataResult.success(list.stream().map(o -> (EdmElement<?>) o));
        } else {
            return DataResult.error(() -> "Not a sequence: " + input);
        }
    }

    @Override
    public DataResult<Stream<Pair<EdmElement<?>, EdmElement<?>>>> getMapValues(EdmElement<?> input) {
        if (input == this.empty()) {
            return DataResult.success(Stream.of());
        } else if (input.value() instanceof Map<?, ?> map) {
            //noinspection rawtypes
            return DataResult.success(map.entrySet().stream().map(entry -> new Pair(EdmElement.string((String) entry.getKey()), entry.getValue())));
        } else {
            return DataResult.error(() -> "Not a map: " + input);
        }
    }

    // ---

    @Override
    public <U> U convertTo(DynamicOps<U> outOps, EdmElement<?> input) {
        if (input == this.empty()) return outOps.empty();
        return switch (input.type()) { // TODO: DO WE NEED TO HANDLE Unsigned Numbers specifically here or nah?
            case I8, U8 -> outOps.createByte(input.cast());
            case I16, U16 -> outOps.createShort(input.cast());
            case I32, U32 -> outOps.createInt(input.cast());
            case I64, U64 -> outOps.createLong(input.cast());
            case F32 -> outOps.createFloat(input.cast());
            case F64 -> outOps.createDouble(input.cast());
            case BOOLEAN -> outOps.createBoolean(input.cast());
            case STRING -> outOps.createString(input.cast());
            case BYTES -> outOps.createByteList(ByteBuffer.wrap(input.cast()));
            case OPTIONAL -> input.<Optional<EdmElement<?>>>cast().map(element -> this.convertTo(outOps, element)).orElse(outOps.empty());
            case SEQUENCE -> outOps.createList(input.<List<EdmElement<?>>>cast().stream().map(element -> this.convertTo(outOps, element)));
            case MAP ->
                    outOps.createMap(input.<Map<String, EdmElement<?>>>cast().entrySet().stream().map(entry -> new Pair<>(outOps.createString(entry.getKey()), this.convertTo(outOps, entry.getValue()))));
        };
    }

    @Override
    public EdmElement<?> remove(EdmElement<?> input, String key) {
        if (input.value() instanceof Map<?, ?> map) {
            var newMap = new HashMap<String, EdmElement<?>>((Map<? extends String, ? extends EdmElement<?>>) map);
            newMap.remove(key);

            return EdmElement.consumeMap(newMap);
        } else {
            return input;
        }
    }
}
