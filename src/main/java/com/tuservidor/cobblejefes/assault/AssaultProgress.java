package com.tuservidor.cobblejefes.assault;

import com.google.gson.Gson;
import com.google.gson.GsonBuilder;
import com.google.gson.reflect.TypeToken;
import com.tuservidor.cobblejefes.CobbleJefes;

import java.io.FileReader;
import java.io.FileWriter;
import java.io.File;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.StandardCopyOption;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

public class AssaultProgress {

    private static final String PATH     = "config/pokefrontier/data/assault_progress.json";
    private static final String PATH_TMP = PATH + ".tmp";

    private static Map<UUID, Map<String, Integer>> DATA = new ConcurrentHashMap<>();
    private static final Gson GSON = new GsonBuilder().setPrettyPrinting().create();

    public static int getStep(UUID player, String baseId) {
        Map<String, Integer> playerMap = DATA.get(player);
        if (playerMap == null) return 0;
        return playerMap.getOrDefault(baseId.toLowerCase(), 0);
    }

    public static synchronized void advanceStep(UUID player, String baseId) {
        DATA.computeIfAbsent(player, k -> new ConcurrentHashMap<>())
            .merge(baseId.toLowerCase(), 1, Integer::sum);
        save();
    }

    public static synchronized void markComplete(UUID player, String baseId, int totalSteps) {
        DATA.computeIfAbsent(player, k -> new ConcurrentHashMap<>())
            .put(baseId.toLowerCase(), totalSteps);
        save();
    }

    public static synchronized void resetBase(UUID player, String baseId) {
        Map<String, Integer> playerMap = DATA.get(player);
        if (playerMap != null) playerMap.remove(baseId.toLowerCase());
        save();
    }

    public static Map<String, Integer> getAllProgress(UUID player) {
        Map<String, Integer> playerMap = DATA.get(player);
        if (playerMap == null) return Map.of();
        return Map.copyOf(playerMap);
    }

    public static void load() {
        File file = new File(PATH);
        if (!file.exists()) return;
        try (FileReader reader = new FileReader(file)) {
            Map<UUID, Map<String, Integer>> loaded = GSON.fromJson(
                reader,
                new TypeToken<Map<UUID, Map<String, Integer>>>(){}.getType()
            );
            if (loaded != null) {
                DATA = new ConcurrentHashMap<>();
                // FIX: Copia profunda para asegurar Thread-Safety
                for (Map.Entry<UUID, Map<String, Integer>> entry : loaded.entrySet()) {
                    DATA.put(entry.getKey(), new ConcurrentHashMap<>(entry.getValue()));
                }
            }
            CobbleJefes.LOGGER.info("[CobbleJefes] Progreso de asaltos cargado ({} jugadores).", DATA.size());
        } catch (IOException e) {
            CobbleJefes.LOGGER.error("[CobbleJefes] Error cargando assault_progress.json", e);
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
            CobbleJefes.LOGGER.error("[CobbleJefes] Error en guardado atómico de assault_progress.json", e);
        }
    }
}