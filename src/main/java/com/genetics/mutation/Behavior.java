package com.genetics.mutation;

import com.genetics.action.Action;
import com.hypixel.hytale.codec.Codec;
import com.hypixel.hytale.codec.KeyedCodec;
import com.hypixel.hytale.codec.builder.BuilderCodec;
import com.hypixel.hytale.codec.codecs.array.ArrayCodec;
import com.hypixel.hytale.codec.lookup.CodecMapCodec;

import javax.annotation.Nonnull;
import javax.annotation.Nullable;

/**
 * A mutation behavior (design doc §3.3): a trigger, referenced from data by {@code "Type"}, that runs actions from the
 * shared {@link Action} system when it fires.
 * <pre>
 * { "Type": "OnLowHealth", "Threshold": 0.25, "Cooldown": 60,
 *   "Actions": [ { "Type": "ApplyEffect", "Id": "Genetics_Speed", "Duration": 6 } ] }
 * </pre>
 * Every behavior accepts {@code Cooldown} (seconds before it can fire again), {@code Actions} and an optional
 * {@code Message} (text shown to the player when it fires).
 */
public abstract class Behavior {
    public static final CodecMapCodec<Behavior> CODEC = new CodecMapCodec<>("Type");

    protected static final BuilderCodec<Behavior> BASE_CODEC = BuilderCodec.abstractBuilder(Behavior.class)
            .append(new KeyedCodec<>("Cooldown", Codec.DOUBLE), (t, v) -> t.cooldown = v, t -> t.cooldown)
            .documentation("Seconds before the behavior can fire again.").add()
            .append(new KeyedCodec<>("Actions", new ArrayCodec<>(Action.CODEC, Action[]::new)), (t, v) -> t.actions = v, t -> t.actions).add()
            .append(new KeyedCodec<>("Message", Codec.STRING), (t, v) -> t.message = v, t -> t.message)
            .documentation("Optional text or translation key shown to the player when the behavior fires.").add()
            .build();

    protected double cooldown;
    protected Action[] actions = new Action[0];
    @Nullable
    protected String message;

    /** Registers the built-in behavior types. Call once, before assets load. */
    public static void registerBuiltIns() {
        CODEC.register(OnLowHealth.TYPE, OnLowHealth.class, OnLowHealth.CODEC);
        CODEC.register(OnDamaged.TYPE, OnDamaged.class, OnDamaged.CODEC);
        CODEC.register(AtNight.TYPE, AtNight.class, AtNight.CODEC);
        CODEC.register(Periodic.TYPE, Periodic.class, Periodic.CODEC);
    }

    public double getCooldown() {
        return Math.max(0, cooldown);
    }

    @Nonnull
    public Action[] getActions() {
        return actions != null ? actions : new Action[0];
    }

    @Nullable
    public String getMessage() {
        return message;
    }

    /**
     * Whether the behavior fires now (cooldowns are handled by the caller).
     *
     * @param armed this behavior's own flag, kept between ticks, for triggers that fire on a change
     */
    public abstract boolean check(@Nonnull Input input, @Nonnull boolean[] armed);

    /** A short description for the Lab Station, e.g. "below 25% health". */
    @Nonnull
    public abstract String describe();

    /** What the triggers look at, read once per tick. */
    public record Input(double healthRatio, double damageTaken, boolean night) {
    }

    /** Fires once each time health drops below {@code Threshold} (a fraction of max health), then re-arms above it. */
    public static final class OnLowHealth extends Behavior {
        public static final String TYPE = "OnLowHealth";
        public static final BuilderCodec<OnLowHealth> CODEC = BuilderCodec.builder(OnLowHealth.class, OnLowHealth::new, BASE_CODEC)
                .append(new KeyedCodec<>("Threshold", Codec.DOUBLE), (t, v) -> t.threshold = v, t -> t.threshold)
                .documentation("Fraction of max health, e.g. 0.25.").add()
                .build();

        private double threshold = 0.25;

        @Override
        public boolean check(@Nonnull Input input, @Nonnull boolean[] armed) {
            if (input.healthRatio() > threshold) {
                armed[0] = true;
                return false;
            }
            if (!armed[0]) return false;
            armed[0] = false;
            return true;
        }

        @Nonnull
        @Override
        public String describe() {
            return "below " + Math.round(threshold * 100) + "% health";
        }
    }

    /** Fires when the entity loses at least {@code MinDamage} health in one tick. */
    public static final class OnDamaged extends Behavior {
        public static final String TYPE = "OnDamaged";
        public static final BuilderCodec<OnDamaged> CODEC = BuilderCodec.builder(OnDamaged.class, OnDamaged::new, BASE_CODEC)
                .append(new KeyedCodec<>("MinDamage", Codec.DOUBLE), (t, v) -> t.minDamage = v, t -> t.minDamage).add()
                .build();

        private double minDamage = 1;

        @Override
        public boolean check(@Nonnull Input input, @Nonnull boolean[] armed) {
            return input.damageTaken() >= Math.max(0.01, minDamage);
        }

        @Nonnull
        @Override
        public String describe() {
            return "when hurt";
        }
    }

    /** Fires while it is night, every Cooldown seconds (pair it with an effect lasting a bit longer than the cooldown). */
    public static final class AtNight extends Behavior {
        public static final String TYPE = "AtNight";
        public static final BuilderCodec<AtNight> CODEC = BuilderCodec.builder(AtNight.class, AtNight::new, BASE_CODEC).build();

        @Override
        public boolean check(@Nonnull Input input, @Nonnull boolean[] armed) {
            return input.night();
        }

        @Nonnull
        @Override
        public String describe() {
            return "at night";
        }
    }

    /** Fires every Cooldown seconds. */
    public static final class Periodic extends Behavior {
        public static final String TYPE = "Periodic";
        public static final BuilderCodec<Periodic> CODEC = BuilderCodec.builder(Periodic.class, Periodic::new, BASE_CODEC).build();

        @Override
        public boolean check(@Nonnull Input input, @Nonnull boolean[] armed) {
            return true;
        }

        @Nonnull
        @Override
        public String describe() {
            return "every " + Math.round(getCooldown()) + " s";
        }
    }
}
