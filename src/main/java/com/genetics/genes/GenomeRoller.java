package com.genetics.genes;

import com.genetics.data.MobGeneProfile;
import com.genetics.data.MobGeneProfile.EffectRoll;
import com.genetics.data.MobGeneProfile.StatRoll;

import javax.annotation.Nonnull;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.random.RandomGenerator;

/** Rolls the genes of one sample from a {@link MobGeneProfile}. */
public final class GenomeRoller {
    private GenomeRoller() {
    }

    @Nonnull
    public static List<Gene> roll(@Nonnull MobGeneProfile profile, @Nonnull RandomGenerator random) {
        List<Gene> genes = new ArrayList<>();

        List<StatRoll> pool = new ArrayList<>(Arrays.asList(profile.getStats()));
        for (int i = 0; i < profile.getStatRolls() && !pool.isEmpty(); i++) {
            StatRoll picked = pickWeighted(pool, random);
            pool.remove(picked);
            genes.add(Gene.stat(picked.getStat(), rollRange(picked, random)));
        }

        EffectRoll temporary = profile.getTemporaryEffect();
        if (temporary != null) genes.add(Gene.temporaryEffect(temporary.getEffect(), temporary.getDuration()));

        for (EffectRoll permanent : profile.getPermanentEffects()) {
            if (random.nextDouble() < permanent.getChance()) genes.add(Gene.permanentEffect(permanent.getEffect()));
        }

        for (StatRoll drawback : profile.getDrawbacks()) {
            if (random.nextDouble() < drawback.getChance()) genes.add(Gene.stat(drawback.getStat(), rollRange(drawback, random)));
        }
        return genes;
    }

    @Nonnull
    private static StatRoll pickWeighted(@Nonnull List<StatRoll> pool, @Nonnull RandomGenerator random) {
        double total = pool.stream().mapToDouble(roll -> Math.max(0, roll.getWeight())).sum();
        if (total <= 0) return pool.get(random.nextInt(pool.size()));
        double target = random.nextDouble() * total;
        for (StatRoll roll : pool) {
            target -= Math.max(0, roll.getWeight());
            if (target < 0) return roll;
        }
        return pool.getLast();
    }

    /** A value in the roll's range, rounded to one decimal. */
    private static double rollRange(@Nonnull StatRoll roll, @Nonnull RandomGenerator random) {
        double value = roll.getMin() >= roll.getMax() ? roll.getMin() : random.nextDouble(roll.getMin(), roll.getMax());
        return Math.round(value * 10) / 10.0;
    }
}
