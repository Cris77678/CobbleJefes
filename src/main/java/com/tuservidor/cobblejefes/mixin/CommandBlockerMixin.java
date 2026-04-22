package com.tuservidor.cobblejefes.mixin;

import com.tuservidor.cobblejefes.assault.AssaultSession;
import com.tuservidor.cobblejefes.config.AssaultConfig;
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
    private void cobblejefes$blockCommands(ServerboundChatCommandPacket packet, CallbackInfo ci) {
        if (!AssaultSession.has(player.getUUID())) return;

        String fullCommand = packet.command().toLowerCase();
        
        String baseCommand = fullCommand.split(" ")[0];
        if (baseCommand.contains(":")) {
            baseCommand = baseCommand.split(":")[1];
        }

        if (fullCommand.startsWith("assault leave")
                || fullCommand.startsWith("assault status")
                || fullCommand.startsWith("assault check")) {
            return;
        }

        // FIX: Usar startsWith para tapar variaciones (tpa, tpaccept, homes, warps)
        boolean blocked =
            baseCommand.equals("pc")         ||
            baseCommand.equals("pokeheal")   ||
            baseCommand.startsWith("trade")  ||
            baseCommand.startsWith("tp")     ||
            baseCommand.startsWith("home")   ||
            baseCommand.startsWith("spawn")  ||
            baseCommand.startsWith("warp")   ||
            baseCommand.equals("back");

        if (blocked) {
            player.sendSystemMessage(Component.literal(
                AssaultConfig.get().format(AssaultConfig.get().getMsgBannedCommand())
            ));
            ci.cancel();
        }
    }
}