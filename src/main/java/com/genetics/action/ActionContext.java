package com.genetics.action;

import com.genetics.codex.Codex;
import com.genetics.codex.MobGeneProfile;
import com.genetics.genome.Genome;
import com.hypixel.hytale.component.ComponentAccessor;
import com.hypixel.hytale.component.Ref;
import com.hypixel.hytale.server.core.asset.type.model.config.ModelAsset;
import com.hypixel.hytale.server.core.asset.type.soundevent.config.SoundEvent;
import com.hypixel.hytale.server.core.universe.world.storage.EntityStore;

import javax.annotation.Nonnull;
import javax.annotation.Nullable;
import java.util.LinkedHashSet;
import java.util.Set;

/**
 * What an action runs on: the entity, what to read and change it through (a command buffer or the store), and the
 * mob the entity's genes came from (the source of its last injection), if any.
 */
public record ActionContext(@Nonnull Ref<EntityStore> entity, @Nonnull ComponentAccessor<EntityStore> buffer, @Nullable Genome.Source source) {
    /** The gene profile of the source mob, or null if there is no source or no matching profile. */
    @Nullable
    public MobGeneProfile sourceProfile() {
        return source != null ? Codex.profileFor(source.mobId()) : null;
    }

    /**
     * The source mob's model id: the one recorded when it was sampled, else a model named after the mob or its icon.
     * Null if there is no source mob or no matching model.
     */
    @Nullable
    public String sourceModel() {
        // Copies of a player carry no mob to turn into.
        if (source == null || "Player".equals(source.mobId())) return null;
        for (String candidate : names()) {
            if (ModelAsset.getAssetMap().getAsset(candidate) != null) return candidate;
        }
        return null;
    }

    /**
     * The source mob's call: the {@code Sound} of its gene profile, else the first of {@code SFX_<Mob>_Idle},
     * {@code SFX_<Mob>_Alerted} that exists, trying the mob's model and role names (with and without their variant
     * suffix, e.g. Wolf_Black then Wolf). Null if none is found.
     */
    @Nullable
    public String sourceSound() {
        if (source == null) return null;
        MobGeneProfile profile = sourceProfile();
        if (profile != null && profile.getSound() != null) return profile.getSound();
        for (String name : names()) {
            for (String kind : new String[]{"Idle", "Alerted"}) {
                String id = "SFX_" + name + "_" + kind;
                if (SoundEvent.getAssetMap().getIndex(id) != Integer.MIN_VALUE) return id;
            }
        }
        return null;
    }

    /** Names the source mob might go by in asset ids, most specific first. */
    @Nonnull
    private Set<String> names() {
        Set<String> names = new LinkedHashSet<>();
        if (source == null) return names;
        if (source.model() != null) names.add(source.model());
        names.add(source.mobId());
        String icon = source.icon();
        if (icon != null) {
            String file = icon.substring(icon.lastIndexOf('/') + 1);
            names.add(file.endsWith(".png") ? file.substring(0, file.length() - 4) : file);
        }
        for (String name : Set.copyOf(names)) {
            if (name.startsWith("Tamed_")) names.add(name.substring("Tamed_".length()));
            int cut = name.lastIndexOf('_');
            if (cut > 0) names.add(name.substring(0, cut));
        }
        return names;
    }
}
