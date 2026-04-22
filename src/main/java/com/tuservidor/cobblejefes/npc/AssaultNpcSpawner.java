package com.tuservidor.cobblejefes.npc;

import com.cobblemon.mod.common.api.npc.NPCClass;
import com.cobblemon.mod.common.api.npc.NPCClasses;
import com.cobblemon.mod.common.api.pokemon.PokemonProperties;
import com.cobblemon.mod.common.api.storage.party.NPCPartyStore;
import com.cobblemon.mod.common.entity.npc.NPCEntity;
import com.cobblemon.mod.common.pokemon.Pokemon;
import com.tuservidor.cobblejefes.PokeFrontier;
import com.tuservidor.cobblejefes.config.AssaultConfig;
import com.tuservidor.cobblejefes.glow.FrontierGlow;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.phys.Vec3;
import org.yaml.snakeyaml.LoaderOptions;
import org.yaml.snakeyaml.Yaml;
import org.yaml.snakeyaml.constructor.SafeConstructor;

import java.io.Reader;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.*;

public class AssaultNpcSpawner {

    /**
     * Genera el NPC entrenador en el mundo, usando la configuración de arena de la base.
     *
     * @param player   Jugador contra quien se genera el NPC
     * @param trainerId ID del entrenador (nombre del archivo YAML sin extensión)
     * @param base     Base a la que pertenece el combate (para posicionamiento)
     * @return UUID de la entidad generada, o null si falló
     */
    public static UUID spawnTrainer(ServerPlayer player, String trainerId,
                                     AssaultConfig.AssaultBase base) {
        try {
            ServerLevel level = player.serverLevel();
            AssaultConfig cfg = AssaultConfig.get();

            // Determinar posición del NPC
            Vec3 pos;
            float yaw;

            if (base != null && base.getArena() != null && base.getArena().getNpcSpawn() != null) {
                AssaultConfig.AssaultBase.LocationDef npcSpawn = base.getArena().getNpcSpawn();
                pos = new Vec3(npcSpawn.getX(), npcSpawn.getY(), npcSpawn.getZ());
                yaw = npcSpawn.getYaw();
            } else {
                pos = getDynamicFrontPosition(player, cfg.getNpcSpawnDistance());
                yaw = cfg.getDefaultNpcYaw();
            }

            // Crear entidad NPC
            NPCEntity npc = new NPCEntity(level);
            NPCClass npcClass = Optional.ofNullable(NPCClasses.INSTANCE.getByName("standard"))
                .orElse(NPCClasses.INSTANCE.random());
            npc.setNpc(npcClass);

            // Propiedades de seguridad
            npc.setInvulnerable(true);
            npc.setNoAi(true);
            npc.setPersistenceRequired();
            npc.getPersistentData().putBoolean("NoLoot", true);
            npc.getPersistentData().putBoolean("prevent_capture", true);

            // Nombre visible con color de la organización
            String orgColor = (base != null) ? base.getDisplayColor() : "§e";
            String orgName  = (base != null) ? base.getOrganization()  : "Base";
            npc.setCustomName(Component.literal(orgColor + "[" + orgName + "] §f" + trainerId));
            npc.setCustomNameVisible(true);

            // Tags para identificación
            npc.addTag("pf_assault");
            npc.addTag("pf_trainer=" + trainerId);
            if (base != null) npc.addTag("pf_base=" + base.getId());

            // Posicionar
            npc.moveTo(pos.x, pos.y + cfg.getNpcYOffset(), pos.z, yaw, 0);
            npc.setYHeadRot(yaw);

            // Cargar equipo desde YAML
            NPCPartyStore party = new NPCPartyStore(npc);
            loadTeamFromYaml(party, trainerId);
            party.initialize();
            npc.setParty(party);

            // Agregar al mundo
            if (level.addFreshEntity(npc)) {
                FrontierGlow.applyNpcGlow(npc, level);
                return npc.getUUID();
            }
            return null;

        } catch (Exception e) {
            PokeFrontier.LOGGER.error("[PokeFrontier] Error al generar NPC '{}': {}", trainerId, e.getMessage(), e);
            return null;
        }
    }

    /**
     * Elimina el NPC del mundo.
     */
    public static void despawnNpc(ServerPlayer player, UUID uuid) {
        if (uuid == null) return;
        Entity entity = player.serverLevel().getEntity(uuid);
        if (entity != null) entity.discard();
    }

    /**
     * Comprueba si una entidad es un NPC de asalto.
     */
    public static boolean isAssaultNpc(Entity entity) {
        return entity.getTags().contains("pf_assault");
    }

    // ── Carga de equipo desde YAML ─────────────────────────────────────────────

    private static void loadTeamFromYaml(NPCPartyStore party, String trainerId) {
        // Buscar en la carpeta de trainers de PokeFrontier primero, luego en BattleFrontier
        Path pfPath = Path.of("config/pokefrontier/trainers/" + trainerId + ".yml");
        Path bfPath = Path.of("config/battlefrontier/trainers/" + trainerId + ".yml");

        Path path = Files.exists(pfPath) ? pfPath : (Files.exists(bfPath) ? bfPath : null);
        if (path == null) {
            PokeFrontier.LOGGER.warn("[PokeFrontier] Archivo de entrenador no encontrado: {}", trainerId);
            return;
        }

        try (Reader r = Files.newBufferedReader(path)) {
            Yaml yaml = new Yaml(new SafeConstructor(new LoaderOptions()));
            Map<String, Object> root = yaml.load(r);
            if (!(root.get("trainers") instanceof List<?> list)) return;

            for (Object entry : list) {
                if (!(entry instanceof Map<?, ?> m)) continue;
                if (!trainerId.equals(m.get("trainer_id"))) continue;

                if (!(m.get("pokemon_team") instanceof List<?> pkmnList)) break;

                int slot = 0;
                for (Object pkmnObj : pkmnList) {
                    if (slot >= 6) break;
                    if (!(pkmnObj instanceof Map<?, ?> pkMap)) continue;

                    String species = (String) pkMap.get("species");
                    if (species == null) continue;

                    int level = pkMap.get("level") instanceof Integer l ? l : 100;

                    StringBuilder props = new StringBuilder("species=").append(species).append(" level=").append(level);

                    // Objeto equipado y mega-stones
                    if (pkMap.get("held_item") instanceof String heldItem && !heldItem.isEmpty()) {
                        props.append(" helditem=").append(heldItem);
                        Map<String, String> megaStones = AssaultConfig.get().getNpcMegaStones();
                        if (megaStones != null && megaStones.containsKey(heldItem)) {
                            props.append(" ").append(megaStones.get(heldItem));
                        }
                    }

                    // Naturaleza
                    if (pkMap.get("nature") instanceof String nature && !nature.isEmpty()) {
                        props.append(" nature=").append(nature);
                    }

                    // Habilidad
                    if (pkMap.get("ability") instanceof String ability && !ability.isEmpty()) {
                        props.append(" ability=").append(ability);
                    }

                    // Movimientos
                    if (pkMap.get("moves") instanceof List<?> movesList) {
                        List<String> moves = new ArrayList<>();
                        for (Object move : movesList) moves.add(move.toString());
                        if (!moves.isEmpty()) props.append(" moves=").append(String.join(",", moves));
                    }

                    // IVs
                    if (pkMap.get("ivs") instanceof Map<?, ?> ivMap) {
                        appendStats(props, "ivs", ivMap);
                    }

                    // EVs
                    if (pkMap.get("evs") instanceof Map<?, ?> evMap) {
                        appendStats(props, "evs", evMap);
                    }

                    Pokemon p = PokemonProperties.Companion.parse(props.toString()).create();
                    if (p != null) {
                        p.recalculateStats();
                        p.heal();
                        party.set(slot++, p);
                    }
                }
                break; // trainer_id encontrado, salir del loop
            }
        } catch (Exception e) {
            PokeFrontier.LOGGER.error("[PokeFrontier] Error leyendo YAML del entrenador '{}': {}", trainerId, e.getMessage());
        }
    }

    private static void appendStats(StringBuilder props, String type, Map<?, ?> statMap) {
        // Cobblemon acepta: ivs=hp:31,attack:31,...
        List<String> parts = new ArrayList<>();
        String[] keys = {"hp", "attack", "defence", "special_attack", "special_defence", "speed"};
        for (String key : keys) {
            Object val = statMap.get(key);
            if (val instanceof Integer i) parts.add(key + ":" + i);
        }
        if (!parts.isEmpty()) props.append(" ").append(type).append("=").append(String.join(",", parts));
    }

    private static Vec3 getDynamicFrontPosition(ServerPlayer player, double distance) {
        double yawRad = Math.toRadians(player.getYRot());
        return new Vec3(
            player.getX() - Math.sin(yawRad) * distance,
            player.getY(),
            player.getZ() + Math.cos(yawRad) * distance
        );
    }
}
