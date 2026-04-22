package com.tuservidor.pokefrontier.assault;

import com.tuservidor.pokefrontier.config.AssaultConfig;
import lombok.Data;

import java.util.Collection;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

/**
 * Estado en RAM de la sesión activa de un jugador en una base.
 * No persiste en disco; se recrea al re-entrar a la base.
 */
@Data
public class AssaultSession {

    private static final ConcurrentHashMap<UUID, AssaultSession> SESSIONS = new ConcurrentHashMap<>();

    private final UUID playerUuid;
    private String baseId;
    private int    currentStep;
    private UUID   currentNpcUuid;
    private State  state = State.IDLE;
    private AssaultConfig.AssaultBase.LocationDef returnLocation;
    private long   startTime;

    public enum State {
        IDLE,
        IN_BATTLE,
        AWAITING_NEXT
    }

    private AssaultSession(UUID playerUuid) {
        this.playerUuid = playerUuid;
        this.startTime  = System.currentTimeMillis();
    }

    // ── Factory / Registro ─────────────────────────────────────────────────────

    public static AssaultSession start(UUID uuid, String baseId, int step) {
        AssaultSession s = new AssaultSession(uuid);
        s.baseId      = baseId;
        s.currentStep = step;
        SESSIONS.put(uuid, s);
        return s;
    }

    public static AssaultSession get(UUID uuid)    { return SESSIONS.get(uuid); }
    public static boolean has(UUID uuid)           { return SESSIONS.containsKey(uuid); }
    public static void remove(UUID uuid)           { SESSIONS.remove(uuid); }
    public static Collection<AssaultSession> all() { return SESSIONS.values(); }
}
