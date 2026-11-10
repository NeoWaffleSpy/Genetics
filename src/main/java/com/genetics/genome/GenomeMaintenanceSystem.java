package com.genetics.genome;

import com.genetics.capacity.GenomeCapacity;
import com.hypixel.hytale.component.ArchetypeChunk;
import com.hypixel.hytale.server.core.modules.entitystats.EntityStatMap;
import com.hypixel.hytale.component.CommandBuffer;
import com.hypixel.hytale.component.Store;
import com.hypixel.hytale.component.query.Query;
import com.hypixel.hytale.component.system.tick.EntityTickingSystem;
import com.hypixel.hytale.server.core.modules.entity.damage.DeathComponent;
import com.hypixel.hytale.server.core.universe.world.storage.EntityStore;

import javax.annotation.Nonnull;

/**
 * Keeps every active genome applied (design doc §9.3: death doesn't affect the active genome). Respawning clears all
 * effects, and other things can remove effects or modifiers too, so about once a second this re-applies whatever is
 * missing. Dead entities are skipped until they respawn.
 */
public class GenomeMaintenanceSystem extends EntityTickingSystem<EntityStore> {
    private static final float INTERVAL = 1;

    @Nonnull
    @Override
    public Query<EntityStore> getQuery() {
        return Query.and(ActiveGenome.getComponentType(), Query.not(DeathComponent.getComponentType()));
    }

    @Override
    public void tick(float dt, int index, @Nonnull ArchetypeChunk<EntityStore> chunk, @Nonnull Store<EntityStore> store,
                     @Nonnull CommandBuffer<EntityStore> commandBuffer) {
        ActiveGenome active = chunk.getComponent(index, ActiveGenome.getComponentType());
        if (active == null) return;
        active.nextCheck -= dt;
        if (active.nextCheck > 0) return;
        active.nextCheck = INTERVAL;
        GenomeApplier.ensure(chunk.getReferenceTo(index), commandBuffer, active.getGenome());
        // Genome Capacity's value is the active genome's Complexity; respawning resets it, so keep it in sync.
        GenomeCapacity.setUsed(chunk.getComponent(index, EntityStatMap.getComponentType()),
                active.getGenome() != null ? active.getGenome().complexity() : 0);
    }
}
