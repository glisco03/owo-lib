package io.wispforest.owo.ui.container;

import io.wispforest.owo.ui.component.Components;
import io.wispforest.owo.ui.component.LabelComponent;
import io.wispforest.owo.ui.core.*;
import io.wispforest.owo.ui.parsing.UIParsing;
import io.wispforest.owo.ui.util.Delta;
import io.wispforest.owo.ui.util.UISounds;
import io.wispforest.owo.util.EventSource;
import io.wispforest.owo.util.EventStream;
import net.minecraft.text.Text;
import net.minecraft.util.Formatting;
import net.minecraft.util.math.RotationAxis;
import org.lwjgl.glfw.GLFW;
import org.w3c.dom.Element;

import java.util.ArrayList;
import java.util.Collection;
import java.util.Collections;
import java.util.List;

public class CollapsibleContainer extends FlowLayout {

    public static final Surface SURFACE = (context, component) -> context.fill(
            component.x() + 5,
            component.y(),
            component.x() + 6,
            component.y() + component.height(),
            0x77FFFFFF
    );

    protected final EventStream<OnToggled> toggledEvents = OnToggled.newStream();

    protected final List<Component> collapsibleChildren = new ArrayList<>();
    protected final List<Component> collapsibleChildrenView = Collections.unmodifiableList(this.collapsibleChildren);
    protected boolean expanded;

    protected final SpinnyBoiComponent spinnyBoi;
    protected final FlowLayout titleLayout;
    protected final FlowLayout contentLayout;

    protected CollapsibleContainer(Sizing horizontalSizing, Sizing verticalSizing, Text title, boolean expanded) {
        super(horizontalSizing, verticalSizing, Algorithm.VERTICAL);

        // Title

        this.titleLayout = Containers.horizontalFlow(Sizing.content(), Sizing.content());
        this.titleLayout.padding(Insets.of(5, 5, 5, 0));
        this.allowOverflow(true);

        title = title.copy().formatted(Formatting.UNDERLINE);
        this.titleLayout.child(Components.label(title).cursorStyle(CursorStyle.HAND));

        this.spinnyBoi = new SpinnyBoiComponent();
        this.titleLayout.child(spinnyBoi);

        this.expanded = expanded;
        this.spinnyBoi.targetRotation = expanded ? 90 : 0;
        this.spinnyBoi.rotation = this.spinnyBoi.targetRotation;

        super.child(this.titleLayout);

        // Content

        this.contentLayout = Containers.verticalFlow(Sizing.content(), Sizing.content());
        this.contentLayout.padding(Insets.left(15));
        this.contentLayout.surface(SURFACE);

        super.child(this.contentLayout);
    }

    public FlowLayout titleLayout() {
        return this.titleLayout;
    }

    public List<Component> collapsibleChildren() {
        return this.collapsibleChildrenView;
    }

    public boolean expanded() {
        return this.expanded;
    }

    public EventSource<OnToggled> onToggled() {
        return this.toggledEvents.source();
    }

    public void toggleExpansion() {
        if (expanded) {
            this.contentLayout.clearChildren();
            this.spinnyBoi.targetRotation = 0;
        } else {
            this.contentLayout.children(this.collapsibleChildren);
            this.spinnyBoi.targetRotation = 90;
        }

        this.expanded = !this.expanded;
        this.toggledEvents.sink().onToggle(this.expanded);
    }

    @Override
    public boolean canFocus(FocusSource source) {
        return source == FocusSource.KEYBOARD_CYCLE;
    }

    @Override
    public boolean onKeyPress(int keyCode, int scanCode, int modifiers) {
        if (keyCode == GLFW.GLFW_KEY_SPACE || keyCode == GLFW.GLFW_KEY_ENTER || keyCode == GLFW.GLFW_KEY_KP_ENTER) {
            this.toggleExpansion();

            super.onKeyPress(keyCode, scanCode, modifiers);
            return true;
        }

        return super.onKeyPress(keyCode, scanCode, modifiers);
    }

    @Override
    public boolean onMouseDown(double mouseX, double mouseY, int button) {
        final var superResult = super.onMouseDown(mouseX, mouseY, button);

        if (mouseY <= this.titleLayout.fullSize().height() && !superResult) {
            this.toggleExpansion();
            UISounds.playInteractionSound();
            return true;
        } else {
            return superResult;
        }
    }

    @Override
    public FlowLayout child(Component child) {
        this.collapsibleChildren.add(child);
        if (this.expanded) this.contentLayout.child(child);
        return this;
    }

    @Override
    public FlowLayout children(Collection<? extends Component> children) {
        this.collapsibleChildren.addAll(children);
        if (this.expanded) this.contentLayout.children(children);
        return this;
    }

    @Override
    public FlowLayout child(int index, Component child) {
        this.collapsibleChildren.add(index, child);
        if (this.expanded) this.contentLayout.child(index, child);
        return this;
    }

    @Override
    public FlowLayout children(int index, Collection<? extends Component> children) {
        this.collapsibleChildren.addAll(index, children);
        if (this.expanded) this.contentLayout.children(index, children);
        return this;
    }

    @Override
    public FlowLayout removeChild(Component child) {
        this.collapsibleChildren.remove(child);
        return this.contentLayout.removeChild(child);
    }

    public static CollapsibleContainer parse(Element element) {
        var textElement = UIParsing.childElements(element).get("text");
        var title = textElement == null ? Text.empty() : UIParsing.parseText(textElement);

        return element.getAttribute("expanded").equals("true")
                ? Containers.collapsible(Sizing.content(), Sizing.content(), title, true)
                : Containers.collapsible(Sizing.content(), Sizing.content(), title, false);
    }

    public interface OnToggled {
        void onToggle(boolean nowExpanded);

        static EventStream<OnToggled> newStream() {
            return new EventStream<>(subscribers -> nowExpanded -> {
                for (var subscriber : subscribers) {
                    subscriber.onToggle(nowExpanded);
                }
            });
        }
    }

    public static class SpinnyBoiComponent extends LabelComponent {

        public float rotation = 90;
        public float targetRotation = 90;

        public SpinnyBoiComponent() {
            super(Text.literal(">"));
            this.margins(Insets.of(0, 0, 5, 10));
            this.cursorStyle(CursorStyle.HAND);
        }

        @Override
        public void update(float delta, int mouseX, int mouseY) {
            super.update(delta, mouseX, mouseY);
            this.rotation += Delta.compute(this.rotation, this.targetRotation, delta * .65);
        }

        @Override
        public void draw(OwoUIDrawContext context, int mouseX, int mouseY, float partialTicks, float delta) {
            var matrices = context.getMatrices();

            matrices.push();
            matrices.translate(this.x + this.width / 2f - 1, this.y + this.height / 2f - 1, 0);
            matrices.multiply(RotationAxis.POSITIVE_Z.rotationDegrees(this.rotation));
            matrices.translate(-(this.x + this.width / 2f - 1), -(this.y + this.height / 2f - 1), 0);

            super.draw(context, mouseX, mouseY, partialTicks, delta);
            matrices.pop();
        }
    }
}
