package io.wispforest.owo.ui.core;

/**
 * Represents a rectangle positioned in 2D-space
 */
public interface PositionedRectangle {

    /**
     * @return The x-coordinate of the top-left corner of this rectangle
     */
    int x();

    /**
     * @return The y-coordinate of the top-left corner of this rectangle
     */
    int y();

    /**
     * @return The width of this rectangle
     */
    int width();

    /**
     * @return The height of this rectangle
     */
    int height();

    /**
     * @return {@code true} if this rectangle contains the given point
     */
    default boolean isInBoundingBox(double x, double y) {
        return x >= this.x() && x <= this.x() + this.width() && y >= this.y() && y <= this.y() + this.height();
    }

    static PositionedRectangle of(int x, int y, Size size) {
        return of(x, y, size.width(), size.height());
    }

    static PositionedRectangle of(int x, int y, int width, int height) {
        return new PositionedRectangle() {
            @Override
            public int x() {
                return x;
            }

            @Override
            public int y() {
                return y;
            }

            @Override
            public int width() {
                return width;
            }

            @Override
            public int height() {
                return height;
            }
        };
    }
}