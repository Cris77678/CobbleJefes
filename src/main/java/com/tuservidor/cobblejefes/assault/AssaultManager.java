package com.tuservidor.cobblejefes.assault;

import com.cobblemon.mod.common.Cobblemon;
import com.cobblemon.mod.common.api.storage.party.PlayerPartyStore;
import com.cobblemon.mod.common.pokemon.Pokemon;
import com.tuservidor.cobblejefes.CobbleJefes;
import com.tuservidor.cobblejefes.config.AssaultConfig;
import com.tuservidor.cobblejefes.npc.AssaultNpcSpawner;
import com.tuservidor.cobblejefes.util.LegendaryChecker;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerPlayer;

import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.UUID;

public class AssaultManager {

    public static void enterBase(ServerPlayer player, String baseId) {
        AssaultConfig cfg = AssaultConfig.get();

        if (AssaultSession.has(player.getUUID())) {
            player.sendSystemMessage(msg(cfg.format(cfg.getMsgAlreadyInSession())));
            return;
        }

        // FIX CRÍTICO: Evitar que un jugador entre si ya está en una batalla salvaje o PvP
        if (Cobblemon.INSTANCE.getBattleRegistry().getBattleByParticipatingPlayer(player) != null) {
            player.sendSystemMessage(msg("%prefix% &cNo puedes iniciar un asalto mientras estás en otro combate."));
            return;
        }

        AssaultConfig.AssaultBase base = cfg.getBase(baseId);
        if (base == null) {
            player.sendSystemMessage(msg(cfg.format(cfg.getMsgBaseNotFound(), "%base%", baseId)));
            return;
        }

        if (base.getArena() != null && base.getArena().isOccupied()) {
            player.sendSystemMessage(msg(cfg.format("%prefix% &cLa arena está ocupada por otro jugador. ¡Espera tu turno!")));
            return;
        }

        int savedStep = AssaultProgress.getStep(player.getUUID(), baseId);

        if (base.isComplete(savedStep)) {
            player.sendSystemMessage(msg(cfg.format(cfg.getMsgBaseComplete(), "%base%", baseId)));
            return;
        }

        if (!validateParty(player, base)) return;

        if (base.getArena() != null) {
            base.getArena().setOccupied(true);
        }

        AssaultSession session = AssaultSession.start(player.getUUID(), baseId, savedStep);
        session.setReturnLocation(new AssaultConfig.AssaultBase.LocationDef(player));

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

    private static void onStepWon(ServerPlayer player, AssaultSession session, AssaultConfig.AssaultBase base) {
        AssaultConfig cfg = AssaultConfig.get();
        int step = session.getCurrentStep();
        String trainerId = base.getTrainerId(step);

        player.sendSystemMessage(msg(cfg.format(cfg.getMsgStepWon(),
            "%trainer%", trainerId != null ? trainerId : "???",
            "%step%",    step + 1,
            "%total%",   base.getSequence().size()
        )));

        // Entregar recompensas individuales del entrenador
        if (trainerId != null) grantTrainerSpecificRewards(player, trainerId);

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

        AssaultProgress.advanceStep(player.getUUID(), session.getBaseId());
        int nextStep = step + 1;
        session.setCurrentStep(nextStep);

        if (base.isComplete(nextStep)) {
            AssaultProgress.markComplete(player.getUUID(), session.getBaseId(), base.getSequence().size());
            player.sendSystemMessage(msg(cfg.format(cfg.getMsgBaseCompleted(),
                "%player%", player.getName().getString()
            )));
            endSession(player, session);
        } else {
            session.setState(AssaultSession.State.AWAITING_NEXT);
            spawnCurrentNpc(player, session, base);
        }
    }

    private static void onStepLost(ServerPlayer player, AssaultSession session) {
        AssaultConfig cfg = AssaultConfig.get();
        player.sendSystemMessage(msg(cfg.format(cfg.getMsgStepLost())));
        endSession(player, session);
    }

    private static void spawnCurrentNpc(ServerPlayer player, AssaultSession session, AssaultConfig.AssaultBase base) {
        AssaultConfig cfg = AssaultConfig.get();
        String trainerId = base.getTrainerId(session.getCurrentStep());

        if (trainerId == null) {
            CobbleJefes.LOGGER.error("[CobbleJefes] trainerId null en paso {} de {}", session.getCurrentStep(), base.getId());
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

    public static void leaveBase(ServerPlayer player) {
        AssaultSession session = AssaultSession.get(player.getUUID());
        if (session == null) return;

        AssaultConfig.AssaultBase base = AssaultConfig.get().getBase(session.getBaseId());
        if (base != null && base.getArena() != null) base.getArena().setOccupied(false);

        despawnCurrentNpc(player, session);
        
        if (session.getReturnLocation() != null) {
            try {
                session.getReturnLocation().teleport(player);
            } catch (Exception ignored) {}
        }
        
        AssaultSession.remove(player.getUUID());
        try {
            player.sendSystemMessage(msg(AssaultConfig.get().format(AssaultConfig.get().getMsgLeaveBase())));
        } catch (Exception ignored) {}
    }

    public static void abortSession(ServerPlayer player) {
        AssaultSession session = AssaultSession.get(player.getUUID());
        if (session == null) return;

        AssaultConfig.AssaultBase base = AssaultConfig.get().getBase(session.getBaseId());
        if (base != null && base.getArena() != null) base.getArena().setOccupied(false);

        despawnCurrentNpc(player, session);

        // Envuelto en Try-Catch para asegurar que la sesión se borre de RAM aunque falle el teleport
        if (session.getReturnLocation() != null) {
            try {
                session.getReturnLocation().teleport(player);
            } catch (Exception ignored) {} 
        }

        AssaultSession.remove(player.getUUID());
        try {
            player.sendSystemMessage(msg(AssaultConfig.get().format(AssaultConfig.get().getMsgSessionAborted())));
        } catch (Exception ignored) {}
    }

    private static void endSession(ServerPlayer player, AssaultSession session) {
        AssaultConfig.AssaultBase base = AssaultConfig.get().getBase(session.getBaseId());
        if (base != null && base.getArena() != null) base.getArena().setOccupied(false);

        despawnCurrentNpc(player, session);
        
        try {
            player.closeContainer();
        } catch (Exception ignored) {}

        if (session.getReturnLocation() != null) {
            try {
                session.getReturnLocation().teleport(player);
            } catch (Exception ignored) {}
        }
        
        AssaultSession.remove(player.getUUID());
    }

    private static boolean validateParty(ServerPlayer player, AssaultConfig.AssaultBase base) {
        AssaultConfig cfg = AssaultConfig.get();
        PlayerPartyStore party = Cobblemon.INSTANCE.getStorage().getParty(player);

        boolean hasUsablePokemon = false;

        for (Pokemon pokemon : party) {
            if (pokemon == null) continue;

            // FIX: Usar getCurrentHealth para prevenir crasheo de compilación
            if (!pokemon.isEgg() && pokemon.getCurrentHealth() > 0) {
                hasUsablePokemon = true;
            }

            String speciesId = pokemon.getSpecies().resourceIdentifier().toString();

            if (base.getBannedPokemon().contains(speciesId)) {
                player.sendSystemMessage(msg(cfg.format(cfg.getMsgBannedPokemon(), "%pokemon%", speciesId)));
                return false;
            }

            if (base.isBanLegendaries() && LegendaryChecker.isLegendary(pokemon)) {
                player.sendSystemMessage(msg(cfg.format(cfg.getMsgBannedLegendary())));
                return false;
            }
        }

        if (!hasUsablePokemon) {
            player.sendSystemMessage(msg("%prefix% &cNo puedes iniciar el asalto. Necesitas al menos un Pokémon sano que no sea un huevo."));
            return false;
        }

        return true;
    }

    private static void despawnCurrentNpc(ServerPlayer player, AssaultSession session) {
        if (session.getCurrentNpcUuid() != null) {
            AssaultNpcSpawner.despawnNpc(player, session.getCurrentNpcUuid());
            session.setCurrentNpcUuid(null);
        }
    }

    private static void grantRewards(ServerPlayer player, List<String> commands) {
        if (commands == null) return;
        commands.forEach(cmd -> {
            String finalCmd = cmd.replace("{player}", player.getScoreboardName());
            player.server.getCommands().performPrefixedCommand(
                player.server.createCommandSourceStack(), finalCmd
            );
        });
    }

    private static void grantTrainerSpecificRewards(ServerPlayer player, String trainerId) {
        Path cjPath = Path.of("config/cobblejefes/trainers/" + trainerId + ".yml");
        Path pfPath = Path.of("config/pokefrontier/trainers/" + trainerId + ".yml");
        Path bfPath = Path.of("config/battlefrontier/trainers/" + trainerId + ".yml");

        Path path = Files.exists(cjPath) ? cjPath : (Files.exists(pfPath) ? pfPath : (Files.exists(bfPath) ? bfPath : null));
        if (path == null) return;

        try (java.io.Reader r = Files.newBufferedReader(path)) {
            org.yaml.snakeyaml.Yaml yaml = new org.yaml.snakeyaml.Yaml(new org.yaml.snakeyaml.constructor.SafeConstructor(new org.yaml.snakeyaml.LoaderOptions()));
            Map<String, Object> root = yaml.load(r);
            if (!(root.get("trainers") instanceof List<?> list)) return;

            for (Object entry : list) {
                if (!(entry instanceof Map<?, ?> m)) continue;
                if (!trainerId.equals(m.get("trainer_id"))) continue;

                if (m.get("rewards") instanceof Map<?, ?> rewMap) {
                    if (rewMap.get("commands") instanceof List<?> cmds) {
                        List<String> commands = new ArrayList<>();
                        for (Object cmd : cmds) commands.add(cmd.toString());
                        grantRewards(player, commands);
                    }
                }
                break;
            }
        } catch (Exception e) {
            CobbleJefes.LOGGER.error("[CobbleJefes] Error leyendo recompensas individuales de {}", trainerId, e);
        }
    }

    private static Component msg(String text) {
        return Component.literal(text.replace("&", "§"));
    }
}