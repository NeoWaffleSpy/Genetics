package com.genetics.labstation;

import com.genetics.codex.Codex;
import com.genetics.codex.MutationEntry;
import com.genetics.genome.Gene;
import com.genetics.genome.Genome;
import com.genetics.genome.InjectionRoller;
import com.genetics.interaction.Injection;
import com.genetics.genome.Translations;
import com.hypixel.hytale.server.core.asset.type.entityeffect.config.EntityEffect;
import com.hypixel.hytale.server.core.ui.builder.UICommandBuilder;

import javax.annotation.Nonnull;
import javax.annotation.Nullable;
import java.util.Locale;

/**
 * Renders a genome into the analysis area of the Lab Station screen: "Empty", or the source mob, whether the sample is
 * raw or processed, its Complexity, one row per stat gene and one card per effect or mutation gene.
 */
final class SampleView {
    private static final String STAT_ROW = "Genetics/StatRow.ui";
    private static final String EFFECT_CARD = "Genetics/EffectCard.ui";

    private SampleView() {
    }

    /**
     * @param used      Complexity of the viewer's active genome
     * @param capacity  the viewer's Genome Capacity
     */
    static void write(@Nonnull UICommandBuilder commands, @Nullable Genome genome, @Nullable String language,
                      double used, double capacity) {
        commands.clear("#StatList");
        commands.clear("#EffectList");
        commands.set("#EmptyLabel.Visible", genome == null);
        commands.set("#Details.Visible", genome != null);

        String icon = genome != null && genome.source() != null ? genome.source().icon() : null;
        commands.set("#MobIcon.Visible", icon != null);
        if (icon != null) commands.set("#MobIcon.AssetPath", icon);
        if (genome == null) return;

        String mobName = genome.source() != null ? genome.source().mobName() : "Unknown";
        boolean identified = genome.identified();
        commands.set("#MobName.Text", genome.displayName());
        commands.set("#Kind.Text", genome.processed() ? "Genetic material" : "Raw sample");
        commands.set("#Capacity.Text", "Your capacity: " + number(used) + " / " + number(capacity));
        commands.set("#Genes.Visible", identified);
        commands.set("#IdentifyPanel.Visible", !identified);
        if (!identified) {
            commands.set("#Complexity.Text", "Complexity ?");
            commands.set("#CapacityWarning.Visible", false);
            commands.set("#CapacityOk.Visible", false);
            return;
        }

        commands.set("#Complexity.Text", "Complexity " + number(genome.complexity()));
        double carried = Injection.lastingComplexity(genome);
        boolean over = carried > capacity;
        commands.set("#CapacityWarning.Visible", over);
        commands.set("#CapacityOk.Visible", !over);
        if (over) {
            commands.set("#CapacityWarning.Text", "Over your capacity (" + number(carried) + " / " + number(capacity)
                    + "): injecting it will degrade it and make you very sick.");
        } else {
            commands.set("#CapacityOk.Text", genome.processed() ? "Fits your capacity. Genetic material is injected exactly as it is."
                    : "Fits your capacity. " + Math.round(InjectionRoller.asIsChance(genome) * 100) + "% chance per gene to come out as-is.");
        }

        int stats = 0;
        int cards = 0;
        for (Gene gene : genome.genes()) {
            switch (gene) {
                case Gene.Stat stat -> {
                    String row = "#StatList[" + stats++ + "]";
                    String value = (stat.percent() > 0 ? "+" : "") + number(stat.percent()) + "%";
                    commands.append("#StatList", STAT_ROW);
                    commands.set(row + " #Name.Text", "Max " + prettify(stat.stat()));
                    commands.set(row + " #Value.Visible", !stat.isDrawback());
                    commands.set(row + " #Drawback.Visible", stat.isDrawback());
                    commands.set(row + (stat.isDrawback() ? " #Drawback.Text" : " #Value.Text"), value);
                }
                case Gene.Effect effect -> {
                    EntityEffect asset = EntityEffect.getAssetMap().getAsset(effect.effect());
                    String name = Translations.resolve(language, asset != null ? asset.getName() : null, prettify(effect.effect()));
                    String kind = effect.permanent() ? "Permanent" : "Temporary  ·  " + duration(effect.duration());
                    double complexity = com.genetics.genome.ComplexityCalculator.effect(effect);
                    card(commands, cards++, name, kind + "  ·  +" + number(complexity) + " Complexity",
                            asset != null ? asset.getStatusEffectIcon() : null);
                }
                case Gene.Mutation mutation -> {
                    MutationEntry entry = Codex.mutation(mutation.mutation());
                    String name = Translations.resolve(language, entry != null ? entry.getName() : null, prettify(mutation.mutation()));
                    String details = entry != null ? "Mutation  ·  +" + number(entry.getComplexity()) + " Complexity" + triggers(entry) : "Unknown mutation";
                    card(commands, cards++, name, details, null);
                }
            }
        }
        commands.set("#NoStats.Visible", stats == 0);
        commands.set("#NoEffects.Visible", cards == 0);
    }

    private static void card(@Nonnull UICommandBuilder commands, int index, @Nonnull String name, @Nonnull String details, @Nullable String icon) {
        String card = "#EffectList[" + index + "]";
        commands.append("#EffectList", EFFECT_CARD);
        commands.set(card + " #Name.Text", name);
        commands.set(card + " #Details.Text", details);
        commands.set(card + " #Icon.Visible", icon != null);
        if (icon != null) commands.set(card + " #Icon.AssetPath", icon);
    }

    /** "  ·  below 25% health, at night", or "" for a mutation without behaviors. */
    @Nonnull
    private static String triggers(@Nonnull MutationEntry entry) {
        java.util.List<String> parts = new java.util.ArrayList<>();
        for (com.genetics.mutation.Behavior behavior : entry.getBehaviors()) parts.add(behavior.describe());
        return parts.isEmpty() ? "" : "  ·  " + String.join(", ", parts);
    }

    @Nonnull
    private static String duration(double seconds) {
        long total = Math.round(seconds);
        return String.format(Locale.ROOT, "%d:%02d", total / 60, total % 60);
    }

    @Nonnull
    private static String prettify(@Nonnull String id) {
        return id.replace('_', ' ');
    }

    @Nonnull
    private static String number(double value) {
        return value == Math.rint(value) ? String.valueOf((long) value) : String.format(Locale.ROOT, "%.1f", value);
    }
}
