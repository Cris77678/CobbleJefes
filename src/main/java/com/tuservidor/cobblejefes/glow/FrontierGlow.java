package com.tuservidor.cobblejefes.glow;

import com.cobblemon.mod.common.api.Priority;
import com.cobblemon.mod.common.api.events.CobblemonEvents;
import com.cobblemon.mod.common.entity.pokemon.PokemonEntity;
import com.tuservidor.cobblejefes.CobbleJefes;
import kotlin.Unit;
import net.minecraft.ChatFormatting;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.effect.MobEffectInstance;
import net.minecraft.world.effect.MobEffects;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.scores.PlayerTeam;
import net.minecraft.world.scores.Scoreboard;
import net.minecraft.world.scores.Team;

public class FrontierGlow {

    public static final String NPC_TEAM = "pf_npc_glow";
    private static final int INFINITE = Integer.MAX_VALUE;

    public static void ensureTeams(MinecraftServer server) {
        CobbleJefes.LOGGER.info("[CobbleJefes] Sistema de auras listo.");
    }

    public static void register() {
        CobblemonEvents.POKEMON_ENTITY_SPAWN.subscribe(Priority.NORMAL, event -> {
            PokemonEntity entity = event.getEntity();
            if (entity.getTags().contains("pf_assault")) {
                applyNpcGlow(entity, (ServerLevel) entity.level());
            }
            return Unit.INSTANCE;
        });
    }

    public static void applyNpcGlow(Entity entity, ServerLevel level) {
        if (!(entity instanceof LivingEntity)) return;
        applyGlow((LivingEntity) entity, level, NPC_TEAM, ChatFormatting.RED);
    }

    public static void removeGlow(Entity entity, ServerLevel level) {
        if (!(entity instanceof LivingEntity living)) return;
        try {
            Scoreboard scoreboard = level.getScoreboard();
            String entry = entity.getScoreboardName();
            PlayerTeam team = scoreboard.getPlayerTeam(NPC_TEAM);
            if (team != null && team.getPlayers().contains(entry)) {
                scoreboard.removePlayerFromTeam(entry, team);
            }
            living.removeEffect(MobEffects.GLOWING);
            entity.setGlowingTag(false);
        } catch (Exception e) {
            CobbleJefes.LOGGER.error("[CobbleJefes] removeGlow error: {}", e.getMessage());
        }
    }

    private static void applyGlow(LivingEntity entity, ServerLevel level,
                                    String teamName, ChatFormatting color) {
        try {
            Scoreboard scoreboard = level.getScoreboard();
            PlayerTeam team = scoreboard.getPlayerTeam(teamName);
            if (team == null) {
                team = scoreboard.addPlayerTeam(teamName);
                team.setNameTagVisibility(Team.Visibility.ALWAYS);
                team.setCollisionRule(Team.CollisionRule.NEVER);
            }
            team.setColor(color);

            String entry = entity.getScoreboardName();
            PlayerTeam current = scoreboard.getPlayersTeam(entry);
            if (current != null && !current.getName().equals(teamName)) {
                scoreboard.removePlayerFromTeam(entry, current);
            }
            scoreboard.addPlayerToTeam(entry, team);
            entity.addEffect(new MobEffectInstance(MobEffects.GLOWING, INFINITE, 0, false, false, false));
        } catch (Exception e) {
            CobbleJefes.LOGGER.error("[CobbleJefes] applyGlow error: {}", e.getMessage());
        }
    }
}