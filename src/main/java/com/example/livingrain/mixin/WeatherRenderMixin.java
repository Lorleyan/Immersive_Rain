package com.example.livingrain.mixin;

import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

import com.example.livingrain.WeatherHooks;

/**
 * Hides vanilla's rain and snow drawing (the particle rain replaces it).
 * Lives in its own optional mixin config: if Minecraft's class or method names differ,
 * the game still starts and only this part is skipped (the log says so).
 */
@Mixin(targets = "net.minecraft.client.renderer.WeatherEffectRenderer")
public abstract class WeatherRenderMixin {

    @Inject(method = { "render", "extractRenderState" }, at = @At("HEAD"), cancellable = true, require = 0)
    private void livingrain$hideVanillaRain(CallbackInfo ci) {
        if (WeatherHooks.shouldCancel()) ci.cancel();
    }
}
