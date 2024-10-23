package io.wispforest.owo.config;

import blue.endless.jankson.Jankson;
import blue.endless.jankson.JsonElement;
import blue.endless.jankson.JsonGrammar;
import blue.endless.jankson.JsonPrimitive;
import blue.endless.jankson.api.DeserializationException;
import blue.endless.jankson.api.SyntaxError;
import blue.endless.jankson.impl.POJODeserializer;
import blue.endless.jankson.magic.TypeMagic;
import io.wispforest.endec.impl.ReflectiveEndecBuilder;
import io.wispforest.owo.Owo;
import io.wispforest.owo.config.annotation.*;
import io.wispforest.owo.config.ui.ConfigScreen;
import io.wispforest.owo.config.ui.ConfigScreenProviders;
import io.wispforest.owo.serialization.endec.MinecraftEndecs;
import io.wispforest.owo.ui.core.Color;
import io.wispforest.owo.util.NumberReflection;
import io.wispforest.owo.util.Observable;
import io.wispforest.owo.util.ReflectionUtils;
import net.minecraft.util.Identifier;
import net.neoforged.api.distmarker.Dist;
import net.neoforged.fml.loading.FMLLoader;
import net.neoforged.fml.loading.FMLPaths;
import org.jetbrains.annotations.Nullable;

import java.io.IOException;
import java.lang.invoke.MethodHandle;
import java.lang.invoke.MethodHandles;
import java.lang.reflect.Field;
import java.lang.reflect.Modifier;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.*;
import java.util.function.Consumer;
import java.util.function.Predicate;
import java.util.regex.Pattern;

/**
 * The common base class of all generated config classes.
 * The majority of all config functionality resides in here
 * <p>
 * Do not extend this class yourself - instead annotate
 * a class describing your config model with {@link Config},
 * just as you would do with other libraries like Cloth Config
 *
 * @see Config
 */
public abstract class ConfigWrapper<C> {

    private static final Map<String, Class<?>> KNOWN_CONFIG_CLASSES = new HashMap<>();

    protected final String name;
    protected final C instance;

    protected boolean loading = false;
    protected final Jankson jankson;

    @SuppressWarnings("rawtypes") protected final Map<Option.Key, Option> options = new LinkedHashMap<>();
    @SuppressWarnings("rawtypes") protected final Map<Option.Key, Option> optionsView = Collections.unmodifiableMap(options);

    protected final ReflectiveEndecBuilder builder;

    protected ConfigWrapper(Class<C> clazz) {
        this(clazz, builder -> {});
    }

    protected ConfigWrapper(Class<C> clazz, Consumer<Jankson.Builder> janksonBuilder) {
        this.builder = MinecraftEndecs.addDefaults(new ReflectiveEndecBuilder());

        ReflectionUtils.requireZeroArgsConstructor(clazz, s -> "Config model class " + s + " must provide a zero-args constructor");
        this.instance = ReflectionUtils.tryInstantiateWithNoArgs(clazz);

        var builder = Jankson.builder()
                .registerSerializer(Identifier.class, (identifier, marshaller) -> new JsonPrimitive(identifier.toString()))
                .registerDeserializer(JsonPrimitive.class, Identifier.class, (primitive, m) -> Identifier.tryParse(primitive.asString()))
                .registerSerializer(Color.class, (color, marshaller) -> new JsonPrimitive(color.asHexString(true)))
                .registerDeserializer(JsonPrimitive.class, Color.class, (primitive, m) -> Color.ofArgb(Integer.parseUnsignedInt(primitive.asString().substring(1), 16)));
        janksonBuilder.accept(builder);
        this.jankson = builder.build();

        var configAnnotation = clazz.getAnnotation(Config.class);
        this.name = configAnnotation.name();

        if (KNOWN_CONFIG_CLASSES.put(this.name, this.getClass()) != null) {
            throw new IllegalStateException("Config name '" + this.name + "'"
                    + " is already taken an by instance of class '" + KNOWN_CONFIG_CLASSES.get(this.name).getName() + "'");
        }

        if (FMLLoader.getDist() == Dist.CLIENT && clazz.isAnnotationPresent(Modmenu.class)) {
            var modmenuAnnotation = clazz.getAnnotation(Modmenu.class);
            ConfigScreenProviders.registerOwoConfigScreen(
                    modmenuAnnotation.modId(),
                    screen -> ConfigScreen.createWithCustomModel(Identifier.of(modmenuAnnotation.uiModelId()), this, screen)
            );
        }

        try {
            this.initializeOptions(configAnnotation.saveOnModification());
            for (var option : this.options.values()) {
                if (option.syncMode().isNone()) continue;

                ConfigSynchronizer.register(this);
                break;
            }
        } catch (IllegalAccessException | NoSuchMethodException e) {
            throw new RuntimeException("Failed to initialize config " + this.name, e);
        }
    }

    /**
     * Save the config represented by this wrapper
     */
    public void save() {
        if (this.loading) return;

        try {
            this.fileLocation().getParent().toFile().mkdirs();
            Files.writeString(this.fileLocation(), this.jankson.toJson(this.instance).toJson(JsonGrammar.JANKSON), StandardCharsets.UTF_8);
        } catch (IOException e) {
            Owo.LOGGER.warn("Could not save config {}", this.name, e);
        }
    }

    /**
     * Load the config represented by this wrapper from
     * its associated file, or create it if it does not exist
     */
    @SuppressWarnings({"unchecked"})
    public void load() {
        if (!Files.exists(this.fileLocation())) {
            this.save();
            return;
        }

        try {
            this.loading = true;
            var configObject = this.jankson.load(Files.readString(this.fileLocation(), StandardCharsets.UTF_8));

            for (var option : this.options.values()) {
                Object newValue;

                final var clazz = option.clazz();
                final var element = configObject.recursiveGet(JsonElement.class, option.key().asString());
                if (element == null) {
                    option.set(option.defaultValue());
                    continue;
                }

                if (Map.class.isAssignableFrom(clazz)) {
                    var field = option.backingField().field();

                    newValue = TypeMagic.createAndCast(clazz);
                    POJODeserializer.unpackMap(
                            (Map<Object, Object>) newValue,
                            ReflectionUtils.getTypeArgument(field.getGenericType(), 0),
                            ReflectionUtils.getTypeArgument(field.getGenericType(), 1),
                            element,
                            this.jankson.getMarshaller()
                    );
                } else if (List.class.isAssignableFrom(clazz) || Set.class.isAssignableFrom(clazz)) {
                    newValue = TypeMagic.createAndCast(clazz);
                    POJODeserializer.unpackCollection(
                            (Collection<Object>) newValue,
                            ReflectionUtils.getTypeArgument(option.backingField().field().getGenericType(), 0),
                            element,
                            this.jankson.getMarshaller()
                    );
                } else {
                    newValue = configObject.getMarshaller().marshall(clazz, element);
                }

                if (!option.verifyConstraint(newValue)) continue;

                option.set(newValue == null ? option.defaultValue() : newValue);
            }
        } catch (IOException | SyntaxError | DeserializationException e) {
            Owo.LOGGER.warn("Could not load config {}", this.name, e);
        } finally {
            this.loading = false;
        }
    }

    /**
     * Query the field associated with a given key. This is relevant
     * in cases where said field is annotated with {@link Nest}, meaning
     * that {@link #optionForKey(Option.Key)} would return {@code null}
     * because the field won't be treated as an option in itself.
     *
     * @param key The for which to query the field
     * @return The field described by {@code key}, or {@code null}
     * if it does not point to a valid field in the config tree
     */
    public @Nullable Field fieldForKey(Option.Key key) {
        try {
            var path = new ArrayList<>(List.of(key.path()));
            var clazz = this.instance.getClass();

            while (path.size() > 1) {
                clazz = clazz.getDeclaredField(path.remove(0)).getType();
            }

            return clazz.getField(path.get(0));
        } catch (NoSuchFieldException e) {
            return null;
        }
    }

    /**
     * @return The name of this config, used for translation
     * keys and the filename
     */
    public String name() {
        return this.name;
    }

    /**
     * @return The location to which this config is saved
     */
    public Path fileLocation() {
        return FMLLoader.getGamePath().resolve(FMLPaths.CONFIGDIR.relative()).resolve(this.name + ".json5");
    }

    /**
     * Query the config option associated with a given key
     *
     * @param key The key for which to query the option
     * @return The option described by {@code key}, or {@code null}
     * if no such option exists
     */
    @SuppressWarnings("unchecked")
    public <T> @Nullable Option<T> optionForKey(Option.Key key) {
        return this.options.get(key);
    }

    /**
     * @return A view of all options contained in this config
     */
    @SuppressWarnings("unchecked")
    public Map<Option.Key, Option<?>> allOptions() {
        return (Map<Option.Key, Option<?>>) (Object) this.optionsView;
    }

    /**
     * Execute the given action once for each option in this config
     */
    public void forEachOption(Consumer<Option<?>> action) {
        for (var option : this.options.values()) {
            action.accept(option);
        }
    }

    private void initializeOptions(boolean hookSave) throws IllegalAccessException, NoSuchMethodException {
        var fields = new LinkedHashMap<Option.Key, Option.BoundField<Object>>();
        collectFieldValues(Option.Key.ROOT, this.instance, fields);

        var instanceSyncMode = this.instance.getClass().isAnnotationPresent(Sync.class)
                ? this.instance.getClass().getAnnotation(Sync.class).value()
                : Option.SyncMode.NONE;

        for (var entry : fields.entrySet()) {
            var key = entry.getKey();
            var boundField = entry.getValue();

            var field = boundField.field();
            var fieldType = field.getType();

            Constraint constraint = null;
            if (field.isAnnotationPresent(RangeConstraint.class)) {
                var annotation = field.getAnnotation(RangeConstraint.class);

                if (NumberReflection.isNumberType(fieldType)) {
                    Predicate<?> predicate;
                    if (fieldType == long.class || fieldType == Long.class) {
                        predicate = o -> o != null && (Long) o >= annotation.min() && (Long) o <= annotation.max();
                    } else {
                        predicate = o -> o != null && ((Number) o).doubleValue() >= annotation.min() && ((Number) o).doubleValue() <= annotation.max();
                    }

                    constraint = new Constraint("Range from " + annotation.min() + " to " + annotation.max(), predicate);
                } else {
                    throw new IllegalStateException("@RangeConstraint can only be applied to numeric fields");
                }
            }

            if (field.isAnnotationPresent(RegexConstraint.class)) {
                var annotation = field.getAnnotation(RegexConstraint.class);

                if (CharSequence.class.isAssignableFrom(fieldType)) {
                    var pattern = Pattern.compile(annotation.value());
                    constraint = new Constraint("Regex " + annotation.value(), o -> o != null && pattern.matcher((CharSequence) o).matches());
                } else {
                    throw new IllegalStateException("@RegexConstraint can only be applied to fields with a string representation");
                }
            }

            if (field.isAnnotationPresent(PredicateConstraint.class)) {
                var annotation = field.getAnnotation(PredicateConstraint.class);
                var method = boundField.owner().getClass().getMethod(annotation.value(), fieldType);

                if (method.getReturnType() != boolean.class) {
                    throw new NoSuchMethodException("Return type of predicate implementation '" + annotation.value() + "' must be 'boolean'");
                }

                if (!Modifier.isStatic(method.getModifiers())) {
                    throw new IllegalStateException("Predicate implementation '" + annotation.value() + "' must be static");
                }

                var handle = MethodHandles.publicLookup().unreflect(method);
                constraint = new Constraint("Predicate method " + annotation.value(), o -> this.invokePredicate(handle, o));
            }

            final var defaultValue = boundField.getValue();

            final var observable = Observable.of(defaultValue);
            if (hookSave) observable.observe(o -> this.save());

            var syncMode = instanceSyncMode;
            if (field.isAnnotationPresent(Sync.class)) {
                syncMode = field.getAnnotation(Sync.class).value();
            } else {
                var parentKey = key.parent();
                while (!parentKey.isRoot()) {
                    var parentField = this.fieldForKey(parentKey);
                    if (parentField.isAnnotationPresent(Sync.class)) {
                        syncMode = parentField.getAnnotation(Sync.class).value();
                    }

                    parentKey = parentKey.parent();
                }
            }

            this.options.put(key, new Option<>(this.name, key, defaultValue, observable, boundField, constraint, syncMode, this.builder));
        }
    }

    private void collectFieldValues(Option.Key parent, Object instance, Map<Option.Key, Option.BoundField<Object>> fields) throws IllegalAccessException {
        for (var field : instance.getClass().getDeclaredFields()) {
            if (Modifier.isTransient(field.getModifiers()) || Modifier.isStatic(field.getModifiers())) continue;

            if (field.isAnnotationPresent(Nest.class)) {
                var fieldValue = field.get(instance);
                if (fieldValue != null) {
                    this.collectFieldValues(parent.child(field.getName()), fieldValue, fields);
                } else {
                    throw new IllegalStateException("Nested config option containers must never be null");
                }
            } else {
                fields.put(parent.child(field.getName()), new Option.BoundField<>(instance, field));
            }
        }
    }

    private boolean invokePredicate(MethodHandle predicate, Object value) {
        try {
            return (boolean) predicate.invoke(value);
        } catch (Throwable e) {
            throw new RuntimeException("Could not invoke predicate", e);
        }
    }

    @SuppressWarnings({"rawtypes", "unchecked"})
    public record Constraint(String formatted, Predicate predicate) {
        public boolean test(Object value) {
            return this.predicate.test(value);
        }
    }

}
