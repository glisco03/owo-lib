package io.wispforest.owo.ui.container;

import io.wispforest.owo.ui.core.Component;
import io.wispforest.owo.ui.core.Sizing;
import net.minecraft.text.Text;

public final class Containers {

    private Containers() {}

    // ------
    // Layout
    // ------

    public static GridLayout grid(Sizing horizontalSizing, Sizing verticalSizing, int rows, int columns) {
        return new GridLayout(horizontalSizing, verticalSizing, rows, columns);
    }

    public static GridLayout grid(Sizing sizing, int rows, int columns) {
        return grid(sizing, sizing, rows, columns);
    }

    public static FlowLayout verticalFlow(Sizing horizontalSizing, Sizing verticalSizing) {
        return new FlowLayout(horizontalSizing, verticalSizing, FlowLayout.Algorithm.VERTICAL);
    }

    public static FlowLayout verticalFlow(Sizing sizing) {
        return verticalFlow(sizing, sizing);
    }

    public static FlowLayout horizontalFlow(Sizing horizontalSizing, Sizing verticalSizing) {
        return new FlowLayout(horizontalSizing, verticalSizing, FlowLayout.Algorithm.HORIZONTAL);
    }

    public static FlowLayout horizontalFlow(Sizing sizing) {
        return horizontalFlow(sizing, sizing);
    }

    public static FlowLayout ltrTextFlow(Sizing horizontalSizing, Sizing verticalSizing) {
        return new FlowLayout(horizontalSizing, verticalSizing, FlowLayout.Algorithm.LTR_TEXT);
    }

    public static FlowLayout ltrTextFlow(Sizing sizing) {
        return ltrTextFlow(sizing, sizing);
    }

    public static StackLayout stack(Sizing horizontalSizing, Sizing verticalSizing) {
        return new StackLayout(horizontalSizing, verticalSizing);
    }

    public static StackLayout stack(Sizing sizing) {
        return stack(sizing, sizing);
    }

    // ------
    // Scroll
    // ------

    public static <C extends Component> ScrollContainer<C> verticalScroll(Sizing horizontalSizing, Sizing verticalSizing, C child) {
        return new ScrollContainer<>(ScrollContainer.ScrollDirection.VERTICAL, horizontalSizing, verticalSizing, child);
    }

    public static <C extends Component> ScrollContainer<C> horizontalScroll(Sizing horizontalSizing, Sizing verticalSizing, C child) {
        return new ScrollContainer<>(ScrollContainer.ScrollDirection.HORIZONTAL, horizontalSizing, verticalSizing, child);
    }

    // ----------------
    // Utility wrappers
    // ----------------

    public static <C extends Component> DraggableContainer<C> draggable(Sizing horizontalSizing, Sizing verticalSizing, C child) {
        return new DraggableContainer<>(horizontalSizing, verticalSizing, child);
    }

    public static <C extends Component> DraggableContainer<C> draggable(Sizing sizing, C child) {
        return draggable(sizing, sizing, child);
    }

    public static CollapsibleContainer collapsible(Sizing horizontalSizing, Sizing verticalSizing, Text title, boolean expanded) {
        return new CollapsibleContainer(horizontalSizing, verticalSizing, title, expanded);
    }

    public static CollapsibleContainer collapsible(Sizing sizing, Text title, boolean expanded) {
        return collapsible(sizing, sizing, title, expanded);
    }

    public static <C extends Component> OverlayContainer<C> overlay(C child) {
        return new OverlayContainer<>(child);
    }

    public static <C extends Component> RenderEffectWrapper<C> renderEffect(C child) {
        return new RenderEffectWrapper<>(child);
    }

}
