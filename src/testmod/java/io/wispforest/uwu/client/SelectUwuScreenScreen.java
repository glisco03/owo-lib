package io.wispforest.uwu.client;

import io.wispforest.owo.config.ui.ConfigScreen;
import io.wispforest.owo.ui.base.BaseOwoScreen;
import io.wispforest.owo.ui.base.BaseUIModelScreen;
import io.wispforest.owo.ui.component.Components;
import io.wispforest.owo.ui.container.Containers;
import io.wispforest.owo.ui.container.FlowLayout;
import io.wispforest.owo.ui.core.*;
import io.wispforest.uwu.Uwu;
import net.minecraft.text.Text;
import net.minecraft.util.Identifier;
import org.jetbrains.annotations.NotNull;

public class SelectUwuScreenScreen extends BaseOwoScreen<FlowLayout> {

    @Override
    protected @NotNull OwoUIAdapter<FlowLayout> createAdapter() {
        return OwoUIAdapter.create(this, Containers::verticalFlow);
    }

    @Override
    protected void build(FlowLayout rootComponent) {
        this.uiAdapter.rootComponent.surface(Surface.flat(0x77000000))
                .verticalAlignment(VerticalAlignment.CENTER)
                .horizontalAlignment(HorizontalAlignment.CENTER);

        this.uiAdapter.rootComponent.child(
                Components.label(Text.literal("Available screens"))
                        .shadow(true)
                        .margins(Insets.bottom(5))
        );

        var panel = Containers.verticalFlow(Sizing.content(), Sizing.content()).<FlowLayout>configure(layout -> {
            layout.gap(6)
                    .padding(Insets.of(5))
                    .surface(Surface.PANEL)
                    .horizontalAlignment(HorizontalAlignment.CENTER);
        });

        panel.child(Components.button(Text.literal("code demo"), button -> this.client.setScreen(new ComponentTestScreen())));
        panel.child(Components.button(Text.literal("xml demo"), button -> this.client.setScreen(new TestParseScreen())));
        panel.child(Components.button(Text.literal("code config"), button -> this.client.setScreen(new TestConfigScreen())));
        panel.child(Components.button(Text.literal("xml config"), button -> this.client.setScreen(ConfigScreen.create(Uwu.CONFIG, null))));
        panel.child(Components.button(Text.literal("optimization test"), button -> this.client.setScreen(new TooManyComponentsScreen())));
        panel.child(Components.button(Text.literal("focus cycle test"), button -> this.client.setScreen(new BaseUIModelScreen<>(FlowLayout.class, Identifier.of("uwu", "focus_cycle_test")) {
            @Override
            protected void build(FlowLayout rootComponent) {}
        })));
        panel.child(Components.button(Text.literal("expand gap test"), button -> this.client.setScreen(new BaseUIModelScreen<>(FlowLayout.class, Identifier.of("uwu", "expand_gap_test")) {
            @Override
            protected void build(FlowLayout rootComponent) {}
        })));
        panel.child(Components.button(Text.literal("smolnite"), button -> this.client.setScreen(new SmolComponentTestScreen())));
        panel.child(Components.button(Text.literal("sizenite"), button -> this.client.setScreen(new SizingTestScreen())));
        panel.child(Components.button(Text.literal("parse fail"), button -> this.client.setScreen(new ParseFailScreen())));
        panel.child(Components.button(Text.literal("blocks"), button -> this.client.setScreen(new BlockTestScreen())));

        this.uiAdapter.rootComponent.child(panel);
    }
}
