package com.tuservidor.cobblejefes.listener;

import com.cobblemon.mod.common.api.Priority;
import com.cobblemon.mod.common.api.events.CobblemonEvents;
import kotlin.Unit;

/**
 * Seguridad para Pokémon de alquiler (compatible con el sistema de exportación de BattleFrontier).
 * Aunque PokeFrontier no usa equipos alquilados, mantiene esta protección para
 * coexistir con BattleFrontier en el mismo servidor.
 */
public class RentalSecurityListener {

    public static void register() {
        CobblemonEvents.EXPERIENCE_GAIN.subscribe(Priority.HIGHEST, event -> {
            if (event.getPokemon().getPersistentData().getBoolean("bf_rental")) {
                event.cancel();
            }
            return Unit.INSTANCE;
        });

        CobblemonEvents.EVOLUTION_START.subscribe(Priority.HIGHEST, event -> {
            if (event.getPokemon().getPersistentData().getBoolean("bf_rental")) {
                event.cancel();
            }
            return Unit.INSTANCE;
        });
    }
}
