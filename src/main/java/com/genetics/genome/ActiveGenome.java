package com.genetics.genome;

import com.hypixel.hytale.codec.KeyedCodec;
import com.hypixel.hytale.codec.builder.BuilderCodec;
import com.hypixel.hytale.codec.codecs.BsonDocumentCodec;
import com.hypixel.hytale.component.Component;
import com.hypixel.hytale.component.ComponentType;
import com.hypixel.hytale.server.core.universe.world.storage.EntityStore;

import javax.annotation.Nonnull;
import javax.annotation.Nullable;

/**
 * The genome currently applied to an entity (design doc §2, "Active genome"). Saved with the entity, so it survives
 * logging out and death. Works on any entity, so mobs can carry genomes later.
 * <p>
 * Only lasting genes are stored: stat genes, permanent effects and mutations. Temporary effects are applied once at
 * injection and are not part of the active genome, so a copy of yourself never carries them (design doc §13.2, X1).
 */
public class ActiveGenome implements Component<EntityStore> {
    public static final String ID = "Genetics_ActiveGenome";

    public static final BuilderCodec<ActiveGenome> CODEC = BuilderCodec.builder(ActiveGenome.class, ActiveGenome::new)
            .append(new KeyedCodec<>("Genome", new BsonDocumentCodec()),
                    (component, doc) -> component.genome = doc != null ? Genome.fromBson(doc) : null,
                    component -> component.genome != null ? component.genome.toBson() : null)
            .add()
            .build();

    private static ComponentType<EntityStore, ActiveGenome> componentType;

    @Nullable
    private Genome genome;
    /** Seconds until the maintenance system next checks this entity. Not saved. */
    transient float nextCheck;

    public ActiveGenome() {
    }

    public ActiveGenome(@Nonnull Genome genome) {
        this.genome = genome;
    }

    public static void setComponentType(@Nonnull ComponentType<EntityStore, ActiveGenome> type) {
        componentType = type;
    }

    public static ComponentType<EntityStore, ActiveGenome> getComponentType() {
        return componentType;
    }

    @Nullable
    public Genome getGenome() {
        return genome;
    }

    public void setGenome(@Nullable Genome genome) {
        this.genome = genome;
        this.nextCheck = 0;
    }

    @Nonnull
    @Override
    public Component<EntityStore> clone() {
        ActiveGenome copy = new ActiveGenome();
        copy.genome = genome;
        return copy;
    }
}
