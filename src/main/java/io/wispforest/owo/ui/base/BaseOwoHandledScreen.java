package io.wispforest.owo.ui.base;

import io.wispforest.owo.Owo;
import io.wispforest.owo.mixin.ui.SlotAccessor;
import io.wispforest.owo.ui.core.*;
import io.wispforest.owo.ui.inject.GreedyInputComponent;
import io.wispforest.owo.ui.util.DisposableScreen;
import io.wispforest.owo.ui.util.UIErrorToast;
import io.wispforest.owo.util.pond.OwoSlotExtension;
import net.fabricmc.fabric.api.client.screen.v1.ScreenEvents;
import net.minecraft.client.gui.DrawContext;
import net.minecraft.client.gui.Element;
import net.minecraft.client.gui.screen.Screen;
import net.minecraft.client.gui.screen.ingame.HandledScreen;
import net.minecraft.entity.player.PlayerInventory;
import net.minecraft.item.ItemStack;
import net.minecraft.screen.ScreenHandler;
import net.minecraft.screen.slot.Slot;
import net.minecraft.text.Text;
import org.jetbrains.annotations.ApiStatus;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;
import org.lwjgl.glfw.GLFW;
import org.lwjgl.opengl.GL11;

import java.util.ArrayList;
import java.util.Optional;
import java.util.function.BiFunction;
import java.util.stream.Stream;

public abstract class BaseOwoHandledScreen<R extends ParentComponent, S extends ScreenHandler> extends HandledScreen<S> implements DisposableScreen {

    /**
     * The UI adapter of this screen. This handles
     * all user input as well as setting up GL state for rendering
     * and managing component focus
     */
    protected OwoUIAdapter<R> uiAdapter = null;

    /**
     * Whether this screen has encountered an unrecoverable
     * error during its lifecycle and should thus close
     * itself on the next frame
     */
    protected boolean invalid = false;

    protected BaseOwoHandledScreen(S handler, PlayerInventory inventory, Text title) {
        super(handler, inventory, title);
    }

    /**
     * Initialize the UI adapter for this screen. Usually
     * the body of this method will simply consist of a call
     * to {@link OwoUIAdapter#create(Screen, BiFunction)}
     *
     * @return The UI adapter for this screen to use
     */
    protected abstract @NotNull OwoUIAdapter<R> createAdapter();

    /**
     * Build the component hierarchy of this screen,
     * called after the adapter and root component have been
     * initialized by {@link #createAdapter()}
     *
     * @param rootComponent The root component created
     *                      in the previous initialization step
     */
    protected abstract void build(R rootComponent);

    @Override
    protected void init() {
        super.init();

        if (this.invalid) return;

        // Check whether this screen was already initialized
        if (this.uiAdapter != null) {
            // If it was, only resize the adapter instead of recreating it - this preserves UI state
            this.uiAdapter.moveAndResize(0, 0, this.width, this.height);
            // Re-add it as a child to circumvent vanilla clearing them
            this.addDrawableChild(this.uiAdapter);
        } else {
            try {
                this.uiAdapter = this.createAdapter();
                this.build(this.uiAdapter.rootComponent);

                this.uiAdapter.inflateAndMount();
            } catch (Exception error) {
                Owo.LOGGER.warn("Could not initialize owo screen", error);
                UIErrorToast.report(error);
                this.invalid = true;
            }
        }

        ScreenEvents.afterRender(this).register((screen, drawContext, mouseX, mouseY, tickDelta) -> {
            this.drawComponentTooltip(drawContext, mouseX, mouseY, tickDelta);
        });
    }

    /**
     * Draw the tooltip of this screen's component tree, invoked
     * by {@link ScreenEvents#afterRender(Screen)} so that tooltips are
     * properly rendered above content
     */
    protected void drawComponentTooltip(DrawContext drawContext, int mouseX, int mouseY, float tickDelta) {
        if (this.uiAdapter != null) this.uiAdapter.drawTooltip(drawContext, mouseX, mouseY, tickDelta);
    }

    /**
     * Disable the slot at the given index. Note
     * that this is hard override and the slot cannot
     * re-enable itself
     *
     * @param index The index of the slot to disable
     */
    protected void disableSlot(int index) {
        this.disableSlot(this.handler.slots.get(index));
    }

    /**
     * Disable the given slot. Note that
     * this is hard override and the slot cannot
     * re-enable itself
     */
    protected void disableSlot(Slot slot) {
        ((OwoSlotExtension) slot).owo$setDisabledOverride(true);
    }

    /**
     * Enable the slot at the given index. Note
     * that this is an override and cannot enable
     * a slot that is disabled through its own will
     *
     * @param index The index of the slot to enable
     */
    protected void enableSlot(int index) {
        this.enableSlot(this.handler.slots.get(index));
    }

    /**
     * Enable the given slot. Note that
     * this is an override and cannot enable
     * a slot that is disabled through its own will
     */
    protected void enableSlot(Slot slot) {
        ((OwoSlotExtension) slot).owo$setDisabledOverride(false);
    }

    /**
     * @return whether the given slot is enabled or disabled
     * using the {@link OwoSlotExtension} disabling functionality
     */
    protected boolean isSlotEnabled(int index) {
        return isSlotEnabled(this.handler.slots.get(index));
    }

    /**
     * @return whether the given slot is enabled or disabled
     * using the {@link OwoSlotExtension} disabling functionality
     */
    protected boolean isSlotEnabled(Slot slot) {
        return !((OwoSlotExtension) slot).owo$getDisabledOverride();
    }

    /**
     * Wrap the slot at the given index in this screen's
     * handler into a component, so it can be managed by the UI system
     *
     * @param index The index the slot occupies in the handler's slot list
     * @return The wrapped slot
     */
    protected SlotComponent slotAsComponent(int index) {
        return new SlotComponent(index);
    }

    /**
     * A convenience shorthand for querying a component from the adapter's
     * root component via {@link ParentComponent#childById(Class, String)}
     */
    protected <C extends Component> C component(Class<C> expectedClass, String id) {
        return this.uiAdapter.rootComponent.childById(expectedClass, id);
    }

    /**
     * Compute a stream of all components for which to
     * generate exclusion areas in a recipe viewer overlay.
     * Called by the REI and EMI plugins
     */
    @ApiStatus.OverrideOnly
    public Stream<Component> componentsForExclusionAreas() {
        if (this.children().isEmpty()) return Stream.of();

        var rootComponent = uiAdapter.rootComponent;
        var children = new ArrayList<Component>();

        rootComponent.collectDescendants(children);
        children.remove(rootComponent);

        return children.stream().filter(component -> !(component instanceof ParentComponent parent) || parent.surface() != Surface.BLANK);
    }

    @Override
    public void renderBackground(DrawContext context, int mouseX, int mouseY, float delta) {}

    @Override
    public void render(DrawContext vanillaContext, int mouseX, int mouseY, float delta) {
        var context = OwoUIDrawContext.of(vanillaContext);
        if (!this.invalid) {
            super.render(context, mouseX, mouseY, delta);

            if (this.uiAdapter.enableInspector) {
                context.getMatrices().translate(0, 0, 500);

                for (int i = 0; i < this.handler.slots.size(); i++) {
                    var slot = this.handler.slots.get(i);
                    if (!slot.isEnabled()) continue;

                    context.drawText(Text.literal("H:" + i),
                        this.x + slot.x + 15, this.y + slot.y + 9, .5f, 0x0096FF,
                        OwoUIDrawContext.TextAnchor.BOTTOM_RIGHT
                    );
                    context.drawText(Text.literal("I:" + slot.getIndex()),
                        this.x + slot.x + 15, this.y + slot.y + 15, .5f, 0x5800FF,
                        OwoUIDrawContext.TextAnchor.BOTTOM_RIGHT
                    );
                }

                context.getMatrices().translate(0, 0, -500);
            }

            this.drawMouseoverTooltip(context, mouseX, mouseY);
        } else {
            this.close();
        }
    }

    @Override
    public boolean keyPressed(int keyCode, int scanCode, int modifiers) {
        if ((modifiers & GLFW.GLFW_MOD_CONTROL) == 0
            && this.uiAdapter.rootComponent.focusHandler().focused() instanceof GreedyInputComponent inputComponent
            && inputComponent.onKeyPress(keyCode, scanCode, modifiers)) {
            return true;
        }

        return super.keyPressed(keyCode, scanCode, modifiers);
    }

    @Override
    public Optional<Element> hoveredElement(double mouseX, double mouseY) {
        return super.hoveredElement(mouseX, mouseY).flatMap(element -> element != this.uiAdapter ? Optional.of(element) : Optional.empty());
    }

    @Override
    public boolean mouseClicked(double mouseX, double mouseY, int button) {
        return this.uiAdapter.mouseClicked(mouseX, mouseY, button) || super.mouseClicked(mouseX, mouseY, button);
    }

    @Override
    public boolean mouseDragged(double mouseX, double mouseY, int button, double deltaX, double deltaY) {
        return this.uiAdapter.mouseDragged(mouseX, mouseY, button, deltaX, deltaY) || super.mouseDragged(mouseX, mouseY, button, deltaX, deltaY);
    }

    @Override
    public boolean mouseScrolled(double mouseX, double mouseY, double horizontalAmount, double verticalAmount) {
        return this.uiAdapter.mouseScrolled(mouseX, mouseY, horizontalAmount, verticalAmount) || super.mouseScrolled(mouseX, mouseY, horizontalAmount, verticalAmount);
    }

    @Nullable
    @Override
    public Element getFocused() {
        return this.uiAdapter;
    }

    @Override
    public void removed() {
        super.removed();
        if (this.uiAdapter != null) {
            this.uiAdapter.cursorAdapter.applyStyle(CursorStyle.NONE);
        }
    }

    @Override
    public void dispose() {
        if (this.uiAdapter != null) this.uiAdapter.dispose();
    }

    @Override
    protected void drawBackground(DrawContext context, float delta, int mouseX, int mouseY) {}

    @Override
    protected void drawSlotHighlightBack(DrawContext context) {
        context.push().translate(0, 0, this.getLayerZOffset(HandledScreenLayer.SLOT));
        super.drawSlotHighlightBack(context);
    }

    @Override
    protected void drawSlotHighlightFront(DrawContext context) {
        super.drawSlotHighlightFront(context);
        context.pop();
    }

    @Override
    protected void drawItem(DrawContext context, ItemStack stack, int x, int y, @Nullable String amountText) {
        context.push().translate(0, 0, this.getLayerZOffset(HandledScreenLayer.CURSOR_ITEM));
        super.drawItem(context, stack, x, y, amountText);
        context.pop();
    }

    @Override
    protected void drawMouseoverTooltip(DrawContext context, int x, int y) {
        context.push().translate(0, 0, this.getLayerZOffset(HandledScreenLayer.ITEM_TOOLTIP));
        super.drawMouseoverTooltip(context, x, y);
        context.pop();
    }

    /**
     * Return the z-offset to apply to rendering the given {@code layer}
     */
    protected int getLayerZOffset(HandledScreenLayer layer) {
        return 300;
    }

    /**
     * Different layers of handled screen rendering, the z-offset
     * of which can be adjusted in an owo screen using {@link #getLayerZOffset(HandledScreenLayer)}
     */
    protected enum HandledScreenLayer {
        /**
         * The items in all slots, along with the highlight
         * of the hovered slot
         */
        SLOT,

        /**
         * The item currently held by the cursor. More specifically, any item
         * rendered through the {@link #drawItem(DrawContext, ItemStack, int, int, String)} method
         */
        CURSOR_ITEM,

        /**
         * The tooltip of an item in a slot. More specifically, any tooltip
         * rendered through {@link #drawMouseoverTooltip(DrawContext, int, int)}
         */
        ITEM_TOOLTIP
    }

    public class SlotComponent extends BaseComponent {

        protected final Slot slot;
        protected boolean didDraw = false;

        protected SlotComponent(int index) {
            this.slot = BaseOwoHandledScreen.this.handler.getSlot(index);
        }

        @Override
        public void draw(OwoUIDrawContext context, int mouseX, int mouseY, float partialTicks, float delta) {
            this.didDraw = true;

            int[] scissor = new int[4];
            GL11.glGetIntegerv(GL11.GL_SCISSOR_BOX, scissor);

            ((OwoSlotExtension) this.slot).owo$setScissorArea(PositionedRectangle.of(
                scissor[0], scissor[1], scissor[2], scissor[3]
            ));
        }

        @Override
        public void update(float delta, int mouseX, int mouseY) {
            super.update(delta, mouseX, mouseY);

            ((OwoSlotExtension) this.slot).owo$setDisabledOverride(!this.didDraw);

            this.didDraw = false;
        }

        @Override
        public void drawTooltip(OwoUIDrawContext context, int mouseX, int mouseY, float partialTicks, float delta) {
            if (!this.slot.hasStack()) {
                super.drawTooltip(context, mouseX, mouseY, partialTicks, delta);
            }
        }

        @Override
        public boolean shouldDrawTooltip(double mouseX, double mouseY) {
            return super.shouldDrawTooltip(mouseX, mouseY);
        }

        @Override
        protected int determineHorizontalContentSize(Sizing sizing) {
            return 16;
        }

        @Override
        protected int determineVerticalContentSize(Sizing sizing) {
            return 16;
        }

        @Override
        public void updateX(int x) {
            super.updateX(x);
            ((SlotAccessor) this.slot).owo$setX(x - BaseOwoHandledScreen.this.x);
        }

        @Override
        public void updateY(int y) {
            super.updateY(y);
            ((SlotAccessor) this.slot).owo$setY(y - BaseOwoHandledScreen.this.y);
        }
    }
}