package com.tuservidor.cobblejefes.model;

import lombok.Data;
import lombok.NoArgsConstructor;
import lombok.AllArgsConstructor;
import java.util.List;
import java.util.ArrayList;

@Data
@NoArgsConstructor
@AllArgsConstructor
public class TrainerExport {
    private String trainerId;
    private String trainerName;
    private String battleDialogue;
    private String defeatDialogue;
    private double cooldownHours;
    private String skin;
    private List<PokemonEntry> pokemonTeam = new ArrayList<>();
    private RewardEntry rewards = new RewardEntry();

    @Data
    @NoArgsConstructor
    @AllArgsConstructor
    public static class PokemonEntry {
        private String species;
        private int    level;
        private String nature;
        private String ability;
        private String heldItem;
        private Ivs    ivs;
        private Evs    evs;
        private List<String> moves = new ArrayList<>();
    }

    @Data @NoArgsConstructor @AllArgsConstructor 
    public static class Ivs { private int hp, atk, def, spa, spd, spe; }
    
    @Data @NoArgsConstructor @AllArgsConstructor 
    public static class Evs { private int hp, atk, def, spa, spd, spe; }

    @Data
    @NoArgsConstructor
    @AllArgsConstructor
    public static class RewardEntry {
        // FIX: Evitamos variables final en los modelos anidados para asegurar la 
        // correcta serialización/deserialización de bibliotecas externas.
        private List<ItemReward> items = new ArrayList<>();
        private List<String>     commands = new ArrayList<>();

        @Data
        @NoArgsConstructor
        @AllArgsConstructor
        public static class ItemReward {
            private String item;
            private int    count;
        }
    }
}