package com.genetics.genes;

import com.genetics.data.MobGeneProfile;
import com.genetics.data.MobProfiles;
import com.hypixel.hytale.component.CommandBuffer;
import com.hypixel.hytale.component.Ref;
import com.hypixel.hytale.logger.HytaleLogger;
import com.hypixel.hytale.server.core.asset.type.model.config.ModelAsset;
import com.hypixel.hytale.server.core.modules.entity.component.PersistentModel;
import com.hypixel.hytale.server.core.universe.world.storage.EntityStore;
import com.hypixel.hytale.server.npc.entities.NPCEntity;
import com.hypixel.hytale.server.npc.role.Role;

import javax.annotation.Nonnull;
import javax.annotation.Nullable;
import java.util.List;
import java.util.concurrent.ThreadLocalRandom;

/** Draws a raw sample from a mob: its identity, plus genes rolled from its gene profile. */
public final class SampleCapture {
    private static final HytaleLogger LOGGER = HytaleLogger.forEnclosingClass();

    private SampleCapture() {
    }

    @Nonnull
    public static GeneSample fromMob(
            @Nonnull Ref<EntityStore> target, @Nonnull NPCEntity npc, @Nullable String language, @Nonnull CommandBuffer<EntityStore> commandBuffer
    ) {
        String roleId = npc.getRoleName();
        Role role = npc.getRole();
        String name = Translations.resolve(language, role != null ? role.getNameTranslationKey() : null, Translations.prettify(roleId));
        SampleSource source = new SampleSource(roleId, name, iconOf(commandBuffer.getComponent(target, PersistentModel.getComponentType())), System.currentTimeMillis());

        MobGeneProfile profile = MobProfiles.forRole(roleId);
        if (profile == null) {
            LOGGER.atWarning().log("No gene profile for '" + roleId + "' and no Default profile: the sample has no genes.");
            return GeneSample.raw(source, List.of());
        }
        return GeneSample.raw(source, GenomeRoller.roll(profile, ThreadLocalRandom.current()));
    }

    @Nullable
    private static String iconOf(@Nullable PersistentModel model) {
        String modelId = model != null ? model.getModelReference().getModelAssetId() : null;
        ModelAsset asset = modelId != null ? ModelAsset.getAssetMap().getAsset(modelId) : null;
        return asset != null ? asset.getIcon() : null;
    }
}
