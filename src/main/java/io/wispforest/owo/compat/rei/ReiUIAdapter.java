package io.wispforest.owo.compat.rei;

import io.wispforest.owo.ui.core.OwoUIAdapter;
import io.wispforest.owo.ui.core.ParentComponent;
import io.wispforest.owo.ui.core.Sizing;
import io.wispforest.owo.ui.util.ScissorStack;
import me.shedaniel.math.Point;
import me.shedaniel.math.Rectangle;
import me.shedaniel.rei.api.client.gui.widgets.Widget;
import me.shedaniel.rei.api.client.gui.widgets.WidgetWithBounds;
import net.fabricmc.fabric.api.client.screen.v1.ScreenEvents;
import net.minecraft.client.MinecraftClient;
import net.minecraft.client.gui.DrawContext;
import net.minecraft.client.gui.Element;
import net.minecraft.client.util.math.MatrixStack;

import java.util.List;
import java.util.function.BiFunction;
import java.util.function.Consumer;
import java.util.function.Function;

public class ReiUIAdapter<T extends ParentComponent> extends Widget {

    public static final Point LAYOUT = new Point(-69, -69);

    public final OwoUIAdapter<T> adapter;

    public ReiUIAdapter(Rectangle bounds, BiFunction<Sizing, Sizing, T> rootComponentMaker) {
        this.adapter = OwoUIAdapter.createWithoutScreen(bounds.x, bounds.y, bounds.width, bounds.height, rootComponentMaker);
        this.adapter.inspectorZOffset = 900;

        var screenWithREI = MinecraftClient.getInstance().currentScreen;

        if (screenWithREI != null) {
            ScreenEvents.remove(screenWithREI).register(screen -> this.adapter.dispose());
            ScreenEvents.afterRender(screenWithREI).register((screen, drawContext, mouseX, mouseY, tickDelta) -> {
                this.adapter.drawTooltip(drawContext, mouseX, mouseY, tickDelta);
            });
        }
    }

    public void prepare() {
        this.adapter.inflateAndMount();
    }

    public T rootComponent() {
        return this.adapter.rootComponent;
    }

    public <W extends WidgetWithBounds> ReiWidgetComponent wrap(W widget) {
        return new ReiWidgetComponent(widget);
    }

    public <W extends WidgetWithBounds> ReiWidgetComponent wrap(Function<Point, W> widgetFactory, Consumer<W> widgetConfigurator) {
        var widget = widgetFactory.apply(LAYOUT);
        widgetConfigurator.accept(widget);
        return new ReiWidgetComponent(widget);
    }

    @Override
    public boolean containsMouse(double mouseX, double mouseY) {
        return this.adapter.isMouseOver(mouseX, mouseY);
    }

    @Override
    public boolean mouseClicked(double mouseX, double mouseY, int button) {
        return this.adapter.mouseClicked(mouseX - this.adapter.x(), mouseY - this.adapter.y(), button);
    }

    @Override
    public boolean mouseScrolled(double mouseX, double mouseY, double horizontalAmount, double verticalAmount) {
        return this.adapter.mouseScrolled(mouseX - this.adapter.x(), mouseY - this.adapter.y(), horizontalAmount, verticalAmount);
    }

    @Override
    public boolean mouseReleased(double mouseX, double mouseY, int button) {
        return this.adapter.mouseReleased(mouseX - this.adapter.x(), mouseY - this.adapter.y(), button);
    }

    @Override
    public boolean mouseDragged(double mouseX, double mouseY, int button, double deltaX, double deltaY) {
        return this.adapter.mouseDragged(mouseX - this.adapter.x(), mouseY - this.adapter.y(), button, deltaX, deltaY);
    }

    @Override
    public boolean keyPressed(int keyCode, int scanCode, int modifiers) {
        return this.adapter.keyPressed(keyCode, scanCode, modifiers);
    }

    @Override
    public boolean keyReleased(int keyCode, int scanCode, int modifiers) {
        return this.adapter.keyReleased(keyCode, scanCode, modifiers);
    }

    @Override
    public boolean charTyped(char chr, int modifiers) {
        return this.adapter.charTyped(chr, modifiers);
    }

    @Override
    public void render(DrawContext context, int mouseX, int mouseY, float partialTicks) {
        ScissorStack.push(this.adapter.x(), this.adapter.y(), this.adapter.width(), this.adapter.height(), context);
        this.adapter.render(context, mouseX, mouseY, partialTicks);
        ScissorStack.pop();

        context.draw();
    }

    @Override
    public List<? extends Element> children() {
        return List.of();
    }
}
