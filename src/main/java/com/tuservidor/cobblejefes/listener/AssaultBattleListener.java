package com.tuservidor.cobblejefes.listener;

import com.cobblemon.mod.common.api.battles.model.PokemonBattle;
import com.cobblemon.mod.common.api.battles.model.actor.BattleActor;
import com.cobblemon.mod.common.api.events.CobblemonEvents;
import com.tuservidor.cobblejefes.assault.AssaultManager;
import com.tuservidor.cobblejefes.assault.AssaultSession;
import kotlin.Unit;
import net.minecraft.server.level.ServerPlayer;

import java.util.List;
import java.util.UUID;

public class AssaultBattleListener {

    public static void register() {

        // Victoria del jugador (o del NPC — se diferencia por quién ganó)
        CobblemonEvents.BATTLE_VICTORY.subscribe(
            com.cobblemon.mod.common.api.Priority.NORMAL, event -> {
                handleResult(event.getBattle(), event.getWinners(), true);
                return Unit.INSTANCE;
            }
        );

        // Huida del jugador → derrota para el sistema de asalto
        CobblemonEvents.BATTLE_FLED.subscribe(
            com.cobblemon.mod.common.api.Priority.NORMAL, event -> {
                handleResult(event.getBattle(), event.getFleeing(), false);
                return Unit.INSTANCE;
            }
        );
    }

    private static void handleResult(PokemonBattle battle,
                                      List<BattleActor> actorsToCheck,
                                      boolean isWin) {
        for (BattleActor actor : actorsToCheck) {
            actor.getPlayerUUIDs().forEach(playerUuid -> {
                ServerPlayer player = battle.getServer()
                    .getPlayerList().getPlayer(playerUuid);

                if (player == null) return;
                if (!AssaultSession.has(playerUuid)) return;

                AssaultSession session = AssaultSession.get(playerUuid);
                if (session.getState() != AssaultSession.State.IN_BATTLE) return;

                // Validar que el combate es exactamente contra el NPC de la sesión
                if (!isFightingAssaultNpc(battle, actor, session)) return;

                AssaultManager.handleBattleEnd(player, isWin);
            });
        }
    }

    private static boolean isFightingAssaultNpc(PokemonBattle battle,
                                                  BattleActor playerActor,
                                                  AssaultSession session) {
        UUID targetNpcUuid = session.getCurrentNpcUuid();
        if (targetNpcUuid == null) return false;

        for (BattleActor rival : battle.getActors()) {
            if (rival == playerActor) continue;
            if (rival.getEntity() != null
                    && targetNpcUuid.equals(rival.getEntity().getUUID())) {
                return true;
            }
        }
        return false;
    }
}
