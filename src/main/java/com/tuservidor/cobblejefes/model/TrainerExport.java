package com.tuservidor.cobblejefes.model;

import lombok.Data;
import java.util.List;

@Data
public class TrainerExport {
    private String trainerId;
    private String trainerName;
    private String battleDialogue;
    private String defeatDialogue;
    private double cooldownHours;
    private String skin;
    private List<PokemonEntry> pokemonTeam;
    private RewardEntry rewards;

    @Data
    public static class PokemonEntry {
        private final String species;
        private final int    level;
        private final String nature;
        private final String ability;
        private final String heldItem;
        private final Ivs    ivs;
        private final Evs    evs;
        private final List<String> moves;
    }

    @Data public static class Ivs { private final int hp, atk, def, spa, spd, spe; }
    @Data public static class Evs { private final int hp, atk, def, spa, spd, spe; }

    @Data
    public static class RewardEntry {
        private final List<ItemReward> items;
        private final List<String>     commands;

        @Data
        public static class ItemReward {
            private final String item;
            private final int    count;
        }
    }
}
