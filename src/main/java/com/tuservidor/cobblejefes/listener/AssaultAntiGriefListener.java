package com.tuservidor.cobblejefes.listener;

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
            // Cada 10 ticks (2 veces por segundo) para no generar lag
            if (++tickCounter % 10 != 0) return;

            AssaultConfig cfg = AssaultConfig.get();
            double radius = cfg.getArenaProtectionRadius();

            for (ServerPlayer player : server.getPlayerList().getPlayers()) {
                // Admins y jugadores en sesión activa pueden estar cerca
                if (player.hasPermission(2)) continue;
                if (AssaultSession.has(player.getUUID())) continue;

                // Revisar todas las bases con arenas activas
                for (AssaultSession session : AssaultSession.all()) {
                    AssaultConfig.AssaultBase base = cfg.getBase(session.getBaseId());
                    if (base == null || base.getArena() == null) continue;
                    AssaultConfig.AssaultBase.LocationDef npcSpawn = base.getArena().getNpcSpawn();
                    if (npcSpawn == null) continue;

                    // Verificar dimensión primero (rápido)
                    String playerWorld = player.level().dimension().location().toString();
                    if (!playerWorld.equals(npcSpawn.getWorld())) continue;

                    Vec3 npcPos = new Vec3(npcSpawn.getX(), npcSpawn.getY(), npcSpawn.getZ());
                    if (player.position().distanceTo(npcPos) < radius) {
                        // Empujar al jugador fuera de la zona
                        Vec3 dir = player.position().subtract(npcPos).normalize();
                        player.teleportTo(
                            npcPos.x + dir.x * (radius + 2),
                            npcPos.y,
                            npcPos.z + dir.z * (radius + 2)
                        );
                        player.sendSystemMessage(Component.literal(
                            "§c[!] Arena en uso. No puedes entrar durante un combate."
                        ));
                        break; // Solo expulsar una vez por tick
                    }
                }
            }
        });
    }
}
