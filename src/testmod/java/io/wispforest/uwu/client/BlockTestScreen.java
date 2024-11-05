package io.wispforest.uwu.client;

import io.wispforest.owo.ui.base.BaseOwoScreen;
import io.wispforest.owo.ui.component.BlockComponent;
import io.wispforest.owo.ui.component.Components;
import io.wispforest.owo.ui.component.DiscreteSliderComponent;
import io.wispforest.owo.ui.container.Containers;
import io.wispforest.owo.ui.container.FlowLayout;
import io.wispforest.owo.ui.core.*;
import net.minecraft.block.Block;
import net.minecraft.block.BlockState;
import net.minecraft.block.Blocks;
import net.minecraft.block.entity.BlockEntity;
import net.minecraft.registry.Registries;
import net.minecraft.text.Text;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;
import org.joml.Vector3f;

import java.util.ArrayList;
import java.util.List;

public class BlockTestScreen extends BaseOwoScreen<FlowLayout> {
    @Override
    protected @NotNull OwoUIAdapter<FlowLayout> createAdapter() {
        return OwoUIAdapter.create(this, Containers::verticalFlow);
    }

    @Override
    protected void build(FlowLayout rootComponent) {
        rootComponent.alignment(HorizontalAlignment.CENTER, VerticalAlignment.TOP);

        var optionsLayout = Containers.horizontalFlow(Sizing.content(), Sizing.content());
        var blocksLayout = Containers.ltrTextFlow(Sizing.fill(100), Sizing.content());

        var airBlock = new TestBlockComponent(Blocks.AIR.getDefaultState(), null);
        var light = airBlock.getLight();
        var light2 = airBlock.getLight2();

        var lightX = this.getSlider(light.x);
        var lightY = this.getSlider(light.y);
        var lightZ = this.getSlider(light.z);
        var lightX2 = this.getSlider(light2.x);
        var lightY2 = this.getSlider(light2.y);
        var lightZ2 = this.getSlider(light2.z);
        var allBlocks = Components.checkbox(Text.literal("All Blocks"));
        var updateButton = Components.button(Text.of("Update blocks"), button -> {
            float lightXValue = (float) lightX.discreteValue();
            float lightYValue = (float) lightY.discreteValue();
            float lightZValue = (float) lightZ.discreteValue();
            float lightX2Value = (float) lightX2.discreteValue();
            float lightY2Value = (float) lightY2.discreteValue();
            float lightZ2Value = (float) lightZ2.discreteValue();
            this.updateBlocks(blocksLayout, allBlocks.isChecked(), lightXValue, lightYValue, lightZValue, lightX2Value, lightY2Value, lightZ2Value);
        });

        var optionsGrid = Containers.grid(Sizing.content(), Sizing.content(), 2, 4);

        optionsGrid.child(lightX, 0, 0);
        optionsGrid.child(lightY, 0, 1);
        optionsGrid.child(lightZ, 0, 2);
        optionsGrid.child(lightX2, 1, 0);
        optionsGrid.child(lightY2, 1, 1);
        optionsGrid.child(lightZ2, 1, 2);

        optionsGrid.child(updateButton, 0, 3);
        optionsGrid.child(allBlocks, 1, 3);

        optionsLayout.child(optionsGrid);

        updateButton.onPress();

        rootComponent.child(optionsLayout);
        rootComponent.child(Containers.verticalScroll(Sizing.content(), Sizing.fill(), blocksLayout));
    }

    private DiscreteSliderComponent getSlider(float value) {
        return Components.discreteSlider(Sizing.fixed(100), -3f, 3f)
                .decimalPlaces(1)
                .setFromDiscreteValue(value);
    }

    private void updateBlocks(FlowLayout layout, boolean allBlocks, float lightX, float lightY, float lightZ, float lightX2, float lightY2, float lightZ2) {
        layout.clearChildren();

        var testBlocks = this.getBlocks(allBlocks);
        var blocksComponents = new ArrayList<Component>();

        for (var block : testBlocks) {
            var states = block.getStateManager().getStates();
            for (var state : states) {
                var blockComponent = new TestBlockComponent(state, null);
                blockComponent.updateLights(new Vector3f(lightX, lightY, lightZ), new Vector3f(lightX2, lightY2, lightZ2));
                blocksComponents.add(blockComponent.sizing(Sizing.fixed(25)).margins(Insets.of(3)));
            }
        }

        layout.children(blocksComponents);
    }

    private List<Block> getBlocks(boolean allBlocks) {
        if (allBlocks) {
            return Registries.BLOCK.stream().toList();

        }
        return List.of(
                Blocks.OAK_STAIRS,
                Blocks.OAK_FENCE,
                Blocks.LADDER,
                Blocks.AMETHYST_CLUSTER,
                Blocks.CAMPFIRE,
                Blocks.POINTED_DRIPSTONE,
                Blocks.PITCHER_CROP,
                Blocks.OAK_SAPLING,
                Blocks.CAVE_VINES,
                Blocks.MANGROVE_PROPAGULE,
                Blocks.CALIBRATED_SCULK_SENSOR,
                Blocks.COPPER_DOOR,
                Blocks.KELP_PLANT,
                Blocks.POTTED_OAK_SAPLING,
                Blocks.WATER_CAULDRON, //FIXME: water color is white
                Blocks.RAIL,
                Blocks.FIRE,
                Blocks.COBWEB,
                Blocks.SHORT_GRASS
        );
    }

    private static class TestBlockComponent extends BlockComponent {

        protected TestBlockComponent(BlockState state, @Nullable BlockEntity entity) {
            super(state, entity);
        }

        private void updateLights(Vector3f light, Vector3f light2) {
            this.light = light;
            this.light2 = light2;
        }

        private Vector3f getLight() {
            return this.light;
        }

        private Vector3f getLight2() {
            return this.light2;
        }
    }
}
