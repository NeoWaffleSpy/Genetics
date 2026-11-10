package com.genetics.interaction;

import com.genetics.genome.Genome;
import com.genetics.genome.GenomeSampler;
import com.hypixel.hytale.codec.builder.BuilderCodec;
import com.hypixel.hytale.component.CommandBuffer;
import com.hypixel.hytale.component.Ref;
import com.hypixel.hytale.protocol.InteractionState;
import com.hypixel.hytale.protocol.InteractionType;
import com.hypixel.hytale.protocol.WaitForDataFrom;
import com.hypixel.hytale.server.core.Message;
import com.hypixel.hytale.server.core.asset.type.item.config.metadata.ItemDisplayMetadata;
import com.hypixel.hytale.server.core.entity.InteractionContext;
import com.hypixel.hytale.server.core.inventory.ItemStack;
import com.hypixel.hytale.server.core.modules.interaction.interaction.CooldownHandler;
import com.hypixel.hytale.server.core.modules.interaction.interaction.config.SimpleInstantInteraction;
import com.hypixel.hytale.server.core.universe.PlayerRef;
import com.hypixel.hytale.server.core.universe.world.storage.EntityStore;
import com.hypixel.hytale.server.npc.entities.NPCEntity;

import javax.annotation.Nonnull;

/**
 * Used by the Syringe item: samples the targeted mob's genes into the held syringe.
 * The syringe is renamed to "&lt;MobName&gt;'s Genes" and holds a raw {@link Genome} in its metadata.
 */
public class UseSyringeInteraction extends SimpleInstantInteraction {
    /** Interaction type id, referenced from item JSON as {@code "Type": "Genetics_UseSyringe"}. */
    public static final String ID = "Genetics_UseSyringe";
    /** Item id of the Syringe (from {@code Server/Item/Items/Tools/Genetics_Syringe.json}). */
    public static final String ITEM_ID = "Genetics_Syringe";

    public static final BuilderCodec<UseSyringeInteraction> CODEC = BuilderCodec.builder(
            UseSyringeInteraction.class, UseSyringeInteraction::new, SimpleInstantInteraction.CODEC
    ).documentation("Samples the targeted mob's genes into the held syringe.").build();

    @Nonnull
    @Override
    public WaitForDataFrom getWaitForDataFrom() {
        return WaitForDataFrom.Server;
    }

    @Override
    protected void firstRun(@Nonnull InteractionType type, @Nonnull InteractionContext context, @Nonnull CooldownHandler cooldownHandler) {
        CommandBuffer<EntityStore> commandBuffer = context.getCommandBuffer();
        ItemStack syringe = context.getHeldItem();
        Ref<EntityStore> target = context.getTargetEntity();
        if (commandBuffer == null || syringe == null || target == null) {
            fail(context);
            return;
        }

        PlayerRef user = commandBuffer.getComponent(context.getEntity(), PlayerRef.getComponentType());
        NPCEntity npc = commandBuffer.getComponent(target, NPCEntity.getComponentType());
        if (user == null || npc == null) {
            fail(context);
            return;
        }

        if (Genome.isPresentOn(syringe)) {
            user.sendMessage(Message.raw("This syringe already holds a sample."));
            fail(context);
            return;
        }

        Genome genome = GenomeSampler.sample(target, npc, user.getLanguage(), commandBuffer);
        String mobName = genome.source() != null ? genome.source().mobName() : npc.getRoleName();
        ItemStack filled = genome.writeTo(syringe)
                .withMetadata(ItemDisplayMetadata.KEYED_CODEC, new ItemDisplayMetadata(Message.raw(mobName + "'s Genes"), null));

        if (!context.getHeldItemContainer().replaceItemStackInSlot(context.getHeldItemSlot(), syringe, filled).succeeded()) {
            fail(context);
            return;
        }
        context.setHeldItem(filled);

        user.sendMessage(Message.raw("Sampled " + mobName + "'s genes (" + genome.genes().size() + " genes, Complexity " + genome.complexity() + ")."));
    }

    private static void fail(@Nonnull InteractionContext context) {
        context.getState().state = InteractionState.Failed;
    }

    @Override
    public String toString() {
        return "UseSyringeInteraction{}" + super.toString();
    }
}
