package com.tuservidor.cobblejefes.config;

import com.google.gson.Gson;
import com.google.gson.GsonBuilder;
import com.tuservidor.cobblejefes.CobbleJefes;
import lombok.Data;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.*;

@Data
public class AssaultConfig {

    // FIX: Ruta unificada al nuevo nombre del mod
    private static final String PATH = "config/cobblejefes/assault_config.json";
    private static AssaultConfig INSTANCE;

    // ── Configuración global ──────────────────────────────────────────────────

    private int maxPlayerLevel = 100;
    private double arenaProtectionRadius = 15.0;

    private List<String> bannedBattleItems = List.of(
        "cobblemon:x_attack", "cobblemon:x_defense", "cobblemon:x_speed",
        "cobblemon:x_sp_atk", "cobblemon:x_sp_def", "cobblemon:x_accuracy",
        "cobblemon:full_restore", "cobblemon:max_potion", "cobblemon:revive",
        "cobblemon:max_revive", "cobblemon:elixir", "cobblemon:max_elixir",
        "cobblemon:full_heal"
    );

    private String prefix = "&7[&cCobble&4Jefes&7] ";
    private String msgAlreadyInSession    = "%prefix% &cYa tienes una sesión activa. Usa /assault leave para salir.";
    private String msgBaseNotFound        = "%prefix% &cBase no encontrada: &e%base%";
    private String msgBaseComplete        = "%prefix% &aYa completaste esta base. Usa /assault reset %base% para reiniciarla.";
    private String msgEnterBase           = "%prefix% &eEntrando a la base: &6%org%&e. Paso &6%step%&e/&6%total%&e.";
    private String msgBannedPokemon       = "%prefix% &cTu equipo contiene un Pokémon prohibido en esta base: &e%pokemon%";
    private String msgBannedLegendary     = "%prefix% &cLos Pokémon legendarios no están permitidos en esta base.";
    private String msgNpcSpawnError       = "%prefix% &c[!] Error al generar el oponente. Sesión cancelada.";
    private String msgStepWon             = "%prefix% &a¡%trainer% derrotado! &7(%step%/%total%)";
    private String msgMinibossDefeated    = "%prefix% &6&l¡Mini-jefe derrotado! &6Recompensas entregadas.";
    private String msgBossDefeated        = "%prefix% &4&l¡&c&l%org% &4&lha sido derrotado! &6¡Recompensas legendarias!";
    private String msgBaseCompleted       = "%prefix% &a&lBase completada. ¡Felicidades, &e%player%&a&l!";
    private String msgStepLost            = "%prefix% &cHas perdido. Tu progreso fue guardado. Vuelve para reintentar.";
    private String msgLeaveBase           = "%prefix% &7Saliste de la base. Tu progreso fue conservado.";
    private String msgSessionAborted      = "%prefix% &7Sesión cancelada.";
    private String msgBannedCommand       = "%prefix% &cComando bloqueado durante el asalto. Usa /assault leave para salir.";
    private String msgBannedItem          = "%prefix% &c[!] Los objetos de batalla no están permitidos en esta base.";
    private String msgMachineBlocked      = "%prefix% &c[!] No puedes usar máquinas durante el asalto.";
    private String msgProgressReset       = "%prefix% &aProgreso reiniciado para la base &e%base%&a.";
    private String msgNoSession           = "%prefix% &cNo estás participando en ningún asalto.";
    private String msgReconnectProgress   = "%prefix% &e¡Bienvenido de vuelta! Tienes progreso guardado en: &6%bases%";

    private Map<String, String> npcMegaStones = new LinkedHashMap<>();

    private double npcYOffset = 0.1;
    private float  defaultNpcYaw = 180f;
    private double npcSpawnDistance = 2.5;

    // ── Bases de organizaciones ────────────────────────────────────────────────

    private List<AssaultBase> bases = new ArrayList<>();

    // ── Getters útiles ────────────────────────────────────────────────────────

    public AssaultBase getBase(String id) {
        return bases.stream()
            .filter(b -> b.getId().equalsIgnoreCase(id))
            .findFirst().orElse(null);
    }

    // ── Formato de mensajes ────────────────────────────────────────────────────

    public String format(String msg, Object... kv) {
        msg = msg.replace("%prefix%", prefix.replace("&", "§"));
        for (int i = 0; i + 1 < kv.length; i += 2)
            msg = msg.replace(String.valueOf(kv[i]), String.valueOf(kv[i + 1]));
        return msg.replace("&", "§");
    }

    // ── Carga y guardado ───────────────────────────────────────────────────────

    public static AssaultConfig get() {
        if (INSTANCE == null) reload();
        return INSTANCE;
    }

    public static void reload() {
        Gson gson = new GsonBuilder().setPrettyPrinting().create();
        Path p = Path.of(PATH);
        try {
            Files.createDirectories(p.getParent());
            if (Files.exists(p)) {
                INSTANCE = gson.fromJson(Files.readString(p), AssaultConfig.class);
                if (INSTANCE == null) INSTANCE = defaultConfig();
            } else {
                INSTANCE = defaultConfig();
            }
            Files.writeString(p, gson.toJson(INSTANCE));
        } catch (Exception e) {
            CobbleJefes.LOGGER.error("[CobbleJefes] Error cargando assault_config.json. Usando defaults.", e);
            INSTANCE = defaultConfig();
        }
    }

    public synchronized void save() {
        try {
            Gson gson = new GsonBuilder().setPrettyPrinting().create();
            Files.writeString(Path.of(PATH), gson.toJson(this));
        } catch (IOException e) {
            CobbleJefes.LOGGER.error("[CobbleJefes] Error guardando assault_config.json", e);
        }
    }

    // ── Configuración por defecto con ejemplos ────────────────────────────────

    private static AssaultConfig defaultConfig() {
        AssaultConfig cfg = new AssaultConfig();
        AssaultBase rocket = new AssaultBase();
        rocket.setId("base_rocket");
        rocket.setOrganization("Team Rocket");
        rocket.setDisplayColor("§c");
        rocket.setSequence(Arrays.asList("rocket_grunt1", "rocket_admin_archer", "giovanni"));
        rocket.setMinibossIndex(1);
        rocket.setBossIndex(2);
        cfg.getBases().add(rocket);
        return cfg;
    }

    // ── Clases internas ────────────────────────────────────────────────────────

    @Data
    public static class AssaultBase {
        private String id;
        private String organization = "Unknown Org";
        private String displayColor = "§f";
        private List<String> sequence = new ArrayList<>();
        private int minibossIndex = -1;
        private int bossIndex     = -1;
        private List<String> minibossRewards = new ArrayList<>();
        private List<String> bossRewards = new ArrayList<>();
        private List<String> bannedPokemon = new ArrayList<>();
        private boolean banLegendaries = false;
        private Arena arena;

        public boolean isMiniboss(int step) { return step == minibossIndex; }
        public boolean isBoss(int step)     { return step == bossIndex; }
        public boolean isComplete(int step) { return step >= sequence.size(); }

        public String getTrainerId(int step) {
            if (step < 0 || step >= sequence.size()) return null;
            return sequence.get(step);
        }

        public String buildStepLabel(int step) {
            if (isBoss(step))     return "JEFE FINAL";
            if (isMiniboss(step)) return "Mini-jefe";
            return "Paso " + (step + 1) + "/" + sequence.size();
        }

        @Data
        public static class Arena {
            private String id;
            private LocationDef playerSpawn;
            private LocationDef npcSpawn;
            private transient boolean occupied = false;
        }

        @Data
        public static class LocationDef {
            private String world = "minecraft:overworld";
            private double x, y, z;
            private float  yaw, pitch;

            public LocationDef() {}

            public LocationDef(ServerPlayer p) {
                this.world = p.level().dimension().location().toString();
                this.x = p.getX(); this.y = p.getY(); this.z = p.getZ();
                this.yaw = p.getYRot(); this.pitch = p.getXRot();
            }

            public void teleport(ServerPlayer p) {
                for (ServerLevel lvl : p.server.getAllLevels()) {
                    if (lvl.dimension().location().toString().equals(this.world)) {
                        p.teleportTo(lvl, x, y, z, yaw, pitch);
                        return;
                    }
                }
            }
        }
    }
}