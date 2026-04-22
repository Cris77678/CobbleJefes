package com.tuservidor.cobblejefes.export;

import com.cobblemon.mod.common.Cobblemon;
import com.cobblemon.mod.common.api.pokemon.stats.Stats;
import com.cobblemon.mod.common.api.storage.party.PlayerPartyStore;
import com.cobblemon.mod.common.pokemon.Pokemon;
import com.tuservidor.cobblejefes.model.TrainerExport;
import net.minecraft.server.level.ServerPlayer;
import org.yaml.snakeyaml.DumperOptions;
import org.yaml.snakeyaml.Yaml;

import java.io.FileWriter;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.*;

public class PartyExporter {

    public TrainerExport buildFromParty(ServerPlayer player) {
        PlayerPartyStore party = Cobblemon.INSTANCE.getStorage().getParty(player);
        List<TrainerExport.PokemonEntry> pokemonList = new ArrayList<>();

        for (int i = 0; i < party.size(); i++) {
            Pokemon p = party.get(i);
            if (p == null) continue;

            List<String> moves = new ArrayList<>();
            p.getMoveSet().forEach(m -> {
                if (m != null && m.getTemplate() != null) moves.add(m.getTemplate().getName());
            });

            String heldItemStr = "";
            if (!p.getHeldItem().isEmpty()) {
                heldItemStr = net.minecraft.core.registries.BuiltInRegistries.ITEM
                    .getKey(p.getHeldItem().getItem()).toString();
            }

            // FIX: Cobblemon utiliza "DEFENCE" con 'C' para Defensa y Defensa Especial
            TrainerExport.Ivs ivs = new TrainerExport.Ivs(
                p.getIvs().getOrDefault(Stats.HP), p.getIvs().getOrDefault(Stats.ATTACK),
                p.getIvs().getOrDefault(Stats.DEFENCE), p.getIvs().getOrDefault(Stats.SPECIAL_ATTACK),
                p.getIvs().getOrDefault(Stats.SPECIAL_DEFENCE), p.getIvs().getOrDefault(Stats.SPEED)
            );

            TrainerExport.Evs evs = new TrainerExport.Evs(
                p.getEvs().getOrDefault(Stats.HP), p.getEvs().getOrDefault(Stats.ATTACK),
                p.getEvs().getOrDefault(Stats.DEFENCE), p.getEvs().getOrDefault(Stats.SPECIAL_ATTACK),
                p.getEvs().getOrDefault(Stats.SPECIAL_DEFENCE), p.getEvs().getOrDefault(Stats.SPEED)
            );

            pokemonList.add(new TrainerExport.PokemonEntry(
                p.getSpecies().getName(), p.getLevel(),
                p.getNature().getName().getPath(), p.getAbility().getName(),
                heldItemStr, ivs, evs, moves
            ));
        }

        TrainerExport export = new TrainerExport();
        export.setPokemonTeam(pokemonList);
        return export;
    }

    public String writeTrainer(TrainerExport trainer) throws IOException {
        Map<String, Object> trainerMap = new LinkedHashMap<>();
        trainerMap.put("trainer_id",   trainer.getTrainerId());
        trainerMap.put("trainer_name", trainer.getTrainerName());
        if (trainer.getBattleDialogue() != null) trainerMap.put("battle_dialogue", trainer.getBattleDialogue());
        if (trainer.getDefeatDialogue() != null) trainerMap.put("defeat_dialogue", trainer.getDefeatDialogue());
        trainerMap.put("cooldown_hours", trainer.getCooldownHours());
        if (trainer.getSkin() != null) trainerMap.put("skin", trainer.getSkin());

        if (trainer.getRewards() != null && trainer.getRewards().getCommands() != null && !trainer.getRewards().getCommands().isEmpty()) {
            Map<String, Object> rewMap = new LinkedHashMap<>();
            rewMap.put("commands", trainer.getRewards().getCommands());
            trainerMap.put("rewards", rewMap);
        }

        List<Map<String, Object>> pkmnList = new ArrayList<>();
        for (TrainerExport.PokemonEntry p : trainer.getPokemonTeam()) {
            Map<String, Object> map = new LinkedHashMap<>();
            map.put("species", p.getSpecies());
            map.put("level",   p.getLevel());
            map.put("nature",  p.getNature());
            map.put("ability", p.getAbility());
            if (p.getHeldItem() != null && !p.getHeldItem().isEmpty()) map.put("held_item", p.getHeldItem());
            map.put("moves", p.getMoves());

            Map<String, Integer> ivs = new LinkedHashMap<>();
            ivs.put("hp", p.getIvs().getHp()); ivs.put("attack", p.getIvs().getAtk());
            ivs.put("defence", p.getIvs().getDef()); ivs.put("special_attack", p.getIvs().getSpa());
            ivs.put("special_defence", p.getIvs().getSpd()); ivs.put("speed", p.getIvs().getSpe());
            map.put("ivs", ivs);

            Map<String, Integer> evs = new LinkedHashMap<>();
            evs.put("hp", p.getEvs().getHp()); evs.put("attack", p.getEvs().getAtk());
            evs.put("defence", p.getEvs().getDef()); evs.put("special_attack", p.getEvs().getSpa());
            evs.put("special_defence", p.getEvs().getSpd()); evs.put("speed", p.getEvs().getSpe());
            map.put("evs", evs);

            pkmnList.add(map);
        }
        trainerMap.put("pokemon_team", pkmnList);

        Map<String, Object> root = new HashMap<>();
        root.put("trainers", List.of(trainerMap));

        Path dir = Path.of("config/cobblejefes/trainers");
        Files.createDirectories(dir);
        Path file = dir.resolve(trainer.getTrainerId() + ".yml");

        DumperOptions options = new DumperOptions();
        options.setDefaultFlowStyle(DumperOptions.FlowStyle.BLOCK);
        Yaml yaml = new Yaml(options);

        try (FileWriter writer = new FileWriter(file.toFile())) {
            yaml.dump(root, writer);
        }
        return file.toString();
    }
}