package io.wispforest.owo.mixin.ui;

import com.llamalad7.mixinextras.injector.ModifyReturnValue;
import com.llamalad7.mixinextras.injector.wrapmethod.WrapMethod;
import com.llamalad7.mixinextras.injector.wrapoperation.Operation;
import com.llamalad7.mixinextras.sugar.Local;
import com.mojang.blaze3d.platform.GlStateManager;
import com.mojang.blaze3d.systems.RenderSystem;
import io.wispforest.owo.ui.base.BaseOwoHandledScreen;
import io.wispforest.owo.util.pond.OwoSlotExtension;
import net.minecraft.client.gui.DrawContext;
import net.minecraft.client.gui.screen.Screen;
import net.minecraft.client.gui.screen.ingame.HandledScreen;
import net.minecraft.screen.slot.Slot;
import net.minecraft.text.Text;
import org.lwjgl.glfw.GLFW;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Unique;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.ModifyVariable;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

@Mixin(HandledScreen.class)
public abstract class HandledScreenMixin extends Screen {

    @Unique
    private static boolean owo$inOwoScreen = false;

    protected HandledScreenMixin(Text title) {
        super(title);
    }

    @SuppressWarnings("ConstantConditions")
    @Inject(method = "render", at = @At("HEAD"))
    private void captureOwoState(DrawContext context, int mouseX, int mouseY, float delta, CallbackInfo ci) {
        owo$inOwoScreen = (Object) this instanceof BaseOwoHandledScreen<?, ?>;
    }

    @Inject(method = "render", at = @At("TAIL"))
    private void resetOwoState(DrawContext context, int mouseX, int mouseY, float delta, CallbackInfo ci) {
        owo$inOwoScreen = false;
    }

    @Inject(method = "drawSlot", at = @At("HEAD"))
    private void injectSlotScissors(DrawContext context, Slot slot, CallbackInfo ci) {
        if (!owo$inOwoScreen) return;

        var scissorArea = ((OwoSlotExtension) slot).owo$getScissorArea();
        if (scissorArea == null) return;

        GlStateManager._enableScissorTest();
        GlStateManager._scissorBox(scissorArea.x(), scissorArea.y(), scissorArea.width(), scissorArea.height());
    }

    @Inject(method = "drawSlot", at = @At("RETURN"))
    private void clearSlotScissors(DrawContext context, Slot slot, CallbackInfo ci) {
        if (!owo$inOwoScreen) return;

        var scissorArea = ((OwoSlotExtension) slot).owo$getScissorArea();
        if (scissorArea == null) return;

        GlStateManager._disableScissorTest();
    }

    @ModifyVariable(method = "mouseClicked", at = @At(value = "INVOKE", target = "Lnet/minecraft/client/option/SimpleOption;getValue()Ljava/lang/Object;", ordinal = 0), ordinal = 3)
    private int doNoThrow(int slotId, @Local() Slot slot) {
        return (((Object) this instanceof BaseOwoHandledScreen<?, ?>) && slot != null) ? slot.id : slotId;
    }

    @Inject(method = "keyPressed", at = @At(value = "INVOKE", target = "Lnet/minecraft/client/gui/screen/ingame/HandledScreen;handleHotbarKeyPressed(II)Z"), cancellable = true)
    private void closeIt(int keyCode, int scanCode, int modifiers, CallbackInfoReturnable<Boolean> cir) {
        if (!((Object) this instanceof BaseOwoHandledScreen<?, ?>)) return;

        if (keyCode == GLFW.GLFW_KEY_ESCAPE && this.shouldCloseOnEsc()) {
            this.close();
            cir.setReturnValue(true);
        }
    }
}
