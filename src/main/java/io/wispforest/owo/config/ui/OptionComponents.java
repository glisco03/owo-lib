package io.wispforest.owo.config.ui;

import io.wispforest.owo.config.Option;
import io.wispforest.owo.config.annotation.RangeConstraint;
import io.wispforest.owo.config.ui.component.*;
import io.wispforest.owo.ui.component.ButtonComponent;
import io.wispforest.owo.ui.component.LabelComponent;
import io.wispforest.owo.ui.container.FlowLayout;
import io.wispforest.owo.ui.core.Positioning;
import io.wispforest.owo.ui.parsing.UIModel;
import net.minecraft.text.Text;
import org.apache.commons.lang3.mutable.MutableBoolean;

import java.util.Map;
import java.util.function.Consumer;
import java.util.function.Function;

@SuppressWarnings("ConstantConditions")
public class OptionComponents {

    public static OptionComponentFactory.Result<FlowLayout, ConfigTextBox> createTextBox(UIModel model, Option<?> option, Consumer<ConfigTextBox> processor) {
        return createTextBox(model, option, Object::toString, processor);
    }

    public static <T> OptionComponentFactory.Result<FlowLayout, ConfigTextBox> createTextBox(UIModel model, Option<T> option, Function<T, String> toStringFunction, Consumer<ConfigTextBox> processor) {
        var optionComponent = model.expandTemplate(FlowLayout.class,
                "text-box-config-option",
                packParameters(option.translationKey(), toStringFunction.apply(option.value()))
        );

        var valueBox = optionComponent.childById(ConfigTextBox.class, "value-box");
        var resetButton = optionComponent.childById(ButtonComponent.class, "reset-button");

        if (option.detached()) {
            resetButton.active = false;
            valueBox.setEditable(false);
        } else {
            resetButton.active = !valueBox.getText().equals(toStringFunction.apply(option.defaultValue()));
            resetButton.onPress(button -> {
                valueBox.setText(toStringFunction.apply(option.defaultValue()));
                button.active = false;
            });

            valueBox.onChanged().subscribe(s -> resetButton.active = !s.equals(toStringFunction.apply(option.defaultValue())));
        }

        processor.accept(valueBox);

        optionComponent.child(new SearchAnchorComponent(
                optionComponent,
                option.key(),
                () -> optionComponent.childById(LabelComponent.class, "option-name").text().getString(),
                valueBox::getText
        ));

        return new OptionComponentFactory.Result<>(optionComponent, valueBox);
    }

    public static OptionComponentFactory.Result<FlowLayout, OptionValueProvider> createRangeControls(UIModel model, Option<? extends Number> option, int decimalPlaces) {
        boolean withDecimals = decimalPlaces > 0;

        // ------------
        // Slider setup
        // ------------

        var value = option.value();
        var optionComponent = model.expandTemplate(FlowLayout.class,
                "range-config-option",
                packParameters(option.translationKey(), value.toString())
        );

        var constraint = option.backingField().field().getAnnotation(RangeConstraint.class);
        double min = constraint.min(), max = constraint.max();

        var sliderInput = optionComponent.childById(ConfigSlider.class, "value-slider");
        sliderInput.min(min).max(max).decimalPlaces(decimalPlaces).snap(!withDecimals).setFromDiscreteValue(value.doubleValue());
        sliderInput.valueType(option.clazz());

        var resetButton = optionComponent.childById(ButtonComponent.class, "reset-button");

        if (option.detached()) {
            resetButton.active = false;
            sliderInput.active = false;
        } else {
            resetButton.active = (withDecimals ? value.doubleValue() : Math.round(value.doubleValue())) != option.defaultValue().doubleValue();
            resetButton.onPress(button -> {
                sliderInput.setFromDiscreteValue(option.defaultValue().doubleValue());
                button.active = false;
            });

            sliderInput.onChanged().subscribe(newValue -> {
                resetButton.active = (withDecimals ? newValue : Math.round(newValue)) != option.defaultValue().doubleValue();
            });
        }

        // ------------------------------------
        // Component handles and text box setup
        // ------------------------------------

        var sliderControls = optionComponent.childById(FlowLayout.class, "slider-controls");
        var textControls = createTextBox(model, option, configTextBox -> {
            configTextBox.configureForNumber(option.clazz());

            var predicate = configTextBox.applyPredicate();
            configTextBox.applyPredicate(predicate.and(s -> {
                final var parsed = Double.parseDouble(s);
                return parsed >= min && parsed <= max;
            }));
        }).baseComponent().childById(FlowLayout.class, "controls-flow").positioning(Positioning.layout());
        var textInput = textControls.childById(ConfigTextBox.class, "value-box");

        // ------------
        // Toggle setup
        // ------------

        var controlsLayout = optionComponent.childById(FlowLayout.class, "controls-flow");
        var toggleButton = optionComponent.childById(ButtonComponent.class, "toggle-button");

        var textMode = new MutableBoolean(false);

        Consumer<ButtonComponent> toggleAction = button -> {
            textMode.setValue(textMode.isFalse());

            if (textMode.isTrue()) {
                sliderControls.remove();
                textInput.text(sliderInput.decimalPlaces() == 0 ? String.valueOf((int) sliderInput.discreteValue()) : String.valueOf(sliderInput.discreteValue()));

                controlsLayout.child(textControls);
            } else {
                textControls.remove();
                sliderInput.setFromDiscreteValue(((Number) textInput.parsedValue()).doubleValue());

                controlsLayout.child(sliderControls);
            }

            button.tooltip(textMode.isTrue()
                    ? Text.translatable("text.owo.config.button.range.edit_with_slider")
                    : Text.translatable("text.owo.config.button.range.edit_as_text")
            );
        };
        toggleButton.onPress(toggleAction);

        if (constraint.defaultOption().equals(RangeConstraint.DefaultOptionType.TEXT_BOX)) toggleAction.accept(toggleButton);

        optionComponent.child(new SearchAnchorComponent(
                optionComponent,
                option.key(),
                () -> optionComponent.childById(LabelComponent.class, "option-name").text().getString(),
                () -> textMode.isTrue() ? textInput.getText() : sliderInput.getMessage().getString()
        ));

        return new OptionComponentFactory.Result<>(optionComponent, new OptionValueProvider() {
            @Override
            public boolean isValid() {
                return textMode.isTrue()
                        ? textInput.isValid()
                        : sliderInput.isValid();
            }

            @Override
            public Object parsedValue() {
                return textMode.isTrue()
                        ? textInput.parsedValue()
                        : sliderInput.parsedValue();
            }
        });
    }

    public static OptionComponentFactory.Result<FlowLayout, ConfigToggleButton> createToggleButton(UIModel model, Option<Boolean> option) {
        var optionComponent = model.expandTemplate(FlowLayout.class,
                "boolean-toggle-config-option",
                packParameters(option.translationKey(), option.value().toString())
        );

        var toggleButton = optionComponent.childById(ConfigToggleButton.class, "toggle-button");
        var resetButton = optionComponent.childById(ButtonComponent.class, "reset-button");

        toggleButton.enabled(option.value());

        if (option.detached()) {
            resetButton.active = false;
            toggleButton.active = false;
        } else {
            resetButton.active = option.value() != option.defaultValue();
            resetButton.onPress(button -> {
                toggleButton.enabled(option.defaultValue());
                button.active = false;
            });

            toggleButton.onPress(button -> resetButton.active = toggleButton.parsedValue() != option.defaultValue());
        }

        optionComponent.child(new SearchAnchorComponent(
                optionComponent,
                option.key(),
                () -> optionComponent.childById(LabelComponent.class, "option-name").text().getString(),
                () -> toggleButton.getMessage().getString()
        ));

        return new OptionComponentFactory.Result<>(optionComponent, toggleButton);
    }

    public static OptionComponentFactory.Result<FlowLayout, ConfigEnumButton> createEnumButton(UIModel model, Option<? extends Enum<?>> option) {
        var optionComponent = model.expandTemplate(FlowLayout.class,
                "enum-config-option",
                packParameters(option.translationKey(), option.value().toString())
        );

        var enumButton = optionComponent.childById(ConfigEnumButton.class, "enum-button");
        var resetButton = optionComponent.childById(ButtonComponent.class, "reset-button");

        enumButton.init(option, option.value().ordinal());

        if (option.detached()) {
            resetButton.active = false;
            enumButton.active = false;
        } else {
            resetButton.active = option.value() != option.defaultValue();
            resetButton.onPress(button -> {
                enumButton.select(option.defaultValue().ordinal());
                button.active = false;
            });

            enumButton.onPress(button -> resetButton.active = enumButton.parsedValue() != option.defaultValue());
        }

        optionComponent.child(new SearchAnchorComponent(
                optionComponent,
                option.key(),
                () -> optionComponent.childById(LabelComponent.class, "option-name").text().getString(),
                () -> enumButton.getMessage().getString()
        ));

        return new OptionComponentFactory.Result<>(optionComponent, enumButton);
    }

    public static Map<String, String> packParameters(String name, String value) {
        return Map.of(
                "config-option-name", name,
                "config-option-value", value
        );
    }
}
