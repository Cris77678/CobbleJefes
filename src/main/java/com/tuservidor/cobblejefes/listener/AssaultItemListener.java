package com.tuservidor.cobblejefes.listener;

import com.tuservidor.cobblejefes.assault.AssaultSession;
import com.tuservidor.cobblejefes.config.AssaultConfig;
import net.fabricmc.fabric.api.event.player.UseEntityCallback;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.network.chat.Component;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.item.ItemStack;

public class AssaultItemListener {

    public static void register() {
        UseEntityCallback.EVENT.register((player, world, hand, entity, hitResult) -> {

            // Solo bloquear si el jugador está en sesión de asalto
            if (!AssaultSession.has(player.getUUID())) return InteractionResult.PASS;

            // Bloquear interacción con NPCs de asalto
            if (entity.getTags().contains("pf_assault")) return InteractionResult.FAIL;

            // Bloquear uso de ítems de batalla
            ItemStack item = player.getItemInHand(hand);
            if (item.isEmpty()) return InteractionResult.PASS;

            String itemId = BuiltInRegistries.ITEM.getKey(item.getItem()).toString();
            AssaultConfig cfg = AssaultConfig.get();

            if (cfg.getBannedBattleItems().contains(itemId)) {
                if (!world.isClientSide()) {
                    player.sendSystemMessage(Component.literal(cfg.format(cfg.getMsgBannedItem())));
                }
                return InteractionResult.FAIL;
            }

            return InteractionResult.PASS;
        });
    }
}
