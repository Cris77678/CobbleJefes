package com.tuservidor.cobblejefes.export;

import com.cobblemon.mod.common.pokemon.Pokemon;
import com.tuservidor.cobblejefes.model.TrainerExport;
import lombok.Data;

import java.util.List;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

/**
 * Estado en RAM del borrador de un NPC siendo exportado por un Administrador.
 */
@Data
public class ExportSession {

    private static final ConcurrentHashMap<UUID, ExportSession> SESSIONS = new ConcurrentHashMap<>();

    private final UUID playerUuid;
    private final TrainerExport draft;
    private final List<Pokemon> realPokemon;

    public static void start(UUID uuid, TrainerExport draft, List<Pokemon> realPokemon) {
        SESSIONS.put(uuid, new ExportSession(uuid, draft, realPokemon));
    }
    
    public static void start(UUID uuid, TrainerExport draft) {
        SESSIONS.put(uuid, new ExportSession(uuid, draft, List.of()));
    }

    public static ExportSession get(UUID uuid) {
        return SESSIONS.get(uuid);
    }

    public static void clear(UUID uuid) {
        SESSIONS.remove(uuid);
    }
}