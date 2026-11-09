package com.genetics.interaction;

import com.hypixel.hytale.codec.builder.BuilderCodec;
import com.hypixel.hytale.component.CommandBuffer;
import com.hypixel.hytale.component.Ref;
import com.hypixel.hytale.protocol.InteractionState;
import com.hypixel.hytale.protocol.InteractionType;
import com.hypixel.hytale.protocol.WaitForDataFrom;
import com.hypixel.hytale.server.core.Message;
import com.hypixel.hytale.server.core.entity.InteractionContext;
import com.hypixel.hytale.server.core.inventory.ItemStack;
import com.hypixel.hytale.server.core.modules.interaction.interaction.CooldownHandler;
import com.hypixel.hytale.server.core.modules.interaction.interaction.config.SimpleInstantInteraction;
import com.hypixel.hytale.server.core.universe.PlayerRef;
import com.hypixel.hytale.server.core.universe.world.storage.EntityStore;
import com.hypixel.hytale.server.npc.entities.NPCEntity;

import javax.annotation.Nonnull;
import javax.annotation.Nullable;

/**
 * Used by the Syringe item: samples the entity the player is aiming at and consumes one syringe.
 */
public class UseSyringeInteraction extends SimpleInstantInteraction {
    /** Interaction type id, referenced from item JSON as {@code "Type": "UseSyringeInteraction"}. */
    public static final String ID = "UseSyringeInteraction";

    public static final BuilderCodec<UseSyringeInteraction> CODEC = BuilderCodec.builder(
            UseSyringeInteraction.class, UseSyringeInteraction::new, SimpleInstantInteraction.CODEC
    ).documentation("Takes a sample from the targeted entity and consumes one held syringe.").build();

    @Nonnull
    @Override
    public WaitForDataFrom getWaitForDataFrom() {
        return WaitForDataFrom.Server;
    }

    @Override
    protected void firstRun(@Nonnull InteractionType type, @Nonnull InteractionContext context, @Nonnull CooldownHandler cooldownHandler) {
        CommandBuffer<EntityStore> commandBuffer = context.getCommandBuffer();
        ItemStack heldItem = context.getHeldItem();
        Ref<EntityStore> target = context.getTargetEntity();
        if (commandBuffer == null || heldItem == null || target == null) {
            fail(context);
            return;
        }

        PlayerRef user = commandBuffer.getComponent(context.getEntity(), PlayerRef.getComponentType());
        if (user == null) {
            fail(context);
            return;
        }

        String targetName = describe(target, commandBuffer);
        if (targetName == null) {
            fail(context);
            return;
        }

        user.sendMessage(Message.raw("Syringe used on " + targetName + "!"));
        context.getHeldItemContainer().removeItemStackFromSlot(context.getHeldItemSlot(), heldItem, 1);
    }

    /** Returns a readable name for a sampleable entity, or {@code null} if it can't be sampled. */
    @Nullable
    private static String describe(@Nonnull Ref<EntityStore> target, @Nonnull CommandBuffer<EntityStore> commandBuffer) {
        NPCEntity npc = commandBuffer.getComponent(target, NPCEntity.getComponentType());
        if (npc != null) return npc.getRoleName();

        PlayerRef player = commandBuffer.getComponent(target, PlayerRef.getComponentType());
        if (player != null) return player.getUsername();

        return null;
    }

    private static void fail(@Nonnull InteractionContext context) {
        context.getState().state = InteractionState.Failed;
    }

    @Override
    public String toString() {
        return "UseSyringeInteraction{}" + super.toString();
    }
}
