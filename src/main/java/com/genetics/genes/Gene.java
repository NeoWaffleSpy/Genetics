package com.genetics.genes;

import com.hypixel.hytale.codec.Codec;
import com.hypixel.hytale.codec.KeyedCodec;
import com.hypixel.hytale.codec.builder.BuilderCodec;
import com.hypixel.hytale.codec.codecs.EnumCodec;
import com.hypixel.hytale.codec.codecs.array.ArrayCodec;
import com.hypixel.hytale.codec.validation.Validators;

import javax.annotation.Nonnull;

/**
 * One trait in a genome (GDD §2). Stored by value, so a gene stays valid if the data files change later.
 *
 * <pre>
 * { "Type": "Stat",     "Id": "Health", "Value": 6.0 }                          // +6% max health; negative = drawback
 * { "Type": "Effect",   "Id": "HealthRegen_Buff_T1", "Permanent": false, "Duration": 180 }
 * { "Type": "Effect",   "Id": "…", "Permanent": true }
 * { "Type": "Mutation", "Id": "TitanBlood" }
 * </pre>
 */
public final class Gene {
    public enum Type {
        /** Modifies a vanilla stat by {@link #getValue()} percent. */
        Stat,
        /** Applies a vanilla effect, for {@link #getDuration()} seconds or permanently. */
        Effect,
        /** A Mutation Codex entry. */
        Mutation
    }

    public static final BuilderCodec<Gene> CODEC = BuilderCodec.builder(Gene.class, Gene::new)
            .append(new KeyedCodec<>("Type", new EnumCodec<>(Type.class)), (g, v) -> g.type = v, g -> g.type)
            .addValidator(Validators.nonNull())
            .add()
            .append(new KeyedCodec<>("Id", Codec.STRING), (g, v) -> g.id = v, g -> g.id)
            .addValidator(Validators.nonNull())
            .add()
            .append(new KeyedCodec<>("Value", Codec.DOUBLE), (g, v) -> g.value = v, g -> g.type == Type.Stat ? g.value : null)
            .add()
            .append(new KeyedCodec<>("Permanent", Codec.BOOLEAN), (g, v) -> g.permanent = v, g -> g.type == Type.Effect ? g.permanent : null)
            .add()
            .append(new KeyedCodec<>("Duration", Codec.DOUBLE), (g, v) -> g.duration = v, g -> g.type == Type.Effect && !g.permanent ? g.duration : null)
            .add()
            .build();
    public static final ArrayCodec<Gene> ARRAY_CODEC = new ArrayCodec<>(CODEC, Gene[]::new);

    private Type type;
    private String id;
    private double value;
    private boolean permanent;
    private double duration;

    private Gene() {
    }

    private Gene(@Nonnull Type type, @Nonnull String id, double value, boolean permanent, double duration) {
        this.type = type;
        this.id = id;
        this.value = value;
        this.permanent = permanent;
        this.duration = duration;
    }

    /** A stat modifier of {@code percent} percent (negative for a drawback). */
    @Nonnull
    public static Gene stat(@Nonnull String statId, double percent) {
        return new Gene(Type.Stat, statId, percent, false, 0);
    }

    @Nonnull
    public static Gene temporaryEffect(@Nonnull String effectId, double durationSeconds) {
        return new Gene(Type.Effect, effectId, 0, false, durationSeconds);
    }

    @Nonnull
    public static Gene permanentEffect(@Nonnull String effectId) {
        return new Gene(Type.Effect, effectId, 0, true, 0);
    }

    @Nonnull
    public static Gene mutation(@Nonnull String mutationId) {
        return new Gene(Type.Mutation, mutationId, 0, false, 0);
    }

    @Nonnull
    public Type getType() {
        return type;
    }

    /** Stat id, effect id or mutation id, depending on {@link #getType()}. */
    @Nonnull
    public String getId() {
        return id;
    }

    /** Stat modifier in percent. Only meaningful for {@link Type#Stat}. */
    public double getValue() {
        return value;
    }

    /** True for a stat gene with a negative modifier. */
    public boolean isDrawback() {
        return type == Type.Stat && value < 0;
    }

    /** Only meaningful for {@link Type#Effect}. */
    public boolean isPermanent() {
        return permanent;
    }

    /** Seconds a temporary effect lasts. Only meaningful for a temporary {@link Type#Effect}. */
    public double getDuration() {
        return duration;
    }

    @Override
    public String toString() {
        return switch (type) {
            case Stat -> "Stat{" + id + " " + value + "%}";
            case Effect -> "Effect{" + id + (permanent ? " permanent" : " " + duration + "s") + "}";
            case Mutation -> "Mutation{" + id + "}";
        };
    }
}
