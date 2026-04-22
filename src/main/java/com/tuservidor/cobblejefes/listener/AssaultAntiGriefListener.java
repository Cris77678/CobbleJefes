package com.tuservidor.cobblejefes.listener;

import com.tuservidor.cobblejefes.assault.AssaultManager;
import com.tuservidor.cobblejefes.assault.AssaultSession;
import com.tuservidor.cobblejefes.config.AssaultConfig;
import net.fabricmc.fabric.api.event.lifecycle.v1.ServerTickEvents;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.phys.Vec3;

public class AssaultAntiGriefListener {

    private static int tickCounter = 0;
    private static final long AFK_TIMEOUT_MS = 300_000L; // 5 minutos de inactividad máxima

    public static void register() {
        ServerTickEvents.START_SERVER_TICK.register(server -> {
            tickCounter++;
            
            // Cada 10 ticks comprobamos movimiento físico y Anti-Grief
            if (tickCounter % 10 == 0) {
                AssaultConfig cfg = AssaultConfig.get();
                double radius = cfg.getArenaProtectionRadius();

                for (ServerPlayer player : server.getPlayerList().getPlayers()) {
                    if (player.hasPermission(2)) continue;

                    AssaultSession playerSession = AssaultSession.get(player.getUUID());

                    for (AssaultSession session : AssaultSession.all()) {
                        AssaultConfig.AssaultBase base = cfg.getBase(session.getBaseId());
                        if (base == null || base.getArena() == null) continue;
                        AssaultConfig.AssaultBase.LocationDef npcSpawn = base.getArena().getNpcSpawn();
                        if (npcSpawn == null) continue;

                        String playerWorld = player.level().dimension().location().toString();
                        if (!playerWorld.equals(npcSpawn.getWorld())) continue;

                        Vec3 npcPos = new Vec3(npcSpawn.getX(), npcSpawn.getY(), npcSpawn.getZ());
                        double distance = player.position().distanceTo(npcPos);

                        // El dueño no puede huir lejos de la arena
                        if (playerSession != null && playerSession.getBaseId().equals(session.getBaseId())) {
                            if (distance > radius * 1.5) {
                                player.sendSystemMessage(Component.literal(
                                    "§c[!] Has abandonado la zona de combate. Asalto cancelado automáticamente."
                                ));
                                AssaultManager.abortSession(player);
                            }
                            continue;
                        }

                        // Repeler intrusos
                        if (distance < radius) {
                            Vec3 dir = player.position().subtract(npcPos).normalize();
                            player.teleportTo(
                                npcPos.x + dir.x * (radius + 2),
                                npcPos.y,
                                npcPos.z + dir.z * (radius + 2)
                            );
                            player.sendSystemMessage(Component.literal(
                                "§c[!] Arena en uso. No puedes entrar durante un combate ajeno."
                            ));
                            break;
                        }
                    }
                }
            }

            // Cada segundo (20 ticks) comprobamos el Timeout por AFK
            if (tickCounter % 20 == 0) {
                long currentTime = System.currentTimeMillis();
                for (AssaultSession session : AssaultSession.all()) {
                    // Si no está peleando activamente y ha pasado el tiempo límite
                    if (session.getState() != AssaultSession.State.IN_BATTLE) {
                        if (currentTime - session.getLastActivityTime() > AFK_TIMEOUT_MS) {
                            ServerPlayer p = server.getPlayerList().getPlayer(session.getPlayerUuid());
                            if (p != null) {
                                p.sendSystemMessage(Component.literal(
                                    "§c[!] Asalto cancelado por inactividad (Has estado AFK demasiado tiempo)."
                                ));
                                AssaultManager.abortSession(p);
                            } else {
                                // Limpieza de emergencia si el jugador está offline
                                AssaultSession.remove(session.getPlayerUuid());
                            }
                        }
                    } else {
                        // Si está en batalla, renovamos su tiempo de actividad
                        session.markActive();
                    }
                }
            }
        });
    }
}