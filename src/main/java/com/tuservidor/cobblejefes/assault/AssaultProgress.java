package com.tuservidor.cobblejefes.assault;

import com.google.gson.Gson;
import com.google.gson.GsonBuilder;
import com.google.gson.reflect.TypeToken;
import com.tuservidor.cobblejefes.PokeFrontier;

import java.io.FileReader;
import java.io.FileWriter;
import java.io.File;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.StandardCopyOption;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

/**
 * Persistencia del progreso por jugador × base.
 * Estructura en disco: { "uuid": { "base_rocket": 2, "base_flare": 0 } }
 *
 * El paso (step) representa el índice del siguiente NPC a enfrentar.
 * step=0 → no iniciado / reiniciado.
 * step=N → ya se derrotaron los NPCs 0..N-1, el siguiente es el N.
 */
public class AssaultProgress {

    private static final String PATH     = "config/pokefrontier/data/assault_progress.json";
    private static final String PATH_TMP = PATH + ".tmp";

    private static Map<UUID, Map<String, Integer>> DATA = new ConcurrentHashMap<>();
    private static final Gson GSON = new GsonBuilder().setPrettyPrinting().create();

    // ── API pública ────────────────────────────────────────────────────────────

    /** Paso actual del jugador en la base (0 = no iniciado). */
    public static int getStep(UUID player, String baseId) {
        Map<String, Integer> playerMap = DATA.get(player);
        if (playerMap == null) return 0;
        return playerMap.getOrDefault(baseId.toLowerCase(), 0);
    }

    /**
     * Avanza el paso del jugador en la base y guarda en disco inmediatamente.
     * Llamar justo después de que el NPC del paso actual sea derrotado.
     */
    public static synchronized void advanceStep(UUID player, String baseId) {
        DATA.computeIfAbsent(player, k -> new ConcurrentHashMap<>())
            .merge(baseId.toLowerCase(), 1, Integer::sum);
        save();
    }

    /**
     * Marca la base como completada (paso = sequence.size()) y guarda.
     */
    public static synchronized void markComplete(UUID player, String baseId, int totalSteps) {
        DATA.computeIfAbsent(player, k -> new ConcurrentHashMap<>())
            .put(baseId.toLowerCase(), totalSteps);
        save();
    }

    /**
     * Reinicia el progreso de un jugador en una base específica.
     */
    public static synchronized void resetBase(UUID player, String baseId) {
        Map<String, Integer> playerMap = DATA.get(player);
        if (playerMap != null) playerMap.remove(baseId.toLowerCase());
        save();
    }

    /**
     * Devuelve todas las bases con progreso > 0 para un jugador (para mensajes de bienvenida).
     */
    public static Map<String, Integer> getAllProgress(UUID player) {
        Map<String, Integer> playerMap = DATA.get(player);
        if (playerMap == null) return Map.of();
        return Map.copyOf(playerMap);
    }

    // ── Carga y guardado atómico ───────────────────────────────────────────────

    public static void load() {
        File file = new File(PATH);
        if (!file.exists()) return;
        try (FileReader reader = new FileReader(file)) {
            Map<UUID, Map<String, Integer>> loaded = GSON.fromJson(
                reader,
                new TypeToken<Map<UUID, Map<String, Integer>>>(){}.getType()
            );
            if (loaded != null) {
                DATA = new ConcurrentHashMap<>(loaded);
            }
            PokeFrontier.LOGGER.info("[PokeFrontier] Progreso de asaltos cargado ({} jugadores).", DATA.size());
        } catch (IOException e) {
            PokeFrontier.LOGGER.error("[PokeFrontier] Error cargando assault_progress.json", e);
        }
    }

    public static synchronized void save() {
        File file    = new File(PATH);
        File tmpFile = new File(PATH_TMP);
        if (!file.getParentFile().exists()) file.getParentFile().mkdirs();

        try (FileWriter writer = new FileWriter(tmpFile)) {
            GSON.toJson(DATA, writer);
            writer.flush();
            Files.move(
                tmpFile.toPath(), file.toPath(),
                StandardCopyOption.REPLACE_EXISTING,
                StandardCopyOption.ATOMIC_MOVE
            );
        } catch (IOException e) {
            PokeFrontier.LOGGER.error("[PokeFrontier] Error en guardado atómico de assault_progress.json", e);
        }
    }
}
