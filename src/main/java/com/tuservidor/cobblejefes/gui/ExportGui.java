package com.tuservidor.cobblejefes.gui;

import com.cobblemon.mod.common.item.PokemonItem;
import com.cobblemon.mod.common.pokemon.Pokemon;
import com.tuservidor.cobblejefes.PokeFrontier;
import com.tuservidor.cobblejefes.export.ExportSession;
import com.tuservidor.cobblejefes.export.PartyExporter;
import com.tuservidor.cobblejefes.model.TrainerExport;
import net.minecraft.core.component.DataComponents;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.SimpleContainer;
import net.minecraft.world.SimpleMenuProvider;
import net.minecraft.world.entity.player.Inventory;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.inventory.ChestMenu;
import net.minecraft.world.inventory.ClickType;
import net.minecraft.world.inventory.MenuType;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.item.component.ItemLore;

import java.io.IOException;
import java.util.*;

public class ExportGui {

    private final ServerPlayer player;
    private final PartyExporter exporter;

    public ExportGui(ServerPlayer player, TrainerExport draft, PartyExporter exporter) {
        this.player   = player;
        this.exporter = exporter;
        if (ExportSession.get(player.getUUID()) == null) {
            ExportSession.start(player.getUUID(), draft);
        }
    }

    public void open() {
        ExportSession session = ExportSession.get(player.getUUID());
        if (session == null) return;
        TrainerExport draft       = session.getDraft();
        List<Pokemon> realPokemon = session.getRealPokemon();

        SimpleContainer container = buildContainer(draft, realPokemon);
        Map<Integer, Runnable> clicks = buildClicks(draft);

        player.openMenu(new SimpleMenuProvider(
            (syncId, inv, p) -> new ExportMenu(syncId, inv, container, clicks),
            Component.literal("§6§lExportar Entrenador")
        ));
    }

    public void refresh() { PokeFrontier.SERVER.execute(this::open); }

    // ── Container ─────────────────────────────────────────────────────────────

    private SimpleContainer buildContainer(TrainerExport draft, List<Pokemon> realPokemon) {
        SimpleContainer c = new SimpleContainer(54);
        for (int i = 0; i < 54; i++) c.setItem(i, filler());

        // Pokémon del equipo
        for (int i = 0; i < Math.min(realPokemon.size(), 6); i++) {
            Pokemon pk = realPokemon.get(i);
            ItemStack sprite = PokemonItem.from(pk);
            List<String> lore = new ArrayList<>();
            lore.add("§7Nivel: §f" + pk.getLevel());
            lore.add("§7Naturaleza: §f" + capitalize(pk.getNature().getName().getPath()));
            lore.add("§7Habilidad: §f" + pk.getAbility().getName());
            if (!pk.heldItem().isEmpty()) {
                String heldId = net.minecraft.core.registries.BuiltInRegistries.ITEM
                    .getKey(pk.heldItem().getItem()).getPath();
                lore.add("§7Objeto: §e" + capitalize(heldId.replace("_", " ")));
            }
            List<String> moveNames = new ArrayList<>();
            pk.getMoveSet().forEach(m -> { if (m != null) moveNames.add(m.getName()); });
            if (!moveNames.isEmpty()) lore.add("§7Movimientos: §f" + String.join(", ", moveNames));

            sprite.set(DataComponents.CUSTOM_NAME,
                Component.literal("§e§l" + capitalize(pk.getSpecies().getName()))
                    .withStyle(s -> s.withItalic(false)));
            sprite.set(DataComponents.LORE, new ItemLore(
                lore.stream().map(l -> (Component) Component.literal(l)
                    .withStyle(s -> s.withItalic(false))).toList()));
            c.setItem(10 + i, sprite);
        }

        // Campos editables
        c.setItem(28, makeItem(Items.NAME_TAG, "§b§lID del Entrenador", List.of(
            "§7Actual: §e" + draft.getTrainerId(), "", "§aClick para editar", "§8→ /bfset id <valor>")));
        c.setItem(30, makeItem(Items.WRITABLE_BOOK, "§a§lNombre del Entrenador", List.of(
            "§7Actual: §e" + draft.getTrainerName(), "", "§aClick para editar", "§8→ /bfset name <valor>")));
        c.setItem(32, makeItem(Items.BOOK, "§d§lDialogo de Batalla", List.of(
            "§7Actual: §e" + truncate(draft.getBattleDialogue(), 35), "", "§aClick para editar", "§8→ /bfset battle <texto>")));
        c.setItem(34, makeItem(Items.BOOK, "§c§lDialogo de Derrota", List.of(
            "§7Actual: §e" + truncate(draft.getDefeatDialogue(), 35), "", "§aClick para editar", "§8→ /bfset defeat <texto>")));

        // Cooldown +/-
        c.setItem(37, makeItem(Items.RED_DYE,  "§c§l- 1h Cooldown", List.of("§7Actual: §e" + draft.getCooldownHours() + "h")));
        c.setItem(39, makeItem(Items.CLOCK,    "§6§lCooldown",       List.of("§7Actual: §e" + draft.getCooldownHours() + " horas")));
        c.setItem(41, makeItem(Items.LIME_DYE, "§a§l+ 1h Cooldown", List.of("§7Actual: §e" + draft.getCooldownHours() + "h")));

        // Acciones
        c.setItem(46, makeItem(Items.LIME_CONCRETE, "§a§l✔ EXPORTAR", List.of(
            "§7ID: §e" + draft.getTrainerId(), "§7Nombre: §e" + draft.getTrainerName(),
            "§7Pokémon: §e" + draft.getPokemonTeam().size(), "§7Cooldown: §e" + draft.getCooldownHours() + "h",
            "", "§aClick para guardar el archivo")));
        c.setItem(52, makeItem(Items.RED_CONCRETE, "§c§l✖ Cancelar", List.of("§7Descarta la exportación")));

        return c;
    }

    // ── Clicks ─────────────────────────────────────────────────────────────────

    private Map<Integer, Runnable> buildClicks(TrainerExport draft) {
        Map<Integer, Runnable> clicks = new HashMap<>();
        clicks.put(28, () -> promptEdit("§b[Export] §7Nuevo ID:", "§e/bfset id <valor>", "§8Ej: /bfset id giovanni"));
        clicks.put(30, () -> promptEdit("§a[Export] §7Nuevo nombre:", "§e/bfset name <valor>", "§8Ej: /bfset name Giovanni"));
        clicks.put(32, () -> promptEdit("§d[Export] §7Diálogo batalla:", "§e/bfset battle <texto>", ""));
        clicks.put(34, () -> promptEdit("§c[Export] §7Diálogo derrota:", "§e/bfset defeat <texto>", ""));
        clicks.put(37, () -> {
            ExportSession s = ExportSession.get(player.getUUID());
            if (s != null) { s.getDraft().setCooldownHours(Math.max(0, s.getDraft().getCooldownHours() - 1)); refresh(); }
        });
        clicks.put(41, () -> {
            ExportSession s = ExportSession.get(player.getUUID());
            if (s != null) { s.getDraft().setCooldownHours(Math.min(720, s.getDraft().getCooldownHours() + 1)); refresh(); }
        });
        clicks.put(46, this::confirmExport);
        clicks.put(52, () -> {
            ExportSession.clear(player.getUUID());
            player.closeContainer();
            player.sendSystemMessage(Component.literal("§7[PokeFrontier] Exportación cancelada."));
        });
        return clicks;
    }

    private void promptEdit(String header, String command, String example) {
        player.closeContainer();
        player.sendSystemMessage(Component.literal(""));
        player.sendSystemMessage(Component.literal(header));
        player.sendSystemMessage(Component.literal(command));
        if (!example.isEmpty()) player.sendSystemMessage(Component.literal(example));
        player.sendSystemMessage(Component.literal("§7(La GUI se reabrirá automáticamente al ejecutar el comando)"));
    }

    private void confirmExport() {
        ExportSession session = ExportSession.get(player.getUUID());
        if (session == null) return;
        TrainerExport trainer = session.getDraft();
        if (trainer.getTrainerId() == null || trainer.getTrainerId().isBlank()
                || trainer.getTrainerId().equals("nuevo_entrenador")) {
            player.sendSystemMessage(Component.literal("§c[PokeFrontier] Cambia el ID primero: §e/bfset id <valor>"));
            return;
        }
        try {
            String path = exporter.writeTrainer(trainer);
            player.sendSystemMessage(Component.literal(
                "§a[PokeFrontier] §7Entrenador §e" + trainer.getTrainerId() + " §7exportado → §e" + path));
            player.sendSystemMessage(Component.literal(
                "§7Agrega '§e" + trainer.getTrainerId() + "§7' a la secuencia de la base en assault_config.json"));
        } catch (IOException e) {
            PokeFrontier.LOGGER.error("[PokeFrontier] Export failed", e);
            player.sendSystemMessage(Component.literal("§c[PokeFrontier] Error al exportar: " + e.getMessage()));
            return;
        }
        ExportSession.clear(player.getUUID());
        player.closeContainer();
    }

    // ── Helpers ────────────────────────────────────────────────────────────────

    private ItemStack makeItem(net.minecraft.world.item.Item item, String name, List<String> lore) {
        ItemStack stack = new ItemStack(item);
        stack.set(DataComponents.CUSTOM_NAME, Component.literal(name).withStyle(s -> s.withItalic(false)));
        if (lore != null && !lore.isEmpty()) {
            stack.set(DataComponents.LORE, new ItemLore(
                lore.stream().map(l -> (Component) Component.literal(l)
                    .withStyle(s -> s.withItalic(false))).toList()));
        }
        return stack;
    }

    private ItemStack filler() { return makeItem(Items.GRAY_STAINED_GLASS_PANE, "§0", null); }
    private String truncate(String s, int max) { if (s == null) return ""; return s.length() <= max ? s : s.substring(0, max) + "..."; }
    private String capitalize(String s) { if (s == null || s.isEmpty()) return s; return Character.toUpperCase(s.charAt(0)) + s.substring(1).toLowerCase(); }

    // ── Menu ───────────────────────────────────────────────────────────────────

    public static class ExportMenu extends ChestMenu {
        private final Map<Integer, Runnable> handlers;

        public ExportMenu(int syncId, Inventory playerInv, SimpleContainer container, Map<Integer, Runnable> handlers) {
            super(MenuType.GENERIC_9x6, syncId, playerInv, container, 6);
            this.handlers = handlers;
        }

        @Override
        public void clicked(int slotId, int button, ClickType clickType, Player player) {
            if (slotId >= 0 && slotId < 54) {
                Runnable handler = handlers.get(slotId);
                if (handler != null) handler.run();
            }
            if (player instanceof ServerPlayer sp) {
                sp.containerMenu.broadcastChanges();
                sp.inventoryMenu.broadcastChanges();
            }
        }

        @Override public boolean stillValid(Player player) { return true; }
        @Override public ItemStack quickMoveStack(Player player, int index) { return ItemStack.EMPTY; }
    }
}
