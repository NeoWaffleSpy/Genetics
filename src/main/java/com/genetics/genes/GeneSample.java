package com.genetics.genes;

import com.hypixel.hytale.component.CommandBuffer;
import com.hypixel.hytale.component.Ref;
import com.hypixel.hytale.server.core.asset.type.entityeffect.config.EntityEffect;
import com.hypixel.hytale.server.core.asset.type.model.config.Model;
import com.hypixel.hytale.server.core.asset.type.model.config.ModelAsset;
import com.hypixel.hytale.server.core.entity.UUIDComponent;
import com.hypixel.hytale.server.core.entity.effect.ActiveEntityEffect;
import com.hypixel.hytale.server.core.entity.effect.EffectControllerComponent;
import com.hypixel.hytale.server.core.inventory.ItemStack;
import com.hypixel.hytale.server.core.modules.entity.component.PersistentModel;
import com.hypixel.hytale.server.core.modules.entitystats.EntityStatMap;
import com.hypixel.hytale.server.core.modules.entitystats.EntityStatValue;
import com.hypixel.hytale.server.core.universe.world.storage.EntityStore;
import com.hypixel.hytale.server.npc.entities.NPCEntity;
import com.hypixel.hytale.server.npc.role.Role;
import org.bson.BsonArray;
import org.bson.BsonBoolean;
import org.bson.BsonDocument;
import org.bson.BsonDouble;
import org.bson.BsonInt64;
import org.bson.BsonString;

import javax.annotation.Nonnull;
import javax.annotation.Nullable;

/**
 * A snapshot of a mob's genetic data, copied by value at sampling time and stored in item metadata.
 * Nothing here references the live entity, so the sample stays valid after the mob changes, despawns or dies.
 *
 * <pre>
 * "Genetics:Genes": {
 *   "MobId": "Cow", "MobName": "Cow", "EntityUuid": "…", "SampledAt": 1762700000000,
 *   "Model":   { "Id": "Cow", "Scale": 1.0, "Icon": "Icons/…" },
 *   "Stats":   { "Health": { "Value": 103.0, "Min": 0.0, "Max": 103.0 }, … },
 *   "Effects": [ { "Id": "Burn", "Name": "Burn", "Icon": "…", "RemainingDuration": 2.5, "InitialDuration": 5.0,
 *                  "Infinite": false, "Debuff": true }, … ]
 * }
 * </pre>
 */
public final class GeneSample {
    /** Item metadata key the sample is stored under. */
    public static final String METADATA_KEY = "Genetics:Genes";

    private final String mobName;
    private final BsonDocument data;

    private GeneSample(@Nonnull String mobName, @Nonnull BsonDocument data) {
        this.mobName = mobName;
        this.data = data;
    }

    /** Copies the target NPC's identity, model, stats and active effects into a new sample. */
    @Nonnull
    public static GeneSample capture(
            @Nonnull Ref<EntityStore> target, @Nonnull NPCEntity npc, @Nullable String language, @Nonnull CommandBuffer<EntityStore> commandBuffer
    ) {
        String mobName = resolveMobName(npc, language);

        BsonDocument data = new BsonDocument()
                .append("MobId", new BsonString(npc.getRoleName()))
                .append("MobName", new BsonString(mobName));

        UUIDComponent uuid = commandBuffer.getComponent(target, UUIDComponent.getComponentType());
        if (uuid != null) data.append("EntityUuid", new BsonString(uuid.getUuid().toString()));

        data.append("SampledAt", new BsonInt64(System.currentTimeMillis()));

        BsonDocument model = captureModel(commandBuffer.getComponent(target, PersistentModel.getComponentType()));
        if (model != null) data.append("Model", model);

        data.append("Stats", captureStats(commandBuffer.getComponent(target, EntityStatMap.getComponentType())));
        data.append("Effects", captureEffects(commandBuffer.getComponent(target, EffectControllerComponent.getComponentType()), language));

        return new GeneSample(mobName, data);
    }

    /** True if the stack already holds a gene sample. */
    public static boolean isPresentOn(@Nonnull ItemStack stack) {
        return read(stack) != null;
    }

    /** The raw sample stored on a stack, or {@code null} if it holds none. */
    @Nullable
    public static BsonDocument read(@Nullable ItemStack stack) {
        BsonDocument metadata = stack != null ? stack.getMetadata() : null;
        if (metadata == null || !metadata.isDocument(METADATA_KEY)) return null;
        return metadata.getDocument(METADATA_KEY);
    }

    @Nonnull
    public String getMobName() {
        return mobName;
    }

    @Nonnull
    public BsonDocument toBson() {
        return data;
    }

    @Nullable
    private static BsonDocument captureModel(@Nullable PersistentModel persistentModel) {
        if (persistentModel == null) return null;
        Model.ModelReference reference = persistentModel.getModelReference();
        String modelId = reference.getModelAssetId();
        if (modelId == null) return null;

        BsonDocument out = new BsonDocument()
                .append("Id", new BsonString(modelId))
                .append("Scale", new BsonDouble(reference.getScale()));
        ModelAsset asset = ModelAsset.getAssetMap().getAsset(modelId);
        if (asset != null && asset.getIcon() != null) out.append("Icon", new BsonString(asset.getIcon()));
        return out;
    }

    @Nonnull
    private static BsonDocument captureStats(@Nullable EntityStatMap stats) {
        BsonDocument out = new BsonDocument();
        if (stats == null) return out;

        for (int i = 0; i < stats.size(); i++) {
            EntityStatValue stat = stats.get(i);
            if (stat == null) continue;
            out.append(stat.getId(), new BsonDocument()
                    .append("Value", new BsonDouble(stat.get()))
                    .append("Min", new BsonDouble(stat.getMin()))
                    .append("Max", new BsonDouble(stat.getMax())));
        }
        return out;
    }

    @Nonnull
    private static BsonArray captureEffects(@Nullable EffectControllerComponent effects, @Nullable String language) {
        BsonArray out = new BsonArray();
        if (effects == null) return out;

        for (ActiveEntityEffect effect : effects.getActiveEffects().values()) {
            EntityEffect asset = EntityEffect.getAssetMap().getAsset(effect.getEntityEffectIndex());
            String id = asset != null ? asset.getId() : "#" + effect.getEntityEffectIndex();

            BsonDocument entry = new BsonDocument()
                    .append("Id", new BsonString(id))
                    .append("Name", new BsonString(Translations.resolve(language, asset != null ? asset.getName() : null, prettify(id))));
            if (asset != null && asset.getStatusEffectIcon() != null) entry.append("Icon", new BsonString(asset.getStatusEffectIcon()));
            entry.append("RemainingDuration", new BsonDouble(effect.getRemainingDuration()))
                    .append("InitialDuration", new BsonDouble(effect.getInitialDuration()))
                    .append("Infinite", new BsonBoolean(effect.isInfinite()))
                    .append("Debuff", new BsonBoolean(effect.isDebuff()));
            out.add(entry);
        }
        return out;
    }

    /** The mob's translated name (e.g. "Cow"), falling back to a prettified role id. */
    @Nonnull
    private static String resolveMobName(@Nonnull NPCEntity npc, @Nullable String language) {
        Role role = npc.getRole();
        return Translations.resolve(language, role != null ? role.getNameTranslationKey() : null, prettify(npc.getRoleName()));
    }

    @Nonnull
    private static String prettify(@Nonnull String id) {
        return id.replace('_', ' ');
    }
}
