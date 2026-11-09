package com.Genetics.interaction;

import com.Genetics.GeneticsPlugin;
import com.hypixel.hytale.codec.builder.BuilderCodec;
import com.hypixel.hytale.component.CommandBuffer;
import com.hypixel.hytale.component.Ref;
import com.hypixel.hytale.protocol.InteractionType;
import com.hypixel.hytale.server.core.Message;
import com.hypixel.hytale.server.core.entity.Entity;
import com.hypixel.hytale.server.core.entity.EntityUtils;
import com.hypixel.hytale.server.core.entity.InteractionContext;
import com.hypixel.hytale.server.core.entity.LivingEntity;
import com.hypixel.hytale.server.core.inventory.InventoryComponent;
import com.hypixel.hytale.server.core.inventory.ItemStack;
import com.hypixel.hytale.server.core.modules.interaction.interaction.CooldownHandler;
import com.hypixel.hytale.server.core.modules.interaction.interaction.config.SimpleInteraction;
import com.hypixel.hytale.server.core.universe.PlayerRef;
import com.hypixel.hytale.server.core.universe.world.storage.EntityStore;
import com.hypixel.hytale.server.npc.NPCPlugin;
import com.hypixel.hytale.server.npc.role.Role;

import javax.annotation.Nonnull;

public class UseSyringeInteraction extends SimpleInteraction {
    public static final BuilderCodec<UseSyringeInteraction> CODEC = BuilderCodec.builder(
            UseSyringeInteraction.class, UseSyringeInteraction::new, SimpleInteraction.CODEC
    ).build();

    @Override
    protected void tick0(
            boolean firstRun, float time, @Nonnull InteractionType type, @Nonnull InteractionContext context, @Nonnull CooldownHandler cooldownHandler
    ) {
        CommandBuffer<EntityStore> commandBuffer = context.getCommandBuffer();
        if (commandBuffer == null) return;
        ItemStack item = context.getHeldItem();
        if (item == null) return;

        Ref<EntityStore> ref = context.getEntity();
        PlayerRef playerRef = commandBuffer.getComponent(ref, PlayerRef.getComponentType());
        if (playerRef == null) return;
        Entity targetEntity = EntityUtils.getEntity(ref, commandBuffer);
        if (!(targetEntity instanceof LivingEntity livingEntity)) return;

        InventoryComponent.Hotbar hotbar = commandBuffer.getComponent(ref, InventoryComponent.Hotbar.getComponentType());
        if (hotbar == null) return;
        byte activeHotbarSlot = hotbar.getActiveSlot();

        if (item.getItemId().equals("Syringe")) {
            String entityName = targetEntity.toString();


            playerRef.sendMessage(Message.raw("Syringe used on " + entityName + "!"));
            activeHotbarSlot.(activeHotbarItem.getQuantity() - 1);
        }
    }
}
