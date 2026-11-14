package com.genetics.journal;

import com.genetics.codex.Codex;
import com.genetics.codex.MobGeneProfile;
import com.genetics.genome.Gene;
import com.genetics.genome.GeneText;

import javax.annotation.Nonnull;
import javax.annotation.Nullable;
import java.util.ArrayList;
import java.util.Collections;
import java.util.HashMap;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
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

    // ---- Species: one per creature (design doc §9.4)

    /** Variant suffixes folded into their base creature, when the base is a creature too (e.g. Cow_Calf to Cow). */
    private static final List<String> VARIANT_SUFFIXES = List.of("_Patrol", "_Wander", "_Wandering", "_Surge", "_Anomaly", "_Ambush",
            "_Turret", "_Enrage", "_Calf", "_Piglet", "_Lamb", "_Kid", "_Foal", "_Chick", "_Orange", "_Pink", "_Phase_2", "_Feeding",
            "_Static", "_Spiderling");
    /** Variant prefixes folded the same way (Tamed_Cow, Coffer_Goblin_Miner, Dungeon_Goblin_Lobber). */
    private static final List<String> VARIANT_PREFIXES = List.of("Tamed_", "Coffer_", "Dungeon_");

    /** Creature roles grouped into species, rebuilt when the mob profiles change. */
    private record Index(int stamp, Map<String, String> speciesOfRole, Map<String, String> profileOfSpecies,
                         Map<String, List<String>> roles, List<String> species) {
    }

    private static volatile Index index;

    @Nonnull
    private static Index index() {
        // getAssetMap() wraps the map in a new view on every call, so fingerprint the profiles themselves (replaced on
        // asset reload), never the view: otherwise the index is rebuilt on every lookup.
        Map<String, MobGeneProfile> profiles = MobGeneProfile.getAssetMap().getAssetMap();
        int stamp = profiles.size();
        for (MobGeneProfile profile : profiles.values()) stamp += System.identityHashCode(profile);
        Index current = index;
        if (current != null && current.stamp() == stamp) return current;

        Set<String> all = new HashSet<>();
        for (MobGeneProfile profile : profiles.values()) {
            if (!profile.getId().equals(Codex.DEFAULT_ID)) all.addAll(List.of(profile.getRoles()));
        }
        Map<String, String> speciesOfRole = new HashMap<>();
        Map<String, String> profileOfSpecies = new LinkedHashMap<>();
        Map<String, List<String>> roles = new LinkedHashMap<>();
        List<MobGeneProfile> sorted = new ArrayList<>(profiles.values());
        sorted.sort(java.util.Comparator.comparing(MobGeneProfile::getId));
        for (MobGeneProfile profile : sorted) {
            if (profile.getId().equals(Codex.DEFAULT_ID)) continue;
            for (String role : profile.getRoles()) {
                String species = canonical(role, all);
                speciesOfRole.put(role, species);
                speciesOfRole.putIfAbsent(species, species);
                profileOfSpecies.putIfAbsent(species, profile.getId());
                roles.computeIfAbsent(species, k -> new ArrayList<>()).add(role);
            }
        }
        index = new Index(stamp, speciesOfRole, profileOfSpecies, roles, new ArrayList<>(profileOfSpecies.keySet()));
        return index;
    }

    /** The base creature of a role: variants (tamed, young, patrols, dungeon copies, boss phases) fold into it. */
    @Nonnull
    static String canonical(@Nonnull String role, @Nonnull Set<String> creatures) {
        if (role.startsWith("Goblin_Gobliterator")) return "Goblin_Gobliterator";
        if (role.equals("Dungeon_Feastmaster_Feeding")) return "Dungeon_Feastmaster_Boss";
        String r = role;
        boolean changed = true;
        while (changed) {
            changed = false;
            for (String prefix : VARIANT_PREFIXES) {
                if (r.startsWith(prefix) && creatures.contains(r.substring(prefix.length()))) {
                    r = r.substring(prefix.length());
                    changed = true;
                }
            }
            for (String suffix : VARIANT_SUFFIXES) {
                if (r.endsWith(suffix) && creatures.contains(r.substring(0, r.length() - suffix.length()))) {
                    r = r.substring(0, r.length() - suffix.length());
                    changed = true;
                }
            }
        }
        return r;
    }

    /**
     * The Catalogue species of an NPC role: the creature itself, with its variants (tamed, young, patrols, dungeon
     * copies, boss phases) folded into it. Null for roles without a gene profile, which pay no species Science.
     */
    @Nullable
    public static String speciesOf(@Nullable String role) {
        return role == null ? null : index().speciesOfRole().get(role);
    }

    /** Every species, grouped by gene profile. */
    @Nonnull
    public static List<String> allSpecies() {
        return Collections.unmodifiableList(index().species());
    }

    /** The NPC roles of a species (itself and its variants). */
    @Nonnull
    public static List<String> rolesOf(@Nonnull String species) {
        return index().roles().getOrDefault(species, List.of());
    }

    /** The gene profile of a species, a role, or a profile id (null if none). */
    @Nullable
    public static MobGeneProfile profileOf(@Nullable String id) {
        if (id == null) return null;
        MobGeneProfile named = MobGeneProfile.getAssetMap().getAsset(id);
        if (named != null) return named;
        String species = speciesOf(id);
        String profile = species != null ? index().profileOfSpecies().get(species) : null;
        return profile != null ? MobGeneProfile.getAssetMap().getAsset(profile) : null;
    }

    /** An icon for a species or profile: the model icon of the creature, else of its variants (null if none). */
    @Nullable
    public static String speciesIcon(@Nonnull String id) {
        MobGeneProfile profile = MobGeneProfile.getAssetMap().getAsset(id);
        List<String> candidates = new ArrayList<>();
        if (profile != null) candidates.addAll(List.of(profile.getRoles()));
        else {
            candidates.add(id);
            candidates.addAll(rolesOf(id));
        }
        MobGeneProfile family = profile != null ? profile : profileOf(id);
        for (String role : candidates) {
            String icon = family != null ? family.getIcon(role) : null;
            if (icon != null) return icon;
        }
        for (String role : candidates) {
            if (role.startsWith("Tamed_")) continue;
            String icon = com.genetics.genome.GenomeSampler.modelIcon(role);
            if (icon != null) return icon;
        }
        return null;
    }

    /**
     * A display name: a profile id gives the profile's Name ("Cattle"); a species gives the creature's name ("Cow"),
     * else the fallback.
     */
    @Nonnull
    public static String speciesName(@Nonnull String id, @Nullable String language, @Nonnull String fallback) {
        MobGeneProfile profile = MobGeneProfile.getAssetMap().getAsset(id);
        if (profile != null) return com.genetics.genome.Translations.resolve(language, profile.getName(), fallback);
        List<String> candidates = new ArrayList<>();
        candidates.add(id);
        candidates.addAll(rolesOf(id));
        for (String role : candidates) {
            String name = com.genetics.genome.Translations.resolve(language, "server.npcRoles." + role + ".name", "");
            if (!name.isBlank()) return name;
        }
        MobGeneProfile family = profileOf(id);
        if (family != null && rolesOf(id).stream().noneMatch(id::equals)) {
            return com.genetics.genome.Translations.resolve(language, family.getName(), GeneText.prettify(id));
        }
        return fallback.isBlank() || fallback.equals(id) ? GeneText.prettify(id) : fallback;
    }

    /** The genes a species (or a role, or a profile id) can carry. */
    @Nonnull
    public static Set<String> possibleGenes(@Nonnull String speciesId) {
        return possibleGenes(profileOf(speciesId));
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
