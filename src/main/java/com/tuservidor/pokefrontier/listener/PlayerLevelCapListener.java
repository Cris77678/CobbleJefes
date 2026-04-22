package com.tuservidor.pokefrontier.listener;

import com.cobblemon.mod.common.Cobblemon;
import com.cobblemon.mod.common.api.Priority;
import com.cobblemon.mod.common.api.events.CobblemonEvents;
import com.cobblemon.mod.common.api.storage.party.PlayerPartyStore;
import com.cobblemon.mod.common.pokemon.Pokemon;
import com.tuservidor.pokefrontier.config.AssaultConfig;
import net.fabricmc.fabric.api.event.lifecycle.v1.ServerTickEvents;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerPlayer;
import kotlin.Unit;

public class PlayerLevelCapListener {

    private static int tickCounter = 0;

    public static void register() {
        int maxLevel = AssaultConfig.get().getMaxPlayerLevel();

        // Bloqueo preventivo de EXP
        CobblemonEvents.EXPERIENCE_GAIN.subscribe(Priority.HIGHEST, event -> {
            if (event.getPokemon().getPersistentData().getBoolean("bf_rental")) return Unit.INSTANCE;
            if (event.getPokemon().getLevel() >= maxLevel) event.cancel();
            return Unit.INSTANCE;
        });

        // Escáner periódico de inventarios
        ServerTickEvents.START_SERVER_TICK.register(server -> {
            if (++tickCounter % 20 != 0) return;

            int cap = AssaultConfig.get().getMaxPlayerLevel();

            for (ServerPlayer player : server.getPlayerList().getPlayers()) {
                if (player.hasPermission(2)) continue;

                PlayerPartyStore party = Cobblemon.INSTANCE.getStorage().getParty(player);
                boolean modified = false;

                for (int i = 0; i < party.size(); i++) {
                    Pokemon pokemon = party.get(i);
                    if (pokemon == null) continue;
                    if (pokemon.getPersistentData().getBoolean("bf_rental")) continue;

                    if (pokemon.getLevel() > cap) {
                        pokemon.setLevel(cap);
                        pokemon.recalculateStats();
                        modified = true;
                        player.sendSystemMessage(Component.literal(
                            "§c[!] Tu " + pokemon.getSpecies().getName()
                            + " superó el nivel máximo. Ajustado a nivel " + cap + "."
                        ));
                    }
                }

                if (modified) party.save();
            }
        });
    }
}
