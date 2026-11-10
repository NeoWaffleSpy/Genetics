package com.genetics.journal;

import com.genetics.codex.Balance;
import com.genetics.genome.Genome;
import com.hypixel.hytale.builtin.crafting.CraftingPlugin;
import com.hypixel.hytale.component.ComponentAccessor;
import com.hypixel.hytale.component.Ref;
import com.hypixel.hytale.server.core.Message;
import com.hypixel.hytale.server.core.asset.type.item.config.Item;
import com.hypixel.hytale.server.core.universe.PlayerRef;
import com.hypixel.hytale.server.core.universe.world.storage.EntityStore;
import com.hypixel.hytale.server.core.util.NotificationUtil;

import javax.annotation.Nonnull;
import javax.annotation.Nullable;

/**
 * What happens when the Gene Journal grows (design doc §6, "discovery moments" and "Journal milestones teach recipes"):
 * the first sample of a species shows a notification, and reaching a milestone teaches its recipes.
 */
public final class Journal {
    private Journal() {
    }

    /** The entity's journal, created if it has none yet. */
    @Nonnull
    public static GeneJournal of(@Nonnull Ref<EntityStore> ref, @Nonnull ComponentAccessor<EntityStore> accessor) {
        GeneJournal journal = accessor.getComponent(ref, GeneJournal.getComponentType());
        if (journal == null) {
            journal = new GeneJournal();
            accessor.putComponent(ref, GeneJournal.getComponentType(), journal);
        }
        return journal;
    }

    /** Records a fresh sample: notifies a new species, records its genes if identified, and checks milestones. */
    public static void onSample(@Nonnull Ref<EntityStore> ref, @Nonnull ComponentAccessor<EntityStore> accessor, @Nonnull PlayerRef player,
                                @Nonnull Genome sample) {
        if (sample.source() == null) return;
        GeneJournal journal = of(ref, accessor);
        boolean first = journal.recordSample(sample.source());
        if (sample.identified()) journal.recordGenes(sample);
        if (first) {
            notify(player, Message.raw("New genome: " + sample.source().mobName()),
                    Message.raw("Gene Journal: " + journal.speciesCount() + " species. Identify it at a Lab Station."),
                    sample.source().icon());
        }
        checkMilestones(ref, accessor, player, journal);
    }

    /** Records the genes of a sample that was just identified. */
    public static void onIdentify(@Nonnull Ref<EntityStore> ref, @Nonnull ComponentAccessor<EntityStore> accessor, @Nonnull Genome sample) {
        of(ref, accessor).recordGenes(sample);
    }

    /** Teaches the recipes of every milestone reached but not yet granted. */
    public static void checkMilestones(@Nonnull Ref<EntityStore> ref, @Nonnull ComponentAccessor<EntityStore> accessor, @Nonnull PlayerRef player,
                                       @Nonnull GeneJournal journal) {
        for (Balance.Milestone milestone : Balance.get().milestones()) {
            if (journal.speciesCount() < milestone.getSpecies() || !journal.reach(milestone.getId())) continue;
            for (String recipe : milestone.getRecipes()) {
                CraftingPlugin.learnRecipe(ref, recipe, accessor);
                Item item = Item.getAssetMap().getAsset(recipe);
                notify(player, Message.raw("Gene Journal milestone: " + milestone.getSpecies() + " species"),
                        Message.raw("New recipe: ").insert(item != null ? item.getTranslationMessage() : Message.raw(recipe)),
                        item != null ? item.getIcon() : null);
            }
        }
    }

    /** The next milestone not reached yet, or null. */
    @Nullable
    public static Balance.Milestone nextMilestone(@Nonnull GeneJournal journal) {
        Balance.Milestone next = null;
        for (Balance.Milestone milestone : Balance.get().milestones()) {
            if (milestone.getSpecies() <= journal.speciesCount()) continue;
            if (next == null || milestone.getSpecies() < next.getSpecies()) next = milestone;
        }
        return next;
    }

    private static void notify(@Nonnull PlayerRef player, @Nonnull Message title, @Nonnull Message subtitle, @Nullable String icon) {
        NotificationUtil.sendNotification(player.getPacketHandler(), title, subtitle, icon);
    }
}
