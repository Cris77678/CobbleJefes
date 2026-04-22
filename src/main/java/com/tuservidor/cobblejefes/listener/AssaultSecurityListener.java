package com.tuservidor.cobblejefes.listener;

import com.tuservidor.cobblejefes.PokeFrontier;
import com.tuservidor.cobblejefes.assault.AssaultManager;
import com.tuservidor.cobblejefes.assault.AssaultProgress;
import com.tuservidor.cobblejefes.assault.AssaultSession;
import com.tuservidor.cobblejefes.config.AssaultConfig;
import com.tuservidor.cobblejefes.npc.AssaultNpcSpawner;
import net.fabricmc.fabric.api.entity.event.v1.ServerEntityWorldChangeEvents;
import net.fabricmc.fabric.api.entity.event.v1.ServerPlayerEvents;
import net.fabricmc.fabric.api.event.lifecycle.v1.ServerEntityEvents;
import net.fabricmc.fabric.api.event.player.AttackEntityCallback;
import net.fabricmc.fabric.api.event.player.UseBlockCallback;
import net.fabricmc.fabric.api.networking.v1.ServerPlayConnectionEvents;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.network.chat.Component;
import net.minecraft.world.InteractionResult;

import java.util.Map;

public class AssaultSecurityListener {

    public static void register() {

        // ── Desconexión: limpiar sesión RAM sin perder progreso ────────────────
        ServerPlayConnectionEvents.DISCONNECT.register((handler, server) -> {
            var player = handler.getPlayer();
            AssaultSession session = AssaultSession.get(player.getUUID());
            if (session != null) {
                // Progreso ya está en disco. Solo limpiar sesión RAM.
                AssaultSession.remove(player.getUUID());
                PokeFrontier.LOGGER.info("[PokeFrontier] Sesión de {} limpiada por desconexión. Progreso conservado.", player.getName().getString());
            }
            // Limpiar sesión de exportación
            try {
                com.tuservidor.cobblejefes.export.ExportSession.clear(player.getUUID());
            } catch (Exception ignored) {}
        });

        // ── Conexión: notificar bases con progreso pendiente ──────────────────
        ServerPlayConnectionEvents.JOIN.register((handler, sender, server) -> {
            var player = handler.getPlayer();
            Map<String, Integer> progress = AssaultProgress.getAllProgress(player.getUUID());
            if (progress.isEmpty()) return;

            StringBuilder basesWithProgress = new StringBuilder();
            AssaultConfig cfg = AssaultConfig.get();
            progress.forEach((baseId, step) -> {
                AssaultConfig.AssaultBase base = cfg.getBase(baseId);
                if (base != null && step > 0 && !base.isComplete(step)) {
                    if (basesWithProgress.length() > 0) basesWithProgress.append(", ");
                    basesWithProgress.append(base.getOrganization())
                        .append(" (").append(step).append("/").append(base.getSequence().size()).append(")");
                }
            });

            if (basesWithProgress.length() > 0) {
                player.sendSystemMessage(Component.literal(
                    cfg.format(cfg.getMsgReconnectProgress(), "%bases%", basesWithProgress.toString())
                ));
            }
        });

        // ── Cambio de dimensión: abortar sesión ───────────────────────────────
        ServerEntityWorldChangeEvents.AFTER_PLAYER_CHANGE_WORLD.register((player, origin, dest) -> {
            if (AssaultSession.has(player.getUUID())) {
                AssaultManager.abortSession(player);
            }
        });

        // ── Respawn: abortar sesión ────────────────────────────────────────────
        ServerPlayerEvents.AFTER_RESPAWN.register((oldP, newP, alive) -> {
            if (AssaultSession.has(newP.getUUID())) {
                AssaultManager.abortSession(newP);
            }
        });

        // ── Eliminar NPCs fantasma al cargar el chunk ─────────────────────────
        ServerEntityEvents.ENTITY_LOAD.register((entity, world) -> {
            if (!entity.getTags().contains("pf_assault")) return;
            boolean isClaimed = AssaultSession.all().stream()
                .anyMatch(s -> entity.getUUID().equals(s.getCurrentNpcUuid()));
            if (!isClaimed) {
                entity.discard();
                PokeFrontier.LOGGER.info("[PokeFrontier] NPC fantasma eliminado: {}", entity.getUUID());
            }
        });

        // ── Bloquear ataques a NPCs de asalto ─────────────────────────────────
        AttackEntityCallback.EVENT.register((player, world, hand, entity, hitResult) -> {
            if (AssaultNpcSpawner.isAssaultNpc(entity)) return InteractionResult.FAIL;
            return InteractionResult.PASS;
        });

        // ── Bloquear uso de máquinas durante asalto ───────────────────────────
        UseBlockCallback.EVENT.register((player, world, hand, hitResult) -> {
            if (player.toServerPlayer() == null) return InteractionResult.PASS;
            if (!AssaultSession.has(player.getUUID())) return InteractionResult.PASS;

            var block = world.getBlockState(hitResult.getBlockPos()).getBlock();
            String id = BuiltInRegistries.BLOCK.getKey(block).toString();
            if (id.contains("pc") || id.contains("trade_machine") || id.contains("healing_machine")) {
                player.sendSystemMessage(Component.literal(
                    AssaultConfig.get().format(AssaultConfig.get().getMsgMachineBlocked())
                ));
                return InteractionResult.FAIL;
            }
            return InteractionResult.PASS;
        });
    }
}
