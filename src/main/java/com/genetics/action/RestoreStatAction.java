package com.genetics.action;

import com.hypixel.hytale.codec.Codec;
import com.hypixel.hytale.codec.KeyedCodec;
import com.hypixel.hytale.codec.builder.BuilderCodec;
import com.hypixel.hytale.server.core.modules.entitystats.EntityStatMap;
import com.hypixel.hytale.server.core.modules.entitystats.EntityStatValue;
import com.hypixel.hytale.server.core.modules.entitystats.asset.EntityStatType;

import javax.annotation.Nonnull;

/** {@code { "Type": "RestoreStat", "Stat": "Mana", "Fraction": 0.2 }}: restores a fraction of a stat's maximum. */
public class RestoreStatAction extends Action {
    public static final String TYPE = "RestoreStat";

    public static final BuilderCodec<RestoreStatAction> CODEC = BuilderCodec.builder(RestoreStatAction.class, RestoreStatAction::new, BASE_CODEC)
            .append(new KeyedCodec<>("Stat", Codec.STRING), (t, v) -> t.stat = v, t -> t.stat).add()
            .append(new KeyedCodec<>("Fraction", Codec.DOUBLE), (t, v) -> t.fraction = v, t -> t.fraction)
            .documentation("Fraction of the stat's maximum to restore, e.g. 0.2.").add()
            .build();

    private String stat = "Health";
    private double fraction;

    @Override
    public void run(@Nonnull ActionContext context) {
        restore(context, stat, fraction);
    }

    /** Adds a fraction of a stat's maximum to it. */
    public static void restore(@Nonnull ActionContext context, @Nonnull String statId, double fraction) {
        EntityStatMap stats = context.buffer().getComponent(context.entity(), EntityStatMap.getComponentType());
        int index = EntityStatType.getAssetMap().getIndex(statId);
        if (stats == null || index == Integer.MIN_VALUE) return;
        EntityStatValue value = stats.get(index);
        if (value != null) stats.addStatValue(index, (float) (value.getMax() * fraction));
    }

    /** Adds a flat amount to a stat. */
    public static void add(@Nonnull ActionContext context, @Nonnull String statId, double amount) {
        EntityStatMap stats = context.buffer().getComponent(context.entity(), EntityStatMap.getComponentType());
        int index = EntityStatType.getAssetMap().getIndex(statId);
        if (stats != null && index != Integer.MIN_VALUE && stats.get(index) != null) stats.addStatValue(index, (float) amount);
    }
}
