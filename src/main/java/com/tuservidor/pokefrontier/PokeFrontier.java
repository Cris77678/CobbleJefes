package com.tuservidor.pokefrontier;

import com.tuservidor.pokefrontier.assault.AssaultProgress;
import com.tuservidor.pokefrontier.command.AssaultCommand;
import com.tuservidor.pokefrontier.command.ExportCommand;
import com.tuservidor.pokefrontier.config.AssaultConfig;
import com.tuservidor.pokefrontier.glow.FrontierGlow;
import com.tuservidor.pokefrontier.listener.*;
import net.fabricmc.api.ModInitializer;
import net.fabricmc.fabric.api.command.v2.CommandRegistrationCallback;
import net.fabricmc.fabric.api.event.lifecycle.v1.ServerLifecycleEvents;
import net.minecraft.server.MinecraftServer;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

public class CobbleJefes implements ModInitializer {

    public static final String MOD_ID = "pokefrontier";
    public static final Logger LOGGER = LoggerFactory.getLogger("PokeFrontier");
    public static MinecraftServer SERVER;

    private static final ExecutorService ASYNC_EXECUTOR = Executors.newCachedThreadPool();

    @Override
    public void onInitialize() {
        LOGGER.info("[PokeFrontier] Iniciando sistema de Asalto a Bases...");

        // Cargar configuración y progreso persistente
        AssaultConfig.reload();
        AssaultProgress.load();

        // Registrar listeners
        AssaultBattleListener.register();
        AssaultSecurityListener.register();
        AssaultItemListener.register();
        AssaultAntiGriefListener.register();
        RentalSecurityListener.register();
        PlayerLevelCapListener.register();
        FrontierGlow.register();

        // Registrar comandos
        CommandRegistrationCallback.EVENT.register((dispatcher, registryAccess, environment) -> {
            AssaultCommand.register(dispatcher);
            ExportCommand.register(dispatcher);
        });

        // Lifecycle
        ServerLifecycleEvents.SERVER_STARTING.register(server -> {
            SERVER = server;
            FrontierGlow.ensureTeams(server);
        });

        ServerLifecycleEvents.SERVER_STOPPING.register(server -> {
            AssaultProgress.save();
        });

        LOGGER.info("[PokeFrontier] Sistema de Asalto a Bases listo.");
    }

    public static void runAsync(Runnable runnable) {
        ASYNC_EXECUTOR.submit(runnable);
    }
}
