package com.genetics.genome;

import com.genetics.codex.Balance;

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
 * Drawbacks already in the sample always come through: there is no luck left to lose on them. All numbers come from
 * the {@link Balance} file.
 */
public final class InjectionRoller {
    public enum Outcome { AS_IS, WEAKENED, DROPPED, INVERTED }

    /** One gene of the sample and what became of it. {@code result} is null when the gene was dropped. */
    public record Roll(@Nonnull Gene original, @Nonnull Outcome outcome, Gene result) {
    }

    private InjectionRoller() {
    }

    /** Chance that each positive gene of a raw sample with this Complexity comes out as-is. */
    public static double asIsChance(double complexity) {
        double c0 = Balance.get().injection().getC0();
        return c0 <= 0 ? 0 : 1 / (1 + Math.max(0, complexity) / c0);
    }

    /** Chance per positive gene for this sample: 1 for genetic material. */
    public static double asIsChance(@Nonnull Genome sample) {
        return sample.processed() ? 1 : asIsChance(sample.complexity());
    }

    @Nonnull
    public static List<Roll> roll(@Nonnull Genome sample, @Nonnull RandomGenerator random) {
        Balance.Injection balance = Balance.get().injection();
        List<Roll> rolls = new ArrayList<>();
        double asIs = asIsChance(sample);
        for (Gene gene : sample.genes()) {
            boolean safe = gene instanceof Gene.Stat stat && stat.isDrawback();
            if (safe || random.nextDouble() < asIs) {
                rolls.add(new Roll(gene, Outcome.AS_IS, gene));
                continue;
            }
            double pick = random.nextDouble();
            Outcome outcome = pick < balance.getWeakenChance() ? Outcome.WEAKENED
                    : pick < balance.getWeakenChance() + balance.getDropChance() ? Outcome.DROPPED : Outcome.INVERTED;
            rolls.add(apply(gene, outcome, balance, random));
        }
        return rolls;
    }

    @Nonnull
    private static Roll apply(@Nonnull Gene gene, @Nonnull Outcome outcome, @Nonnull Balance.Injection balance, @Nonnull RandomGenerator random) {
        double min = Math.min(balance.getKeepMin(), balance.getKeepMax());
        double keep = min + random.nextDouble() * Math.abs(balance.getKeepMax() - balance.getKeepMin());
        return switch (gene) {
            case Gene.Stat stat -> switch (outcome) {
                case WEAKENED -> new Roll(gene, outcome, new Gene.Stat(stat.stat(), round(stat.percent() * keep)));
                case INVERTED -> new Roll(gene, outcome, new Gene.Stat(stat.stat(), -round(stat.percent() * keep)));
                default -> new Roll(gene, Outcome.DROPPED, null);
            };
            case Gene.Effect effect -> outcome == Outcome.WEAKENED
                    ? new Roll(gene, outcome, effect.permanent()
                    ? new Gene.Effect(effect.effect(), false, balance.getWeakenedPermanentDuration())
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
