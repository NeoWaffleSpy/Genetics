package com.genetics.interaction;

import com.genetics.capacity.GenomeCapacity;
import com.genetics.genome.GeneText;
import com.hypixel.hytale.codec.Codec;
import com.hypixel.hytale.codec.KeyedCodec;
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

import javax.annotation.Nonnull;

/**
 * Used by Genome Expansion items: permanently raises the user's Genome Capacity to the item's {@code Capacity}
 * (design doc §3.4). An expansion at or below the current capacity can't be used and isn't consumed, so higher ones
 * can be used directly without the lower ones first.
 * <pre>
 * { "Type": "Genetics_UseExpansion", "Capacity": 45 }
 * </pre>
 */
public class UseExpansionInteraction extends SimpleInstantInteraction {
    public static final String ID = "Genetics_UseExpansion";

    public static final BuilderCodec<UseExpansionInteraction> CODEC = BuilderCodec.builder(
                    UseExpansionInteraction.class, UseExpansionInteraction::new, SimpleInstantInteraction.CODEC)
            .documentation("Raises the user's Genome Capacity to Capacity, consuming the held item.")
            .append(new KeyedCodec<>("Capacity", Codec.DOUBLE), (t, v) -> t.capacity = v, t -> t.capacity)
            .documentation("The user's new Genome Capacity.").add()
            .build();

    private double capacity;

    @Nonnull
    @Override
    public WaitForDataFrom getWaitForDataFrom() {
        return WaitForDataFrom.Server;
    }

    @Override
    protected void firstRun(@Nonnull InteractionType type, @Nonnull InteractionContext context, @Nonnull CooldownHandler cooldownHandler) {
        CommandBuffer<EntityStore> buffer = context.getCommandBuffer();
        ItemStack held = context.getHeldItem();
        Ref<EntityStore> self = context.getEntity();
        PlayerRef user = buffer != null ? buffer.getComponent(self, PlayerRef.getComponentType()) : null;
        if (buffer == null || held == null || user == null) {
            context.getState().state = InteractionState.Failed;
            return;
        }

        float before = GenomeCapacity.capacity(self, buffer);
        if (!GenomeCapacity.expandTo(self, buffer, capacity)) {
            user.sendMessage(Message.raw("Your Genome Capacity is already " + GeneText.number(before)
                    + ". This expansion would only raise it to " + GeneText.number(capacity) + "."));
            context.getState().state = InteractionState.Failed;
            return;
        }

        ItemStack rest = held.getQuantity() > 1 ? held.withQuantity(held.getQuantity() - 1) : null;
        if (rest != null) {
            context.getHeldItemContainer().replaceItemStackInSlot(context.getHeldItemSlot(), held, rest);
        } else {
            context.getHeldItemContainer().removeItemStackFromSlot(context.getHeldItemSlot());
        }
        context.setHeldItem(rest);
        user.sendMessage(Message.raw("Your body adapts. Genome Capacity: " + GeneText.number(before) + " -> " + GeneText.number(capacity) + ".")
                .color("#8fd17a"));
    }

    @Override
    public String toString() {
        return "UseExpansionInteraction{capacity=" + capacity + "}" + super.toString();
    }
}
