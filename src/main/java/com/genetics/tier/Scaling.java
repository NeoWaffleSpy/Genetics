package com.genetics.tier;

import com.hypixel.hytale.codec.Codec;
import com.hypixel.hytale.codec.KeyedCodec;
import com.hypixel.hytale.codec.builder.BuilderCodec;
import com.hypixel.hytale.codec.lookup.CodecMapCodec;

/**
 * Turns a number of points into an amount (design doc §4.2), referenced from data by {@code "Type"}:
 * <pre>
 * { "Type": "Flat", "Amount": 2 }                     always 2
 * { "Type": "Linear", "Base": 1, "PerPoint": 0.25 }   1 + 0.25 per point
 * { "Type": "Exponential", "Base": 1, "Rate": 3 }     1 x 3^points
 * </pre>
 * For tier costs, the points are the Complexity above the tier's own minimum, so a cost grows smoothly inside a tier.
 * Other code can register more rules on {@link #CODEC}.
 */
public abstract class Scaling {
    public static final CodecMapCodec<Scaling> CODEC = new CodecMapCodec<>("Type");

    /** Registers the built-in rules. Call once, before assets load. */
    public static void registerBuiltIns() {
        CODEC.register("Flat", Flat.class, Flat.CODEC);
        CODEC.register("Linear", Linear.class, Linear.CODEC);
        CODEC.register("Exponential", Exponential.class, Exponential.CODEC);
    }

    /** The amount for this many points (never negative). */
    public abstract double amount(double points);

    public static final class Flat extends Scaling {
        public static final BuilderCodec<Flat> CODEC = BuilderCodec.builder(Flat.class, Flat::new)
                .append(new KeyedCodec<>("Amount", Codec.DOUBLE), (t, v) -> t.amount = v, t -> t.amount).add()
                .build();

        private double amount;

        @Override
        public double amount(double points) {
            return Math.max(0, amount);
        }
    }

    public static final class Linear extends Scaling {
        public static final BuilderCodec<Linear> CODEC = BuilderCodec.builder(Linear.class, Linear::new)
                .append(new KeyedCodec<>("Base", Codec.DOUBLE), (t, v) -> t.base = v, t -> t.base).add()
                .append(new KeyedCodec<>("PerPoint", Codec.DOUBLE), (t, v) -> t.perPoint = v, t -> t.perPoint).add()
                .build();

        private double base;
        private double perPoint;

        @Override
        public double amount(double points) {
            return Math.max(0, base + perPoint * Math.max(0, points));
        }
    }

    public static final class Exponential extends Scaling {
        public static final BuilderCodec<Exponential> CODEC = BuilderCodec.builder(Exponential.class, Exponential::new)
                .append(new KeyedCodec<>("Base", Codec.DOUBLE), (t, v) -> t.base = v, t -> t.base).add()
                .append(new KeyedCodec<>("Rate", Codec.DOUBLE), (t, v) -> t.rate = v, t -> t.rate).add()
                .build();

        private double base = 1;
        private double rate = 1;

        @Override
        public double amount(double points) {
            return Math.max(0, base * Math.pow(rate, Math.max(0, points)));
        }
    }
}
