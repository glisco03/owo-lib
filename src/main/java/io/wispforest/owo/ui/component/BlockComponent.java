package io.wispforest.owo.ui.component;

import com.mojang.blaze3d.systems.RenderSystem;
import com.mojang.brigadier.exceptions.CommandSyntaxException;
import io.wispforest.owo.mixin.ui.access.BlockEntityAccessor;
import io.wispforest.owo.ui.base.BaseComponent;
import io.wispforest.owo.ui.core.OwoUIDrawContext;
import io.wispforest.owo.ui.parsing.UIModelParsingException;
import io.wispforest.owo.ui.parsing.UIParsing;
import net.minecraft.block.BlockRenderType;
import net.minecraft.block.BlockState;
import net.minecraft.block.entity.BlockEntity;
import net.minecraft.client.MinecraftClient;
import net.minecraft.client.render.DiffuseLighting;
import net.minecraft.client.render.LightmapTextureManager;
import net.minecraft.client.render.OverlayTexture;
import net.minecraft.command.argument.BlockArgumentParser;
import net.minecraft.nbt.NbtCompound;
import net.minecraft.registry.Registries;
import net.minecraft.util.math.RotationAxis;
import org.jetbrains.annotations.Nullable;
import org.joml.Vector3f;
import org.w3c.dom.Element;

public class BlockComponent extends BaseComponent {

    private final MinecraftClient client = MinecraftClient.getInstance();

    private final BlockState state;
    private final @Nullable BlockEntity entity;
    protected Vector3f light = new Vector3f(.6f, -.5f, 0.6f), light2 = new Vector3f(-.6f, -.5f, 0.5f);

    protected BlockComponent(BlockState state, @Nullable BlockEntity entity) {
        this.state = state;
        this.entity = entity;
    }

    @Override
    @SuppressWarnings("NonAsciiCharacters")
    public void draw(OwoUIDrawContext context, int mouseX, int mouseY, float partialTicks, float delta) {
        context.getMatrices().push();

        context.getMatrices().translate(x + this.width / 2f, y + this.height / 2f, 100);
        context.getMatrices().scale(40 * this.width / 64f, -40 * this.height / 64f, 40);

        context.getMatrices().multiply(RotationAxis.POSITIVE_X.rotationDegrees(30));
        context.getMatrices().multiply(RotationAxis.POSITIVE_Y.rotationDegrees(45 + 180));

        context.getMatrices().translate(-.5, -.5, -.5);

        RenderSystem.runAsFancy(() -> {
            final var vertexConsumers = this.client.getBufferBuilders().getEntityVertexConsumers();
            if (this.state.getRenderType() != BlockRenderType.ENTITYBLOCK_ANIMATED) {
                this.client.getBlockRenderManager().renderBlockAsEntity(
                        this.state, context.getMatrices(), vertexConsumers,
                        LightmapTextureManager.MAX_LIGHT_COORDINATE, OverlayTexture.DEFAULT_UV
                );
            }

            if (this.entity != null) {
                var медведь = this.client.getBlockEntityRenderDispatcher().get(this.entity);
                if (медведь != null) {
                    медведь.render(entity, partialTicks, context.getMatrices(), vertexConsumers, LightmapTextureManager.MAX_LIGHT_COORDINATE, OverlayTexture.DEFAULT_UV);
                }
            }

            RenderSystem.setShaderLights(this.light, this.light2);
            vertexConsumers.draw();
            DiffuseLighting.enableGuiDepthLighting();
        });

        context.getMatrices().pop();
    }

    protected static void prepareBlockEntity(BlockState state, BlockEntity blockEntity, @Nullable NbtCompound nbt) {
        if (blockEntity == null) return;

        var world = MinecraftClient.getInstance().world;

        ((BlockEntityAccessor) blockEntity).owo$setCachedState(state);
        blockEntity.setWorld(world);

        if (nbt == null) return;

        final var nbtCopy = nbt.copy();

        nbtCopy.putInt("x", 0);
        nbtCopy.putInt("y", 0);
        nbtCopy.putInt("z", 0);

        blockEntity.read(nbtCopy, world.getRegistryManager());
    }

    public static BlockComponent parse(Element element) {
        UIParsing.expectAttributes(element, "state");

        try {
            var result = BlockArgumentParser.block(Registries.BLOCK.getReadOnlyWrapper(), element.getAttribute("state"), true);
            return Components.block(result.blockState(), result.nbt());
        } catch (CommandSyntaxException cse) {
            throw new UIModelParsingException("Invalid block state", cse);
        }
    }
}
