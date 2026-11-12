package com.genetics.labstation;

import com.genetics.genome.GeneText;
import com.genetics.tier.Materials;
import com.hypixel.hytale.component.ComponentAccessor;
import com.hypixel.hytale.component.Ref;
import com.hypixel.hytale.server.core.inventory.MaterialQuantity;
import com.hypixel.hytale.server.core.ui.Anchor;
import com.hypixel.hytale.server.core.ui.Value;
import com.hypixel.hytale.server.core.ui.builder.UICommandBuilder;
import com.hypixel.hytale.server.core.universe.world.storage.EntityStore;

import javax.annotation.Nonnull;
import java.util.List;

/** Pieces shared by the Lab Station tabs: the capacity gauge, gene rows and cost chips (design: Genetics identity). */
final class Widgets {
    static final String GENE_ROW = "Genetics/GeneRow.ui";
    static final String COST_CHIP = "Genetics/CostChip.ui";

    static final String STRONG = "#eaf1f4";
    static final String TEXT = "#a9b9c7";
    static final String FAINT = "#66788a";
    static final String TEAL = "#3ec9a7";
    static final String GLOW = "#8ff5dc";
    static final String AMBER = "#f0b54a";
    static final String GOOD = "#8fd17a";
    static final String BAD = "#e86a6a";
    static final String VIOLET = "#b78cf2";

    /** Gene types, matching the #Icon&lt;Type&gt; groups of the row templates. */
    static final List<String> TYPES = List.of("Stat", "Drawback", "Effect", "Mutation");

    /** Inner width of the capacity tracks in pixels (panel 1000 - 2 x 17 padding - 190 rail - 2 x 12 padding - 2 outline). */
    static final String DETAIL_ROW = "Genetics/DetailRow.ui";
    private static final int TRACK = 750;

    private Widgets() {
    }

    /**
     * Fills a capacity gauge ({@code #<p>CapNumbers}, {@code #<p>CapFill}, {@code #<p>CapOver}, {@code #<p>CapLegend}):
     * this genome against the player's capacity, the excess in red.
     */
    static void capacity(@Nonnull UICommandBuilder c, @Nonnull String p, double complexity, double capacity, @Nonnull String label) {
        boolean over = complexity > capacity;
        double excess = complexity - capacity;
        c.set("#" + p + "CapNumbers.Text", GeneText.number(round(complexity)) + " / " + GeneText.number(round(capacity))
                + (over ? "  ·  over by " + GeneText.number(round(excess)) : ""));
        c.set("#" + p + "CapNumbers.Style.TextColor", over ? BAD : STRONG);
        int fill;
        int extra;
        if (capacity <= 0) {
            fill = 0;
            extra = complexity > 0 ? TRACK : 0;
        } else if (!over) {
            fill = (int) Math.round(TRACK * Math.max(0, complexity) / capacity);
            extra = 0;
        } else {
            fill = (int) Math.round(TRACK * capacity / complexity);
            extra = TRACK - fill;
        }
        c.setObject("#" + p + "CapFill.Anchor", width(fill));
        c.setObject("#" + p + "CapOver.Anchor", width(extra));
        c.set("#" + p + "CapLegend.Text", label + " " + GeneText.number(round(complexity))
                + (over ? "     Over capacity " + GeneText.number(round(excess)) : "     Free " + GeneText.number(round(capacity - complexity))));
    }

    /**
     * Fills a gene row (GeneRow.ui).
     *
     * @param kept      false shows the gene faded (dropped)
     * @param toggle    show the keep tickbox
     */
    static void geneRow(@Nonnull UICommandBuilder c, @Nonnull String row, @Nonnull String type, @Nonnull String name, @Nonnull String value,
                        @Nonnull String complexity, boolean kept, boolean toggle) {
        c.set(row + " #ToggleBox.Visible", toggle);
        typeIcon(c, row, type);
        c.set(row + " #Name.Text", name);
        c.set(row + " #Name.Style.TextColor", kept ? STRONG : FAINT);
        c.set(row + " #Value.Text", value);
        c.set(row + " #Value.Style.TextColor", kept ? valueColor(type) : FAINT);
        c.set(row + " #Complexity.Text", complexity);
        c.set(row + " #Complexity.Style.TextColor", kept ? AMBER : FAINT);
    }

    /** Shows the row's icon for this gene type (Stat, Drawback, Effect, Mutation), hides the others. */
    static void typeIcon(@Nonnull UICommandBuilder c, @Nonnull String row, @Nonnull String type) {
        for (String t : TYPES) c.set(row + " #Icon" + t + ".Visible", t.equals(type));
    }

    @Nonnull
    static String valueColor(@Nonnull String type) {
        return switch (type) {
            case "Drawback" -> BAD;
            case "Effect" -> GLOW;
            case "Mutation" -> VIOLET;
            default -> GOOD;
        };
    }

    /** Writes a cost as chips (icon, item name, have / need) into a list group. */
    static void costs(@Nonnull UICommandBuilder c, @Nonnull String list, @Nonnull List<MaterialQuantity> cost,
                      @Nonnull Ref<EntityStore> ref, @Nonnull ComponentAccessor<EntityStore> accessor) {
        c.clear(list);
        boolean free = Materials.isFree(ref, accessor);
        for (int i = 0; i < cost.size(); i++) {
            MaterialQuantity material = cost.get(i);
            String entry = list + "[" + i + "] ";
            c.append(list, COST_CHIP);
            c.set(entry + "#Icon.Visible", material.getItemId() != null);
            if (material.getItemId() != null) c.set(entry + "#Icon.ItemId", material.getItemId());
            c.set(entry + "#Item.TextSpans", Materials.name(material));
            int have = Materials.carried(ref, accessor, material);
            c.set(entry + "#Amount.Text", have + " / " + material.getQuantity());
            c.set(entry + "#Amount.Style.TextColor", free || have >= material.getQuantity() ? GOOD : BAD);
        }
    }

    @Nonnull
    static Anchor width(int pixels) {
        Anchor anchor = new Anchor();
        anchor.setWidth(Value.of(Math.max(0, pixels)));
        return anchor;
    }

    static double round(double value) {
        return Math.round(value * 10) / 10.0;
    }
}
