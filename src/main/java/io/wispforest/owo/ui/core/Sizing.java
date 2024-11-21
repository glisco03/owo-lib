package io.wispforest.owo.ui.core;

import io.wispforest.owo.ui.parsing.UIModelParsingException;
import net.minecraft.util.math.MathHelper;
import org.w3c.dom.Element;

import java.util.Locale;
import java.util.Objects;
import java.util.Random;
import java.util.function.Function;

public class Sizing implements Animatable<Sizing> {

    private static final Sizing CONTENT_SIZING = new Sizing(0, Method.CONTENT);

    public final Method method;
    public final int value;

    private Sizing(int value, Method method) {
        this.method = method;
        this.value = value;
    }

    /**
     * Inflate into the given space
     *
     * @param space               The available space
     * @param contentSizeFunction A function for making the component set the
     *                            size based on its content
     */
    public int inflate(int space, Function<Sizing, Integer> contentSizeFunction) {
        return switch (this.method) {
            case FIXED -> this.value;
            case FILL, EXPAND -> Math.round((this.value / 100f) * space);
            case CONTENT -> contentSizeFunction.apply(this) + this.value * 2;
        };
    }

    public static Sizing fixed(int value) {
        return new Sizing(value, Method.FIXED);
    }

    /**
     * Dynamically size the component based on its content,
     * without any padding
     */
    public static Sizing content() {
        return CONTENT_SIZING;
    }

    /**
     * Dynamically size the component based on its content
     *
     * @param padding Padding to add onto the size of the content
     */
    public static Sizing content(int padding) {
        return new Sizing(padding, Method.CONTENT);
    }

    /**
     * Dynamically size the component to fill the available space
     */
    public static Sizing fill() {
        return fill(100);
    }

    /**
     * Dynamically size the component based on the available space
     *
     * @param percent How many percent of the available space to take up
     */
    public static Sizing fill(int percent) {
        return new Sizing(percent, Method.FILL);
    }

    /**
     * Dynamically size the component based on the remaining space
     * <i>after all other components have been laid out</i>
     */
    public static Sizing expand() {
        return expand(100);
    }

    /**
     * Dynamically size the component based on the remaining space
     * <i>after all other components have been laid out</i>
     *
     * @param percent How many percent of the available space to take up
     */
    public static Sizing expand(int percent) {
        return new Sizing(percent, Method.EXPAND);
    }

    /**
     * A collection of utility methods for generating random sizing instances
     *
     * @author chyzman
     */
    public static class Random {
        private static final java.util.Random SIZING_RANDOM = new java.util.Random();

        /**
         * Generate a random fill sizing instance with a value between {@code min} and {@code max}
         *
         * @param min The minimum value
         * @param max The maximum value
         * @return A random sizing instance
         */
        public static Sizing fill(int min, int max) {
            return Sizing.fill(SIZING_RANDOM.nextInt(min, max));
        }

        /**
         * Generate a random fill sizing instance with a value between 0 and {@code max}
         *
         * @param max The maximum value
         * @return A random sizing instance
         */
        public static Sizing fill(int max) {
            return Sizing.fill(SIZING_RANDOM.nextInt(max));
        }

        /**
         * Generate a random fill sizing instance with a value between 0 and 100
         *
         * @return A random sizing instance
         */
        public static Sizing fill() {
            return Sizing.fill(SIZING_RANDOM.nextInt(100));
        }

        /**
         * Generate a random expand sizing instance with a value between {@code min} and {@code max}
         *
         * @param min The minimum value
         * @param max The maximum value
         * @return A random sizing instance
         */
        public static Sizing expand(int min, int max) {
            return Sizing.expand(SIZING_RANDOM.nextInt(min, max));
        }

        /**
         * Generate a random expand sizing instance with a value between 0 and {@code max}
         *
         * @param max The maximum value
         * @return A random sizing instance
         */
        public static Sizing expand(int max) {
            return Sizing.expand(SIZING_RANDOM.nextInt(max));
        }

        /**
         * Generate a random expand sizing instance with a value between 0 and 100
         *
         * @return A random sizing instance
         */
        public static Sizing expand() {
            return Sizing.expand(SIZING_RANDOM.nextInt(100));
        }

        /**
         * Generate a random fixed sizing instance with a value between {@code min} and {@code max}
         *
         * @param min The minimum value
         * @param max The maximum value
         * @return A random sizing instance
         */
        public static Sizing fixed(int min, int max) {
            return Sizing.fixed(SIZING_RANDOM.nextInt(min, max));
        }

        /**
         * Generate a random fixed sizing instance with a value between 0 and {@code max}
         *
         * @param max The maximum value
         * @return A random sizing instance
         */
        public static Sizing fixed(int max) {
            return Sizing.fixed(SIZING_RANDOM.nextInt(max));
        }

        /**
         * Generate a random content sizing instance with a padding value between {@code min} and {@code max}
         *
         * @param min The minimum value
         * @param max The maximum value
         * @return A random sizing instance
         */
        public static Sizing content(int min, int max) {
            return Sizing.content(SIZING_RANDOM.nextInt(min, max));
        }

        /**
         * Generate a random content sizing instance with a padding value between 0 and {@code max}
         *
         * @param max The maximum value
         * @return A random sizing instance
         */
        public static Sizing content(int max) {
            return Sizing.content(SIZING_RANDOM.nextInt(max));
        }

        /**
         * Generate a random content sizing instance with a padding value between 0 and 100
         *
         * @return A random sizing instance
         */
        public static Sizing content() {
            return Sizing.content(SIZING_RANDOM.nextInt(100));
        }

        /**
         * Generate a random sizing instance with a value between {@code min} and {@code max}
         *
         * @param min The minimum value
         * @param max The maximum value
         * @return A random sizing instance
         * @apiNote May crash if put on a component that doesn't support content sizing
         */
        public static Sizing random(int min, int max) {
            return switch (SIZING_RANDOM.nextInt(3)) {
                case 0 -> fill(min, max);
                case 1 -> expand(min, max);
                case 2 -> content(min, max);
                default -> throw new IllegalStateException("Unexpected value: " + SIZING_RANDOM.nextInt(3));
            };
        }

        /**
         * Generate a random sizing instance with a value between 0 and {@code max}
         *
         * @param max The maximum value
         * @return A random sizing instance
         * @apiNote May crash if put on a component that doesn't support content sizing
         */
        public static Sizing random(int max) {
            return random(max);
        }

        /**
         * Generate a random sizing instance with a value between 0 and 100
         *
         * @return A random sizing instance
         * @apiNote May crash if put on a component that doesn't support content sizing
         */
        public static Sizing random() {
            return random(100);
        }

        /**
         * Generate a random sizing instance with a value between {@code min} and {@code max}
         * that is not content-based
         *
         * @param min The minimum value
         * @param max The maximum value
         * @return A random sizing instance
         */
        public static Sizing noContent(int min, int max) {
            return switch (SIZING_RANDOM.nextInt(2)) {
                case 0 -> fill(min, max);
                case 1 -> expand(min, max);
                default -> throw new IllegalStateException("Unexpected value: " + SIZING_RANDOM.nextInt(2));
            };
        }

        /**
         * Generate a random sizing instance with a value between 0 and {@code max}
         * that is not content-based
         *
         * @param max The maximum value
         * @return A random sizing instance
         */
        public static Sizing noContent(int max) {
            return noContent(max);
        }

        /**
         * Generate a random sizing instance that is not content-based
         *
         * @return A random sizing instance
         */
        public static Sizing noContent() {
            return noContent(100);
        }
    }

    /**
     * @return {@code true} if this sizing instance
     * uses the {@linkplain Method#CONTENT CONTENT} method
     */
    public boolean isContent() {
        return this.method == Method.CONTENT;
    }

    /**
     * @return {@code true} if this sizing instance
     * uses the {@linkplain Method#EXPAND EXPAND} method
     */
    public boolean isExpand() {
        return this.method == Method.EXPAND;
    }

    /**
     * The content factor of a sizing instance describes where
     * on the spectrum from content to fixed sizing it sits. Specifically, this is
     * used to lerp the reference frame used for calculating {@code fill(...)} sizing
     * on children between the available space in this component (content factor 0)
     * and this component's own available space (content factor 1), both of which can be
     * independently determined prior to layout calculations
     */
    public float contentFactor() {
        return this.isContent() ? 1f : 0f;
    }

    @Override
    public Sizing interpolate(Sizing next, float delta) {
        if (next.method != this.method) {
            return new MergedSizing(this, next, delta);
        } else {
            return new Sizing(MathHelper.lerp(delta, this.value, next.value), this.method);
        }
    }

    public enum Method {
        FIXED, CONTENT, FILL, EXPAND
    }

    public static Sizing parse(Element sizingElement) {
        var methodString = sizingElement.getAttribute("method");
        if (methodString.isBlank()) {
            throw new UIModelParsingException("Missing 'method' attribute on sizing declaration. Must be one of: fixed, content, fill");
        }

        var method = Method.valueOf(methodString.toUpperCase(Locale.ROOT));
        var value = sizingElement.getTextContent().strip();

        if (method == Method.CONTENT) {
            if (!value.matches("(-?\\d+)?")) {
                throw new UIModelParsingException("Invalid value in sizing declaration");
            }

            return new Sizing(value.isEmpty() ? 0 : Integer.parseInt(value), method);
        } else {
            if (!value.matches("-?\\d+")) {
                throw new UIModelParsingException("Invalid value in sizing declaration");
            }

            return new Sizing(Integer.parseInt(value), method);
        }
    }

    @Override
    public boolean equals(Object o) {
        if (this == o) return true;
        if (o == null || getClass() != o.getClass()) return false;
        Sizing sizing = (Sizing) o;
        return value == sizing.value && method == sizing.method;
    }

    @Override
    public int hashCode() {
        return Objects.hash(method, value);
    }

    private static final class MergedSizing extends Sizing {

        private final Sizing first, second;
        private final float delta;

        private MergedSizing(Sizing first, Sizing second, float delta) {
            super(first.value, first.method);
            this.first = first;
            this.second = second;
            this.delta = delta;
        }

        @Override
        public int inflate(int space, Function<Sizing, Integer> contentSizeFunction) {
            return MathHelper.lerp(
                    this.delta,
                    this.first.inflate(space, contentSizeFunction),
                    this.second.inflate(space, contentSizeFunction)
            );
        }

        @Override
        public Sizing interpolate(Sizing next, float delta) {
            return this.first.interpolate(next, delta);
        }

        @Override
        public boolean isContent() {
            return this.first.isContent() || this.second.isContent();
        }

        @Override
        public float contentFactor() {
            if (this.first.isContent() && this.second.isContent()) return super.contentFactor();

            if (this.first.isContent()) {
                return 1f - delta;
            } else if (this.second.isContent()) {
                return delta;
            } else {
                return 0f;
            }
        }

        @Override
        public boolean equals(Object o) {
            if (this == o) return true;
            if (o == null || getClass() != o.getClass()) return false;
            if (!super.equals(o)) return false;
            MergedSizing that = (MergedSizing) o;
            return Float.compare(delta, that.delta) == 0 && Objects.equals(first, that.first) && Objects.equals(second, that.second);
        }

        @Override
        public int hashCode() {
            return Objects.hash(super.hashCode(), first, second, delta);
        }
    }

}
