package com.tuservidor.cobblejefes.listener;

import com.tuservidor.cobblejefes.CobbleJefes;
import com.tuservidor.cobblejefes.assault.AssaultManager;
import com.tuservidor.cobblejefes.assault.AssaultProgress;
import com.tuservidor.cobblejefes.assault.AssaultSession;
import com.tuservidor.cobblejefes.config.AssaultConfig;
import com.tuservidor.cobblejefes.npc.AssaultNpcSpawner;
import net.fabricmc.fabric.api.entity.event.v1.ServerEntityWorldChangeEvents;
import net.fabricmc.fabric.api.entity.event.v1.ServerPlayerEvents;
import net.fabricmc.fabric.api.event.lifecycle.v1.ServerEntityEvents;
import net.fabricmc.fabric.api.event.player.AttackBlockCallback;
import net.fabricmc.fabric.api.event.player.AttackEntityCallback;
import net.fabricmc.fabric.api.event.player.UseBlockCallback;
import net.fabricmc.fabric.api.networking.v1.ServerPlayConnectionEvents;
import net.minecraft.core.BlockPos;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.item.BlockItem;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.Level;
import net.minecraft.world.phys.Vec3;

import java.util.Map;

public class AssaultSecurityListener {

    public static void register() {

        ServerPlayConnectionEvents.DISCONNECT.register((handler, server) -> {
            var player = handler.getPlayer();
            if (AssaultSession.has(player.getUUID())) {
                AssaultManager.abortSession(player);
                CobbleJefes.LOGGER.info("[CobbleJefes] Sesión de {} limpiada por desconexión. Progreso conservado.", player.getName().getString());
            }
            try {
                com.tuservidor.cobblejefes.export.ExportSession.clear(player.getUUID());
            } catch (Exception ignored) {}
        });

        ServerPlayConnectionEvents.JOIN.register((handler, sender, server) -> {
            var player = handler.getPlayer();
            AssaultConfig cfg = AssaultConfig.get();

            // ── FIX CRÍTICO: EVACUACIÓN ANTI-SOFTLOCK ──
            // Si el servidor se reinició mientras estaba en la arena, lo sacamos al Spawn
            for (AssaultConfig.AssaultBase base : cfg.getBases()) {
                if (base.getArena() != null && base.getArena().getNpcSpawn() != null) {
                    var spawn = base.getArena().getNpcSpawn();
                    if (player.level().dimension().location().toString().equals(spawn.getWorld())) {
                        double dist = player.position().distanceTo(new Vec3(spawn.getX(), spawn.getY(), spawn.getZ()));
                        if (dist < cfg.getArenaProtectionRadius() + 5.0) {
                            ServerLevel overworld = server.getLevel(Level.OVERWORLD);
                            if (overworld != null) {
                                BlockPos sharedSpawn = overworld.getSharedSpawnPos();
                                player.teleportTo(overworld, sharedSpawn.getX(), sharedSpawn.getY(), sharedSpawn.getZ(), 0, 0);
                                player.sendSystemMessage(Component.literal("§e[CobbleJefes] Fuiste evacuado de la arena por tu seguridad (Desconexión en combate)."));
                            }
                            break;
                        }
                    }
                }
            }

            Map<String, Integer> progress = AssaultProgress.getAllProgress(player.getUUID());
            if (progress.isEmpty()) return;

            StringBuilder basesWithProgress = new StringBuilder();
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

        ServerEntityWorldChangeEvents.AFTER_PLAYER_CHANGE_WORLD.register((player, origin, dest) -> {
            if (AssaultSession.has(player.getUUID())) AssaultManager.abortSession(player);
        });

        ServerPlayerEvents.AFTER_RESPAWN.register((oldP, newP, alive) -> {
            if (AssaultSession.has(newP.getUUID())) AssaultManager.abortSession(newP);
        });

        ServerEntityEvents.ENTITY_LOAD.register((entity, world) -> {
            if (!entity.getTags().contains("pf_assault")) return;
            boolean isClaimed = AssaultSession.all().stream()
                .anyMatch(s -> entity.getUUID().equals(s.getCurrentNpcUuid()));
            if (!isClaimed) {
                entity.discard();
                CobbleJefes.LOGGER.info("[CobbleJefes] NPC fantasma eliminado: {}", entity.getUUID());
            }
        });

        AttackEntityCallback.EVENT.register((player, world, hand, entity, hitResult) -> {
            if (AssaultNpcSpawner.isAssaultNpc(entity)) return InteractionResult.FAIL;
            return InteractionResult.PASS;
        });

        AttackBlockCallback.EVENT.register((player, world, hand, pos, direction) -> {
            if (!player.hasPermissions(2) && AssaultSession.has(player.getUUID())) {
                return InteractionResult.FAIL;
            }
            return InteractionResult.PASS;
        });

        UseBlockCallback.EVENT.register((player, world, hand, hitResult) -> {
            if (player.toServerPlayer() == null) return InteractionResult.PASS;
            if (!AssaultSession.has(player.getUUID())) return InteractionResult.PASS;

            ItemStack itemInHand = player.getItemInHand(hand);
            if (itemInHand.getItem() instanceof BlockItem && !player.hasPermissions(2)) {
                return InteractionResult.FAIL;
            }

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