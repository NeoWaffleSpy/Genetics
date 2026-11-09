package com.genetics.genes;

import com.hypixel.hytale.component.CommandBuffer;
import com.hypixel.hytale.component.Ref;
import com.hypixel.hytale.server.core.asset.type.entityeffect.config.EntityEffect;
import com.hypixel.hytale.server.core.entity.UUIDComponent;
import com.hypixel.hytale.server.core.entity.effect.ActiveEntityEffect;
import com.hypixel.hytale.server.core.entity.effect.EffectControllerComponent;
import com.hypixel.hytale.server.core.inventory.ItemStack;
import com.hypixel.hytale.server.core.modules.entitystats.EntityStatMap;
import com.hypixel.hytale.server.core.modules.entitystats.EntityStatValue;
import com.hypixel.hytale.server.core.modules.i18n.I18nModule;
import com.hypixel.hytale.server.core.universe.world.storage.EntityStore;
import com.hypixel.hytale.server.npc.entities.NPCEntity;
import com.hypixel.hytale.server.npc.role.Role;
import org.bson.BsonArray;
import org.bson.BsonBoolean;
import org.bson.BsonDocument;
import org.bson.BsonDouble;
import org.bson.BsonInt64;
import org.bson.BsonString;
import org.bson.json.JsonWriterSettings;

import javax.annotation.Nonnull;
import javax.annotation.Nullable;

/**
 * A snapshot of a mob's genetic data, copied by value at sampling time and stored in item metadata.
 * Nothing here references the live entity, so the sample stays valid after the mob changes, despawns or dies.
 *
 * <pre>
 * "Genetics:Genes": {
 *   "MobId": "Cow", "MobName": "Cow", "EntityUuid": "…", "SampledAt": 1762700000000,
 *   "Stats":   { "Health": { "Value": 103.0, "Min": 0.0, "Max": 103.0 }, … },
 *   "Effects": [ { "Id": "Burn", "RemainingDuration": 2.5, "InitialDuration": 5.0, "Infinite": false, "Debuff": true }, … ]
 * }
 * </pre>
 */
public final class GeneSample {
    /** Item metadata key the sample is stored under. */
    public static final String METADATA_KEY = "Genetics:Genes";

    private static final JsonWriterSettings PRETTY_JSON = JsonWriterSettings.builder().indent(true).build();

    private final String mobName;
    private final BsonDocument data;

    private GeneSample(@Nonnull String mobName, @Nonnull BsonDocument data) {
        this.mobName = mobName;
        this.data = data;
    }

    /** Copies the target NPC's identity, stats and active effects into a new sample. */
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
        data.append("Stats", captureStats(commandBuffer.getComponent(target, EntityStatMap.getComponentType())));
        data.append("Effects", captureEffects(commandBuffer.getComponent(target, EffectControllerComponent.getComponentType())));

        return new GeneSample(mobName, data);
    }

    /** True if the stack already holds a gene sample. */
    public static boolean isPresentOn(@Nonnull ItemStack stack) {
        BsonDocument metadata = stack.getMetadata();
        return metadata != null && metadata.containsKey(METADATA_KEY);
    }

    @Nonnull
    public String getMobName() {
        return mobName;
    }

    @Nonnull
    public BsonDocument toBson() {
        return data;
    }

    @Nonnull
    public String toPrettyJson() {
        return data.toJson(PRETTY_JSON);
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
    private static BsonArray captureEffects(@Nullable EffectControllerComponent effects) {
        BsonArray out = new BsonArray();
        if (effects == null) return out;

        for (ActiveEntityEffect effect : effects.getActiveEffects().values()) {
            EntityEffect asset = EntityEffect.getAssetMap().getAsset(effect.getEntityEffectIndex());
            out.add(new BsonDocument()
                    .append("Id", new BsonString(asset != null ? asset.getId() : "#" + effect.getEntityEffectIndex()))
                    .append("RemainingDuration", new BsonDouble(effect.getRemainingDuration()))
                    .append("InitialDuration", new BsonDouble(effect.getInitialDuration()))
                    .append("Infinite", new BsonBoolean(effect.isInfinite()))
                    .append("Debuff", new BsonBoolean(effect.isDebuff())));
        }
        return out;
    }

    /** The mob's translated name (e.g. "Cow"), falling back to a prettified role id. */
    @Nonnull
    private static String resolveMobName(@Nonnull NPCEntity npc, @Nullable String language) {
        Role role = npc.getRole();
        String key = role != null ? role.getNameTranslationKey() : null;
        if (key != null && !key.isEmpty()) {
            I18nModule i18n = I18nModule.get();
            String lang = language != null ? language : I18nModule.DEFAULT_LANGUAGE;
            String translated = i18n.getMessage(lang, key);
            if (translated == null && key.startsWith("server.")) translated = i18n.getMessage(lang, key.substring("server.".length()));
            if (translated != null && !translated.isBlank()) return translated;
        }
        return npc.getRoleName().replace('_', ' ');
    }
}
