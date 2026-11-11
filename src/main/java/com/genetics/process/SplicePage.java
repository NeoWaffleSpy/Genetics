package com.genetics.process;

import com.genetics.genome.Gene;
import com.genetics.genome.GeneText;
import com.genetics.genome.Genome;
import com.genetics.interaction.Syringes;
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
 * second genetic material (B) from their inventory and sees every gene in A, in B and in the result (the two added
 * together), with a tickbox per gene to leave it out (required when the same buff is kept at two tiers), plus the
 * mutation chance and the cost (from the tier of the two inputs' average Complexity). Splicing puts the result, an
 * unidentified raw sample, in A's syringe and empties B's.
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
    private boolean[] keep = new boolean[0];
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
                UIEventBuilder events = new UIEventBuilder();
                buildLines(commands, events);
                render(commands, ref, store);
                sendUpdate(commands, events, false);
            }
            case "Toggle" -> {
                int line = parse(data.index);
                if (line < 0 || line >= keep.length) return;
                keep[line] = data.checked != null ? data.checked : !keep[line];
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

        Splicer.Plan plan = Splicer.plan(a, b.genome(), station.tier(), keep);
        String language = playerRef.getLanguage();
        for (int i = 0; i < plan.lines().size(); i++) {
            Splicer.Line line = plan.lines().get(i);
            String row = "#OddsList[" + i + "] ";
            Gene any = line.fromA() != null ? line.fromA() : line.fromB();
            commands.set(row + "#Gene.Text", label(any, language));
            commands.set(row + "#Gene.Style.TextColor", line.kept() ? "#d0d7e0" : "#5c6470");
            commands.set(row + "#FromA.Text", value(line.fromA(), language));
            commands.set(row + "#FromB.Text", value(line.fromB(), language));
            commands.set(row + "#Result.Text", line.kept() ? value(line.result(), language) : "dropped");
        }
        Genome result = plan.result();
        commands.set("#Mutation.Text", plan.mutationChance() > 0 ? "New mutation chance: " + percent(plan.mutationChance())
                : "The mutation is inherited, so no new one can appear.");
        commands.set("#Tier.Text", "Result: " + result.title() + "  ·  Complexity " + GeneText.number(result.complexity())
                + "  ·  Cost tier: " + (plan.tier() != null ? plan.tier().getName() : "-"));
        String warning = plan.blocked() != null ? plan.blocked() : plan.conflict();
        double capacity = com.genetics.capacity.GenomeCapacity.capacity(ref, store);
        if (warning == null && com.genetics.interaction.Injection.lastingComplexity(result) > capacity) {
            warning = "The result is over your capacity (" + GeneText.number(capacity) + "): injecting it would degrade it and make you very sick.";
        }
        if (warning == null && !Materials.canPay(ref, store, plan.cost())) warning = "You don't carry enough materials.";
        commands.set("#Warning.Visible", warning != null);
        if (warning != null) commands.set("#Warning.Text", warning);
        CostView.write(commands, "#CostList", plan.cost(), ref, store);
        commands.set("#SpliceButton.Disabled", plan.blocked() != null || plan.conflict() != null || plan.result().genes().isEmpty()
                || !Materials.canPay(ref, store, plan.cost()));
    }

    private void splice(@Nonnull Ref<EntityStore> ref, @Nonnull Store<EntityStore> store) {
        if (picked < 0) return;
        Candidate b = candidates.get(picked);
        Splicer.Plan plan = Splicer.plan(a, b.genome(), station.tier(), keep);
        if (plan.blocked() != null || plan.conflict() != null || plan.result().genes().isEmpty()) return;
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
        Splicer.Result result = Splicer.splice(a, b.genome(), keep, ThreadLocalRandom.current());
        b.container().setItemStackForSlot(b.slot(), Syringes.empty(currentB));
        station.container().setItemStackForSlot(Station.SAMPLE_SLOT, Syringes.fill(currentA, result.genome()));

        playerRef.sendMessage(Message.raw("Spliced into an unidentified sample. Process it to reveal the result."));
        if (result.newMutation() != null) {
            playerRef.sendMessage(Message.raw("The splice reacted strangely... something new may have formed.").color("#c58cf0"));
        }
        station.reopen(ref, store, playerRef);
    }

    /** One row (with a ticked keep box) per gene line of the picked pair. */
    private void buildLines(@Nonnull UICommandBuilder commands, @Nonnull UIEventBuilder events) {
        commands.clear("#OddsList");
        Candidate b = picked >= 0 ? candidates.get(picked) : null;
        int count = b != null ? Splicer.plan(a, b.genome(), station.tier()).lines().size() : 0;
        keep = new boolean[count];
        java.util.Arrays.fill(keep, true);
        for (int i = 0; i < count; i++) {
            String toggle = "#OddsList[" + i + "] #Toggle";
            commands.append("#OddsList", ODDS_ROW);
            commands.set(toggle + ".Value", true);
            events.addEventBinding(CustomUIEventBindingType.ValueChanged, toggle,
                    new EventData().append("Action", "Toggle").append("Index", String.valueOf(i)).append("@Checked", toggle + ".Value"), false);
        }
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
        return genome.title() + "  ·  Complexity " + GeneText.number(genome.complexity());
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

    /** A gene's value: "+6%", "2:00", "yes" for a permanent effect, the mutation's name, or "-" if absent. */
    @Nonnull
    private static String value(@Nullable Gene gene, @Nullable String language) {
        if (gene == null) return "-";
        return switch (gene) {
            case Gene.Stat stat -> (stat.percent() > 0 ? "+" : "") + GeneText.number(stat.percent()) + "%";
            case Gene.Effect effect -> effect.permanent() ? "yes" : GeneText.duration(effect.duration());
            case Gene.Mutation mutation -> GeneText.mutationName(mutation.mutation(), language);
        };
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
                .append(new KeyedCodec<>("@Checked", Codec.BOOLEAN), (p, v) -> p.checked = v, p -> p.checked).add()
                .build();

        private String action;
        private String index;
        private Boolean checked;
    }
}
