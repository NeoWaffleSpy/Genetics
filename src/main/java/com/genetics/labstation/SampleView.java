package com.genetics.labstation;

import com.genetics.data.MutationDefinition;
import com.genetics.genes.Gene;
import com.genetics.genes.GeneSample;
import com.genetics.genes.Translations;
import com.hypixel.hytale.server.core.asset.type.entityeffect.config.EntityEffect;
import com.hypixel.hytale.server.core.ui.builder.UICommandBuilder;

import javax.annotation.Nonnull;
import javax.annotation.Nullable;
import java.util.Locale;

/**
 * Renders a gene sample into the analysis area of the Lab Station screen: "Empty", or the source's portrait and name,
 * the sample's kind and Complexity, one row per stat gene, and one card per effect gene or mutation.
 */
final class SampleView {
    private static final String STAT_ROW = "Genetics/StatRow.ui";
    private static final String EFFECT_CARD = "Genetics/EffectCard.ui";

    private SampleView() {
    }

    static void write(@Nonnull UICommandBuilder commands, @Nullable GeneSample sample, @Nullable String language) {
        commands.clear("#StatList");
        commands.clear("#EffectList");
        commands.set("#EmptyLabel.Visible", sample == null);
        commands.set("#Details.Visible", sample != null);

        String icon = sample != null ? sample.getSource().getIcon() : null;
        commands.set("#MobIcon.Visible", icon != null);
        if (icon != null) commands.set("#MobIcon.AssetPath", icon);
        if (sample == null) return;

        commands.set("#MobName.Text", sample.getSource().getName() + "'s Genes");
        commands.set("#SampleInfo.Text", (sample.isProcessed() ? "Genetic material" : "Raw sample")
                + "  ·  Complexity " + (sample.isComplexityKnown() ? number(sample.getComplexity()) : "not calculated"));

        int stats = 0;
        int cards = 0;
        for (Gene gene : sample.getGenes()) {
            if (gene.getType() == Gene.Type.Stat) {
                String row = "#StatList[" + stats++ + "]";
                commands.append("#StatList", STAT_ROW);
                commands.set(row + " #Name.Text", Translations.prettify(gene.getId()));
                commands.set(row + " #Value.Text", (gene.getValue() >= 0 ? "+" : "") + number(gene.getValue()) + "%");
            } else {
                String card = "#EffectList[" + cards++ + "]";
                commands.append("#EffectList", EFFECT_CARD);
                writeCard(commands, card, gene, language);
            }
        }
        commands.set("#NoStats.Visible", stats == 0);
        commands.set("#NoEffects.Visible", cards == 0);
    }

    private static void writeCard(@Nonnull UICommandBuilder commands, @Nonnull String card, @Nonnull Gene gene, @Nullable String language) {
        String name;
        String details;
        String icon = null;
        if (gene.getType() == Gene.Type.Effect) {
            EntityEffect effect = EntityEffect.getAssetMap().getAsset(gene.getId());
            name = Translations.resolve(language, effect != null ? effect.getName() : null, Translations.prettify(gene.getId()));
            details = gene.isPermanent() ? "Permanent effect" : "Temporary effect  ·  " + number(gene.getDuration()) + "s";
            icon = effect != null ? effect.getStatusEffectIcon() : null;
        } else {
            MutationDefinition mutation = MutationDefinition.getAssetMap().getAsset(gene.getId());
            name = Translations.resolve(language, mutation != null ? mutation.getName() : null, Translations.prettify(gene.getId()));
            details = "Mutation";
        }

        commands.set(card + " #Name.Text", name);
        commands.set(card + " #Details.Text", details);
        commands.set(card + " #Icon.Visible", icon != null);
        if (icon != null) commands.set(card + " #Icon.AssetPath", icon);
    }

    @Nonnull
    private static String number(double value) {
        return value == Math.rint(value) ? String.valueOf((long) value) : String.format(Locale.ROOT, "%.1f", value);
    }
}
