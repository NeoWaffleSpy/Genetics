package com.genetics.genome;

import com.genetics.codex.Codex;
import com.genetics.codex.MobGeneProfile;
import com.hypixel.hytale.component.CommandBuffer;
import com.hypixel.hytale.component.Ref;
import com.hypixel.hytale.server.core.asset.type.model.config.ModelAsset;
import com.hypixel.hytale.server.core.modules.entity.component.PersistentModel;
import com.hypixel.hytale.server.core.modules.entitystats.EntityStatMap;
import com.hypixel.hytale.server.core.modules.entitystats.EntityStatValue;
import com.hypixel.hytale.server.core.universe.world.storage.EntityStore;
import com.hypixel.hytale.server.npc.entities.NPCEntity;
import com.hypixel.hytale.server.npc.role.Role;

import javax.annotation.Nonnull;
import javax.annotation.Nullable;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.HashSet;
import java.util.List;
import java.util.Set;
import java.util.concurrent.ThreadLocalRandom;
import java.util.random.RandomGenerator;

/**
 * Draws a raw sample from a mob: rolls genes from the mob's {@link MobGeneProfile} (design doc §6). The values rolled
 * here are what the sample could give; the injection rolls its own outcome later.
 */
public final class GenomeSampler {
    private GenomeSampler() {
    }

    @Nonnull
    public static Genome sample(
            @Nonnull Ref<EntityStore> target, @Nonnull NPCEntity npc, @Nullable String language, @Nonnull CommandBuffer<EntityStore> commandBuffer,
            boolean identified
    ) {
        String role = npc.getRoleName();
        PersistentModel model = commandBuffer.getComponent(target, PersistentModel.getComponentType());
        String modelId = model != null ? model.getModelReference().getModelAssetId() : null;
        Genome.Source source = new Genome.Source(role, mobName(npc, language), modelId, modelIcon(modelId), System.currentTimeMillis());

        MobGeneProfile profile = Codex.profileFor(role);
        if (profile == null) return Genome.raw(source, List.of(), identified);

        boolean fallback = profile.getId().equals(MobGeneProfile.DEFAULT_ID) && !Arrays.asList(profile.getRoles()).contains(role);
        Set<String> mobStats = fallback ? statsWithMax(commandBuffer.getComponent(target, EntityStatMap.getComponentType())) : null;
        return Genome.raw(source, roll(profile, mobStats, ThreadLocalRandom.current()), identified);
    }

    /**
     * Rolls a profile's genes.
     *
     * @param onlyStats if not null, the stat pool is narrowed to these stats (used by the Default profile)
     */
    @Nonnull
    public static List<Gene> roll(@Nonnull MobGeneProfile profile, @Nullable Set<String> onlyStats, @Nonnull RandomGenerator random) {
        List<Gene> genes = new ArrayList<>();

        List<MobGeneProfile.StatRoll> pool = new ArrayList<>();
        for (MobGeneProfile.StatRoll roll : profile.getStatPool()) {
            if (roll.getStat() != null && roll.getWeight() > 0 && (onlyStats == null || onlyStats.contains(roll.getStat()))) pool.add(roll);
        }
        Set<String> rolledStats = new HashSet<>();
        for (int i = 0; i < profile.getStatRolls() && !pool.isEmpty(); i++) {
            MobGeneProfile.StatRoll roll = pool.remove(pickIndex(pool, MobGeneProfile.StatRoll::getWeight, random));
            genes.add(new Gene.Stat(roll.getStat(), round(between(roll.getMin(), roll.getMax(), random))));
            rolledStats.add(roll.getStat());
        }

        MobGeneProfile.EffectRoll temporary = profile.getTemporaryEffect();
        if (temporary != null && temporary.getId() != null) {
            genes.add(new Gene.Effect(temporary.getId(), false, temporary.getDuration()));
        }

        List<MobGeneProfile.EffectRoll> permanents = new ArrayList<>();
        for (MobGeneProfile.EffectRoll effect : profile.getPermanentEffects()) {
            if (effect.getId() != null && effect.getWeight() > 0) permanents.add(effect);
        }
        if (!permanents.isEmpty() && random.nextDouble() < profile.getPermanentEffectChance()) {
            MobGeneProfile.EffectRoll effect = permanents.get(pickIndex(permanents, MobGeneProfile.EffectRoll::getWeight, random));
            genes.add(new Gene.Effect(effect.getId(), true, 0));
        }

        for (MobGeneProfile.Drawback drawback : profile.getDrawbacks()) {
            String stat = drawback.getStat();
            if (stat == null || rolledStats.contains(stat) || (onlyStats != null && !onlyStats.contains(stat))) continue;
            if (random.nextDouble() < drawback.getChance()) {
                genes.add(new Gene.Stat(stat, -round(between(drawback.getMin(), drawback.getMax(), random))));
                rolledStats.add(stat);
            }
        }
        return genes;
    }

    private static <T> int pickIndex(@Nonnull List<T> items, @Nonnull java.util.function.ToDoubleFunction<T> weight, @Nonnull RandomGenerator random) {
        double total = 0;
        for (T item : items) total += weight.applyAsDouble(item);
        double pick = random.nextDouble() * total;
        for (int i = 0; i < items.size(); i++) {
            pick -= weight.applyAsDouble(items.get(i));
            if (pick < 0) return i;
        }
        return items.size() - 1;
    }

    private static double between(double min, double max, @Nonnull RandomGenerator random) {
        return max > min ? min + random.nextDouble() * (max - min) : min;
    }

    private static double round(double value) {
        return Math.round(value * 10) / 10.0;
    }

    @Nonnull
    private static Set<String> statsWithMax(@Nullable EntityStatMap stats) {
        Set<String> out = new HashSet<>();
        if (stats == null) return out;
        for (int i = 0; i < stats.size(); i++) {
            EntityStatValue stat = stats.get(i);
            if (stat != null && stat.getMax() > 0) out.add(stat.getId());
        }
        return out;
    }

    @Nullable
    private static String modelIcon(@Nullable String modelId) {
        ModelAsset asset = modelId != null ? ModelAsset.getAssetMap().getAsset(modelId) : null;
        return asset != null ? asset.getIcon() : null;
    }

    /** The mob's translated name (e.g. "Cow"), falling back to a prettified role id. */
    @Nonnull
    private static String mobName(@Nonnull NPCEntity npc, @Nullable String language) {
        Role role = npc.getRole();
        return Translations.resolve(language, role != null ? role.getNameTranslationKey() : null, npc.getRoleName().replace('_', ' '));
    }
}
