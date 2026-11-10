package com.genetics.genes;

import com.hypixel.hytale.codec.Codec;
import com.hypixel.hytale.codec.KeyedCodec;
import com.hypixel.hytale.codec.builder.BuilderCodec;

import javax.annotation.Nonnull;
import javax.annotation.Nullable;

/**
 * Where a genome came from, copied by value so it can be shown without the entity:
 * {@code { "Id": "Cow", "Name": "Cow", "Icon": "Icons/…", "SampledAt": 1762700000000 }}.
 * {@code Id} is the NPC role id; {@code Name} is translated at sampling time.
 */
public final class SampleSource {
    public static final BuilderCodec<SampleSource> CODEC = BuilderCodec.builder(SampleSource.class, SampleSource::new)
            .append(new KeyedCodec<>("Id", Codec.STRING), (s, v) -> s.id = v, s -> s.id)
            .add()
            .append(new KeyedCodec<>("Name", Codec.STRING), (s, v) -> s.name = v, s -> s.name)
            .add()
            .append(new KeyedCodec<>("Icon", Codec.STRING), (s, v) -> s.icon = v, s -> s.icon)
            .add()
            .append(new KeyedCodec<>("SampledAt", Codec.LONG), (s, v) -> s.sampledAt = v, s -> s.sampledAt)
            .add()
            .build();

    private String id;
    private String name;
    private String icon;
    private long sampledAt;

    private SampleSource() {
    }

    public SampleSource(@Nonnull String id, @Nonnull String name, @Nullable String icon, long sampledAt) {
        this.id = id;
        this.name = name;
        this.icon = icon;
        this.sampledAt = sampledAt;
    }

    @Nonnull
    public String getId() {
        return id != null ? id : "Unknown";
    }

    @Nonnull
    public String getName() {
        return name != null ? name : getId();
    }

    @Nullable
    public String getIcon() {
        return icon;
    }

    /** Epoch millis. */
    public long getSampledAt() {
        return sampledAt;
    }
}
