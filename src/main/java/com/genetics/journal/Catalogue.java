package com.genetics.journal;

import com.genetics.codex.Codex;
import com.genetics.codex.MobGeneProfile;
import com.genetics.genome.Gene;
import com.genetics.genome.GeneText;

import javax.annotation.Nonnull;
import javax.annotation.Nullable;
import java.util.LinkedHashSet;
import java.util.Set;

/**
 * Catalogue keys and lookups (design doc §9.4). A Catalogue key names what a gene is, not its value:
 * {@code Stat:Health}, {@code Drawback:Mana}, {@code Effect:Meat_Buff_T1} (temporary and permanent alike) or
 * {@code Mutation:AdrenalSurge}.
 */
public final class Catalogue {
    public static final String STAT = "Stat:";
    public static final String DRAWBACK = "Drawback:";
    public static final String EFFECT = "Effect:";
    public static final String MUTATION = "Mutation:";

    private Catalogue() {
    }

    @Nonnull
    public static String key(@Nonnull Gene gene) {
        return switch (gene) {
            case Gene.Stat stat -> (stat.isDrawback() ? DRAWBACK : STAT) + stat.stat();
            case Gene.Effect effect -> EFFECT + effect.effect();
            case Gene.Mutation mutation -> MUTATION + mutation.mutation();
        };
    }

    /** Every gene a species can carry, as Catalogue keys, from its mob profile (empty if it has none). */
    @Nonnull
    public static Set<String> possibleGenes(@Nullable MobGeneProfile profile) {
        Set<String> keys = new LinkedHashSet<>();
        if (profile == null) return keys;
        for (MobGeneProfile.StatRoll roll : profile.getStatPool()) if (roll.getStat() != null) keys.add(STAT + roll.getStat());
        if (profile.getTemporaryEffect() != null && profile.getTemporaryEffect().getId() != null) keys.add(EFFECT + profile.getTemporaryEffect().getId());
        for (MobGeneProfile.EffectRoll effect : profile.getPermanentEffects()) if (effect.getId() != null) keys.add(EFFECT + effect.getId());
        for (MobGeneProfile.Drawback drawback : profile.getDrawbacks()) if (drawback.getStat() != null) keys.add(DRAWBACK + drawback.getStat());
        return keys;
    }

    /** The genes a sampled species (by NPC role) can carry. */
    @Nonnull
    public static Set<String> possibleGenes(@Nonnull String speciesId) {
        return possibleGenes(Codex.profileFor(speciesId));
    }

    /** "Max Health", "Max Mana (drawback)", the effect's or mutation's name. */
    @Nonnull
    public static String name(@Nonnull String key, @Nullable String language) {
        if (key.startsWith(STAT)) return "Max " + GeneText.prettify(key.substring(STAT.length()));
        if (key.startsWith(DRAWBACK)) return "Max " + GeneText.prettify(key.substring(DRAWBACK.length()));
        if (key.startsWith(EFFECT)) return GeneText.effectName(key.substring(EFFECT.length()), language);
        if (key.startsWith(MUTATION)) return GeneText.mutationName(key.substring(MUTATION.length()), language);
        return key;
    }

    /** The row type of a key: Stat, Drawback, Effect or Mutation. */
    @Nonnull
    public static String type(@Nonnull String key) {
        int colon = key.indexOf(':');
        return colon > 0 ? key.substring(0, colon) : "Stat";
    }

    /** The value range a profile gives for a key: "2–4%", "3:00", "permanent", or "". */
    @Nonnull
    public static String range(@Nullable MobGeneProfile profile, @Nonnull String key) {
        if (profile == null) return "";
        if (key.startsWith(STAT)) {
            String stat = key.substring(STAT.length());
            for (MobGeneProfile.StatRoll roll : profile.getStatPool()) {
                if (stat.equals(roll.getStat())) return GeneText.number(roll.getMin()) + "–" + GeneText.number(roll.getMax()) + "%";
            }
        } else if (key.startsWith(DRAWBACK)) {
            String stat = key.substring(DRAWBACK.length());
            for (MobGeneProfile.Drawback drawback : profile.getDrawbacks()) {
                if (stat.equals(drawback.getStat())) return "-" + GeneText.number(drawback.getMin()) + "–" + GeneText.number(drawback.getMax()) + "%";
            }
        } else if (key.startsWith(EFFECT)) {
            String effect = key.substring(EFFECT.length());
            if (profile.getTemporaryEffect() != null && effect.equals(profile.getTemporaryEffect().getId())) {
                return GeneText.duration(profile.getTemporaryEffect().getDuration());
            }
            return "permanent";
        }
        return "";
    }
}
