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

    public static void register() {
        ServerTickEvents.START_SERVER_TICK.register(server -> {
            if (++tickCounter % 10 != 0) return;

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

                    // FIX CRÍTICO: El dueño de la sesión no puede huir de la arena y dejarla bloqueada
                    if (playerSession != null && playerSession.getBaseId().equals(session.getBaseId())) {
                        if (distance > radius * 1.5) { // Si se aleja 1.5 veces el radio de protección
                            player.sendSystemMessage(Component.literal(
                                "§c[!] Has abandonado la zona de combate. Asalto cancelado automáticamente."
                            ));
                            AssaultManager.abortSession(player);
                        }
                        continue;
                    }

                    // Protección contra intrusos (jugadores ajenos al asalto)
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
        });
    }
}