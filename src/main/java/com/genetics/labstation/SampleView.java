package com.genetics.labstation;

import com.hypixel.hytale.server.core.ui.builder.UICommandBuilder;
import org.bson.BsonArray;
import org.bson.BsonDocument;
import org.bson.BsonValue;

import javax.annotation.Nonnull;
import javax.annotation.Nullable;
import java.util.Locale;
import java.util.Map;

/**
 * Renders a gene sample into the analysis area of the Lab Station screen: "Empty", or the mob's portrait and name,
 * each stat's max value, and one card per status effect.
 */
final class SampleView {
    private static final String STAT_ROW = "Genetics/StatRow.ui";
    private static final String EFFECT_CARD = "Genetics/EffectCard.ui";

    private SampleView() {
    }

    static void write(@Nonnull UICommandBuilder commands, @Nullable BsonDocument sample) {
        commands.clear("#StatList");
        commands.clear("#EffectList");
        commands.set("#EmptyLabel.Visible", sample == null);
        commands.set("#Details.Visible", sample != null);

        BsonDocument model = sample != null && sample.isDocument("Model") ? sample.getDocument("Model") : null;
        String icon = model != null ? string(model, "Icon", null) : null;
        commands.set("#MobIcon.Visible", icon != null);
        if (icon != null) commands.set("#MobIcon.AssetPath", icon);
        if (sample == null) return;

        commands.set("#MobName.Text", string(sample, "MobName", "Unknown") + "'s Genes");

        if (sample.isDocument("Stats")) {
            int i = 0;
            for (Map.Entry<String, BsonValue> stat : sample.getDocument("Stats").entrySet()) {
                if (!stat.getValue().isDocument()) continue;
                BsonDocument values = stat.getValue().asDocument();
                // Stats with no maximum (e.g. Ammo on a mob) carry no information.
                if (!values.isNumber("Max") || values.getNumber("Max").doubleValue() <= 0) continue;
                String row = "#StatList[" + i++ + "]";
                commands.append("#StatList", STAT_ROW);
                commands.set(row + " #Name.Text", stat.getKey());
                commands.set(row + " #Value.Text", number(values, "Max"));
            }
        }

        BsonArray effects = sample.isArray("Effects") ? sample.getArray("Effects") : new BsonArray();
        commands.set("#NoEffects.Visible", effects.isEmpty());
        int i = 0;
        for (BsonValue value : effects) {
            if (!value.isDocument()) continue;
            BsonDocument effect = value.asDocument();
            String card = "#EffectList[" + i++ + "]";
            commands.append("#EffectList", EFFECT_CARD);
            commands.set(card + " #Name.Text", string(effect, "Name", string(effect, "Id", "Unknown")));
            commands.set(card + " #Details.Text", describeEffect(effect));

            String effectIcon = string(effect, "Icon", null);
            commands.set(card + " #Icon.Visible", effectIcon != null);
            if (effectIcon != null) commands.set(card + " #Icon.AssetPath", effectIcon);
        }
    }

    @Nonnull
    private static String describeEffect(@Nonnull BsonDocument effect) {
        String kind = effect.isBoolean("Debuff") && effect.getBoolean("Debuff").getValue() ? "Debuff" : "Buff";
        if (effect.isBoolean("Infinite") && effect.getBoolean("Infinite").getValue()) return kind + "  ·  Infinite";
        return kind + "  ·  " + number(effect, "RemainingDuration") + "s left of " + number(effect, "InitialDuration") + "s";
    }

    @Nullable
    static String string(@Nonnull BsonDocument doc, @Nonnull String key, @Nullable String fallback) {
        return doc.isString(key) ? doc.getString(key).getValue() : fallback;
    }

    @Nonnull
    private static String number(@Nonnull BsonDocument doc, @Nonnull String key) {
        if (!doc.isNumber(key)) return "-";
        double value = doc.getNumber(key).doubleValue();
        return value == Math.rint(value) ? String.valueOf((long) value) : String.format(Locale.ROOT, "%.1f", value);
    }
}
