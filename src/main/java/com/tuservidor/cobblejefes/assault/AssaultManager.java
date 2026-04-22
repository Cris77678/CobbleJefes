package com.tuservidor.cobblejefes.assault;

import com.cobblemon.mod.common.Cobblemon;
import com.cobblemon.mod.common.api.storage.party.PlayerPartyStore;
import com.cobblemon.mod.common.pokemon.Pokemon;
import com.tuservidor.cobblejefes.PokeFrontier;
import com.tuservidor.cobblejefes.config.AssaultConfig;
import com.tuservidor.cobblejefes.npc.AssaultNpcSpawner;
import com.tuservidor.cobblejefes.util.LegendaryChecker;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerPlayer;

import java.util.UUID;

/**
 * Motor central del sistema de Asalto a Bases.
 *
 * Flujo normal:
 *   enterBase() → spawnCurrentNpc() → [combate] → handleBattleEnd()
 *     → onStepWon() → advanceStep() + spawnCurrentNpc() (siguiente NPC)
 *     → ... → base completada → endSession()
 *
 * Flujo de re-entrada tras desconexión:
 *   enterBase() detecta savedStep > 0 → spawnCurrentNpc() con el paso guardado
 */
public class AssaultManager {

    // ── Punto de entrada ───────────────────────────────────────────────────────

    /**
     * Llamado cuando el jugador interactúa con el trigger de una base.
     * Si tiene progreso guardado, continúa desde donde lo dejó.
     */
    public static void enterBase(ServerPlayer player, String baseId) {
        AssaultConfig cfg = AssaultConfig.get();

        // Bloquear si ya está en sesión
        if (AssaultSession.has(player.getUUID())) {
            player.sendSystemMessage(msg(cfg.format(cfg.getMsgAlreadyInSession())));
            return;
        }

        AssaultConfig.AssaultBase base = cfg.getBase(baseId);
        if (base == null) {
            player.sendSystemMessage(msg(cfg.format(cfg.getMsgBaseNotFound(), "%base%", baseId)));
            return;
        }

        int savedStep = AssaultProgress.getStep(player.getUUID(), baseId);

        // Base ya completada
        if (base.isComplete(savedStep)) {
            player.sendSystemMessage(msg(cfg.format(cfg.getMsgBaseComplete(), "%base%", baseId)));
            return;
        }

        // Validar equipo del jugador
        if (!validateParty(player, base)) return;

        // Crear sesión y guardar ubicación de retorno
        AssaultSession session = AssaultSession.start(player.getUUID(), baseId, savedStep);
        session.setReturnLocation(new AssaultConfig.AssaultBase.LocationDef(player));

        // Teletransportar al jugador a la arena si está configurada
        if (base.getArena() != null && base.getArena().getPlayerSpawn() != null) {
            base.getArena().getPlayerSpawn().teleport(player);
        }

        player.sendSystemMessage(msg(cfg.format(cfg.getMsgEnterBase(),
            "%org%",   base.getOrganization(),
            "%step%",  savedStep + 1,
            "%total%", base.getSequence().size()
        )));

        spawnCurrentNpc(player, session, base);
    }

    // ── Fin de combate ─────────────────────────────────────────────────────────

    /**
     * Llamado por AssaultBattleListener cuando termina un combate contra el NPC activo.
     */
    public static void handleBattleEnd(ServerPlayer player, boolean won) {
        AssaultSession session = AssaultSession.get(player.getUUID());
        if (session == null || session.getState() != AssaultSession.State.IN_BATTLE) return;

        despawnCurrentNpc(player, session);

        AssaultConfig.AssaultBase base = AssaultConfig.get().getBase(session.getBaseId());
        if (base == null) { endSession(player, session); return; }

        if (won) {
            onStepWon(player, session, base);
        } else {
            onStepLost(player, session);
        }
    }

    // ── Lógica de victoria/derrota ─────────────────────────────────────────────

    private static void onStepWon(ServerPlayer player, AssaultSession session,
                                   AssaultConfig.AssaultBase base) {
        AssaultConfig cfg = AssaultConfig.get();
        int step = session.getCurrentStep();

        // Notificación de paso completado
        String trainerId = base.getTrainerId(step);
        player.sendSystemMessage(msg(cfg.format(cfg.getMsgStepWon(),
            "%trainer%", trainerId != null ? trainerId : "???",
            "%step%",    step + 1,
            "%total%",   base.getSequence().size()
        )));

        // Recompensas especiales
        if (base.isMiniboss(step)) {
            grantRewards(player, base.getMinibossRewards());
            player.sendSystemMessage(msg(cfg.format(cfg.getMsgMinibossDefeated())));
        }
        if (base.isBoss(step)) {
            grantRewards(player, base.getBossRewards());
            player.sendSystemMessage(msg(cfg.format(cfg.getMsgBossDefeated(),
                "%org%", base.getOrganization()
            )));
        }

        // Avanzar ANTES de generar el siguiente NPC (seguridad ante crash)
        AssaultProgress.advanceStep(player.getUUID(), session.getBaseId());
        int nextStep = step + 1;
        session.setCurrentStep(nextStep);

        if (base.isComplete(nextStep)) {
            // Base completada
            AssaultProgress.markComplete(player.getUUID(), session.getBaseId(), base.getSequence().size());
            player.sendSystemMessage(msg(cfg.format(cfg.getMsgBaseCompleted(),
                "%player%", player.getName().getString()
            )));
            endSession(player, session);
        } else {
            // Generar el siguiente NPC en secuencia
            session.setState(AssaultSession.State.AWAITING_NEXT);
            spawnCurrentNpc(player, session, base);
        }
    }

    private static void onStepLost(ServerPlayer player, AssaultSession session) {
        AssaultConfig cfg = AssaultConfig.get();
        // El progreso NO se reinicia; el jugador puede volver a intentarlo
        player.sendSystemMessage(msg(cfg.format(cfg.getMsgStepLost())));
        endSession(player, session);
    }

    // ── Spawn del NPC activo ───────────────────────────────────────────────────

    /**
     * Genera el NPC correspondiente al paso actual de la sesión.
     * Se llama tanto al iniciar como al re-entrar y al avanzar de paso.
     */
    private static void spawnCurrentNpc(ServerPlayer player, AssaultSession session,
                                         AssaultConfig.AssaultBase base) {
        AssaultConfig cfg = AssaultConfig.get();
        String trainerId = base.getTrainerId(session.getCurrentStep());

        if (trainerId == null) {
            PokeFrontier.LOGGER.error("[PokeFrontier] trainerId null en paso {} de {}", session.getCurrentStep(), base.getId());
            endSession(player, session);
            return;
        }

        UUID npcId = AssaultNpcSpawner.spawnTrainer(player, trainerId, base);
        if (npcId == null) {
            player.sendSystemMessage(msg(cfg.format(cfg.getMsgNpcSpawnError())));
            endSession(player, session);
            return;
        }

        session.setCurrentNpcUuid(npcId);
        session.setState(AssaultSession.State.IN_BATTLE);

        String label = base.buildStepLabel(session.getCurrentStep());
        player.sendSystemMessage(msg("§7Rival: §e" + trainerId + " §8(" + label + ")"));
    }

    // ── Salir de la base (sin perder progreso) ────────────────────────────────

    /**
     * El jugador abandona la base voluntariamente o se desconecta.
     * El NPC desaparece pero el progreso persiste.
     */
    public static void leaveBase(ServerPlayer player) {
        AssaultSession session = AssaultSession.get(player.getUUID());
        if (session == null) return;

        despawnCurrentNpc(player, session);
        // Progreso ya está en disco; solo limpiamos la sesión RAM
        AssaultSession.remove(player.getUUID());
        player.sendSystemMessage(msg(AssaultConfig.get().format(AssaultConfig.get().getMsgLeaveBase())));
    }

    /**
     * Abortar sesión completamente (p. ej. cambio de dimensión, respawn).
     * Igual que leaveBase pero con mensaje distinto.
     */
    public static void abortSession(ServerPlayer player) {
        AssaultSession session = AssaultSession.get(player.getUUID());
        if (session == null) return;

        despawnCurrentNpc(player, session);

        if (session.getReturnLocation() != null) {
            session.getReturnLocation().teleport(player);
        }

        AssaultSession.remove(player.getUUID());
        player.sendSystemMessage(msg(AssaultConfig.get().format(AssaultConfig.get().getMsgSessionAborted())));
    }

    // ── Fin de sesión limpia ───────────────────────────────────────────────────

    private static void endSession(ServerPlayer player, AssaultSession session) {
        despawnCurrentNpc(player, session);
        player.closeContainer();

        if (session.getReturnLocation() != null) {
            session.getReturnLocation().teleport(player);
        }
        AssaultSession.remove(player.getUUID());
    }

    // ── Validación del equipo ─────────────────────────────────────────────────

    private static boolean validateParty(ServerPlayer player, AssaultConfig.AssaultBase base) {
        AssaultConfig cfg = AssaultConfig.get();
        PlayerPartyStore party = Cobblemon.INSTANCE.getStorage().getParty(player);

        for (Pokemon pokemon : party) {
            if (pokemon == null) continue;
            String speciesId = pokemon.getSpecies().resourceIdentifier().toString();

            // Verificar Pokémon individuales prohibidos
            if (base.getBannedPokemon().contains(speciesId)) {
                player.sendSystemMessage(msg(cfg.format(cfg.getMsgBannedPokemon(), "%pokemon%", speciesId)));
                return false;
            }

            // Verificar ban de legendarios
            if (base.isBanLegendaries() && LegendaryChecker.isLegendary(pokemon)) {
                player.sendSystemMessage(msg(cfg.format(cfg.getMsgBannedLegendary())));
                return false;
            }
        }
        return true;
    }

    // ── Utilidades ─────────────────────────────────────────────────────────────

    private static void despawnCurrentNpc(ServerPlayer player, AssaultSession session) {
        if (session.getCurrentNpcUuid() != null) {
            AssaultNpcSpawner.despawnNpc(player, session.getCurrentNpcUuid());
            session.setCurrentNpcUuid(null);
        }
    }

    private static void grantRewards(ServerPlayer player, java.util.List<String> commands) {
        if (commands == null) return;
        commands.forEach(cmd -> {
            String finalCmd = cmd.replace("{player}", player.getScoreboardName());
            player.server.getCommands().performPrefixedCommand(
                player.server.createCommandSourceStack(), finalCmd
            );
        });
    }

    private static Component msg(String text) {
        return Component.literal(text.replace("&", "§"));
    }
}
