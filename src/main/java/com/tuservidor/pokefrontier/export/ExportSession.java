package com.tuservidor.pokefrontier.export;

import com.cobblemon.mod.common.pokemon.Pokemon;
import com.tuservidor.pokefrontier.model.TrainerExport;

import java.util.List;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

public class ExportSession {

    private static final ConcurrentHashMap<UUID, ExportSession> SESSIONS = new ConcurrentHashMap<>();

    private final UUID playerUuid;
    private TrainerExport draft;
    private List<Pokemon> realPokemon;

    public ExportSession(UUID playerUuid, TrainerExport draft, List<Pokemon> realPokemon) {
        this.playerUuid  = playerUuid;
        this.draft       = draft;
        this.realPokemon = realPokemon != null ? realPokemon : List.of();
    }

    public UUID          getPlayerUuid()  { return playerUuid; }
    public TrainerExport getDraft()       { return draft; }
    public void          setDraft(TrainerExport d) { this.draft = d; }
    public List<Pokemon> getRealPokemon() { return realPokemon; }

    public static ExportSession start(UUID uuid, TrainerExport draft, List<Pokemon> realPokemon) {
        ExportSession s = new ExportSession(uuid, draft, realPokemon);
        SESSIONS.put(uuid, s);
        return s;
    }

    public static ExportSession start(UUID uuid, TrainerExport draft) {
        return start(uuid, draft, List.of());
    }

    public static ExportSession get(UUID uuid)   { return SESSIONS.get(uuid); }
    public static void          clear(UUID uuid) { SESSIONS.remove(uuid); }
}
