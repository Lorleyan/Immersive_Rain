package com.example.livingrain.mixin;

import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

import com.example.livingrain.WeatherHooks;

/** Stops vanilla's rain splashes and rain sounds (this mod makes its own). Optional, like WeatherRenderMixin. */
@Mixin(targets = "net.minecraft.client.renderer.WeatherEffectRenderer")
public abstract class RainTickMixin {

    @Inject(method = "tickRainParticles", at = @At("HEAD"), cancellable = true, require = 0)
    private void livingrain$noVanillaSplashes(CallbackInfo ci) {
        if (WeatherHooks.shouldCancel()) ci.cancel();
    }
}
