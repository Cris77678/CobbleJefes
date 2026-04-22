package com.tuservidor.cobblejefes.npc;

import com.cobblemon.mod.common.api.npc.NPCClass;
import com.cobblemon.mod.common.api.npc.NPCClasses;
import com.cobblemon.mod.common.api.pokemon.PokemonProperties;
import com.cobblemon.mod.common.api.storage.party.NPCPartyStore;
import com.cobblemon.mod.common.entity.npc.NPCEntity;
import com.cobblemon.mod.common.pokemon.Pokemon;
import com.tuservidor.cobblejefes.CobbleJefes;
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

    public static UUID spawnTrainer(ServerPlayer player, String trainerId,
                                     AssaultConfig.AssaultBase base) {
        try {
            ServerLevel level = player.serverLevel();
            AssaultConfig cfg = AssaultConfig.get();

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

            NPCEntity npc = new NPCEntity(level);
            NPCClass npcClass = Optional.ofNullable(NPCClasses.INSTANCE.getByName("standard"))
                .orElse(NPCClasses.INSTANCE.random());
            npc.setNpc(npcClass);

            npc.setInvulnerable(true);
            npc.setNoAi(true);
            npc.setPersistenceRequired();
            npc.getPersistentData().putBoolean("NoLoot", true);
            npc.getPersistentData().putBoolean("prevent_capture", true);

            String orgColor = (base != null) ? base.getDisplayColor() : "§e";
            String orgName  = (base != null) ? base.getOrganization()  : "Base";
            npc.setCustomName(Component.literal(orgColor + "[" + orgName + "] §f" + trainerId));
            npc.setCustomNameVisible(true);

            npc.addTag("pf_assault");
            npc.addTag("pf_trainer=" + trainerId);
            if (base != null) npc.addTag("pf_base=" + base.getId());

            npc.moveTo(pos.x, pos.y + cfg.getNpcYOffset(), pos.z, yaw, 0);
            npc.setYHeadRot(yaw);

            NPCPartyStore party = new NPCPartyStore(npc);
            loadTeamFromYaml(party, trainerId);
            party.initialize();
            npc.setParty(party);

            if (level.addFreshEntity(npc)) {
                FrontierGlow.applyNpcGlow(npc, level);
                return npc.getUUID();
            }
            return null;

        } catch (Exception e) {
            CobbleJefes.LOGGER.error("[CobbleJefes] Error al generar NPC '{}': {}", trainerId, e.getMessage(), e);
            return null;
        }
    }

    public static void despawnNpc(ServerPlayer player, UUID uuid) {
        if (uuid == null) return;
        Entity entity = player.serverLevel().getEntity(uuid);
        if (entity != null) {
            FrontierGlow.removeGlow(entity, player.serverLevel());
            entity.discard();
        }
    }

    public static boolean isAssaultNpc(Entity entity) {
        return entity.getTags().contains("pf_assault");
    }

    private static void loadTeamFromYaml(NPCPartyStore party, String trainerId) {
        // FIX: Buscar en cobblejefes primero, pero permitir retrocompatibilidad
        Path cjPath = Path.of("config/cobblejefes/trainers/" + trainerId + ".yml");
        Path pfPath = Path.of("config/pokefrontier/trainers/" + trainerId + ".yml");
        Path bfPath = Path.of("config/battlefrontier/trainers/" + trainerId + ".yml");

        Path path = Files.exists(cjPath) ? cjPath : (Files.exists(pfPath) ? pfPath : (Files.exists(bfPath) ? bfPath : null));
        
        if (path == null) {
            CobbleJefes.LOGGER.warn("[CobbleJefes] Archivo de entrenador no encontrado: {}", trainerId);
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

                    if (pkMap.get("held_item") instanceof String heldItem && !heldItem.isEmpty()) {
                        props.append(" helditem=").append(heldItem);
                        Map<String, String> megaStones = AssaultConfig.get().getNpcMegaStones();
                        if (megaStones != null && megaStones.containsKey(heldItem)) {
                            props.append(" ").append(megaStones.get(heldItem));
                        }
                    }

                    if (pkMap.get("nature") instanceof String nature && !nature.isEmpty()) {
                        props.append(" nature=").append(nature);
                    }

                    if (pkMap.get("ability") instanceof String ability && !ability.isEmpty()) {
                        props.append(" ability=").append(ability);
                    }

                    if (pkMap.get("moves") instanceof List<?> movesList) {
                        List<String> moves = new ArrayList<>();
                        for (Object move : movesList) moves.add(move.toString());
                        if (!moves.isEmpty()) props.append(" moves=").append(String.join(",", moves));
                    }

                    if (pkMap.get("ivs") instanceof Map<?, ?> ivMap) {
                        appendStats(props, "ivs", ivMap);
                    }

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
                break;
            }
        } catch (Exception e) {
            CobbleJefes.LOGGER.error("[CobbleJefes] Error leyendo YAML del entrenador '{}': {}", trainerId, e.getMessage());
        }
    }

    private static void appendStats(StringBuilder props, String type, Map<?, ?> statMap) {
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