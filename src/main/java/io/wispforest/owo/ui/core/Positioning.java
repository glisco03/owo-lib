package io.wispforest.owo.ui.core;

import io.wispforest.owo.Owo;
import io.wispforest.owo.ui.parsing.UIModelParsingException;
import net.minecraft.util.math.MathHelper;
import org.w3c.dom.Element;

import java.util.Locale;
import java.util.Objects;
import java.util.Random;

public class Positioning implements Animatable<Positioning> {

    private static final Positioning LAYOUT_POSITIONING = new Positioning(0, 0, Type.LAYOUT);

    public final Type type;
    public final int x, y;

    private Positioning(int x, int y, Type type) {
        this.type = type;
        this.x = x;
        this.y = y;
    }

    public Positioning withX(int x) {
        return new Positioning(x, this.y, this.type);
    }

    public Positioning withY(int y) {
        return new Positioning(this.x, y, this.type);
    }

    public boolean isRelative() {
        return this.type == Type.RELATIVE || this.type == Type.ACROSS;
    }

    @Override
    public Positioning interpolate(Positioning next, float delta) {
        if (next.type != this.type) {
            Owo.LOGGER.warn("Cannot interpolate between positioning of type " + this.type + " and " + next.type);
            return this;
        }

        return new Positioning(
                MathHelper.lerp(delta, this.x, next.x),
                MathHelper.lerp(delta, this.y, next.y),
                this.type
        );
    }

    /**
     * Position the component at an absolute offset
     * from the root of parent
     *
     * @param xPixels The offset on the x-axis
     * @param yPixels The offset on the y-axis
     */
    public static Positioning absolute(int xPixels, int yPixels) {
        return new Positioning(xPixels, yPixels, Type.ABSOLUTE);
    }

    /**
     * Position the component at a relative offset
     * inside the parent. This respect the size of
     * the component itself. As such:
     * <ul>
     *     <li>50,50 centers the component inside the parent</li>
     *     <li>100,50 centers to component vertically and pushes it all the way to the right</li>
     *     <li>100,100 pushes the component all the way into the bottom right corner of the parent</li>
     * </ul>
     *
     * @param xPercent The offset on the x-axis
     * @param yPercent The offset on the y-axis
     */
    public static Positioning relative(int xPercent, int yPercent) {
        return new Positioning(xPercent, yPercent, Type.RELATIVE);
    }

    /**
     * Position the component the specified percentage
     * across the parent, <i>not including the component's own size</i>
     *
     * @param xPercent The offset on the x-axis
     * @param yPercent The offset on the y-axis
     */
    public static Positioning across(int xPercent, int yPercent) {
        return new Positioning(xPercent, yPercent, Type.ACROSS);
    }

    /**
     * Position the component using whatever layout
     * method the parent component wants to apply
     */
    public static Positioning layout() {
        return LAYOUT_POSITIONING;
    }

    /**
     * A collection of utility methods for generating random positioning instances
     *
     * @author chyzman
     */
    public static class Random {
        private static final java.util.Random POSITIONING_RANDOM = new java.util.Random();


        /**
         * Generate a random absolute positioning
         *
         * @param minX The minimum x offset
         * @param maxX The maximum x offset
         * @param minY The minimum y offset
         * @param maxY The maximum y offset
         */
        public static Positioning absolute(int minX, int maxX, int minY, int maxY) {
            return Positioning.absolute(
                    POSITIONING_RANDOM.nextInt(minX, maxX),
                    POSITIONING_RANDOM.nextInt(minY, maxY)
            );
        }

        /**
         * Generate a random absolute positioning
         *
         * @param min The minimum offset
         * @param max The maximum offset
         */
        public static Positioning absolute(int min, int max) {
            return Positioning.absolute(
                    POSITIONING_RANDOM.nextInt(min, max),
                    POSITIONING_RANDOM.nextInt(min, max)
            );
        }

        /**
         * Generate a random absolute positioning
         *
         * @param max The maximum offset
         */
        public static Positioning absolute(int max) {
            return Positioning.absolute(
                    POSITIONING_RANDOM.nextInt(max),
                    POSITIONING_RANDOM.nextInt(max)
            );
        }

        /**
         * Generate a random relative positioning
         *
         * @param minX The minimum x offset
         * @param maxX The maximum x offset
         * @param minY The minimum y offset
         * @param maxY The maximum y offset
         */
        public static Positioning relative(int minX, int maxX, int minY, int maxY) {
            return Positioning.relative(
                    POSITIONING_RANDOM.nextInt(minX, maxX),
                    POSITIONING_RANDOM.nextInt(minY, maxY)
            );
        }

        /**
         * Generate a random relative positioning
         *
         * @param min The minimum offset
         * @param max The maximum offset
         */
        public static Positioning relative(int min, int max) {
            return Positioning.relative(
                    POSITIONING_RANDOM.nextInt(min, max),
                    POSITIONING_RANDOM.nextInt(min, max)
            );
        }

        /**
         * Generate a random relative positioning
         *
         * @param max The maximum offset
         */
        public static Positioning relative(int max) {
            return Positioning.relative(
                    POSITIONING_RANDOM.nextInt(max),
                    POSITIONING_RANDOM.nextInt(max)
            );
        }

        /**
         * Generate a random relative positioning
         */
        public static Positioning relative() {
            return Positioning.relative(
                    POSITIONING_RANDOM.nextInt(100),
                    POSITIONING_RANDOM.nextInt(100)
            );
        }

        /**
         * Generate a random across positioning
         *
         * @param minX The minimum x offset
         * @param maxX The maximum x offset
         * @param minY The minimum y offset
         * @param maxY The maximum y offset
         */
        public static Positioning across(int minX, int maxX, int minY, int maxY) {
            return Positioning.across(
                    POSITIONING_RANDOM.nextInt(minX, maxX),
                    POSITIONING_RANDOM.nextInt(minY, maxY)
            );
        }

        /**
         * Generate a random across positioning
         *
         * @param min The minimum offset
         * @param max The maximum offset
         */
        public static Positioning across(int min, int max) {
            return Positioning.across(
                    POSITIONING_RANDOM.nextInt(min, max),
                    POSITIONING_RANDOM.nextInt(min, max)
            );
        }

        /**
         * Generate a random across positioning
         *
         * @param max The maximum offset
         */
        public static Positioning across(int max) {
            return Positioning.across(
                    POSITIONING_RANDOM.nextInt(max),
                    POSITIONING_RANDOM.nextInt(max)
            );
        }

        /**
         * Generate a random across positioning
         */
        public static Positioning across() {
            return Positioning.across(
                    POSITIONING_RANDOM.nextInt(100),
                    POSITIONING_RANDOM.nextInt(100)
            );
        }

        /**
         * Generate a random positioning instance
         *
         * @param minX The minimum x offset
         * @param maxX The maximum x offset
         * @param minY The minimum y offset
         * @param maxY The maximum y offset
         */
        public static Positioning random(int minX, int maxX, int minY, int maxY) {
            return switch (POSITIONING_RANDOM.nextInt(2)) {
                case 0 -> relative(minX, maxX, minY, maxY);
                case 1 -> across(minX, maxX, minY, maxY);
                default -> throw new IllegalStateException("Unexpected value: " + POSITIONING_RANDOM.nextInt(2));
            };
        }

        /**
         * Generate a random positioning instance
         *
         * @param min The minimum offset
         * @param max The maximum offset
         */
        public static Positioning random(int min, int max) {
            return random(min, max, min, max);
        }

        /**
         * Generate a random positioning instance
         *
         * @param max The maximum offset
         */
        public static Positioning random(int max) {
            return random(0, max);
        }

        /**
         * Generate a random positioning instance
         */
        public static Positioning random() {
            return random(100);
        }
    }

    public enum Type {
        RELATIVE, ACROSS, ABSOLUTE, LAYOUT
    }

    public static Positioning parse(Element positioningElement) {
        var typeString = positioningElement.getAttribute("type");
        if (typeString.isBlank()) {
            throw new UIModelParsingException("Missing 'type' attribute on positioning declaration. Must be one of: relative, absolute, layout");
        }

        var type = Type.valueOf(typeString.toUpperCase(Locale.ROOT));

        var values = positioningElement.getTextContent().strip();
        if (!values.matches("-?\\d+,-?\\d+")) {
            throw new UIModelParsingException("Invalid value in positioning declaration");
        }

        int x = Integer.parseInt(values.split(",")[0]);
        int y = Integer.parseInt(values.split(",")[1]);

        return new Positioning(x, y, type);
    }

    @Override
    public boolean equals(Object o) {
        if (this == o) return true;
        if (o == null || getClass() != o.getClass()) return false;
        Positioning that = (Positioning) o;
        return x == that.x && y == that.y && type == that.type;
    }

    @Override
    public int hashCode() {
        return Objects.hash(type, x, y);
    }
}
