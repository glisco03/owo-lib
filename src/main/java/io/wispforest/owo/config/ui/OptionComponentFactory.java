package io.wispforest.owo.config.ui;

import io.wispforest.owo.config.Option;
import io.wispforest.owo.config.annotation.RangeConstraint;
import io.wispforest.owo.config.annotation.WithAlpha;
import io.wispforest.owo.config.ui.component.ListOptionContainer;
import io.wispforest.owo.config.ui.component.OptionValueProvider;
import io.wispforest.owo.ui.component.BoxComponent;
import io.wispforest.owo.ui.component.ButtonComponent;
import io.wispforest.owo.ui.component.ColorPickerComponent;
import io.wispforest.owo.ui.component.Components;
import io.wispforest.owo.ui.container.Containers;
import io.wispforest.owo.ui.container.FlowLayout;
import io.wispforest.owo.ui.core.*;
import io.wispforest.owo.ui.parsing.UIModel;
import io.wispforest.owo.util.NumberReflection;
import net.minecraft.util.Identifier;

import java.util.List;
import java.util.Map;
import java.util.function.Supplier;

/**
 * A function which creates an instance of {@link OptionValueProvider}
 * fitting for the given config option. Whatever component is created
 * should accurately reflect if the option is currently detached
 * and thus immutable - ideally it is non-interactable
 *
 * @param <T> The type of option for which this factory can create components
 */
public interface OptionComponentFactory<T> {

    OptionComponentFactory<? extends Number> NUMBER = (model, option) -> {
        var field = option.backingField().field();

        if (field.isAnnotationPresent(RangeConstraint.class)) {
            var constraint = field.getAnnotation(RangeConstraint.class);
            if (constraint.allowSlider() && constraint.min() != -Double.MAX_VALUE && constraint.max() != Double.MAX_VALUE) {
                return OptionComponents.createRangeControls(
                        model, option,
                        NumberReflection.isFloatingPointType(field.getType())
                                ? constraint.decimalPlaces()
                                : 0
                );
            }
        }
        return OptionComponents.createTextBox(model, option, configTextBox -> {
            configTextBox.configureForNumber(option.clazz());
        });
    };

    OptionComponentFactory<? extends CharSequence> STRING = (model, option) -> {
        return OptionComponents.createTextBox(model, option, configTextBox -> {
            if (option.constraint() != null) {
                configTextBox.applyPredicate(option.constraint()::test);
            }
        });
    };

    OptionComponentFactory<Identifier> IDENTIFIER = (model, option) -> {
        return OptionComponents.createTextBox(model, option, configTextBox -> {
            configTextBox.inputPredicate(s -> s.matches("[a-z0-9_.:\\-]*"));
            configTextBox.applyPredicate(s -> Identifier.tryParse(s) != null);
            configTextBox.valueParser(Identifier::of);
        });
    };

    @SuppressWarnings("DataFlowIssue")
    OptionComponentFactory<Color> COLOR = (model, option) -> {
        boolean withAlpha = option.backingField().hasAnnotation(WithAlpha.class);

        final var result = OptionComponents.createTextBox(model, option, color -> color.asHexString(withAlpha), configTextBox -> {
            configTextBox.inputPredicate(withAlpha ? s -> s.matches("#[a-zA-Z\\d]{0,8}") : s -> s.matches("#[a-zA-Z\\d]{0,6}"));
            configTextBox.applyPredicate(withAlpha ? s -> s.matches("#[a-zA-Z\\d]{8}") : s -> s.matches("#[a-zA-Z\\d]{6}"));
            configTextBox.valueParser(withAlpha
                    ? s -> Color.ofArgb(Integer.parseUnsignedInt(s.substring(1), 16))
                    : s -> Color.ofRgb(Integer.parseUnsignedInt(s.substring(1), 16))
            );
        });

        result.baseComponent.childById(FlowLayout.class, "controls-flow").<FlowLayout>configure(controls -> {
            Supplier<Color> valueGetter = () -> result.optionProvider.isValid()
                    ? (Color) result.optionProvider.parsedValue()
                    : Color.BLACK;

            var box = Components.box(Sizing.fixed(15), Sizing.fixed(15)).color(valueGetter.get()).fill(true);
            box.margins(Insets.right(5)).cursorStyle(CursorStyle.HAND);
            controls.child(0, box);

            result.optionProvider.onChanged().subscribe(value -> box.color(valueGetter.get()));

            box.mouseDown().subscribe((mouseX, mouseY, button) -> {
                ((FlowLayout) box.root()).child(Containers.overlay(
                        model.expandTemplate(
                                FlowLayout.class,
                                "color-picker-panel",
                                Map.of("color", valueGetter.get().asHexString(withAlpha), "with-alpha", String.valueOf(withAlpha))
                        ).<FlowLayout>configure(flowLayout -> {
                            var picker = flowLayout.childById(ColorPickerComponent.class, "color-picker");
                            var previewBox = flowLayout.childById(BoxComponent.class, "current-color");

                            picker.onChanged().subscribe(previewBox::color);

                            flowLayout.childById(ButtonComponent.class, "confirm-button").onPress(confirmButton -> {
                                result.optionProvider.text(picker.selectedColor().asHexString(withAlpha));
                                flowLayout.parent().remove();
                            });

                            flowLayout.childById(ButtonComponent.class, "cancel-button").onPress(cancelButton -> {
                                flowLayout.parent().remove();
                            });
                        })
                ).zIndex(100));

                return true;
            });
        });

        return result;
    };

    OptionComponentFactory<Boolean> BOOLEAN = OptionComponents::createToggleButton;

    OptionComponentFactory<? extends Enum<?>> ENUM = OptionComponents::createEnumButton;

    @SuppressWarnings({"unchecked", "rawtypes"})
    OptionComponentFactory<List<?>> LIST = (model, option) -> {
        var layout = new ListOptionContainer(option);
        return new Result(layout, layout);
    };

    /**
     * Create a new component fitting for, and bound to,
     * the given config option
     *
     * @param model  The UI model of the enclosing screen, used
     *               for expanding templates
     * @param option The option for which to create a component
     * @return The option component as well as a potential wrapping
     * component, this simply be the option component itself
     */
    Result<?, ?> make(UIModel model, Option<T> option);

    record Result<B extends Component, P extends OptionValueProvider>(B baseComponent, P optionProvider) {}
}
