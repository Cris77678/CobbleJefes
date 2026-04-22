package com.tuservidor.cobblejefes.command;

import com.mojang.brigadier.CommandDispatcher;
import com.mojang.brigadier.arguments.StringArgumentType;
import com.mojang.brigadier.context.CommandContext;
import com.tuservidor.cobblejefes.assault.AssaultManager;
import com.tuservidor.cobblejefes.assault.AssaultProgress;
import com.tuservidor.cobblejefes.assault.AssaultSession;
import com.tuservidor.cobblejefes.config.AssaultConfig;
import net.minecraft.commands.CommandSourceStack;
import net.minecraft.commands.Commands;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerPlayer;

import java.util.Map;

/**
 * Comandos del sistema de Asalto a Bases.
 *
 * /assault enter <base_id>   — Entrar a una base (o continuar progreso guardado)
 * /assault leave             — Salir de la base (conserva progreso)
 * /assault status            — Ver progreso en todas las bases
 * /assault reset <base_id>  — Reiniciar progreso en una base (admin)
 * /assault reload            — Recargar configuración (admin)
 * /assault setarena <base_id> player|npc — Guardar posición actual como spawn (admin)
 */
public class AssaultCommand {

    public static void register(CommandDispatcher<CommandSourceStack> dispatcher) {
        var root = Commands.literal("assault");

        // /assault enter <base_id>
        root.then(Commands.literal("enter")
            .then(Commands.argument("base_id", StringArgumentType.word())
                .executes(ctx -> enterBase(ctx, StringArgumentType.getString(ctx, "base_id")))));

        // /assault leave
        root.then(Commands.literal("leave").executes(AssaultCommand::leaveBase));

        // /assault status
        root.then(Commands.literal("status").executes(AssaultCommand::showStatus));

        // /assault reset <base_id> (op)
        root.then(Commands.literal("reset")
            .requires(src -> src.hasPermission(2))
            .then(Commands.argument("base_id", StringArgumentType.word())
                .executes(ctx -> resetProgress(ctx, StringArgumentType.getString(ctx, "base_id")))));

        // /assault reload (op)
        root.then(Commands.literal("reload")
            .requires(src -> src.hasPermission(2))
            .executes(AssaultCommand::reloadConfig));

        // /assault setarena <base_id> player|npc (op)
        root.then(Commands.literal("setarena")
            .requires(src -> src.hasPermission(2))
            .then(Commands.argument("base_id", StringArgumentType.word())
                .then(Commands.literal("player")
                    .executes(ctx -> setArenaSpawn(ctx,
                        StringArgumentType.getString(ctx, "base_id"), "player")))
                .then(Commands.literal("npc")
                    .executes(ctx -> setArenaSpawn(ctx,
                        StringArgumentType.getString(ctx, "base_id"), "npc")))));

        dispatcher.register(root);
    }

    // ── Handlers ──────────────────────────────────────────────────────────────

    private static int enterBase(CommandContext<CommandSourceStack> ctx, String baseId) {
        try {
            ServerPlayer player = ctx.getSource().getPlayerOrException();
            AssaultManager.enterBase(player, baseId);
            return 1;
        } catch (Exception e) {
            ctx.getSource().sendFailure(Component.literal("Error al entrar a la base."));
            return 0;
        }
    }

    private static int leaveBase(CommandContext<CommandSourceStack> ctx) {
        try {
            ServerPlayer player = ctx.getSource().getPlayerOrException();
            if (!AssaultSession.has(player.getUUID())) {
                player.sendSystemMessage(Component.literal(
                    AssaultConfig.get().format(AssaultConfig.get().getMsgNoSession())
                ));
                return 0;
            }
            AssaultManager.leaveBase(player);
            return 1;
        } catch (Exception e) {
            return 0;
        }
    }

    private static int showStatus(CommandContext<CommandSourceStack> ctx) {
        try {
            ServerPlayer player = ctx.getSource().getPlayerOrException();
            AssaultConfig cfg = AssaultConfig.get();
            Map<String, Integer> progress = AssaultProgress.getAllProgress(player.getUUID());

            player.sendSystemMessage(Component.literal("§6§l=== Tu Progreso en Bases ==="));

            if (cfg.getBases().isEmpty()) {
                player.sendSystemMessage(Component.literal("§7No hay bases configuradas."));
                return 1;
            }

            for (AssaultConfig.AssaultBase base : cfg.getBases()) {
                int step = progress.getOrDefault(base.getId().toLowerCase(), 0);
                boolean complete = base.isComplete(step);
                String color = complete ? "§a" : (step > 0 ? "§e" : "§7");
                String status = complete
                    ? "§a✔ COMPLETADA"
                    : (step > 0 ? "§eEn progreso: " + step + "/" + base.getSequence().size() : "§7Sin iniciar");

                player.sendSystemMessage(Component.literal(
                    color + base.getOrganization() + " §8(" + base.getId() + "): " + status
                ));
            }

            return 1;
        } catch (Exception e) {
            return 0;
        }
    }

    private static int resetProgress(CommandContext<CommandSourceStack> ctx, String baseId) {
        try {
            ServerPlayer player = ctx.getSource().getPlayerOrException();
            AssaultConfig.AssaultBase base = AssaultConfig.get().getBase(baseId);
            if (base == null) {
                player.sendSystemMessage(Component.literal(
                    AssaultConfig.get().format(AssaultConfig.get().getMsgBaseNotFound(), "%base%", baseId)
                ));
                return 0;
            }
            AssaultProgress.resetBase(player.getUUID(), baseId);
            player.sendSystemMessage(Component.literal(
                AssaultConfig.get().format(AssaultConfig.get().getMsgProgressReset(), "%base%", baseId)
            ));
            return 1;
        } catch (Exception e) {
            return 0;
        }
    }

    private static int reloadConfig(CommandContext<CommandSourceStack> ctx) {
        try {
            AssaultConfig.reload();
            ctx.getSource().sendSuccess(
                () -> Component.literal("§a[PokeFrontier] Configuración recargada."), false
            );
            return 1;
        } catch (Exception e) {
            ctx.getSource().sendFailure(Component.literal("§cError al recargar la configuración."));
            return 0;
        }
    }

    private static int setArenaSpawn(CommandContext<CommandSourceStack> ctx,
                                      String baseId, String type) {
        try {
            ServerPlayer player = ctx.getSource().getPlayerOrException();
            AssaultConfig cfg = AssaultConfig.get();
            AssaultConfig.AssaultBase base = cfg.getBase(baseId);

            if (base == null) {
                player.sendSystemMessage(Component.literal(
                    cfg.format(cfg.getMsgBaseNotFound(), "%base%", baseId)
                ));
                return 0;
            }

            if (base.getArena() == null) {
                base.setArena(new AssaultConfig.AssaultBase.Arena());
                base.getArena().setId("arena_" + baseId);
            }

            AssaultConfig.AssaultBase.LocationDef loc = new AssaultConfig.AssaultBase.LocationDef(player);

            if ("player".equals(type)) {
                base.getArena().setPlayerSpawn(loc);
                player.sendSystemMessage(Component.literal(
                    "§a[PokeFrontier] Spawn de jugador guardado para §e" + baseId
                    + " §7en §f" + formatLoc(loc)
                ));
            } else {
                base.getArena().setNpcSpawn(loc);
                player.sendSystemMessage(Component.literal(
                    "§a[PokeFrontier] Spawn de NPC guardado para §e" + baseId
                    + " §7en §f" + formatLoc(loc)
                ));
            }

            cfg.save();
            return 1;
        } catch (Exception e) {
            ctx.getSource().sendFailure(Component.literal("Error al guardar spawn."));
            return 0;
        }
    }

    private static String formatLoc(AssaultConfig.AssaultBase.LocationDef loc) {
        return String.format("%.1f, %.1f, %.1f (yaw=%.0f)", loc.getX(), loc.getY(), loc.getZ(), loc.getYaw());
    }
}
