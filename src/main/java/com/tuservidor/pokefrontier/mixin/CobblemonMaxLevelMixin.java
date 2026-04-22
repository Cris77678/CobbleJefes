package com.tuservidor.pokefrontier.mixin;

import com.cobblemon.mod.common.config.CobblemonConfig;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

@Mixin(value = CobblemonConfig.class, remap = false)
public class CobblemonMaxLevelMixin {

    @Inject(method = "getMaxPokemonLevel", at = @At("HEAD"), cancellable = true, remap = false)
    private void pokefrontier$raiseMaxLevel(CallbackInfoReturnable<Integer> cir) {
        // Permite niveles hasta 1000 para soportar NPCs de nivel 120 en el Asalto
        cir.setReturnValue(1000);
    }
}
