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
            .documentation("The user's new Genome Capacity (one-time tiers).").add()
            .append(new KeyedCodec<>("Increase", Codec.DOUBLE), (t, v) -> t.increase = v, t -> t.increase)
            .documentation("Instead of Capacity: raise the capacity by this much each use, up to the HardCap in the Balance file.").add()
            .append(new KeyedCodec<>("Floor", Codec.DOUBLE), (t, v) -> t.floor = v, t -> t.floor)
            .documentation("With Increase: a capacity below this counts as this before the increase (so the first use reaches Floor + Increase).").add()
            .build();

    private double capacity;
    private double increase;
    private double floor;

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
        double hardCap = com.genetics.codex.Balance.get().capacity().getHardCap();
        double target = Math.min(hardCap, increase > 0 ? Math.max(before, floor) + increase : capacity);
        if (!GenomeCapacity.expandTo(self, buffer, target)) {
            user.sendMessage(Message.raw(before >= hardCap - 1e-6
                    ? "Your Genome Capacity is already at its limit (" + GeneText.number(hardCap) + ")."
                    : "Your Genome Capacity is already " + GeneText.number(before) + ". This expansion would only raise it to " + GeneText.number(target) + "."));
            context.getState().state = InteractionState.Failed;
            return;
        }
        double after = target;

        ItemStack rest = held.getQuantity() > 1 ? held.withQuantity(held.getQuantity() - 1) : null;
        if (rest != null) {
            context.getHeldItemContainer().replaceItemStackInSlot(context.getHeldItemSlot(), held, rest);
        } else {
            context.getHeldItemContainer().removeItemStackFromSlot(context.getHeldItemSlot());
        }
        context.setHeldItem(rest);
        user.sendMessage(Message.raw("Your body adapts. Genome Capacity: " + GeneText.number(before) + " -> " + GeneText.number(after) + ".")
                .color("#8fd17a"));
    }

    @Override
    public String toString() {
        return "UseExpansionInteraction{capacity=" + capacity + ", increase=" + increase + ", floor=" + floor + "}" + super.toString();
    }
}
