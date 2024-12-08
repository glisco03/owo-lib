package io.wispforest.owo.ui.component;

import io.wispforest.owo.Owo;
import io.wispforest.owo.mixin.ui.access.EditBoxAccessor;
import io.wispforest.owo.mixin.ui.access.EditBoxWidgetAccessor;
import io.wispforest.owo.ui.core.CursorStyle;
import io.wispforest.owo.ui.core.Size;
import io.wispforest.owo.ui.core.Sizing;
import io.wispforest.owo.ui.parsing.UIModel;
import io.wispforest.owo.ui.parsing.UIParsing;
import io.wispforest.owo.util.EventSource;
import io.wispforest.owo.util.EventStream;
import io.wispforest.owo.util.Observable;
import net.minecraft.client.MinecraftClient;
import net.minecraft.client.gui.DrawContext;
import net.minecraft.client.gui.EditBox;
import net.minecraft.client.gui.widget.EditBoxWidget;
import net.minecraft.client.input.CursorMovement;
import net.minecraft.client.util.math.MatrixStack;
import net.minecraft.text.Text;
import org.lwjgl.glfw.GLFW;
import org.w3c.dom.Element;

import java.util.Map;
import java.util.function.Consumer;

public class TextAreaComponent extends EditBoxWidget {

    protected final Observable<String> textValue = Observable.of("");
    protected final EventStream<OnChanged> changedEvents = OnChanged.newStream();
    protected final EditBox editBox;

    protected final Observable<Boolean> displayCharCount = Observable.of(false);
    protected final Observable<Integer> maxLines = Observable.of(-1);

    protected TextAreaComponent(Sizing horizontalSizing, Sizing verticalSizing) {
        super(MinecraftClient.getInstance().textRenderer, 0, 0, 0, 0, Text.empty(), Text.empty());
        this.editBox = ((EditBoxWidgetAccessor) this).owo$getEditBox();
        this.sizing(horizontalSizing, verticalSizing);

        this.textValue.observe(this.changedEvents.sink()::onChanged);
        Observable.observeAll(this.widgetWrapper()::notifyParentIfMounted, this.displayCharCount, this.maxLines);

        super.setChangeListener(s -> {
            this.textValue.set(s);

            if (this.maxLines.get() < 0) return;
            this.widgetWrapper().notifyParentIfMounted();
        });
    }

    @Override
    @Deprecated(forRemoval = true)
    public void setChangeListener(Consumer<String> changeListener) {
        Owo.debugWarn(Owo.LOGGER, "setChangeListener stub on TextAreaComponent invoked");
    }

    @Override
    public void update(float delta, int mouseX, int mouseY) {
        super.update(delta, mouseX, mouseY);
        this.cursorStyle(this.overflows() && mouseX >= this.getX() + this.width - 9 ? CursorStyle.NONE : CursorStyle.TEXT);
    }

    @Override
    protected void renderOverlay(DrawContext context) {
        this.height -= 1;

        var matrices = context.getMatrices();
        matrices.push();
        matrices.translate(-9, 1, 0);

        int previousMaxLength = this.editBox.getMaxLength();
        this.editBox.setMaxLength(Integer.MAX_VALUE);

        super.renderOverlay(context);

        this.editBox.setMaxLength(previousMaxLength);

        matrices.pop();
        this.height += 1;

        if (this.displayCharCount.get()) {
            var text = this.editBox.hasMaxLength()
                    ? Text.translatable("gui.multiLineEditBox.character_limit", this.editBox.getText().length(), this.editBox.getMaxLength())
                    : Text.literal(String.valueOf(this.editBox.getText().length()));

            var textRenderer = MinecraftClient.getInstance().textRenderer;
            context.drawTextWithShadow(textRenderer, text, this.getX() + this.width - textRenderer.getWidth(text), this.getY() + this.height + 3, 0xa0a0a0);
        }
    }

    @Override
    public boolean mouseClicked(double mouseX, double mouseY, int button) {
        this.width -= 9;
        var result = super.mouseClicked(mouseX, mouseY, button);
        this.width += 9;

        return result;
    }

    @Override
    public boolean keyPressed(int keyCode, int scanCode, int modifiers) {
        boolean result = super.keyPressed(keyCode, scanCode, modifiers);

        if (keyCode == GLFW.GLFW_KEY_TAB) {
            this.editBox.replaceSelection("    ");
            return true;
        } else {
            return result;
        }
    }

    @Override
    public void inflate(Size space) {
        super.inflate(space);

        int cursor = this.editBox.getCursor();
        int selection = ((EditBoxAccessor) this.editBox).owo$getSelectionEnd();

        ((EditBoxAccessor) this.editBox).owo$setWidth(this.width() - this.getPadding() - 9);
        this.editBox.setText(this.getText());

        super.inflate(space);
        this.editBox.setText(this.getText());

        this.editBox.moveCursor(CursorMovement.ABSOLUTE, cursor);
        ((EditBoxAccessor) this.editBox).owo$setSelectionEnd(selection);
    }

    public EventSource<OnChanged> onChanged() {
        return changedEvents.source();
    }

    public TextAreaComponent maxLines(int maxLines) {
        this.maxLines.set(maxLines);
        return this;
    }

    public int maxLines() {
        return this.maxLines.get();
    }

    public TextAreaComponent displayCharCount(boolean displayCharCount) {
        this.displayCharCount.set(displayCharCount);
        return this;
    }

    public boolean displayCharCount() {
        return this.displayCharCount.get();
    }

    public TextAreaComponent text(String text) {
        this.setText(text);
        return this;
    }

    @Override
    public int heightOffset() {
        return this.displayCharCount.get() ? -12 : 0;
    }

    @Override
    public void parseProperties(UIModel model, Element element, Map<String, Element> children) {
        super.parseProperties(model, element, children);

        UIParsing.apply(children, "display-char-count", UIParsing::parseBool, this::displayCharCount);
        UIParsing.apply(children, "max-length", UIParsing::parseUnsignedInt, this::setMaxLength);
        UIParsing.apply(children, "max-lines", UIParsing::parseUnsignedInt, this::maxLines);
        UIParsing.apply(children, "text", $ -> $.getTextContent().strip(), this::text);
    }

    public interface OnChanged {
        void onChanged(String value);

        static EventStream<OnChanged> newStream() {
            return new EventStream<>(subscribers -> value -> {
                for (var subscriber : subscribers) {
                    subscriber.onChanged(value);
                }
            });
        }
    }
}
