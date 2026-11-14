package com.genetics.journal;

import com.genetics.codex.Balance;
import com.genetics.genome.Gene;
import com.genetics.genome.Genome;
import com.hypixel.hytale.component.ComponentAccessor;
import com.hypixel.hytale.component.Ref;
import com.hypixel.hytale.server.core.Message;
import com.hypixel.hytale.server.core.universe.PlayerRef;
import com.hypixel.hytale.server.core.universe.world.storage.EntityStore;
import com.hypixel.hytale.server.core.util.NotificationUtil;

import javax.annotation.Nonnull;
import javax.annotation.Nullable;
import java.util.ArrayList;
import java.util.List;
import java.util.Set;

/**
 * What happens when the Catalogue grows (design doc §9.4): discoveries pay Science, shown in a notification.
 * <ul>
 *   <li>Drawing the first sample of a species: {@code FirstSample}.</li>
 *   <li>Processing (which reveals the genes): each gene new for that species ({@code NewGene}), each effect never
 *   seen on any species ({@code NewBuff}), each new mutation ({@code NewMutation}), and {@code SpeciesComplete} once
 *   every gene the species can carry has been found.</li>
 * </ul>
 * Spliced samples mix several species, so they only count for buffs and mutations.
 */
public final class Journal {
    private Journal() {
    }

    /** The entity's Catalogue, created if it has none yet. */
    @Nonnull
    public static GeneJournal of(@Nonnull Ref<EntityStore> ref, @Nonnull ComponentAccessor<EntityStore> accessor) {
        GeneJournal journal = accessor.getComponent(ref, GeneJournal.getComponentType());
        if (journal == null) {
            journal = new GeneJournal();
            accessor.putComponent(ref, GeneJournal.getComponentType(), journal);
        }
        journal.migrateSpecies(Catalogue::speciesOf, (species, fallback) -> Catalogue.speciesName(species, null, fallback));
        return journal;
    }

    /** The Catalogue species of a fresh (not spliced) sample, or null. */
    @Nullable
    public static String speciesOf(@Nonnull Genome sample) {
        Genome.Source source = sample.source();
        return source != null && !source.spliced() ? Catalogue.speciesOf(source.mobId()) : null;
    }

    /** Records a fresh sample, paying Science for a new species. */
    public static void onSample(@Nonnull Ref<EntityStore> ref, @Nonnull ComponentAccessor<EntityStore> accessor, @Nonnull PlayerRef player,
                                @Nonnull Genome sample) {
        String species = speciesOf(sample);
        if (species == null) return;
        GeneJournal journal = of(ref, accessor);
        String name = Catalogue.speciesName(species, player.getLanguage(), sample.source().mobName());
        boolean first = journal.recordSample(species, Catalogue.speciesName(species, null, sample.source().mobName()), sample.source());
        if (!first) return;
        int science = Balance.get().science().getFirstSample();
        journal.addScience(science);
        notify(player, Message.raw("New species: " + name),
                Message.raw("+" + science + " Science  ·  Catalogue: " + journal.speciesCount() + " species. Process it at a Lab Station."),
                sample.source().icon());
    }

    /**
     * Records the genes of a sample that was just processed (identified), paying Science for what is new.
     * Returns the Science earned.
     */
    public static int onIdentify(@Nonnull Ref<EntityStore> ref, @Nonnull ComponentAccessor<EntityStore> accessor, @Nullable PlayerRef player,
                                 @Nonnull Genome sample) {
        GeneJournal journal = of(ref, accessor);
        journal.recordGenes(sample);
        Balance.Science rewards = Balance.get().science();
        String species = speciesOf(sample);
        int science = 0;
        List<String> found = new ArrayList<>();
        int newGenes = 0;
        for (Gene gene : sample.genes()) {
            String key = Catalogue.key(gene);
            if (species != null && !(gene instanceof Gene.Mutation) && journal.addSpeciesGene(species, key)) {
                science += rewards.getNewGene();
                newGenes++;
            }
            if (gene instanceof Gene.Effect effect && journal.addBuff(effect.effect())) {
                science += rewards.getNewBuff();
                found.add("new buff " + Catalogue.name(key, player != null ? player.getLanguage() : null));
            }
            if (gene instanceof Gene.Mutation mutation && journal.addMutation(mutation.mutation())) {
                science += rewards.getNewMutation();
                found.add("new mutation " + Catalogue.name(key, player != null ? player.getLanguage() : null));
            }
        }
        if (newGenes > 0) found.addFirst(newGenes + (newGenes == 1 ? " new gene" : " new genes"));
        if (species != null && !journal.isCompleted(species)) {
            Set<String> possible = Catalogue.possibleGenes(species);
            if (!possible.isEmpty() && journal.genesOf(species).containsAll(possible) && journal.markCompleted(species)) {
                science += rewards.getSpeciesComplete();
                found.add(Catalogue.speciesName(species, player != null ? player.getLanguage() : null, species) + " complete");
            }
        }
        journal.addScience(science);
        if (science > 0 && player != null) {
            notify(player, Message.raw("+" + science + " Science"), Message.raw("Catalogue: " + String.join(", ", found) + "."), null);
        }
        return science;
    }

    /** Science the sample would earn when processed (shown before processing). */
    public static int scienceFor(@Nonnull GeneJournal journal, @Nonnull Genome sample) {
        Balance.Science rewards = Balance.get().science();
        String species = speciesOf(sample);
        int science = 0;
        Set<String> speciesKnown = species != null ? new java.util.HashSet<>(journal.genesOf(species)) : Set.of();
        Set<String> buffs = new java.util.HashSet<>(journal.getBuffs());
        Set<String> mutations = new java.util.HashSet<>(journal.getMutations());
        for (Gene gene : sample.genes()) {
            String key = Catalogue.key(gene);
            if (species != null && !(gene instanceof Gene.Mutation) && speciesKnown.add(key)) science += rewards.getNewGene();
            if (gene instanceof Gene.Effect effect && buffs.add(effect.effect())) science += rewards.getNewBuff();
            if (gene instanceof Gene.Mutation mutation && mutations.add(mutation.mutation())) science += rewards.getNewMutation();
        }
        if (species != null && !journal.isCompleted(species)) {
            Set<String> possible = Catalogue.possibleGenes(species);
            if (!possible.isEmpty() && speciesKnown.containsAll(possible)) science += rewards.getSpeciesComplete();
        }
        return science;
    }

    /** True if this gene would be new for its species (shown as NEW before processing). */
    public static boolean isNew(@Nonnull GeneJournal journal, @Nonnull Genome sample, @Nonnull Gene gene) {
        String species = speciesOf(sample);
        if (species != null && !(gene instanceof Gene.Mutation)) return !journal.genesOf(species).contains(Catalogue.key(gene));
        return switch (gene) {
            case Gene.Effect effect -> !journal.getBuffs().contains(effect.effect());
            case Gene.Mutation mutation -> !journal.getMutations().contains(mutation.mutation());
            case Gene.Stat ignored -> false;
        };
    }

    private static void notify(@Nonnull PlayerRef player, @Nonnull Message title, @Nonnull Message subtitle, @Nullable String icon) {
        NotificationUtil.sendNotification(player.getPacketHandler(), title, subtitle, icon);
    }
}
