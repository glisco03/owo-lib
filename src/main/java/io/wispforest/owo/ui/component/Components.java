package io.wispforest.owo.ui.component;

import io.wispforest.owo.ui.container.Containers;
import io.wispforest.owo.ui.container.FlowLayout;
import io.wispforest.owo.ui.core.Component;
import io.wispforest.owo.ui.core.Sizing;
import net.minecraft.block.BlockEntityProvider;
import net.minecraft.block.BlockState;
import net.minecraft.block.entity.BlockEntity;
import net.minecraft.client.MinecraftClient;
import net.minecraft.client.gui.widget.ClickableWidget;
import net.minecraft.client.texture.Sprite;
import net.minecraft.client.util.SpriteIdentifier;
import net.minecraft.entity.Entity;
import net.minecraft.entity.EntityType;
import net.minecraft.item.ItemStack;
import net.minecraft.nbt.NbtCompound;
import net.minecraft.text.Text;
import net.minecraft.util.Identifier;
import org.jetbrains.annotations.Nullable;

import java.util.List;
import java.util.function.Consumer;
import java.util.function.Function;
import java.util.function.Supplier;

// TODO paginated and tabbed containers

/**
 * Utility methods for creating UI components
 */
public final class Components {

    private Components() {}

    // -----------------------
    // Wrapped Vanilla Widgets
    // -----------------------

    public static ButtonComponent button(Text message, Consumer<ButtonComponent> onPress) {
        return new ButtonComponent(message, onPress);
    }

    public static TextBoxComponent textBox(Sizing horizontalSizing) {
        return new TextBoxComponent(horizontalSizing);
    }

    public static TextBoxComponent textBox(Sizing horizontalSizing, String text) {
        var textBox = new TextBoxComponent(horizontalSizing);
        textBox.text(text);
        return textBox;
    }

    public static TextAreaComponent textArea(Sizing horizontalSizing, Sizing verticalSizing) {
        return new TextAreaComponent(horizontalSizing, verticalSizing);
    }

    public static TextAreaComponent textArea(Sizing sizing) {
        return textArea(sizing, sizing);
    }

    public static TextAreaComponent textArea(Sizing horizontalSizing, Sizing verticalSizing, String text) {
        var textArea = new TextAreaComponent(horizontalSizing, verticalSizing);
        textArea.setText(text);
        return textArea;
    }

    public static TextAreaComponent textArea(Sizing sizing, String text) {
        return textArea(sizing, sizing, text);
    }

    // ------------------
    // Default Components
    // ------------------

    public static <E extends Entity> EntityComponent<E> entity(Sizing sizing, EntityType<E> type, @Nullable NbtCompound nbt) {
        return new EntityComponent<>(sizing, type, nbt);
    }

    public static <E extends Entity> EntityComponent<E> entity(Sizing sizing, E entity) {
        return new EntityComponent<>(sizing, entity);
    }

    public static ItemComponent item(ItemStack item) {
        return new ItemComponent(item);
    }

    public static BlockComponent block(BlockState state) {
        return new BlockComponent(state, null);
    }

    public static BlockComponent block(BlockState state, BlockEntity blockEntity) {
        return new BlockComponent(state, blockEntity);
    }

    public static BlockComponent block(BlockState state, @Nullable NbtCompound nbt) {
        final var client = MinecraftClient.getInstance();

        BlockEntity blockEntity = null;

        if (state.getBlock() instanceof BlockEntityProvider provider) {
            blockEntity = provider.createBlockEntity(client.player.getBlockPos(), state);
            BlockComponent.prepareBlockEntity(state, blockEntity, nbt);
        }

        return new BlockComponent(state, blockEntity);
    }

    public static LabelComponent label(Text text) {
        return new LabelComponent(text);
    }

    public static LabelComponent label(String string) {
        return new LabelComponent(Text.literal(string));
    }

    public static CheckboxComponent checkbox(Text message) {
        return new CheckboxComponent(message);
    }

    public static SliderComponent slider(Sizing horizontalSizing) {
        return new SliderComponent(horizontalSizing);
    }

    public static DiscreteSliderComponent discreteSlider(Sizing horizontalSizing, double min, double max) {
        return new DiscreteSliderComponent(horizontalSizing, min, max);
    }

    public static SpriteComponent sprite(SpriteIdentifier spriteId) {
        return new SpriteComponent(
                spriteId.getAtlasId().equals(Identifier.of("textures/atlas/gui.png"))
                        ? MinecraftClient.getInstance().getGuiAtlasManager().getSprite(spriteId.getTextureId())
                        : spriteId.getSprite()
        );
    }

    public static SpriteComponent sprite(Sprite sprite) {
        return new SpriteComponent(sprite);
    }

    public static TextureComponent texture(Identifier texture, int u, int v, int regionWidth, int regionHeight, int textureWidth, int textureHeight) {
        return new TextureComponent(texture, u, v, regionWidth, regionHeight, textureWidth, textureHeight);
    }

    public static TextureComponent texture(Identifier texture, int u, int v, int regionWidth, int regionHeight) {
        return new TextureComponent(texture, u, v, regionWidth, regionHeight, 256, 256);
    }

    public static BoxComponent box(Sizing horizontalSizing, Sizing verticalSizing) {
        return new BoxComponent(horizontalSizing, verticalSizing);
    }

    public static BoxComponent box(Sizing sizing) {return box(sizing, sizing);}

    public static DropdownComponent dropdown(Sizing horizontalSizing) {
        return new DropdownComponent(horizontalSizing);
    }

    public static SlimSliderComponent slimSlider(SlimSliderComponent.Axis axis) {
        return new SlimSliderComponent(axis);
    }

    public static SmallCheckboxComponent smallCheckbox(Text label) {
        return new SmallCheckboxComponent(label);
    }

    public static SpacerComponent spacer(int percent) {
        return new SpacerComponent(percent);
    }

    public static SpacerComponent spacer() {
        return spacer(100);
    }

    // -------
    // Utility
    // -------

    public static <T, C extends Component> FlowLayout list(List<T> data, Consumer<FlowLayout> layoutConfigurator, Function<T, C> componentMaker, boolean vertical) {
        var layout = vertical ? Containers.verticalFlow(Sizing.content(), Sizing.content()) : Containers.horizontalFlow(Sizing.content(), Sizing.content());
        layoutConfigurator.accept(layout);

        for (var value : data) {
            layout.child(componentMaker.apply(value));
        }

        return layout;
    }

    public static VanillaWidgetComponent wrapVanillaWidget(ClickableWidget widget) {
        return new VanillaWidgetComponent(widget);
    }

    public static <T extends Component> T createWithSizing(Supplier<T> componentMaker, Sizing horizontalSizing, Sizing verticalSizing) {
        var component = componentMaker.get();
        component.sizing(horizontalSizing, verticalSizing);
        return component;
    }

    public static <T extends Component> T createWithSizing(Supplier<T> componentMaker, Sizing sizing) {
        return createWithSizing(componentMaker, sizing, sizing);
    }
}
