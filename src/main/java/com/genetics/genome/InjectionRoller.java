package com.genetics.genome;

import javax.annotation.Nonnull;
import java.util.ArrayList;
import java.util.List;
import java.util.random.RandomGenerator;

/**
 * Rolls what an injection actually gives (design doc §6, "how raw injection quality works").
 * <p>
 * Genetic material (processed) is applied exactly as it is. In a raw sample, each positive gene rolls on its own:
 * <pre>
 * chance the gene comes out as-is = 1 / (1 + Complexity / C0)
 * otherwise: weakened, dropped, or turned into a drawback
 * </pre>
 * Drawbacks already in the sample always come through: there is no luck left to lose on them.
 */
public final class InjectionRoller {
    /** Complexity at which a gene has a 50% chance to come out as-is. */
    public static final double C0 = 20;
    /** When a gene doesn't come out as-is: chance it is weakened, dropped, or (stats only) turned into a drawback. */
    public static final double WEAKEN_CHANCE = 0.5;
    public static final double DROP_CHANCE = 0.3;
    /** A weakened or inverted stat keeps between these fractions of its value. */
    public static final double KEEP_MIN = 0.3;
    public static final double KEEP_MAX = 0.7;
    /** How long a permanent effect lasts when it comes out weakened: it becomes temporary. */
    public static final double WEAKENED_PERMANENT_DURATION = 180;

    public enum Outcome { AS_IS, WEAKENED, DROPPED, INVERTED }

    /** One gene of the sample and what became of it. {@code result} is null when the gene was dropped. */
    public record Roll(@Nonnull Gene original, @Nonnull Outcome outcome, Gene result) {
    }

    private InjectionRoller() {
    }

    public static double asIsChance(double complexity) {
        return 1 / (1 + Math.max(0, complexity) / C0);
    }

    @Nonnull
    public static List<Roll> roll(@Nonnull Genome sample, @Nonnull RandomGenerator random) {
        List<Roll> rolls = new ArrayList<>();
        double asIs = sample.processed() ? 1 : asIsChance(sample.complexity());
        for (Gene gene : sample.genes()) {
            boolean safe = gene instanceof Gene.Stat stat && stat.isDrawback();
            if (safe || random.nextDouble() < asIs) {
                rolls.add(new Roll(gene, Outcome.AS_IS, gene));
                continue;
            }
            double pick = random.nextDouble();
            Outcome outcome = pick < WEAKEN_CHANCE ? Outcome.WEAKENED : pick < WEAKEN_CHANCE + DROP_CHANCE ? Outcome.DROPPED : Outcome.INVERTED;
            rolls.add(apply(gene, outcome, random));
        }
        return rolls;
    }

    @Nonnull
    private static Roll apply(@Nonnull Gene gene, @Nonnull Outcome outcome, @Nonnull RandomGenerator random) {
        double keep = KEEP_MIN + random.nextDouble() * (KEEP_MAX - KEEP_MIN);
        return switch (gene) {
            case Gene.Stat stat -> switch (outcome) {
                case WEAKENED -> new Roll(gene, outcome, new Gene.Stat(stat.stat(), round(stat.percent() * keep)));
                case INVERTED -> new Roll(gene, outcome, new Gene.Stat(stat.stat(), -round(stat.percent() * keep)));
                default -> new Roll(gene, Outcome.DROPPED, null);
            };
            case Gene.Effect effect -> outcome == Outcome.WEAKENED
                    ? new Roll(gene, outcome, effect.permanent()
                    ? new Gene.Effect(effect.effect(), false, WEAKENED_PERMANENT_DURATION)
                    : new Gene.Effect(effect.effect(), false, Math.round(effect.duration() / 2)))
                    : new Roll(gene, Outcome.DROPPED, null);
            // A mutation is all or nothing.
            case Gene.Mutation mutation -> new Roll(gene, Outcome.DROPPED, null);
        };
    }

    private static double round(double value) {
        double rounded = Math.round(value * 10) / 10.0;
        return rounded == 0 ? Math.copySign(0.1, value) : rounded;
    }
}
