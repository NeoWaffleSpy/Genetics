package com.genetics.action;

import com.hypixel.hytale.codec.Codec;
import com.hypixel.hytale.codec.KeyedCodec;
import com.hypixel.hytale.codec.builder.BuilderCodec;
import com.hypixel.hytale.codec.lookup.CodecMapCodec;

import javax.annotation.Nonnull;

/**
 * A small, reusable effect on an entity, referenced from data by {@code "Type"} (design doc §4.2 and §9.2). Actions are
 * shared by everything that triggers effects from data: sickness events now, mutation behaviors later.
 * <pre>
 * { "Type": "Impulse", "Up": 0.4, "Repeat": 3, "Interval": 1.5 }
 * </pre>
 * Every action accepts {@code Delay} (seconds before its first run, default 0), {@code Repeat} (how many times it
 * runs, default 1) and {@code Interval} (seconds between runs, default 1). Timing is handled by {@link ActionRunner}.
 */
public abstract class Action {
    public static final CodecMapCodec<Action> CODEC = new CodecMapCodec<>("Type");

    protected static final BuilderCodec<Action> BASE_CODEC = BuilderCodec.abstractBuilder(Action.class)
            .append(new KeyedCodec<>("Repeat", Codec.INTEGER), (t, v) -> t.repeat = v, t -> t.repeat)
            .documentation("How many times the action runs. Default 1.").add()
            .append(new KeyedCodec<>("Interval", Codec.DOUBLE), (t, v) -> t.interval = v, t -> t.interval)
            .documentation("Seconds between two runs when Repeat is above 1. Default 1.").add()
            .append(new KeyedCodec<>("Delay", Codec.DOUBLE), (t, v) -> t.delay = v, t -> t.delay)
            .documentation("Seconds before the first run, to chain actions in an event. Default 0.").add()
            .build();

    protected int repeat = 1;
    protected double interval = 1;
    protected double delay;

    /** Registers the built-in action types. Call once, before assets load. */
    public static void registerBuiltIns() {
        CODEC.register(ApplyEffectAction.TYPE, ApplyEffectAction.class, ApplyEffectAction.CODEC);
        CODEC.register(ApplySourceMobEffectAction.TYPE, ApplySourceMobEffectAction.class, ApplySourceMobEffectAction.CODEC);
        CODEC.register(PlaySoundAction.TYPE, PlaySoundAction.class, PlaySoundAction.CODEC);
        CODEC.register(PlaySourceMobSoundAction.TYPE, PlaySourceMobSoundAction.class, PlaySourceMobSoundAction.CODEC);
        CODEC.register(ImpulseAction.TYPE, ImpulseAction.class, ImpulseAction.CODEC);
        CODEC.register(CameraShakeAction.TYPE, CameraShakeAction.class, CameraShakeAction.CODEC);
        CODEC.register(MorphIntoSourceMobAction.TYPE, MorphIntoSourceMobAction.class, MorphIntoSourceMobAction.CODEC);
        CODEC.register(RestoreStatAction.TYPE, RestoreStatAction.class, RestoreStatAction.CODEC);
        CODEC.register(RemoveEffectAction.TYPE, RemoveEffectAction.class, RemoveEffectAction.CODEC);
        CODEC.register(AreaEffectAction.TYPE, AreaEffectAction.class, AreaEffectAction.CODEC);
        CODEC.register(AreaImpulseAction.TYPE, AreaImpulseAction.class, AreaImpulseAction.CODEC);
        CODEC.register(PushAwayAction.TYPE, PushAwayAction.class, PushAwayAction.CODEC);
        CODEC.register(DropItemAction.TYPE, DropItemAction.class, DropItemAction.CODEC);
        CODEC.register(DropLootAction.TYPE, DropLootAction.class, DropLootAction.CODEC);
    }

    public int getRepeat() {
        return Math.max(1, repeat);
    }

    public double getInterval() {
        return Math.max(0, interval);
    }

    public double getDelay() {
        return Math.max(0, delay);
    }

    /** Runs the action once. */
    public abstract void run(@Nonnull ActionContext context);
}
