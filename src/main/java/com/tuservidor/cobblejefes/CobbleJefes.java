package com.tuservidor.cobblejefes;

import com.tuservidor.cobblejefes.assault.AssaultProgress;
import com.tuservidor.cobblejefes.command.AssaultCommand;
import com.tuservidor.cobblejefes.command.ExportCommand;
import com.tuservidor.cobblejefes.config.AssaultConfig;
import com.tuservidor.cobblejefes.glow.FrontierGlow;
import com.tuservidor.cobblejefes.listener.*;
import net.fabricmc.api.ModInitializer;
import net.fabricmc.fabric.api.command.v2.CommandRegistrationCallback;
import net.fabricmc.fabric.api.event.lifecycle.v1.ServerLifecycleEvents;
import net.minecraft.server.MinecraftServer;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

public class CobbleJefes implements ModInitializer {

    public static final String MOD_ID = "cobblejefes";
    public static final Logger LOGGER = LoggerFactory.getLogger("CobbleJefes");
    public static MinecraftServer SERVER;

    private static final ExecutorService ASYNC_EXECUTOR = Executors.newCachedThreadPool();

    @Override
    public void onInitialize() {
        LOGGER.info("[CobbleJefes] Iniciando sistema de Asalto a Bases...");

        AssaultConfig.reload();
        AssaultProgress.load();

        AssaultBattleListener.register();
        AssaultSecurityListener.register();
        AssaultItemListener.register();
        AssaultAntiGriefListener.register();
        RentalSecurityListener.register();
        PlayerLevelCapListener.register();
        FrontierGlow.register();

        CommandRegistrationCallback.EVENT.register((dispatcher, registryAccess, environment) -> {
            AssaultCommand.register(dispatcher);
            ExportCommand.register(dispatcher);
        });

        ServerLifecycleEvents.SERVER_STARTING.register(server -> {
            SERVER = server;
            FrontierGlow.ensureTeams(server);
        });

        ServerLifecycleEvents.SERVER_STOPPING.register(server -> {
            AssaultProgress.save();
        });

        LOGGER.info("[CobbleJefes] Sistema de Asalto a Bases listo.");
    }

    public static void runAsync(Runnable runnable) {
        ASYNC_EXECUTOR.submit(runnable);
    }
}