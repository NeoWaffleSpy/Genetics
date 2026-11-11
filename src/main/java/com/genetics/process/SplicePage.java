package com.genetics.process;

import com.genetics.genome.Gene;
import com.genetics.genome.GeneText;
import com.genetics.genome.Genome;
import com.genetics.interaction.Syringes;
import com.genetics.journal.Journal;
import com.genetics.tier.Materials;
import com.hypixel.hytale.codec.Codec;
import com.hypixel.hytale.codec.KeyedCodec;
import com.hypixel.hytale.codec.builder.BuilderCodec;
import com.hypixel.hytale.component.Ref;
import com.hypixel.hytale.component.Store;
import com.hypixel.hytale.protocol.packets.interface_.CustomPageLifetime;
import com.hypixel.hytale.protocol.packets.interface_.CustomUIEventBindingType;
import com.hypixel.hytale.server.core.Message;
import com.hypixel.hytale.server.core.entity.entities.player.pages.InteractiveCustomUIPage;
import com.hypixel.hytale.server.core.inventory.InventoryComponent;
import com.hypixel.hytale.server.core.inventory.ItemStack;
import com.hypixel.hytale.server.core.inventory.container.ItemContainer;
import com.hypixel.hytale.server.core.ui.builder.EventData;
import com.hypixel.hytale.server.core.ui.builder.UICommandBuilder;
import com.hypixel.hytale.server.core.ui.builder.UIEventBuilder;
import com.hypixel.hytale.server.core.universe.PlayerRef;
import com.hypixel.hytale.server.core.universe.world.storage.EntityStore;

import javax.annotation.Nonnull;
import javax.annotation.Nullable;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.ThreadLocalRandom;

import static com.hypixel.hytale.server.core.inventory.InventoryComponent.HOTBAR_SECTION_ID;
import static com.hypixel.hytale.server.core.inventory.InventoryComponent.STORAGE_SECTION_ID;

/**
 * The splicing screen (design doc §7), opened from a Lab Station T2 holding genetic material (A). The player picks a
 * second genetic material (B) from their inventory and sees, for every gene, the chance it comes from A, from B, or
 * is lost, plus the mutation chance and the cost (from the tier of the two inputs' average Complexity). Splicing puts
 * the result, a raw sample, in A's syringe and empties B's.
 */
public class SplicePage extends InteractiveCustomUIPage<SplicePage.EventPayload> {
    private static final String LAYOUT = "Genetics/Splice.ui";
    private static final String CANDIDATE_ROW = "Genetics/SpliceCandidateRow.ui";
    private static final String ODDS_ROW = "Genetics/SpliceOddsRow.ui";

    /** A genetic material in the player's inventory. */
    private record Candidate(@Nonnull ItemContainer container, short slot, @Nonnull ItemStack stack, @Nonnull Genome genome) {
    }

    private final Station station;
    private final ItemStack syringeA;
    private final Genome a;
    private final List<Candidate> candidates = new ArrayList<>();
    private int picked = -1;
    private boolean done;

    public SplicePage(@Nonnull PlayerRef playerRef, @Nonnull Station station, @Nonnull ItemStack syringeA, @Nonnull Genome a) {
        super(playerRef, CustomPageLifetime.CanDismiss, EventPayload.CODEC);
        this.station = station;
        this.syringeA = syringeA;
        this.a = a;
    }

    @Override
    public void build(@Nonnull Ref<EntityStore> ref, @Nonnull UICommandBuilder commands, @Nonnull UIEventBuilder events, @Nonnull Store<EntityStore> store) {
        commands.append(LAYOUT);
        events.addEventBinding(CustomUIEventBindingType.Activating, "#CancelButton", new EventData().append("Action", "Back"), false);
        events.addEventBinding(CustomUIEventBindingType.Activating, "#Panel #CloseButton", new EventData().append("Action", "Close"), false);
        events.addEventBinding(CustomUIEventBindingType.Activating, "#SpliceButton", new EventData().append("Action", "Splice"), false);
        commands.set("#NameA.Text", name(a));

        findCandidates(ref, store);
        commands.set("#NoCandidates.Visible", candidates.isEmpty());
        for (int i = 0; i < candidates.size(); i++) {
            commands.append("#Candidates", CANDIDATE_ROW);
            commands.set("#Candidates[" + i + "] #Name.Text", name(candidates.get(i).genome()));
            events.addEventBinding(CustomUIEventBindingType.Activating, "#Candidates[" + i + "] #Pick",
                    new EventData().append("Action", "Pick").append("Index", String.valueOf(i)), false);
        }
        render(commands, ref, store);
    }

    @Override
    public void handleDataEvent(@Nonnull Ref<EntityStore> ref, @Nonnull Store<EntityStore> store, @Nonnull EventPayload data) {
        if (done || data.action == null) return;
        switch (data.action) {
            case "Pick" -> {
                int index = parse(data.index);
                if (index < 0 || index >= candidates.size()) return;
                picked = index;
                UICommandBuilder commands = new UICommandBuilder();
                render(commands, ref, store);
                sendUpdate(commands, false);
            }
            case "Splice" -> splice(ref, store);
            case "Back" -> {
                done = true;
                station.reopen(ref, store, playerRef);
            }
            case "Close" -> {
                done = true;
                close();
            }
            default -> {
            }
        }
    }

    private void render(@Nonnull UICommandBuilder commands, @Nonnull Ref<EntityStore> ref, @Nonnull Store<EntityStore> store) {
        for (int i = 0; i < candidates.size(); i++) {
            commands.set("#Candidates[" + i + "] #Pick.Text", i == picked ? "Picked" : "Pick");
        }
        Candidate b = picked >= 0 ? candidates.get(picked) : null;
        commands.set("#PickHint.Visible", b == null);
        commands.set("#Result.Visible", b != null);
        commands.set("#SpliceButton.Disabled", b == null);
        if (b == null) return;

        Splicer.Plan plan = Splicer.plan(a, b.genome(), station.tier());
        String language = playerRef.getLanguage();
        commands.clear("#OddsList");
        for (int i = 0; i < plan.odds().size(); i++) {
            Splicer.Odds odds = plan.odds().get(i);
            String row = "#OddsList[" + i + "] ";
            commands.append("#OddsList", ODDS_ROW);
            Gene any = odds.fromA() != null ? odds.fromA() : odds.fromB();
            commands.set(row + "#Gene.Text", label(any, language));
            commands.set(row + "#FromA.Text", side(odds.fromA(), odds.chanceA(), language));
            commands.set(row + "#FromB.Text", side(odds.fromB(), odds.chanceB(), language));
            commands.set(row + "#Lost.Text", odds.chanceLost() > 0.001 ? percent(odds.chanceLost()) : "-");
        }
        commands.set("#Mutation.Text", "New mutation chance: " + percent(plan.mutationChance())
                + (hasMutation(a) || hasMutation(b.genome()) ? " (only if no mutation is inherited)" : ""));
        commands.set("#Tier.Text", "Average Complexity " + GeneText.number((a.complexity() + b.genome().complexity()) / 2)
                + "  ·  Tier: " + (plan.tier() != null ? plan.tier().getName() : "-") + "  ·  Result is a raw sample");
        String warning = plan.blocked();
        if (warning == null && !Materials.canPay(ref, store, plan.cost())) warning = "You don't carry enough materials.";
        commands.set("#Warning.Visible", warning != null);
        if (warning != null) commands.set("#Warning.Text", warning);
        CostView.write(commands, "#CostList", plan.cost(), ref, store);
        commands.set("#SpliceButton.Disabled", plan.blocked() != null || !Materials.canPay(ref, store, plan.cost()));
    }

    private void splice(@Nonnull Ref<EntityStore> ref, @Nonnull Store<EntityStore> store) {
        if (picked < 0) return;
        Candidate b = candidates.get(picked);
        Splicer.Plan plan = Splicer.plan(a, b.genome(), station.tier());
        if (plan.blocked() != null) return;
        // Both syringes must still be where they were when the screen opened.
        ItemStack currentA = station.container().getItemStack(Station.SAMPLE_SLOT);
        ItemStack currentB = b.container().getItemStack(b.slot());
        if (currentA == null || !currentA.equals(syringeA) || currentB == null || !currentB.equals(b.stack())) {
            playerRef.sendMessage(Message.raw("One of the samples has moved. Open the splicing screen again."));
            done = true;
            station.reopen(ref, store, playerRef);
            return;
        }
        if (!Materials.pay(ref, store, plan.cost())) {
            playerRef.sendMessage(Message.raw("You don't carry enough materials to splice these samples."));
            return;
        }
        done = true;
        Splicer.Result result = Splicer.splice(a, b.genome(), ThreadLocalRandom.current());
        b.container().setItemStackForSlot(b.slot(), Syringes.empty(currentB));
        station.container().setItemStackForSlot(Station.SAMPLE_SLOT, Syringes.fill(currentA, result.genome()));
        Journal.onIdentify(ref, store, result.genome());

        playerRef.sendMessage(Message.raw("Spliced into a raw sample: " + result.genome().genes().size() + " genes, Complexity "
                + GeneText.number(result.genome().complexity()) + ". Process it before splicing it again."));
        if (result.newMutation() != null) {
            playerRef.sendMessage(Message.raw("A new mutation appeared: " + GeneText.mutationName(result.newMutation(), playerRef.getLanguage()) + "!")
                    .color("#c58cf0"));
        }
        station.reopen(ref, store, playerRef);
    }

    /** Genetic materials in the player's hotbar and storage. */
    private void findCandidates(@Nonnull Ref<EntityStore> ref, @Nonnull Store<EntityStore> store) {
        candidates.clear();
        for (int section : new int[]{HOTBAR_SECTION_ID, STORAGE_SECTION_ID}) {
            InventoryComponent component = store.getComponent(ref, InventoryComponent.getComponentTypeById(section));
            ItemContainer container = component != null ? component.getInventory() : null;
            if (container == null) continue;
            for (short slot = 0; slot < container.getCapacity(); slot++) {
                ItemStack stack = container.getItemStack(slot);
                if (!Syringes.isSyringe(stack)) continue;
                Genome genome = Genome.read(stack);
                if (genome != null && genome.processed()) candidates.add(new Candidate(container, slot, stack, genome));
            }
        }
    }

    @Nonnull
    private static String name(@Nonnull Genome genome) {
        return (genome.source() != null ? genome.source().mobName() : "Unknown") + "'s Genes  ·  Complexity " + GeneText.number(genome.complexity());
    }

    private static boolean hasMutation(@Nonnull Genome genome) {
        return genome.genes().stream().anyMatch(g -> g instanceof Gene.Mutation);
    }

    /** What the gene modifies, without its value: "Max Health", "Night Vision (permanent)", "Mutation". */
    @Nonnull
    private static String label(@Nonnull Gene gene, @Nullable String language) {
        return switch (gene) {
            case Gene.Stat stat -> "Max " + GeneText.prettify(stat.stat());
            case Gene.Effect effect -> GeneText.effectName(effect.effect(), language) + (effect.permanent() ? " (permanent)" : "");
            case Gene.Mutation ignored -> "Mutation";
        };
    }

    /** One side's value and chance: "+6% 65%", or "-" if that side doesn't have the gene. */
    @Nonnull
    private static String side(@Nullable Gene gene, double chance, @Nullable String language) {
        if (gene == null || chance <= 0) return "-";
        String value = switch (gene) {
            case Gene.Stat stat -> (stat.percent() > 0 ? "+" : "") + GeneText.number(stat.percent()) + "%";
            case Gene.Effect effect -> effect.permanent() ? "yes" : GeneText.duration(effect.duration());
            case Gene.Mutation mutation -> GeneText.mutationName(mutation.mutation(), language);
        };
        return value + "  " + percent(chance);
    }

    @Nonnull
    private static String percent(double chance) {
        return Math.round(chance * 100) + "%";
    }

    private static int parse(String value) {
        try {
            return value != null ? Integer.parseInt(value.trim()) : -1;
        } catch (NumberFormatException e) {
            return -1;
        }
    }

    public static class EventPayload {
        public static final BuilderCodec<EventPayload> CODEC = BuilderCodec.builder(EventPayload.class, EventPayload::new)
                .append(new KeyedCodec<>("Action", Codec.STRING), (p, v) -> p.action = v, p -> p.action).add()
                .append(new KeyedCodec<>("Index", Codec.STRING), (p, v) -> p.index = v, p -> p.index).add()
                .build();

        private String action;
        private String index;
    }
}
