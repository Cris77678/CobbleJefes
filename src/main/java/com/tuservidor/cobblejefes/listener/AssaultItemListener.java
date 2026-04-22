package com.tuservidor.cobblejefes.listener;

import com.tuservidor.cobblejefes.assault.AssaultSession;
import com.tuservidor.cobblejefes.config.AssaultConfig;
import net.fabricmc.fabric.api.event.player.UseEntityCallback;
import net.fabricmc.fabric.api.event.player.UseItemCallback;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.network.chat.Component;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.InteractionResultHolder;
import net.minecraft.world.item.ItemStack;

public class AssaultItemListener {

    public static void register() {
        
        // Bloqueo al hacer click en entidades (ej. clickear un Pokémon o NPC)
        UseEntityCallback.EVENT.register((player, world, hand, entity, hitResult) -> {
            if (!AssaultSession.has(player.getUUID())) return InteractionResult.PASS;
            if (entity.getTags().contains("pf_assault")) return InteractionResult.FAIL;

            ItemStack item = player.getItemInHand(hand);
            if (item.isEmpty()) return InteractionResult.PASS;

            if (isBannedItem(item)) {
                if (!world.isClientSide()) {
                    player.sendSystemMessage(Component.literal(AssaultConfig.get().format(AssaultConfig.get().getMsgBannedItem())));
                }
                return InteractionResult.FAIL;
            }
            return InteractionResult.PASS;
        });

        // FIX CRÍTICO: Bloqueo al hacer click al aire o a bloques (usar objetos curativos desde la mano)
        UseItemCallback.EVENT.register((player, world, hand) -> {
            if (!AssaultSession.has(player.getUUID())) return InteractionResultHolder.pass(player.getItemInHand(hand));

            ItemStack item = player.getItemInHand(hand);
            if (item.isEmpty()) return InteractionResultHolder.pass(item);

            if (isBannedItem(item)) {
                if (!world.isClientSide()) {
                    player.sendSystemMessage(Component.literal(AssaultConfig.get().format(AssaultConfig.get().getMsgBannedItem())));
                }
                return InteractionResultHolder.fail(item);
            }
            return InteractionResultHolder.pass(item);
        });
    }

    private static boolean isBannedItem(ItemStack item) {
        String itemId = BuiltInRegistries.ITEM.getKey(item.getItem()).toString();
        return AssaultConfig.get().getBannedBattleItems().contains(itemId);
    }
}