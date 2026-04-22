package com.tuservidor.pokefrontier.command;

import com.mojang.brigadier.CommandDispatcher;
import com.mojang.brigadier.arguments.DoubleArgumentType;
import com.mojang.brigadier.arguments.StringArgumentType;
import com.tuservidor.pokefrontier.PokeFrontier;
import com.tuservidor.pokefrontier.export.ExportSession;
import com.tuservidor.pokefrontier.export.PartyExporter;
import com.tuservidor.pokefrontier.gui.ExportGui;
import com.tuservidor.pokefrontier.model.TrainerExport;
import net.minecraft.commands.CommandSourceStack;
import net.minecraft.commands.Commands;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerPlayer;

import java.io.IOException;
import java.util.ArrayList;
import java.util.function.Consumer;

public class ExportCommand {

    private static final PartyExporter EXPORTER = new PartyExporter();

    public static void register(CommandDispatcher<CommandSourceStack> dispatcher) {

        // ── /exporttrainer ────────────────────────────────────────────────────
        var exportBase = Commands.literal("exporttrainer")
            .requires(src -> src.hasPermission(2));

        exportBase.then(Commands.literal("gui").executes(ctx -> openGui(ctx.getSource())));

        exportBase.then(Commands.literal("clear").executes(ctx -> {
            ServerPlayer player = ctx.getSource().getPlayerOrException();
            ExportSession.clear(player.getUUID());
            player.sendSystemMessage(Component.literal("§a[PokeFrontier] Sesión de exportación limpiada."));
            return 1;
        }));

        exportBase.then(Commands.argument("trainer_id", StringArgumentType.word())
            .executes(ctx -> quickExport(ctx.getSource(),
                StringArgumentType.getString(ctx, "trainer_id"))));

        exportBase.executes(ctx -> openGui(ctx.getSource()));
        dispatcher.register(exportBase);

        // ── /bfset ────────────────────────────────────────────────────────────
        var bfset = Commands.literal("bfset")
            .requires(src -> src.hasPermission(2));

        bfset.then(Commands.literal("id")
            .then(Commands.argument("value", StringArgumentType.word())
                .executes(ctx -> setField(ctx.getSource(),
                    d -> d.setTrainerId(StringArgumentType.getString(ctx, "value").toLowerCase()),
                    "ID", StringArgumentType.getString(ctx, "value")))));

        bfset.then(Commands.literal("name")
            .then(Commands.argument("value", StringArgumentType.greedyString())
                .executes(ctx -> setField(ctx.getSource(),
                    d -> d.setTrainerName(StringArgumentType.getString(ctx, "value")),
                    "Nombre", StringArgumentType.getString(ctx, "value")))));

        bfset.then(Commands.literal("battle")
            .then(Commands.argument("value", StringArgumentType.greedyString())
                .executes(ctx -> setField(ctx.getSource(),
                    d -> d.setBattleDialogue(StringArgumentType.getString(ctx, "value")),
                    "Diálogo de batalla", StringArgumentType.getString(ctx, "value")))));

        bfset.then(Commands.literal("defeat")
            .then(Commands.argument("value", StringArgumentType.greedyString())
                .executes(ctx -> setField(ctx.getSource(),
                    d -> d.setDefeatDialogue(StringArgumentType.getString(ctx, "value")),
                    "Diálogo de derrota", StringArgumentType.getString(ctx, "value")))));

        bfset.then(Commands.literal("skin")
            .then(Commands.argument("value", StringArgumentType.word())
                .executes(ctx -> setField(ctx.getSource(),
                    d -> d.setSkin(StringArgumentType.getString(ctx, "value")),
                    "Skin", StringArgumentType.getString(ctx, "value")))));

        bfset.then(Commands.literal("cooldown")
            .then(Commands.argument("hours", DoubleArgumentType.doubleArg(0, 720))
                .executes(ctx -> setField(ctx.getSource(),
                    d -> d.setCooldownHours(DoubleArgumentType.getDouble(ctx, "hours")),
                    "Cooldown", DoubleArgumentType.getDouble(ctx, "hours") + "h"))));

        bfset.then(Commands.literal("rewardcmd")
            .then(Commands.argument("cmd", StringArgumentType.greedyString())
                .executes(ctx -> setField(ctx.getSource(),
                    d -> d.getRewards().getCommands().add(StringArgumentType.getString(ctx, "cmd")),
                    "Comando de Recompensa añadido", StringArgumentType.getString(ctx, "cmd")))));

        dispatcher.register(bfset);
    }

    private static int openGui(CommandSourceStack src) {
        try {
            ServerPlayer player = src.getPlayerOrException();

            ExportSession existing = ExportSession.get(player.getUUID());
            if (existing != null) {
                PokeFrontier.SERVER.execute(() -> new ExportGui(player, existing.getDraft(), EXPORTER).open());
                return 1;
            }

            TrainerExport draft = EXPORTER.buildFromParty(player);
            if (draft.getPokemonTeam().isEmpty()) {
                player.sendSystemMessage(Component.literal("§c[PokeFrontier] No tienes Pokémon en tu equipo."));
                return 0;
            }

            draft.setTrainerId("nuevo_entrenador");
            draft.setTrainerName(player.getName().getString());
            draft.setBattleDialogue("¡Prepárate para pelear!");
            draft.setDefeatDialogue("¡Increíble, me ganaste!");
            draft.setCooldownHours(12);
            draft.setRewards(new TrainerExport.RewardEntry(new ArrayList<>(), new ArrayList<>()));

            var party = com.cobblemon.mod.common.Cobblemon.INSTANCE.getStorage().getParty(player);
            java.util.List<com.cobblemon.mod.common.pokemon.Pokemon> realPokemon = new ArrayList<>();
            for (var pk : party) { if (pk != null) realPokemon.add(pk); }

            ExportSession.start(player.getUUID(), draft, realPokemon);
            PokeFrontier.SERVER.execute(() -> new ExportGui(player, draft, EXPORTER).open());
            return 1;

        } catch (Exception e) {
            PokeFrontier.LOGGER.error("[PokeFrontier] Error abriendo GUI de exportación", e);
            return 0;
        }
    }

    private static int quickExport(CommandSourceStack src, String trainerId) {
        try {
            ServerPlayer player = src.getPlayerOrException();
            TrainerExport trainer = EXPORTER.buildFromParty(player);
            if (trainer.getPokemonTeam().isEmpty()) {
                player.sendSystemMessage(Component.literal("§c[PokeFrontier] No tienes Pokémon en tu equipo."));
                return 0;
            }

            trainer.setTrainerId(trainerId.toLowerCase());
            trainer.setTrainerName(player.getName().getString());
            trainer.setBattleDialogue("¡Prepárate para pelear!");
            trainer.setDefeatDialogue("¡Increíble, me ganaste!");
            trainer.setCooldownHours(12);
            trainer.setRewards(new TrainerExport.RewardEntry(new ArrayList<>(), new ArrayList<>()));

            String path = EXPORTER.writeTrainer(trainer);
            player.sendSystemMessage(Component.literal(
                "§a[PokeFrontier] §7Exportado como §e" + trainerId + " §7→ §e" + path
            ));
            return 1;

        } catch (IOException e) {
            PokeFrontier.LOGGER.error("[PokeFrontier] Error en quick export", e);
            src.sendFailure(Component.literal("§c[PokeFrontier] Error: " + e.getMessage()));
            return 0;
        }
    }

    private static int setField(CommandSourceStack src, Consumer<TrainerExport> setter,
                                  String fieldName, Object value) {
        try {
            ServerPlayer player = src.getPlayerOrException();
            ExportSession session = ExportSession.get(player.getUUID());
            if (session == null) {
                player.sendSystemMessage(Component.literal(
                    "§c[PokeFrontier] No tienes sesión activa. Usa §e/exporttrainer gui §cprimero."
                ));
                return 0;
            }
            setter.accept(session.getDraft());
            player.sendSystemMessage(Component.literal(
                "§a[PokeFrontier] §7" + fieldName + " actualizado: §e" + value
            ));
            PokeFrontier.SERVER.execute(() -> new ExportGui(player, session.getDraft(), EXPORTER).open());
            return 1;
        } catch (Exception e) {
            return 0;
        }
    }
}
