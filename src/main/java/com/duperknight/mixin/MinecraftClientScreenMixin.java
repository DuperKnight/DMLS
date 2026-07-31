package com.duperknight.mixin;

import com.duperknight.client.reimbursement.ContainerSelectionController;
import net.minecraft.client.MinecraftClient;
import net.minecraft.client.gui.screen.Screen;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

/** Keeps reimbursement container selection in its modal in-world state. */
@Mixin(MinecraftClient.class)
public abstract class MinecraftClientScreenMixin {
    @Inject(method = "setScreen", at = @At("HEAD"), cancellable = true)
    private void dmls$blockScreensDuringContainerSelection(Screen screen, CallbackInfo callback) {
        if (screen != null && ContainerSelectionController.active()) callback.cancel();
    }
}
