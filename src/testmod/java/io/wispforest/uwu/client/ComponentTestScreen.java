package io.wispforest.uwu.client;

import com.mojang.authlib.GameProfile;
import io.wispforest.owo.ui.component.*;
import io.wispforest.owo.ui.container.Containers;
import io.wispforest.owo.ui.container.FlowLayout;
import io.wispforest.owo.ui.container.RenderEffectWrapper;
import io.wispforest.owo.ui.container.ScrollContainer;
import io.wispforest.owo.ui.core.*;
import net.minecraft.block.Blocks;
import net.minecraft.client.MinecraftClient;
import net.minecraft.client.gui.DrawContext;
import net.minecraft.client.gui.Element;
import net.minecraft.client.gui.screen.Screen;
import net.minecraft.client.gui.tooltip.Tooltip;
import net.minecraft.client.texture.SpriteAtlasTexture;
import net.minecraft.client.util.SpriteIdentifier;
import net.minecraft.item.ItemStack;
import net.minecraft.item.Items;
import net.minecraft.nbt.NbtCompound;
import net.minecraft.nbt.NbtList;
import net.minecraft.text.ClickEvent;
import net.minecraft.text.HoverEvent;
import net.minecraft.text.Text;
import net.minecraft.util.Identifier;
import net.minecraft.util.math.RotationAxis;
import org.jetbrains.annotations.Nullable;
import org.lwjgl.glfw.GLFW;

import java.io.IOException;
import java.io.OutputStreamWriter;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.UUID;
import java.util.stream.IntStream;

public class ComponentTestScreen extends Screen {

    private OwoUIAdapter<FlowLayout> uiAdapter = null;
    private RenderEffectWrapper<?>.RenderEffectSlot fadeSlot = null;

    public ComponentTestScreen() {
        super(Text.empty());
    }

    @Override
    protected void init() {
        this.uiAdapter = OwoUIAdapter.create(this, Containers::horizontalFlow);
        final var rootComponent = uiAdapter.rootComponent;

        rootComponent.child(
                Containers.verticalFlow(Sizing.content(), Sizing.content())
                        .child(Components.button(Text.of("Dark Background"), button -> rootComponent.surface(Surface.flat(0x77000000))).horizontalSizing(Sizing.fixed(95)))
                        .child(Components.button(Text.of("No Background"), button -> rootComponent.surface(Surface.BLANK)).margins(Insets.vertical(5)).horizontalSizing(Sizing.fixed(95)))
                        .child(Components.button(Text.of("Dirt Background"), button -> rootComponent.surface(Surface.OPTIONS_BACKGROUND)).horizontalSizing(Sizing.fixed(95)))
                        .child(Components.checkbox(Text.of("bruh")).onChanged(aBoolean -> this.client.player.sendMessage(Text.of("bruh: " + aBoolean))).margins(Insets.top(5)))
                        .padding(Insets.of(10))
                        .surface(Surface.flat(0x77000000))
                        .positioning(Positioning.relative(1, 1))
        );

        final var innerLayout = Containers.verticalFlow(Sizing.content(100), Sizing.content());
        var verticalAnimation = innerLayout.verticalSizing().animate(350, Easing.SINE, Sizing.content(50));

        verticalAnimation.finished().subscribe((direction, looping) -> {
            client.inGameHud.getChatHud().addMessage(Text.literal("vertical animation finished in direction " + direction.name()));
        });

        final var bruh = Components.box(Sizing.fixed(150), Sizing.fixed(20));
        bruh.horizontalSizing().animate(5000, Easing.QUARTIC, Sizing.fixed(10)).forwards();
        innerLayout.child(bruh);

        final var otherBox = Containers.verticalFlow(Sizing.fixed(150), Sizing.fixed(20));
        otherBox.surface(Surface.flat(Color.BLACK.argb())).horizontalSizing().animate(5000, Easing.QUARTIC, Sizing.fixed(10)).forwards();
        innerLayout.child(otherBox);

        innerLayout.child(Containers.verticalScroll(Sizing.content(), Sizing.fixed(50), Containers.verticalFlow(Sizing.content(), Sizing.content())
                                .child(new BoxComponent(Sizing.fixed(20), Sizing.fixed(40)).margins(Insets.of(5)))
                                .child(new BoxComponent(Sizing.fixed(45), Sizing.fixed(45)).margins(Insets.of(5)))
                                .child(Components.textBox(Sizing.fixed(60)))
                                .horizontalAlignment(HorizontalAlignment.RIGHT)
                                .surface(Surface.flat(0x77000000)))
                        .scrollbar(ScrollContainer.Scrollbar.vanilla())
                        .fixedScrollbarLength(15)
                        .scrollbarThiccness(12)
                        .id("scrollnite")
                )
                .child(Components.button(Text.of("+"), (ButtonComponent button) -> {
                            verticalAnimation.reverse();

                            button.setMessage(verticalAnimation.direction() == Animation.Direction.FORWARDS
                                    ? Text.of("-")
                                    : Text.of("+")
                            );
                        }).<ButtonComponent>configure(button -> {
                            button.setTooltip(Tooltip.of(Text.of("a vanilla tooltip")));
                            button.margins(Insets.of(5)).sizing(Sizing.fixed(12));
                        })
                )
                .child(new BoxComponent(Sizing.fixed(40), Sizing.fixed(20)).margins(Insets.of(5)))
                .horizontalAlignment(HorizontalAlignment.CENTER)
                .verticalAlignment(VerticalAlignment.CENTER)
                .padding(Insets.of(5));

        innerLayout.child(Components.textArea(Sizing.fixed(75), Sizing.content()).maxLines(5).displayCharCount(true));
        innerLayout.child(Components.textArea(Sizing.fixed(75), Sizing.fixed(75)).<TextAreaComponent>configure(textArea -> {
            textArea.displayCharCount(true).setMaxLength(100);
        }));

        rootComponent.child(Containers.horizontalScroll(Sizing.fill(20), Sizing.content(), innerLayout)
                .scrollbarThiccness(6)
                .scrollbar(ScrollContainer.Scrollbar.vanillaFlat())
                .surface(Surface.DARK_PANEL)
                .padding(Insets.of(3))
        );

        rootComponent.child(Containers.verticalFlow(Sizing.content(), Sizing.content())
                .child(Components.label(Text.literal("A profound vertical Flow Layout, as well as a really long text to demonstrate wrapping").styled(style -> style.withFont(MinecraftClient.UNICODE_FONT_ID))
                                .styled(style -> {
                                    return style.withClickEvent(new ClickEvent(ClickEvent.Action.COPY_TO_CLIPBOARD, "yes"))
                                            .withHoverEvent(new HoverEvent(HoverEvent.Action.SHOW_ITEM, new HoverEvent.ItemStackContent(Items.SCULK_SHRIEKER.getDefaultStack())));
                                }))
                        .shadow(true)
                        .lineHeight(7)
                        .maxWidth(100)
                        .margins(Insets.horizontal(15)))
        );

        final var buttonPanel = Containers.horizontalFlow(Sizing.content(), Sizing.content())
                .child(Components.label(Text.literal("A horizontal Flow").append(Text.literal("Layout")
                                .styled(style -> style.withHoverEvent(new HoverEvent(HoverEvent.Action.SHOW_ITEM, new HoverEvent.ItemStackContent(Items.SCULK_SHRIEKER.getDefaultStack())))))
                        .append(Text.literal("\nthat's a tooltip?"))).margins(Insets.of(5)))
                .child(Components.button(Text.of("⇄"), button -> this.clearAndInit()).sizing(Sizing.fixed(20)))
                .child(Components.button(Text.of("X"), button -> this.close()).sizing(Sizing.fixed(20)))
                .positioning(Positioning.relative(100, 0))
                .verticalAlignment(VerticalAlignment.CENTER)
                .surface(Surface.TOOLTIP)
                .padding(Insets.of(5))
                .margins(Insets.of(10));

        final var growingTextBox = Components.textBox(Sizing.fixed(60));
        final var growAnimation = growingTextBox.horizontalSizing().animate(500, Easing.SINE, Sizing.fixed(80));
        growingTextBox.mouseEnter().subscribe(growAnimation::forwards);
        growingTextBox.mouseLeave().subscribe(growAnimation::backwards);

        var weeAnimation = buttonPanel.positioning().animate(1000, Easing.CUBIC, Positioning.relative(0, 100));
        rootComponent.child(Containers.verticalFlow(Sizing.content(), Sizing.content())
                .child(growingTextBox)
                .child(new SmallCheckboxComponent())
                .child(Components.textBox(Sizing.fixed(60)))
                .child(Components.button(Text.of("weeeee"), button -> {
                    weeAnimation.loop(!weeAnimation.looping());
                    rootComponent.<FlowLayout>configure(layout -> {
                        var padding = layout.padding().get();
                        for (int i = 0; i < 696969; i++) {
                            layout.padding(Insets.of(i));
                        }
                        layout.padding(padding.add(5, 5, 5, 5));
                    });
                }).renderer(ButtonComponent.Renderer.flat(0x77000000, 0x77070707, 0xA0000000)).sizing(Sizing.content()))
                .child(Components.discreteSlider(Sizing.fill(10), 0, 5).<DiscreteSliderComponent>configure(
                        slider -> slider.snap(true)
                                .decimalPlaces(1)
                                .message(value -> Text.translatable("text.ui.test_slider", value))
                                .onChanged().subscribe(value -> {
                                    slider.parent().surface(Surface.blur(3, (float) (value * 3)));
                                    this.client.player.sendMessage(Text.of("sliding towards " + value));
                                })
                ))
                .gap(10)
                .padding(Insets.both(5, 10))
                .horizontalAlignment(HorizontalAlignment.CENTER)
                .surface(Surface.blur(3, 0))
        );

        var dropdown = Components.dropdown(Sizing.content())
                .checkbox(Text.of("more checking"), true, aBoolean -> {})
                .text(Text.of("hahayes"))
                .button(Text.of("epic button"), dropdownComponent -> {})
                .divider()
                .text(Text.of("very good"))
                .checkbox(Text.of("checking time"), false, aBoolean -> {})
                .nested(Text.of("nested entry"), Sizing.content(), nested -> {
                    nested.text(Text.of("nest title"))
                            .divider()
                            .button(Text.of("nest button"), dropdownComponent -> {});
                });

        var dropdownButton = Components.button(Text.of("Dropdown"), button -> {
            if (dropdown.hasParent()) return;
            rootComponent.child(dropdown.positioning(Positioning.absolute(button.x(), button.y() + button.height())));
        }).margins(Insets.horizontal(8));
        dropdown.mouseLeave().subscribe(() -> dropdown.closeWhenNotHovered(true));

        rootComponent.child(
                Containers.renderEffect(
                        Containers.verticalFlow(Sizing.content(), Sizing.content())
                                .child(Containers.renderEffect(
                                        Components.sprite(new SpriteIdentifier(SpriteAtlasTexture.BLOCK_ATLAS_TEXTURE, new Identifier("block/stone"))).margins(Insets.of(5))
                                ).<RenderEffectWrapper<?>>configure(wrapper -> {
                                    wrapper.effect(RenderEffectWrapper.RenderEffect.rotate(RotationAxis.POSITIVE_Z, -45));
                                    wrapper.effect(RenderEffectWrapper.RenderEffect.color(Color.ofHsv(.5f, 1f, 1f)));
                                }))
                                .child(dropdownButton)
                ).<RenderEffectWrapper<?>>configure(wrapper -> {
                    wrapper.effect(RenderEffectWrapper.RenderEffect.transform(matrices -> matrices.translate(0, 25, 0)));

                    wrapper.effect(RenderEffectWrapper.RenderEffect.rotate(90f));
                    this.fadeSlot = wrapper.effect(RenderEffectWrapper.RenderEffect.color(Color.WHITE));
                })
        );

        rootComponent.mouseDown().subscribe((mouseX, mouseY, button) -> {
            if (button != GLFW.GLFW_MOUSE_BUTTON_RIGHT) return false;
            DropdownComponent.openContextMenu(this, rootComponent, FlowLayout::child, mouseX, mouseY, contextMenu -> {
                contextMenu.text(Text.literal("That's a context menu"));
                contextMenu.checkbox(Text.literal("Yup"), true, aBoolean -> {});
                contextMenu.divider();
                contextMenu.button(Text.literal("Delet"), Component::remove);
            });
            return true;
        });

        rootComponent.child(
//                new BaseComponent() {
//                    @Override
//                    public void draw(MatrixStack matrices, int mouseX, int mouseY, float partialTicks, float delta) {
//                        Drawer.drawCircle(
//                                matrices,
//                                this.x + this.width / 2,
//                                this.y + this.height / 2,
//                                75,
//                                this.width / 2f,
//                                Color.ofArgb(0x99000000)
//                        );
//
//                        Drawer.drawRing(
//                                matrices,
//                                this.x + this.width / 2,
//                                this.y + this.height / 2,
//                                75,
//                                (this.width - 125) / 2f,
//                                this.width / 2f,
//                                Color.ofArgb(0x99000000),
//                                Color.ofArgb(0x99000000)
//                        );
//
//                        var time = (System.currentTimeMillis() / 1000d) % (Math.PI * 2);
//                        Drawer.drawLine(
//                                matrices,
//                                (int) (this.x + this.width / 2 + Math.cos(time) * this.width / 2),
//                                (int) (this.y + this.height / 2 + Math.sin(time) * this.height / 2),
//                                (int) (this.x + this.width / 2 + Math.sin(time) * this.width / 2),
//                                (int) (this.y + this.height / 2 + Math.cos(time) * this.height / 2),
//                                1,
//                                Color.BLUE
//                        );
//
//                        Drawer.drawSpectrum(matrices, this.x, this.y, this.width, (int) (this.height * (Math.sin(time) * .5 + .5)), true);
//                    }
//                }.positioning(Positioning.relative(50, 50)).sizing(Sizing.fixed(350))
                Components.button(Text.of("overlay"), button -> {
                    rootComponent.child(Containers.overlay(
                            Containers.verticalFlow(Sizing.content(), Sizing.content())
                                    .child(new ColorPickerComponent()
                                            .showAlpha(true)
                                            .selectedColor(Color.ofArgb(0x7F3955E5))
                                            .sizing(Sizing.fixed(160), Sizing.fixed(100))
                                    ).padding(Insets.of(5)).surface(Surface.DARK_PANEL)
                    ));
                })
        );


        // i knew it all along, chyz truly is a pig
        var pig = EntityComponent.createRenderablePlayer(new GameProfile(UUID.fromString("09de8a6d-86bf-4c15-bb93-ce3384ce4e96"), "chyzman"));
        pig.setOnFire(true);

        rootComponent.child(
                Components.entity(Sizing.fixed(100), pig)
                        .allowMouseRotation(true)
                        .scaleToFit(true)
                        .showNametag(true)
        );

        rootComponent.child(
                Components.block(Blocks.FURNACE.getDefaultState(), (NbtCompound) null).sizing(Sizing.fixed(100))
        );

        var bundle = Items.BUNDLE.getDefaultStack();
        var itemList = new NbtList();
        itemList.add(new ItemStack(Items.EMERALD, 16).writeNbt(new NbtCompound()));
        bundle.getOrCreateNbt().put("Items", itemList);

        rootComponent.child(Components.item(new ItemStack(Items.EMERALD, 16))
                .showOverlay(true)
                .setTooltipFromStack(true)
                .positioning(Positioning.absolute(120, 30))
        );

        final var buttonGrid = Containers.grid(Sizing.content(), Sizing.fixed(85), 3, 5);
        for (int row = 0; row < 3; row++) {
            for (int column = 0; column < 5; column++) {
                buttonGrid.child(
                        Components.button(Text.of("" + (row * 5 + column)), button -> {
                            if (button.getMessage().getString().equals("11")) {
                                buttonGrid.child(Components.button(Text.of("long boiii"), b -> buttonGrid.child(button, 2, 1)).margins(Insets.of(3)), 2, 1);
                            } else if (button.getMessage().getString().equals("8")) {
                                final var box = Components.textBox(Sizing.fill(10));
                                box.setSuggestion("thicc boi");
                                box.sizing(box.horizontalSizing().get(), Sizing.fixed(40));

                                buttonGrid.child(box.margins(Insets.of(3)), 1, 3);
                            }
                        }).margins(Insets.of(3)).sizing(Sizing.fixed(20)),
                        row, column
                );
            }
        }

        rootComponent.child(buttonGrid
                .horizontalAlignment(HorizontalAlignment.CENTER)
                .verticalAlignment(VerticalAlignment.CENTER)
                .surface(Surface.PANEL)
                .padding(Insets.of(4))
        );

        var data = IntStream.rangeClosed(1, 15).boxed().toList();
        rootComponent.child(
                Containers.horizontalScroll(
                                Sizing.fixed(26 * 7 + 8),
                                Sizing.content(),
                                Containers.list(
                                        data,
                                        flowLayout -> flowLayout.margins(Insets.bottom(10)),
                                        integer -> Components.button(Text.literal(integer.toString()), (ButtonComponent button) -> {}).margins(Insets.horizontal(3)).horizontalSizing(Sizing.fixed(20)),
                                        false
                                )
                        )
                        .scrollStep(26)
                        .scrollbarThiccness(7)
                        .scrollbar(ScrollContainer.Scrollbar.vanilla())
                        .surface(Surface.PANEL)
                        .padding(Insets.of(4, 5, 5, 5))
                        .margins(Insets.bottom(5))
                        .positioning(Positioning.relative(50, 100))
        );

        // infinity scroll test
//        rootComponent.child(
//                Containers.verticalScroll(Sizing.fixed(243), Sizing.fixed(145),
//                        Components.box(Sizing.fixed(235), Sizing.fixed(144))
//                                .startColor(Color.GREEN)
//                                .endColor(Color.BLUE)
//                                .direction(BoxComponent.GradientDirection.TOP_TO_BOTTOM)
//                                .fill(true)
//                ).padding(Insets.of(4)).positioning(Positioning.absolute(150, 40))
//        );

        rootComponent.child(buttonPanel);
        rootComponent.surface(Surface.flat(0x77000000))
                .verticalAlignment(VerticalAlignment.CENTER)
                .horizontalAlignment(HorizontalAlignment.CENTER);

        uiAdapter.inflateAndMount();
    }

    @Override
    public void render(DrawContext context, int mouseX, int mouseY, float delta) {
        super.render(context, mouseX, mouseY, delta);
        this.fadeSlot.update(RenderEffectWrapper.RenderEffect.color(new Color(
                1f, 1f, 1f,
                (float) (Math.sin(System.currentTimeMillis() / 1000d) * .5 + .5)
        )));
    }

    @Override
    public boolean keyPressed(int keyCode, int scanCode, int modifiers) {
        if (keyCode == GLFW.GLFW_KEY_ESCAPE) {
            this.close();
            return true;
        }

        if (keyCode == GLFW.GLFW_KEY_F12) {
            try (var out = Files.newOutputStream(Path.of("component_tree.dot")); var writer = new OutputStreamWriter(out, StandardCharsets.UTF_8)) {
                writer.write("digraph D {\n");

                final var tree = new ArrayList<Component>();
                this.uiAdapter.rootComponent.collectDescendants(tree);

                for (var component : tree) {
                    writer.write("  \"" + format(component.parent()) + "\" -> \"" + format(component) + "\"\n");
                }

                writer.write("}");
                writer.flush();
            } catch (IOException e) {
                e.printStackTrace();
            }
            return true;
        } else {
            return this.uiAdapter.keyPressed(keyCode, scanCode, modifiers);
        }
    }

    @Override
    public boolean mouseDragged(double mouseX, double mouseY, int button, double deltaX, double deltaY) {
        return this.uiAdapter.mouseDragged(mouseX, mouseY, button, deltaX, deltaY);
    }

    @Override
    public boolean shouldPause() {
        return false;
    }

    @Nullable
    @Override
    public Element getFocused() {
        return this.uiAdapter;
    }

    @Override
    public void removed() {
        this.uiAdapter.dispose();
    }

    private String format(@Nullable Component component) {
        if (component == null) {
            return "root";
        } else {
            return component.getClass().getSimpleName() + "@" + Integer.toHexString(component.hashCode())
                    + "(" + component.x() + " " + component.y() + ")";
        }
    }
}