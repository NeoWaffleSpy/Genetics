package com.genetics.interaction;

import com.genetics.codex.Balance;
import com.genetics.codex.Codex;
import com.genetics.codex.MobGeneProfile;
import com.genetics.genome.GeneText;
import com.genetics.genome.Genome;
import com.genetics.genome.GenomeSampler;
import com.genetics.journal.Journal;
import com.hypixel.hytale.codec.Codec;
import com.hypixel.hytale.codec.KeyedCodec;
import com.hypixel.hytale.codec.builder.BuilderCodec;
import com.hypixel.hytale.component.CommandBuffer;
import com.hypixel.hytale.component.Ref;
import com.hypixel.hytale.protocol.InteractionState;
import com.hypixel.hytale.protocol.InteractionType;
import com.hypixel.hytale.protocol.WaitForDataFrom;
import com.hypixel.hytale.server.core.Message;
import com.hypixel.hytale.server.core.asset.type.item.config.Item;
import com.hypixel.hytale.server.core.entity.InteractionContext;
import com.hypixel.hytale.server.core.inventory.ItemStack;
import com.hypixel.hytale.server.core.modules.entitystats.EntityStatMap;
import com.hypixel.hytale.server.core.modules.entitystats.EntityStatValue;
import com.hypixel.hytale.server.core.modules.interaction.interaction.CooldownHandler;
import com.hypixel.hytale.server.core.modules.interaction.interaction.config.SimpleInstantInteraction;
import com.hypixel.hytale.server.core.universe.PlayerRef;
import com.hypixel.hytale.server.core.universe.world.storage.EntityStore;
import com.hypixel.hytale.server.npc.entities.NPCEntity;

import javax.annotation.Nonnull;
import java.util.List;

/**
 * Used by every syringe on a mob (primary use): samples the mob's genes into the held syringe, if the syringe's tier
 * is high enough for that mob (design doc §6, syringe tiers).
 * <p>
 * Every sample starts as an "Unidentified &lt;Mob&gt; Sample" until identified at the Lab Station. The first sample of a
 * species adds it to the Gene Journal.
 * <pre>
 * { "Type": "Genetics_UseSyringe", "Tier": 1 }
 * </pre>
 */
public class UseSyringeInteraction extends SimpleInstantInteraction {
    /** Interaction type id, referenced from item JSON as {@code "Type": "Genetics_UseSyringe"}. */
    public static final String ID = "Genetics_UseSyringe";
    /** Syringe item ids by tier, from tier 1 (index 0) up. Used to name the syringe a mob needs. */
    public static final List<String> TIER_ITEMS = List.of(
            "Genetics_Syringe", "Genetics_Syringe_Copper", "Genetics_Syringe_Thorium", "Genetics_Syringe_Adamantite");

    public static final BuilderCodec<UseSyringeInteraction> CODEC = BuilderCodec.builder(
                    UseSyringeInteraction.class, UseSyringeInteraction::new, SimpleInstantInteraction.CODEC)
            .documentation("Samples the targeted mob's genes into the held syringe.")
            .append(new KeyedCodec<>("Tier", Codec.INTEGER), (t, v) -> t.tier = v, t -> t.tier)
            .documentation("Syringe tier: the strongest mobs it can sample (1 = Crude).").add()
            .build();

    private int tier = 1;

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

        Ref<EntityStore> self = context.getEntity();
        PlayerRef user = commandBuffer.getComponent(self, PlayerRef.getComponentType());
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

        int needed = requiredTier(npc, commandBuffer.getComponent(target, EntityStatMap.getComponentType()));
        if (needed > tier) {
            String itemId = TIER_ITEMS.get(Math.min(needed, TIER_ITEMS.size()) - 1);
            Item item = Item.getAssetMap().getAsset(itemId);
            user.sendMessage(Message.raw("This creature is too strong for this syringe. You need a ")
                    .insert(item != null ? item.getTranslationMessage() : Message.raw(itemId))
                    .insert(Message.raw(" or better.")));
            fail(context);
            return;
        }

        Genome genome = GenomeSampler.sample(target, npc, user.getLanguage(), commandBuffer, false);
        ItemStack filled = Syringes.fill(syringe, genome);
        if (!context.getHeldItemContainer().replaceItemStackInSlot(context.getHeldItemSlot(), syringe, filled).succeeded()) {
            fail(context);
            return;
        }
        context.setHeldItem(filled);
        Journal.onSample(self, commandBuffer, user, genome);

        String mobName = genome.source() != null ? genome.source().mobName() : npc.getRoleName();
        user.sendMessage(Message.raw(genome.identified()
                ? "Sampled " + mobName + "'s genes (" + genome.genes().size() + " genes, Complexity " + GeneText.number(genome.complexity()) + ")."
                : "Drew an unidentified " + mobName + " sample. Identify it at a Lab Station to see its genes."));
    }

    /** The syringe tier a mob needs: its gene profile's SyringeTier, else one based on its max health. */
    public static int requiredTier(@Nonnull NPCEntity npc, EntityStatMap stats) {
        MobGeneProfile profile = Codex.profileFor(npc.getRoleName());
        boolean ownProfile = profile != null && !profile.getId().equals(MobGeneProfile.DEFAULT_ID);
        if (ownProfile && profile.getSyringeTier() > 0) return profile.getSyringeTier();
        EntityStatValue health = stats != null ? stats.get("Health") : null;
        return Balance.get().syringes().tierForHealth(health != null ? health.getMax() : 0);
    }

    private static void fail(@Nonnull InteractionContext context) {
        context.getState().state = InteractionState.Failed;
    }

    @Override
    public String toString() {
        return "UseSyringeInteraction{tier=" + tier + "}" + super.toString();
    }
}
