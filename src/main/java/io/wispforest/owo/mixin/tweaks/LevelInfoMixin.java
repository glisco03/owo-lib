package io.wispforest.owo.mixin.tweaks;

import io.wispforest.owo.Owo;
import net.fabricmc.loader.api.FabricLoader;
import net.minecraft.resource.DataConfiguration;
import net.minecraft.world.Difficulty;
import net.minecraft.world.GameMode;
import net.minecraft.world.GameRules;
import net.minecraft.world.level.LevelInfo;
import org.spongepowered.asm.mixin.Final;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

@Mixin(LevelInfo.class)
public class LevelInfoMixin {

    @Shadow
    @Final
    private GameRules gameRules;

    @Inject(method = "<init>", at = @At("TAIL"))
    private void simulationIsForNerds(String name, GameMode gameMode, boolean hardcore, Difficulty difficulty, boolean allowCommands, GameRules gameRules, DataConfiguration dataConfiguration, CallbackInfo ci) {
        if (!(Owo.DEBUG && Owo.PREVENT_DAYLIGHT_CYCLE && FabricLoader.getInstance().isDevelopmentEnvironment())) return;

        this.gameRules.get(GameRules.DO_DAYLIGHT_CYCLE).set(false, null);
        this.gameRules.get(GameRules.DO_WEATHER_CYCLE).set(false, null);
    }

}
