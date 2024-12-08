package io.wispforest.owo.mixin.recipe_remainders;

import net.minecraft.recipe.ServerRecipeManager;
import net.minecraft.resource.ResourceFinder;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.gen.Accessor;

@Mixin(ServerRecipeManager.class)
public interface ServerRecipeManagerAccessor {

    @Accessor("FINDER")
    static ResourceFinder owo$getFinder() {
        throw new UnsupportedOperationException();
    }
}
