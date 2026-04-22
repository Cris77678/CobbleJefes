package com.tuservidor.pokefrontier.mixin;

import com.tuservidor.pokefrontier.assault.AssaultSession;
import com.tuservidor.pokefrontier.config.AssaultConfig;
import net.minecraft.network.chat.Component;
import net.minecraft.network.protocol.game.ServerboundChatCommandPacket;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.server.network.ServerGamePacketListenerImpl;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

@Mixin(ServerGamePacketListenerImpl.class)
public abstract class CommandBlockerMixin {

    @Shadow public ServerPlayer player;

    @Inject(method = "handleChatCommand", at = @At("HEAD"), cancellable = true)
    private void pokefrontier$blockCommands(ServerboundChatCommandPacket packet, CallbackInfo ci) {
        if (!AssaultSession.has(player.getUUID())) return;

        String command = packet.command().toLowerCase();

        // Permitir comandos de PokeFrontier
        if (command.startsWith("assault leave")
                || command.startsWith("assault status")
                || command.startsWith("assault check")) {
            return;
        }

        // Comandos peligrosos durante el asalto
        boolean blocked =
            command.startsWith("pc")         ||
            command.startsWith("pokeheal")   ||
            command.startsWith("trade")      ||
            command.startsWith("tpa")        ||
            command.startsWith("home")       ||
            command.startsWith("spawn")      ||
            command.startsWith("warp")       ||
            command.startsWith("back")       ||
            command.startsWith("tp ")        ||
            command.startsWith("teleport");

        if (blocked) {
            player.sendSystemMessage(Component.literal(
                AssaultConfig.get().format(AssaultConfig.get().getMsgBannedCommand())
            ));
            ci.cancel();
        }
    }
}
