package com.genetics.command;

import com.genetics.capacity.GenomeCapacity;
import com.genetics.codex.Balance;
import com.genetics.codex.MutationEntry;
import com.genetics.codex.SicknessEvent;
import com.genetics.genome.ComplexityCalculator;
import com.genetics.genome.Gene;
import com.genetics.genome.GenomeApplier;
import com.genetics.genome.GeneText;
import com.genetics.journal.GeneJournal;
import com.genetics.journal.Journal;
import com.genetics.genome.ActiveGenome;
import com.genetics.genome.Genome;
import com.genetics.sickness.Sickness;
import com.genetics.sickness.SicknessSystem;
import com.hypixel.hytale.component.Ref;
import com.hypixel.hytale.component.Store;
import com.hypixel.hytale.server.core.Message;
import com.hypixel.hytale.server.core.command.system.CommandContext;
import com.hypixel.hytale.server.core.command.system.arguments.system.RequiredArg;
import com.hypixel.hytale.server.core.command.system.arguments.types.ArgTypes;
import com.hypixel.hytale.server.core.command.system.basecommands.AbstractCommandCollection;
import com.hypixel.hytale.server.core.command.system.basecommands.AbstractPlayerCommand;
import com.hypixel.hytale.server.core.modules.entitystats.EntityStatMap;
import com.hypixel.hytale.server.core.universe.PlayerRef;
import com.hypixel.hytale.server.core.universe.world.World;
import com.hypixel.hytale.server.core.universe.world.storage.EntityStore;

import javax.annotation.Nonnull;
import java.util.ArrayList;
import java.util.List;
import java.util.TreeSet;

/**
 * Testing and admin tools, for the Builder permission group:
 * <pre>
 * /genetics sickness &lt;0-100&gt;   set your Genetic Sickness
 * /genetics event &lt;id&gt;         fire a sickness event on yourself now (ignores bands and cooldowns)
 * /genetics events             list the sickness event ids
 * /genetics capacity &lt;value&gt;   set your Genome Capacity (test tool; expansions only ever raise it)
 * /genetics journal            show your Catalogue (species, genes, Science, research)
 * /genetics science &lt;amount&gt;   add Science (negative removes it)
 * /genetics research &lt;id&gt;      research a node for free; "all" researches every node, "reset" forgets them
 * /genetics nodes              list the research node ids
 * /genetics library            list your Genetic Library
 * /genetics mutation &lt;id&gt;      give your active genome this mutation (replacing its mutation), "none" removes it
 * /genetics mutations          list the mutation ids
 * </pre>
 */
public class GeneticsCommand extends AbstractCommandCollection {
    public GeneticsCommand() {
        super("genetics", "server.genetics.commands.genetics.desc");
        setPermissionGroups("hytale:Builder");
        addSubCommand(new SetSickness());
        addSubCommand(new FireEvent());
        addSubCommand(new ListEvents());
        addSubCommand(new SetCapacity());
        addSubCommand(new ShowJournal());
        addSubCommand(new AddScience());
        addSubCommand(new GrantResearch());
        addSubCommand(new ListNodes());
        addSubCommand(new LibraryCommand());
        addSubCommand(new CatalogueCommand());
        addSubCommand(new SetMutation());
        addSubCommand(new ListMutations());
    }

    private static final class SetCapacity extends AbstractPlayerCommand {
        private final RequiredArg<Float> value = withRequiredArg("value", "server.genetics.commands.capacity.value.desc", ArgTypes.FLOAT);

        SetCapacity() {
            super("capacity", "server.genetics.commands.capacity.desc");
            setPermissionGroups("hytale:Builder");
        }

        @Override
        protected void execute(@Nonnull CommandContext context, @Nonnull Store<EntityStore> store, @Nonnull Ref<EntityStore> ref,
                               @Nonnull PlayerRef playerRef, @Nonnull World world) {
            float before = GenomeCapacity.capacity(ref, store);
            GenomeCapacity.setCapacity(ref, store, value.get(context));
            context.sendMessage(Message.raw("Genome Capacity: " + GeneText.number(before) + " -> " + GeneText.number(GenomeCapacity.capacity(ref, store)) + "."));
        }
    }

    private static final class SetMutation extends AbstractPlayerCommand {
        private final RequiredArg<String> id = withRequiredArg("id", "server.genetics.commands.mutation.id.desc", ArgTypes.STRING);

        SetMutation() {
            super("mutation", "server.genetics.commands.mutation.desc");
            setPermissionGroups("hytale:Builder");
        }

        @Override
        protected void execute(@Nonnull CommandContext context, @Nonnull Store<EntityStore> store, @Nonnull Ref<EntityStore> ref,
                               @Nonnull PlayerRef playerRef, @Nonnull World world) {
            String wanted = id.get(context);
            MutationEntry entry = null;
            for (MutationEntry candidate : MutationEntry.getAssetMap().getAssetMap().values()) {
                if (candidate.getId().equalsIgnoreCase(wanted)) entry = candidate;
            }
            if (entry == null && !wanted.equalsIgnoreCase("none")) {
                context.sendMessage(Message.raw("Unknown mutation '" + wanted + "'. Mutations: " + mutationList() + ", or none."));
                return;
            }
            ActiveGenome active = store.getComponent(ref, ActiveGenome.getComponentType());
            Genome previous = active != null ? active.getGenome() : null;
            List<Gene> genes = new ArrayList<>();
            if (previous != null) for (Gene gene : previous.genes()) if (!(gene instanceof Gene.Mutation)) genes.add(gene);
            if (entry != null) genes.add(new Gene.Mutation(entry.getId()));
            Genome next = new Genome(Genome.FORMAT_VERSION, true, true, ComplexityCalculator.compute(genes),
                    previous != null ? previous.source() : null, genes);
            if (active != null) active.setGenome(next);
            else store.putComponent(ref, ActiveGenome.getComponentType(), new ActiveGenome(next));
            GenomeApplier.replace(ref, store, previous, next);
            GenomeCapacity.setUsed(store.getComponent(ref, EntityStatMap.getComponentType()), next.complexity());
            context.sendMessage(Message.raw(entry != null ? "Your genome now carries the " + entry.getId() + " mutation." : "Removed your mutation."));
        }
    }

    private static final class ListMutations extends AbstractPlayerCommand {
        ListMutations() {
            super("mutations", "server.genetics.commands.mutations.desc");
            setPermissionGroups("hytale:Builder");
        }

        @Override
        protected void execute(@Nonnull CommandContext context, @Nonnull Store<EntityStore> store, @Nonnull Ref<EntityStore> ref,
                               @Nonnull PlayerRef playerRef, @Nonnull World world) {
            context.sendMessage(Message.raw("Mutations: " + mutationList() + "."));
        }
    }

    @Nonnull
    private static String mutationList() {
        TreeSet<String> ids = new TreeSet<>();
        for (MutationEntry entry : MutationEntry.getAssetMap().getAssetMap().values()) ids.add(entry.getId() + " (" + entry.getMinTier() + ")");
        return String.join(", ", ids);
    }

    private static final class ShowJournal extends AbstractPlayerCommand {
        ShowJournal() {
            super("journal", "server.genetics.commands.journal.desc");
            setPermissionGroups("hytale:Builder");
        }

        @Override
        protected void execute(@Nonnull CommandContext context, @Nonnull Store<EntityStore> store, @Nonnull Ref<EntityStore> ref,
                               @Nonnull PlayerRef playerRef, @Nonnull World world) {
            GeneJournal journal = Journal.of(ref, store);
            TreeSet<String> names = new TreeSet<>();
            journal.getSpecies().values().forEach(entry -> names.add(entry.getName() + " x" + entry.getSamples()));
            context.sendMessage(Message.raw("Catalogue: " + journal.speciesCount() + " species, " + journal.getBuffs().size() + " buffs, "
                    + journal.getMutations().size() + " mutations, " + journal.getScience() + " Science."));
            if (!names.isEmpty()) context.sendMessage(Message.raw("Species: " + String.join(", ", names) + "."));
            context.sendMessage(Message.raw("Researched: " + (journal.getResearched().isEmpty() ? "starters only" : String.join(", ", journal.getResearched())) + "."));
        }
    }

    private static final class AddScience extends AbstractPlayerCommand {
        private final RequiredArg<Integer> amount = withRequiredArg("amount", "server.genetics.commands.science.amount.desc", ArgTypes.INTEGER);

        AddScience() {
            super("science", "server.genetics.commands.science.desc");
            setPermissionGroups("hytale:Builder");
        }

        @Override
        protected void execute(@Nonnull CommandContext context, @Nonnull Store<EntityStore> store, @Nonnull Ref<EntityStore> ref,
                               @Nonnull PlayerRef playerRef, @Nonnull World world) {
            GeneJournal journal = Journal.of(ref, store);
            journal.addScience(amount.get(context));
            context.sendMessage(Message.raw("Science: " + journal.getScience() + "."));
        }
    }

    private static final class GrantResearch extends AbstractPlayerCommand {
        private final RequiredArg<String> id = withRequiredArg("id", "server.genetics.commands.research.id.desc", ArgTypes.STRING);

        GrantResearch() {
            super("research", "server.genetics.commands.research.desc");
            setPermissionGroups("hytale:Builder");
        }

        @Override
        protected void execute(@Nonnull CommandContext context, @Nonnull Store<EntityStore> store, @Nonnull Ref<EntityStore> ref,
                               @Nonnull PlayerRef playerRef, @Nonnull World world) {
            String value = id.get(context);
            GeneJournal journal = Journal.of(ref, store);
            if (value.equalsIgnoreCase("reset")) {
                journal.resetResearch();
                context.sendMessage(Message.raw("Research reset (learned recipes are kept)."));
                return;
            }
            if (value.equalsIgnoreCase("all")) {
                for (com.genetics.research.ResearchNode node : com.genetics.research.Research.all()) com.genetics.research.Research.grant(ref, store, node);
                context.sendMessage(Message.raw("Every research node is researched."));
                return;
            }
            com.genetics.research.ResearchNode node = com.genetics.research.ResearchNode.getAssetMap().getAsset(value);
            if (node == null) {
                context.sendMessage(Message.raw("Unknown node '" + value + "'. Nodes: " + nodeList()));
                return;
            }
            com.genetics.research.Research.grant(ref, store, node);
            context.sendMessage(Message.raw("Researched " + node.getName() + "."));
        }
    }

    private static final class ListNodes extends AbstractPlayerCommand {
        ListNodes() {
            super("nodes", "server.genetics.commands.nodes.desc");
            setPermissionGroups("hytale:Builder");
        }

        @Override
        protected void execute(@Nonnull CommandContext context, @Nonnull Store<EntityStore> store, @Nonnull Ref<EntityStore> ref,
                               @Nonnull PlayerRef playerRef, @Nonnull World world) {
            context.sendMessage(Message.raw("Research nodes: " + nodeList()));
        }
    }

    /** {@code /genetics library show|fill|flush}: list, fill with the best of every gene, or empty the Genetic Library. */
    private static final class LibraryCommand extends AbstractPlayerCommand {
        private final RequiredArg<String> action = withRequiredArg("action", "server.genetics.commands.library.action.desc", ArgTypes.STRING);

        LibraryCommand() {
            super("library", "server.genetics.commands.library.desc");
            setPermissionGroups("hytale:Builder");
        }

        @Override
        protected void execute(@Nonnull CommandContext context, @Nonnull Store<EntityStore> store, @Nonnull Ref<EntityStore> ref,
                               @Nonnull PlayerRef playerRef, @Nonnull World world) {
            com.genetics.library.GeneLibrary library = com.genetics.library.Library.of(ref, store);
            switch (action.get(context).toLowerCase()) {
                case "fill" -> {
                    int added = 0;
                    for (Gene gene : bestGenes()) if (library.add(gene, "Command")) added++;
                    context.sendMessage(Message.raw("Genetic Library filled: " + added + " genes added or improved ("
                            + library.getEntries().size() + " in total)."));
                }
                case "flush", "clear" -> context.sendMessage(Message.raw("Genetic Library emptied (" + library.clear() + " genes removed)."));
                case "show" -> {
                    List<String> lines = new ArrayList<>();
                    for (com.genetics.library.GeneLibrary.Entry entry : library.getEntries()) {
                        Gene gene = entry.getGene();
                        lines.add(gene != null ? GeneText.describe(gene, playerRef.getLanguage()) : "?");
                    }
                    context.sendMessage(Message.raw("Genetic Library: " + (lines.isEmpty() ? "empty." : String.join(", ", lines) + ".")));
                }
                default -> context.sendMessage(Message.raw("Usage: /genetics library show|fill|flush"));
            }
        }

        /**
         * The best of every gene the game can give: each profile stat and drawback at its maximum, every temporary
         * effect at the timer cap, every profile permanent effect, and every mutation.
         */
        @Nonnull
        private static List<Gene> bestGenes() {
            List<Gene> genes = new ArrayList<>();
            double cap = Math.min(com.genetics.codex.Balance.get().splicing().getMaxEffectDuration(), 30 * 60);
            for (com.genetics.codex.MobGeneProfile profile : com.genetics.codex.MobGeneProfile.getAssetMap().getAssetMap().values()) {
                for (com.genetics.codex.MobGeneProfile.StatRoll roll : profile.getStatPool()) {
                    if (roll.getStat() != null) genes.add(new Gene.Stat(roll.getStat(), roll.getMax()));
                }
                for (com.genetics.codex.MobGeneProfile.Drawback drawback : profile.getDrawbacks()) {
                    if (drawback.getStat() != null) genes.add(new Gene.Stat(drawback.getStat(), -drawback.getMax()));
                }
                if (profile.getTemporaryEffect() != null && profile.getTemporaryEffect().getId() != null) {
                    genes.add(new Gene.Effect(profile.getTemporaryEffect().getId(), false, cap));
                }
                for (com.genetics.codex.MobGeneProfile.EffectRoll effect : profile.getPermanentEffects()) {
                    if (effect.getId() != null) genes.add(new Gene.Effect(effect.getId(), true, 0));
                }
            }
            for (String mutation : com.genetics.codex.MutationEntry.getAssetMap().getAssetMap().keySet()) genes.add(new Gene.Mutation(mutation));
            return genes;
        }
    }

    /** {@code /genetics catalogue show|complete|flush}: show, discover everything, or forget every discovery. */
    private static final class CatalogueCommand extends AbstractPlayerCommand {
        private final RequiredArg<String> action = withRequiredArg("action", "server.genetics.commands.catalogue.action.desc", ArgTypes.STRING);

        CatalogueCommand() {
            super("catalogue", "server.genetics.commands.catalogue.desc");
            setPermissionGroups("hytale:Builder");
        }

        @Override
        protected void execute(@Nonnull CommandContext context, @Nonnull Store<EntityStore> store, @Nonnull Ref<EntityStore> ref,
                               @Nonnull PlayerRef playerRef, @Nonnull World world) {
            GeneJournal journal = Journal.of(ref, store);
            switch (action.get(context).toLowerCase()) {
                case "complete" -> {
                    long now = System.currentTimeMillis();
                    for (String species : com.genetics.journal.Catalogue.allSpecies()) {
                        String name = com.genetics.journal.Catalogue.speciesName(species, playerRef.getLanguage(), species);
                        journal.recordSample(species, name, new com.genetics.genome.Genome.Source(species, name, null,
                                com.genetics.journal.Catalogue.speciesIcon(species), now));
                        for (String key : com.genetics.journal.Catalogue.possibleGenes(species)) {
                            journal.addSpeciesGene(species, key);
                            if (key.startsWith(com.genetics.journal.Catalogue.EFFECT)) journal.addBuff(key.substring(com.genetics.journal.Catalogue.EFFECT.length()));
                        }
                        journal.markCompleted(species);
                    }
                    for (String mutation : com.genetics.codex.MutationEntry.getAssetMap().getAssetMap().keySet()) journal.addMutation(mutation);
                    context.sendMessage(Message.raw("Catalogue complete: " + journal.speciesCount() + " species, " + journal.getBuffs().size()
                            + " buffs, " + journal.getMutations().size() + " mutations. No Science was given (use /genetics science)."));
                }
                case "flush", "clear" -> {
                    journal.clearDiscoveries();
                    context.sendMessage(Message.raw("Catalogue emptied. Science (" + journal.getScience() + ") and research are kept."));
                }
                case "show" -> {
                    TreeSet<String> names = new TreeSet<>();
                    journal.getSpecies().values().forEach(entry -> names.add(entry.getName() + " x" + entry.getSamples()));
                    context.sendMessage(Message.raw("Catalogue: " + journal.speciesCount() + " species, " + journal.getBuffs().size() + " buffs, "
                            + journal.getMutations().size() + " mutations, " + journal.getScience() + " Science."));
                    if (!names.isEmpty()) context.sendMessage(Message.raw("Species: " + String.join(", ", names) + "."));
                }
                default -> context.sendMessage(Message.raw("Usage: /genetics catalogue show|complete|flush"));
            }
        }
    }

    @Nonnull
    private static String nodeList() {
        List<String> ids = new ArrayList<>();
        for (com.genetics.research.ResearchNode node : com.genetics.research.Research.all()) ids.add(node.getId());
        return String.join(", ", ids);
    }

    private static final class SetSickness extends AbstractPlayerCommand {
        private final RequiredArg<Float> value = withRequiredArg("value", "server.genetics.commands.sickness.value.desc", ArgTypes.FLOAT);

        SetSickness() {
            super("sickness", "server.genetics.commands.sickness.desc");
            setPermissionGroups("hytale:Builder");
        }

        @Override
        protected void execute(@Nonnull CommandContext context, @Nonnull Store<EntityStore> store, @Nonnull Ref<EntityStore> ref,
                               @Nonnull PlayerRef playerRef, @Nonnull World world) {
            float before = Sickness.get(store.getComponent(ref, EntityStatMap.getComponentType()));
            float after = Sickness.set(ref, store, value.get(context));
            context.sendMessage(Message.raw("Genetic Sickness: " + Math.round(before) + " -> " + Math.round(after) + "."));
        }
    }

    private static final class FireEvent extends AbstractPlayerCommand {
        private final RequiredArg<String> id = withRequiredArg("id", "server.genetics.commands.event.id.desc", ArgTypes.STRING);

        FireEvent() {
            super("event", "server.genetics.commands.event.desc");
            setPermissionGroups("hytale:Builder");
        }

        @Override
        protected void execute(@Nonnull CommandContext context, @Nonnull Store<EntityStore> store, @Nonnull Ref<EntityStore> ref,
                               @Nonnull PlayerRef playerRef, @Nonnull World world) {
            String wanted = id.get(context);
            SicknessEvent event = SicknessEvent.getAssetMap().getAsset(wanted);
            if (event == null) {
                for (SicknessEvent candidate : SicknessEvent.getAssetMap().getAssetMap().values()) {
                    if (candidate.getId().equalsIgnoreCase(wanted)) event = candidate;
                }
            }
            if (event == null) {
                context.sendMessage(Message.raw("Unknown sickness event '" + wanted + "'. Events: " + eventList() + "."));
                return;
            }
            SicknessSystem.trigger(ref, store, event);
            ActiveGenome active = store.getComponent(ref, ActiveGenome.getComponentType());
            Genome.Source source = active != null && active.getGenome() != null ? active.getGenome().source() : null;
            context.sendMessage(Message.raw("Fired sickness event " + event.getId() + (source != null
                    ? " (source mob: " + source.mobName() + ")."
                    : ". No source mob: inject a sample first for MobCall and Morph.")));
        }
    }

    private static final class ListEvents extends AbstractPlayerCommand {
        ListEvents() {
            super("events", "server.genetics.commands.events.desc");
            setPermissionGroups("hytale:Builder");
        }

        @Override
        protected void execute(@Nonnull CommandContext context, @Nonnull Store<EntityStore> store, @Nonnull Ref<EntityStore> ref,
                               @Nonnull PlayerRef playerRef, @Nonnull World world) {
            context.sendMessage(Message.raw("Sickness events: " + eventList() + "."));
        }
    }

    @Nonnull
    private static String eventList() {
        TreeSet<String> ids = new TreeSet<>();
        for (SicknessEvent event : SicknessEvent.getAssetMap().getAssetMap().values()) {
            ids.add(event.getId() + " (" + event.getMinBand() + ")");
        }
        return String.join(", ", ids);
    }
}
