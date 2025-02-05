package io.wispforest.owo.ui.component;

import io.wispforest.owo.ui.base.BaseComponent;
import io.wispforest.owo.ui.core.*;
import io.wispforest.owo.ui.parsing.UIModel;
import io.wispforest.owo.ui.parsing.UIParsing;
import io.wispforest.owo.util.Observable;
import net.minecraft.client.MinecraftClient;
import net.minecraft.client.font.TextRenderer;
import net.minecraft.text.OrderedText;
import net.minecraft.text.Style;
import net.minecraft.text.Text;
import org.w3c.dom.Element;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.function.Function;

public class LabelComponent extends BaseComponent {

    protected final TextRenderer textRenderer = MinecraftClient.getInstance().textRenderer;

    protected Text text;
    protected List<OrderedText> wrappedText;

    protected VerticalAlignment verticalTextAlignment = VerticalAlignment.TOP;
    protected HorizontalAlignment horizontalTextAlignment = HorizontalAlignment.LEFT;

    protected final AnimatableProperty<Color> color = AnimatableProperty.of(Color.WHITE);
    protected final Observable<Integer> lineHeight = Observable.of(this.textRenderer.fontHeight);
    protected final Observable<Integer> lineSpacing = Observable.of(2);
    protected boolean shadow;
    protected int maxWidth;

    protected Function<Style, Boolean> textClickHandler = style -> {
        OwoUIDrawContext.utilityScreen().captureLinkSource();
        return OwoUIDrawContext.utilityScreen().handleTextClick(style);
    };

    protected LabelComponent(Text text) {
        this.text = text;
        this.wrappedText = new ArrayList<>();

        this.shadow = false;
        this.maxWidth = Integer.MAX_VALUE;

        Observable.observeAll(this::notifyParentIfMounted, this.lineHeight, this.lineSpacing);
    }

    public LabelComponent text(Text text) {
        this.text = text;
        this.notifyParentIfMounted();
        return this;
    }

    public Text text() {
        return this.text;
    }

    public LabelComponent maxWidth(int maxWidth) {
        this.maxWidth = maxWidth;
        this.notifyParentIfMounted();
        return this;
    }

    public int maxWidth() {
        return this.maxWidth;
    }

    public LabelComponent shadow(boolean shadow) {
        this.shadow = shadow;
        return this;
    }

    public boolean shadow() {
        return this.shadow;
    }

    public LabelComponent color(Color color) {
        this.color.set(color);
        return this;
    }

    public AnimatableProperty<Color> color() {
        return this.color;
    }

    public LabelComponent verticalTextAlignment(VerticalAlignment verticalAlignment) {
        this.verticalTextAlignment = verticalAlignment;
        return this;
    }

    public VerticalAlignment verticalTextAlignment() {
        return this.verticalTextAlignment;
    }

    public LabelComponent horizontalTextAlignment(HorizontalAlignment horizontalAlignment) {
        this.horizontalTextAlignment = horizontalAlignment;
        return this;
    }

    public HorizontalAlignment horizontalTextAlignment() {
        return this.horizontalTextAlignment;
    }

    public LabelComponent lineHeight(int lineHeight) {
        this.lineHeight.set(lineHeight);
        return this;
    }

    public int lineHeight() {
        return this.lineHeight.get();
    }

    public LabelComponent lineSpacing(int lineSpacing) {
        this.lineSpacing.set(lineSpacing);
        return this;
    }

    public int lineSpacing() {
        return this.lineSpacing.get();
    }

    public LabelComponent textClickHandler(Function<Style, Boolean> textClickHandler) {
        this.textClickHandler = textClickHandler;
        return this;
    }

    public Function<Style, Boolean> textClickHandler() {
        return textClickHandler;
    }

    @Override
    protected int determineHorizontalContentSize(Sizing sizing) {
        int widestText = 0;
        for (var line : this.wrappedText) {
            int width = this.textRenderer.getWidth(line);
            if (width > widestText) widestText = width;
        }

        if (widestText > this.maxWidth) {
            this.wrapLines();
            return this.determineHorizontalContentSize(sizing);
        } else {
            return widestText;
        }
    }

    @Override
    protected int determineVerticalContentSize(Sizing sizing) {
        this.wrapLines();
        return this.textHeight();
    }

    @Override
    public void inflate(Size space) {
        super.inflate(space);
        this.wrapLines();
        super.inflate(space);
    }

    private void wrapLines() {
        this.wrappedText = this.textRenderer.wrapLines(this.text, this.horizontalSizing.get().isContent() ? this.maxWidth : this.width);
    }

    protected int textHeight() {
        return (this.wrappedText.size() * (this.lineHeight() + this.lineSpacing())) - this.lineSpacing();
    }

    @Override
    public void update(float delta, int mouseX, int mouseY) {
        super.update(delta, mouseX, mouseY);
        this.color.update(delta);
    }

    @Override
    public void draw(OwoUIDrawContext context, int mouseX, int mouseY, float partialTicks, float delta) {
        var matrices = context.getMatrices();

        matrices.push();
        matrices.translate(0, 1 / MinecraftClient.getInstance().getWindow().getScaleFactor(), 0);

        int x = this.x;
        int y = this.y;

        if (this.horizontalSizing.get().isContent()) {
            x += this.horizontalSizing.get().value;
        }
        if (this.verticalSizing.get().isContent()) {
            y += this.verticalSizing.get().value;
        }

        switch (this.verticalTextAlignment) {
            case CENTER -> y += (this.height - (this.textHeight())) / 2;
            case BOTTOM -> y += this.height - (this.textHeight());
        }

        final int lambdaX = x;
        final int lambdaY = y;

        context.draw((vertexConsumerProvider) -> {
            for (int i = 0; i < this.wrappedText.size(); i++) {
                var renderText = this.wrappedText.get(i);
                int renderX = lambdaX;

                switch (this.horizontalTextAlignment) {
                    case CENTER -> renderX += (this.width - this.textRenderer.getWidth(renderText)) / 2;
                    case RIGHT -> renderX += this.width - this.textRenderer.getWidth(renderText);
                }

                int renderY = lambdaY + i * (this.lineHeight() + this.lineSpacing());
                renderY += this.lineHeight() - this.textRenderer.fontHeight;

                context.drawText(this.textRenderer, renderText, renderX, renderY, this.color.get().argb(), this.shadow);
            }
        });

        matrices.pop();
    }

    @Override
    public void drawTooltip(OwoUIDrawContext context, int mouseX, int mouseY, float partialTicks, float delta) {
        super.drawTooltip(context, mouseX, mouseY, partialTicks, delta);
        context.drawHoverEvent(this.textRenderer, this.styleAt(mouseX - this.x, mouseY - this.y), mouseX, mouseY);
    }

    @Override
    public boolean shouldDrawTooltip(double mouseX, double mouseY) {
        var hoveredStyle = this.styleAt((int) (mouseX - this.x), (int) (mouseY - this.y));
        return super.shouldDrawTooltip(mouseX, mouseY) || (hoveredStyle != null && hoveredStyle.getHoverEvent() != null && this.isInBoundingBox(mouseX, mouseY));
    }

    @Override
    public boolean onMouseDown(double mouseX, double mouseY, int button) {
        return this.textClickHandler.apply(this.styleAt((int) mouseX, (int) mouseY)) | super.onMouseDown(mouseX, mouseY, button);
    }

    protected Style styleAt(int mouseX, int mouseY) {
        return this.textRenderer.getTextHandler().getStyleAt(this.wrappedText.get(Math.min(mouseY / (this.lineHeight() + this.lineSpacing()), this.wrappedText.size() - 1)), mouseX);
    }

    @Override
    public void parseProperties(UIModel model, Element element, Map<String, Element> children) {
        super.parseProperties(model, element, children);
        UIParsing.apply(children, "text", UIParsing::parseText, this::text);
        UIParsing.apply(children, "max-width", UIParsing::parseUnsignedInt, this::maxWidth);
        UIParsing.apply(children, "color", Color::parse, this::color);
        UIParsing.apply(children, "shadow", UIParsing::parseBool, this::shadow);
        UIParsing.apply(children, "line-height", UIParsing::parseUnsignedInt, this::lineHeight);
        UIParsing.apply(children, "line-spacing", UIParsing::parseUnsignedInt, this::lineSpacing);

        UIParsing.apply(children, "vertical-text-alignment", VerticalAlignment::parse, this::verticalTextAlignment);
        UIParsing.apply(children, "horizontal-text-alignment", HorizontalAlignment::parse, this::horizontalTextAlignment);
    }
}
