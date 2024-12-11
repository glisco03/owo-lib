package io.wispforest.owo.ui.core;

import com.mojang.blaze3d.platform.GlStateManager;
import com.mojang.blaze3d.systems.RenderSystem;
import io.wispforest.owo.Owo;
import io.wispforest.owo.renderdoc.RenderDoc;
import io.wispforest.owo.ui.util.CursorAdapter;
import net.minecraft.client.MinecraftClient;
import net.minecraft.client.gui.DrawContext;
import net.minecraft.client.gui.Drawable;
import net.minecraft.client.gui.Element;
import net.minecraft.client.gui.Selectable;
import net.minecraft.client.gui.screen.Screen;
import net.minecraft.client.gui.screen.narration.NarrationMessageBuilder;
import org.lwjgl.glfw.GLFW;

import java.util.function.BiFunction;

/**
 * A UI adapter constitutes the main entrypoint to using owo-ui.
 * It takes care of rendering the UI tree correctly, handles input events
 * and cursor styling as well as the component inspector.
 * <p>
 * Additionally, the adapter implements all interfaces required for it
 * to be treated as a normal widget by the vanilla screen system - this means
 * even if you choose to not use {@link io.wispforest.owo.ui.base.BaseOwoScreen}
 * you can always simply add it as a widget and get most of the functionality
 * working out of the box
 * <p>
 * To draw the UI tree managed by this adapter, call {@link OwoUIAdapter#render(DrawContext, int, int, float)}.
 * Note that this does not draw the current tooltip of the UI - this must be done separately
 * by invoking {@link #drawTooltip(DrawContext, int, int, float)}. If in a scenario with multiple adapters
 * or other sources rendering UI elements to the screen, it is generally desirable to delay tooltip
 * drawing until after all UI is drawn to avoid layering issues.
 *
 * @see io.wispforest.owo.ui.base.BaseOwoScreen
 */
public class OwoUIAdapter<R extends ParentComponent> implements Element, Drawable, Selectable {

    private static boolean isRendering = false;

    public final R rootComponent;
    public final CursorAdapter cursorAdapter;

    protected boolean disposed = false;
    protected boolean captureFrame = false;

    protected int x, y;
    protected int width, height;

    public boolean enableInspector = false;
    public boolean globalInspector = false;
    public int inspectorZOffset = 1000;

    protected OwoUIAdapter(int x, int y, int width, int height, R rootComponent) {
        this.x = x;
        this.y = y;
        this.width = width;
        this.height = height;

        this.cursorAdapter = CursorAdapter.ofClientWindow();
        this.rootComponent = rootComponent;
    }

    /**
     * Create a UI adapter for the given screen. This also sets it up
     * to be rendered and receive input events, without needing you to
     * do any more setup
     *
     * @param screen             The screen for which to create an adapter
     * @param rootComponentMaker A function which will create the root component of this screen
     * @param <R>                The type of root component the created adapter will use
     * @return The new UI adapter, already set up for the given screen
     */
    public static <R extends ParentComponent> OwoUIAdapter<R> create(Screen screen, BiFunction<Sizing, Sizing, R> rootComponentMaker) {
        var rootComponent = rootComponentMaker.apply(Sizing.fill(100), Sizing.fill(100));

        var adapter = new OwoUIAdapter<>(0, 0, screen.width, screen.height, rootComponent);
        screen.addDrawableChild(adapter);
        screen.setFocused(adapter);

        return adapter;
    }

    /**
     * Create a new UI adapter without the specific context of a screen - use this
     * method when you want to embed owo-ui into a different context
     *
     * @param x                  The x-coordinate of the top-left corner of the root component
     * @param y                  The y-coordinate of the top-left corner of the root component
     * @param width              The width of the available area, in pixels
     * @param height             The height of the available area, in pixels
     * @param rootComponentMaker A function which will create the root component of the adapter
     * @param <R>                The type of root component the created adapter will use
     * @return The new UI adapter, ready for layout inflation
     */
    public static <R extends ParentComponent> OwoUIAdapter<R> createWithoutScreen(int x, int y, int width, int height, BiFunction<Sizing, Sizing, R> rootComponentMaker) {
        var rootComponent = rootComponentMaker.apply(Sizing.fill(100), Sizing.fill(100));
        return new OwoUIAdapter<>(x, y, width, height, rootComponent);
    }

    /**
     * Begin the layout process of the UI tree and
     * mount the tree once the layout is inflated
     * <p>
     * After this method has executed, this adapter is ready for rendering
     */
    public void inflateAndMount() {
        this.rootComponent.inflate(Size.of(this.width, this.height));
        this.rootComponent.mount(null, this.x, this.y);
    }

    public void moveAndResize(int x, int y, int width, int height) {
        this.x = x;
        this.y = y;
        this.width = width;
        this.height = height;

        this.inflateAndMount();
    }

    /**
     * Dispose this UI adapter - this will destroy the cursor
     * objects held onto by this adapter and stop updating the cursor style
     * <p>
     * After this method has executed, this adapter can safely be garbage-collected
     */
    // TODO properly dispose root component
    public void dispose() {
        this.cursorAdapter.dispose();
        this.disposed = true;
    }

    /**
     * @return Toggle rendering of the inspector
     */
    public boolean toggleInspector() {
        return this.enableInspector = !this.enableInspector;
    }

    /**
     * @return Toggle the inspector between
     * hovered and global mode
     */
    public boolean toggleGlobalInspector() {
        return this.globalInspector = !this.globalInspector;
    }

    public int x() {
        return this.x;
    }

    public int y() {
        return this.y;
    }

    public int width() {
        return this.width;
    }

    public int height() {
        return this.height;
    }

    @Override
    public void render(DrawContext context, int mouseX, int mouseY, float partialTicks) {
        if (!(context instanceof OwoUIDrawContext)) context = OwoUIDrawContext.of(context);
        var owoContext = (OwoUIDrawContext) context;

        try {
            isRendering = true;

            if (this.captureFrame) RenderDoc.startFrameCapture();

            final var delta = MinecraftClient.getInstance().getRenderTickCounter().getLastFrameDuration();
            final var window = MinecraftClient.getInstance().getWindow();

            this.rootComponent.update(delta, mouseX, mouseY);

            RenderSystem.enableDepthTest();
            GlStateManager._enableScissorTest();

            GlStateManager._scissorBox(0, 0, window.getFramebufferWidth(), window.getFramebufferHeight());
            this.rootComponent.draw(owoContext, mouseX, mouseY, partialTicks, delta);

            GlStateManager._disableScissorTest();
            RenderSystem.disableDepthTest();

            final var hovered = this.rootComponent.childAt(mouseX, mouseY);
            if (!disposed && hovered != null) {
                this.cursorAdapter.applyStyle(hovered.cursorStyle());
            }

            if (this.enableInspector) {
                context.getMatrices().translate(0, 0, this.inspectorZOffset);
                owoContext.drawInspector(this.rootComponent, mouseX, mouseY, !this.globalInspector);
                context.getMatrices().translate(0, 0, -this.inspectorZOffset);
            }

            if (this.captureFrame) RenderDoc.endFrameCapture();
        } finally {
            isRendering = false;
            this.captureFrame = false;
        }
    }

    /**
     * Draw the current tooltip of the UI managed by this adapter. This method
     * must not be called without a previous, corresponding call to {@link #render(DrawContext, int, int, float)}
     *
     *
     * @since 0.12.19
     */
    public void drawTooltip(DrawContext context, int mouseX, int mouseY, float partialTicks) {
        if (!(context instanceof OwoUIDrawContext)) context = OwoUIDrawContext.of(context);
        var owoContext = (OwoUIDrawContext) context;

        final var delta = MinecraftClient.getInstance().getRenderTickCounter().getLastFrameDuration();

        this.rootComponent.drawTooltip(owoContext, mouseX, mouseY, partialTicks, delta);
    }

    @Override
    public boolean isMouseOver(double mouseX, double mouseY) {
        return this.rootComponent.isInBoundingBox(mouseX, mouseY);
    }

    @Override
    public void setFocused(boolean focused) {}

    @Override
    public boolean isFocused() {
        return true;
    }

    @Override
    public boolean mouseClicked(double mouseX, double mouseY, int button) {
        return this.rootComponent.onMouseDown(mouseX, mouseY, button);
    }

    @Override
    public boolean mouseReleased(double mouseX, double mouseY, int button) {
        return this.rootComponent.onMouseUp(mouseX, mouseY, button);
    }

    @Override
    public boolean mouseScrolled(double mouseX, double mouseY, double horizontalAmount, double verticalAmount) {
        return this.rootComponent.onMouseScroll(mouseX, mouseY, verticalAmount);
    }

    @Override
    public boolean mouseDragged(double mouseX, double mouseY, int button, double deltaX, double deltaY) {
        return this.rootComponent.onMouseDrag(mouseX, mouseY, deltaX, deltaY, button);
    }

    @Override
    public boolean keyPressed(int keyCode, int scanCode, int modifiers) {
        if (Owo.DEBUG && keyCode == GLFW.GLFW_KEY_LEFT_SHIFT) {
            if ((modifiers & GLFW.GLFW_MOD_CONTROL) != 0) {
                this.toggleInspector();
            } else if ((modifiers & GLFW.GLFW_MOD_ALT) != 0) {
                this.toggleGlobalInspector();
            }
        }

        if (Owo.DEBUG && keyCode == GLFW.GLFW_KEY_R && RenderDoc.isAvailable()) {
            if ((modifiers & GLFW.GLFW_MOD_ALT) != 0 && (modifiers & GLFW.GLFW_MOD_CONTROL) != 0) {
                this.captureFrame = true;
            }
        }

        return this.rootComponent.onKeyPress(keyCode, scanCode, modifiers);
    }

    @Override
    public boolean charTyped(char chr, int modifiers) {
        return this.rootComponent.onCharTyped(chr, modifiers);
    }

    @Override
    public SelectionType getType() {
        return SelectionType.NONE;
    }

    @Override
    public void appendNarrations(NarrationMessageBuilder builder) {}

    public static boolean isRendering() {
        return isRendering;
    }
}
