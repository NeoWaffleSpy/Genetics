package com.genetics.action;

import com.hypixel.hytale.component.Ref;
import com.hypixel.hytale.component.spatial.SpatialResource;
import com.hypixel.hytale.server.core.modules.entity.component.TransformComponent;
import com.hypixel.hytale.server.core.universe.PlayerRef;
import com.hypixel.hytale.server.core.universe.world.storage.EntityStore;
import com.hypixel.hytale.server.npc.NPCPlugin;
import org.joml.Vector3d;

import javax.annotation.Nonnull;
import javax.annotation.Nullable;
import java.util.ArrayList;
import java.util.List;

/** Finds the NPCs around an entity, for area actions. */
final class Nearby {
    private Nearby() {
    }

    @Nullable
    static Vector3d position(@Nonnull ActionContext context, @Nonnull Ref<EntityStore> ref) {
        TransformComponent transform = context.buffer().getComponent(ref, TransformComponent.getComponentType());
        return transform != null ? new Vector3d(transform.getPosition()) : null;
    }

    /** NPCs (never players) within {@code radius} of the context's entity, excluding it. */
    @Nonnull
    static List<Ref<EntityStore>> npcs(@Nonnull ActionContext context, double radius) {
        List<Ref<EntityStore>> out = new ArrayList<>();
        Vector3d center = position(context, context.entity());
        if (center == null) return out;
        SpatialResource<Ref<EntityStore>, EntityStore> spatial = context.buffer().getResource(NPCPlugin.get().getNpcSpatialResource());
        if (spatial == null) return out;
        List<Ref<EntityStore>> found = SpatialResource.getThreadLocalReferenceList();
        spatial.getSpatialStructure().collect(center, radius, found);
        for (Ref<EntityStore> ref : found) {
            if (ref == null || !ref.isValid() || ref.equals(context.entity())) continue;
            if (context.buffer().getComponent(ref, PlayerRef.getComponentType()) != null) continue;
            out.add(ref);
        }
        return out;
    }
}
