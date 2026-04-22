package com.tuservidor.cobblejefes.util;

import com.cobblemon.mod.common.pokemon.Pokemon;
import java.util.List;
import java.util.Set;

public class LegendaryChecker {

    private static final Set<String> LEGENDARY_LABELS = Set.of(
        "legendary", "mythical", "ultra_beast", "sub_legendary", "paradox"
    );

    public static boolean isLegendary(Pokemon pokemon) {
        try {
            var labels = pokemon.getSpecies().getLabels();
            for (String label : LEGENDARY_LABELS) {
                if (labels.contains(label)) return true;
            }
            return false;
        } catch (Exception e) {
            return false;
        }
    }

    public static int countLegendaries(List<Pokemon> team) {
        int count = 0;
        for (Pokemon p : team) {
            if (p != null && isLegendary(p)) count++;
        }
        return count;
    }
}
