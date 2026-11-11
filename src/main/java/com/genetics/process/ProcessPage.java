package com.genetics.process;

import com.genetics.capacity.GenomeCapacity;
import com.genetics.genome.Gene;
import com.genetics.genome.GeneText;
import com.genetics.genome.Genome;
import com.genetics.interaction.Injection;
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
import com.hypixel.hytale.server.core.inventory.ItemStack;
import com.hypixel.hytale.server.core.ui.builder.EventData;
import com.hypixel.hytale.server.core.ui.builder.UICommandBuilder;
import com.hypixel.hytale.server.core.ui.builder.UIEventBuilder;
import com.hypixel.hytale.server.core.universe.PlayerRef;
import com.hypixel.hytale.server.core.universe.world.storage.EntityStore;

import javax.annotation.Nonnull;
import java.util.Arrays;

/**
 * The processing screen (design doc §7), opened from a Lab Station T2 holding an identified raw sample. Every gene
 * starts kept; the player drops the ones they don't want and sees the Complexity, tier and cost change. Processing
 * pays the cost and turns the sample in the station into genetic material.
 */
public class ProcessPage extends InteractiveCustomUIPage<ProcessPage.EventPayload> {
    private static final String LAYOUT = "Genetics/Process.ui";
    private static final String ROW = "Genetics/ProcessGeneRow.ui";

    private final Station station;
    private final ItemStack syringe;
    private final Genome sample;
    private final boolean[] keep;
    private boolean done;

    public ProcessPage(@Nonnull PlayerRef playerRef, @Nonnull Station station, @Nonnull ItemStack syringe, @Nonnull Genome sample) {
        super(playerRef, CustomPageLifetime.CanDismiss, EventPayload.CODEC);
        this.station = station;
        this.syringe = syringe;
        this.sample = sample;
        this.keep = new boolean[sample.genes().size()];
        Arrays.fill(keep, true);
    }

    @Override
    public void build(@Nonnull Ref<EntityStore> ref, @Nonnull UICommandBuilder commands, @Nonnull UIEventBuilder events, @Nonnull Store<EntityStore> store) {
        commands.append(LAYOUT);
        events.addEventBinding(CustomUIEventBindingType.Activating, "#CancelButton", new EventData().append("Action", "Back"), false);
        events.addEventBinding(CustomUIEventBindingType.Activating, "#Panel #CloseButton", new EventData().append("Action", "Close"), false);
        events.addEventBinding(CustomUIEventBindingType.Activating, "#ProcessButton", new EventData().append("Action", "Process"), false);
        commands.set("#SampleName.Text", sample.source() != null ? sample.source().mobName() + "'s Genes" : "Sample");
        for (int i = 0; i < sample.genes().size(); i++) {
            commands.append("#GeneList", ROW);
            events.addEventBinding(CustomUIEventBindingType.Activating, "#GeneList[" + i + "] #Toggle",
                    new EventData().append("Action", "Toggle").append("Gene", String.valueOf(i)), false);
        }
        render(commands, ref, store);
    }

    @Override
    public void handleDataEvent(@Nonnull Ref<EntityStore> ref, @Nonnull Store<EntityStore> store, @Nonnull EventPayload data) {
        if (done || data.action == null) return;
        switch (data.action) {
            case "Toggle" -> {
                int gene = parse(data.gene);
                if (gene < 0 || gene >= keep.length) return;
                keep[gene] = !keep[gene];
                UICommandBuilder commands = new UICommandBuilder();
                render(commands, ref, store);
                sendUpdate(commands, false);
            }
            case "Process" -> process(ref, store);
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
        String language = playerRef.getLanguage();
        for (int i = 0; i < sample.genes().size(); i++) {
            Gene gene = sample.genes().get(i);
            String row = "#GeneList[" + i + "] ";
            commands.set(row + "#Toggle.Text", keep[i] ? "Keep" : "Drop");
            commands.set(row + "#Name.Text", GeneText.describe(gene, language));
            commands.set(row + "#Name.Style.TextColor", !keep[i] ? "#5c6470" : isDrawback(gene) ? "#e06666" : "#d0d7e0");
            double complexity = GeneText.complexity(gene);
            commands.set(row + "#Cost.Text", (complexity >= 0 ? "+" : "") + GeneText.number(round(complexity)) + " Complexity");
        }

        Processing.Plan plan = Processing.plan(sample, keep, station.tier());
        Genome result = plan.result();
        double capacity = GenomeCapacity.capacity(ref, store);
        commands.set("#Complexity.Text", "Complexity " + (result != null ? GeneText.number(result.complexity()) : "-")
                + "  (was " + GeneText.number(sample.complexity()) + ")");
        commands.set("#Tier.Text", "Tier: " + (plan.tier() != null ? plan.tier().getName() : "-"));
        commands.set("#Capacity.Text", "Your capacity: " + GeneText.number(capacity));

        String warning = plan.blocked();
        if (warning == null && result != null && Injection.lastingComplexity(result) > capacity) {
            warning = "The result is over your capacity: injecting it would degrade it and make you very sick.";
        }
        if (warning == null && !Materials.canPay(ref, store, plan.cost())) warning = "You don't carry enough materials.";
        commands.set("#Warning.Visible", warning != null);
        if (warning != null) commands.set("#Warning.Text", warning);
        CostView.write(commands, "#CostList", plan.cost(), ref, store);
        commands.set("#ProcessButton.Disabled", plan.blocked() != null || result == null || !Materials.canPay(ref, store, plan.cost()));
    }

    private void process(@Nonnull Ref<EntityStore> ref, @Nonnull Store<EntityStore> store) {
        Processing.Plan plan = Processing.plan(sample, keep, station.tier());
        if (plan.result() == null || plan.blocked() != null) return;
        // Only process the syringe this screen was opened for, if it is still in the station.
        ItemStack current = station.container().getItemStack(Station.SAMPLE_SLOT);
        if (current == null || !current.equals(syringe)) {
            playerRef.sendMessage(Message.raw("The sample is no longer in the Lab Station."));
            done = true;
            close();
            return;
        }
        if (!Materials.pay(ref, store, plan.cost())) {
            playerRef.sendMessage(Message.raw("You don't carry enough materials to process this sample."));
            return;
        }
        done = true;
        station.container().setItemStackForSlot(Station.SAMPLE_SLOT, Syringes.fill(current, plan.result()));
        playerRef.sendMessage(Message.raw("Processed into genetic material: " + plan.result().genes().size() + " genes, Complexity "
                + GeneText.number(plan.result().complexity()) + "."));
        station.reopen(ref, store, playerRef);
    }

    private static boolean isDrawback(@Nonnull Gene gene) {
        return gene instanceof Gene.Stat stat && stat.isDrawback();
    }

    private static double round(double value) {
        return Math.round(value * 10) / 10.0;
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
                .append(new KeyedCodec<>("Gene", Codec.STRING), (p, v) -> p.gene = v, p -> p.gene).add()
                .build();

        private String action;
        private String gene;
    }
}
