package io.wispforest.uwu.client;

import io.wispforest.owo.network.OwoNetChannel;
import io.wispforest.owo.particles.ClientParticles;
import io.wispforest.owo.particles.systems.ParticleSystemController;
import io.wispforest.owo.ui.component.ButtonComponent;
import io.wispforest.owo.ui.component.Components;
import io.wispforest.owo.ui.component.EntityComponent;
import io.wispforest.owo.ui.container.Containers;
import io.wispforest.owo.ui.container.FlowLayout;
import io.wispforest.owo.ui.core.*;
import io.wispforest.owo.ui.hud.Hud;
import io.wispforest.owo.ui.layers.Layer;
import io.wispforest.owo.ui.layers.Layers;
import io.wispforest.owo.ui.parsing.UIModel;
import io.wispforest.owo.ui.util.UISounds;
import io.wispforest.uwu.Uwu;
import io.wispforest.uwu.network.UwuNetworkExample;
import io.wispforest.uwu.network.UwuOptionalNetExample;
import net.minecraft.client.MinecraftClient;
import net.minecraft.client.gui.screen.GameMenuScreen;
import net.minecraft.client.gui.screen.ingame.HandledScreens;
import net.minecraft.client.gui.screen.ingame.InventoryScreen;
import net.minecraft.client.gui.widget.ButtonWidget;
import net.minecraft.client.gui.widget.TextFieldWidget;
import net.minecraft.client.option.KeyBinding;
import net.minecraft.entity.EntityType;
import net.minecraft.entity.passive.AllayEntity;
import net.minecraft.item.Items;
import net.minecraft.particle.ParticleTypes;
import net.minecraft.text.Text;
import net.minecraft.text.TranslatableTextContent;
import net.minecraft.util.Identifier;
import net.neoforged.api.distmarker.Dist;
import net.neoforged.bus.api.IEventBus;
import net.neoforged.fml.common.Mod;
import net.neoforged.neoforge.client.event.ClientTickEvent;
import net.neoforged.neoforge.client.event.RegisterKeyMappingsEvent;
import net.neoforged.neoforge.client.event.RegisterMenuScreensEvent;
import net.neoforged.neoforge.common.NeoForge;
import org.lwjgl.glfw.GLFW;

import java.nio.file.Path;
import java.util.Map;
import java.util.function.Supplier;

@Mod(value = "uwu", dist = Dist.CLIENT)
public class UwuClient {

    public UwuClient(IEventBus modBus) {
        UwuNetworkExample.Client.init(modBus);
        UwuOptionalNetExample.Client.init(modBus);

        modBus.addListener(RegisterMenuScreensEvent.class, event -> {
            event.register(Uwu.EPIC_SCREEN_HANDLER_TYPE, EpicHandledScreen::new);
//            HandledScreens.register(EPIC_SCREEN_HANDLER_TYPE, EpicHandledModelScreen::new);
        });

        final var binding = new KeyBinding("key.uwu.hud_test", GLFW.GLFW_KEY_J, "misc");
        final var bindingButCooler = new KeyBinding("key.uwu.hud_test_two", GLFW.GLFW_KEY_K, "misc");

        modBus.addListener(RegisterKeyMappingsEvent.class, event -> {
            event.register(binding);
            event.register(bindingButCooler);
        });

        final var hudComponentId = Identifier.of("uwu", "test_element");
        final Supplier<Component> hudComponent = () ->
                Containers.verticalFlow(Sizing.content(), Sizing.content())
                        .child(Components.item(Items.DIAMOND.getDefaultStack()).margins(Insets.of(3)))
                        .child(Components.label(Text.literal("epic stuff in hud")))
                        .child(Components.entity(Sizing.fixed(50), EntityType.ALLAY, null))
                        .alignment(HorizontalAlignment.CENTER, VerticalAlignment.CENTER)
                        .padding(Insets.of(5))
                        .surface(Surface.PANEL)
                        .margins(Insets.of(5))
                        .positioning(Positioning.relative(100, 25));

        final var coolerComponentId = Identifier.of("uwu", "test_element_two");
        final Supplier<Component> coolerComponent = () -> UIModel.load(Path.of("../src/testmod/resources/assets/uwu/owo_ui/test_element_two.xml")).expandTemplate(FlowLayout.class, "hud-element", Map.of());
        Hud.add(coolerComponentId, coolerComponent);

        NeoForge.EVENT_BUS.addListener(ClientTickEvent.Post.class, event -> {
            while (binding.wasPressed()) {
                if (Hud.hasComponent(hudComponentId)) {
                    Hud.remove(hudComponentId);
                } else {
                    Hud.add(hudComponentId, hudComponent);
                }
            }

            if (bindingButCooler.wasPressed()) {
                Hud.remove(coolerComponentId);
                Hud.add(coolerComponentId, coolerComponent);

                //noinspection StatementWithEmptyBody
                while (bindingButCooler.wasPressed()) {}
            }
        });

        Uwu.CHANNEL.registerClientbound(Uwu.OtherTestMessage.class, (message, access) -> {
            access.player().sendMessage(Text.of("Message '" + message.message() + "' from " + message.pos()), false);
        });

        if (Uwu.WE_TESTEN_HANDSHAKE) {
            OwoNetChannel.create(Identifier.of("uwu", "client_only_channel"));

            Uwu.CHANNEL.registerServerbound(WeirdMessage.class, (data, access) -> {
            });
            Uwu.CHANNEL.registerClientbound(WeirdMessage.class, (data, access) -> {
            });

            new ParticleSystemController(Identifier.of("uwu", "client_only_particles"));
            Uwu.PARTICLE_CONTROLLER.register(WeirdMessage.class, (world, pos, data) -> {
            });
        }

        Uwu.CUBE.setHandler((world, pos, data) -> {
            ClientParticles.setParticleCount(5);
            ClientParticles.spawnCubeOutline(ParticleTypes.END_ROD, world, pos, 1, .01f);
        });

        Layers.add(Containers::verticalFlow, instance -> {
            if (MinecraftClient.getInstance().world == null) return;

            instance.adapter.rootComponent.child(
                    Containers.horizontalFlow(Sizing.content(), Sizing.content())
                            .child(Components.entity(Sizing.fixed(20), EntityType.ALLAY, null).<EntityComponent<AllayEntity>>configure(component -> {
                                component.allowMouseRotation(true)
                                        .scale(.75f);

                                component.mouseDown().subscribe((mouseX, mouseY, button) -> {
                                    UISounds.playInteractionSound();
                                    return true;
                                });
                            })).child(Components.textBox(Sizing.fixed(100), "allay text").<TextFieldWidget>configure(textBox -> {
                                textBox.verticalSizing(Sizing.fixed(9));
                                textBox.setDrawsBackground(false);
                            })).<FlowLayout>configure(layout -> {
                                layout.gap(5).margins(Insets.left(4)).verticalAlignment(VerticalAlignment.CENTER);

                                instance.alignComponentToWidget(widget -> {
                                    if (!(widget instanceof ButtonWidget button)) return false;
                                    return button.getMessage().getContent() instanceof TranslatableTextContent translatable && translatable.getKey().equals("gui.stats");
                                }, Layer.Instance.AnchorSide.RIGHT, 0, layout);
                            })
            );
        }, GameMenuScreen.class);

        Layers.add(Containers::verticalFlow, instance -> {
            ButtonComponent button;
            instance.adapter.rootComponent.child(
                    (button = Components.button(Text.literal(":)"), buttonComponent -> {
                        MinecraftClient.getInstance().player.sendMessage(Text.literal("handled screen moment"));
                    })).verticalSizing(Sizing.fixed(12))
            );

            instance.alignComponentToHandledScreenCoordinates(button, 125, 65);
        }, InventoryScreen.class);
    }

    public record WeirdMessage(int e) {}
}
