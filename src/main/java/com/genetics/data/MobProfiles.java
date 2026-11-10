package com.genetics.data;

import javax.annotation.Nonnull;
import javax.annotation.Nullable;

/** Finds the gene profile of a mob by its NPC role id. */
public final class MobProfiles {
    private MobProfiles() {
    }

    /** The profile listing {@code roleId}, else the {@value MobGeneProfile#DEFAULT_ID} profile, else {@code null}. */
    @Nullable
    public static MobGeneProfile forRole(@Nonnull String roleId) {
        // A handful of profiles: a scan is cheaper to keep correct across hot reloads than an index.
        for (MobGeneProfile profile : MobGeneProfile.getAssetMap().getAssetMap().values()) {
            for (String role : profile.getRoles()) {
                if (roleId.equals(role)) return profile;
            }
        }
        return MobGeneProfile.getAssetMap().getAsset(MobGeneProfile.DEFAULT_ID);
    }
}
