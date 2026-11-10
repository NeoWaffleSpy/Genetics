package com.genetics.data;

import com.hypixel.hytale.assetstore.map.DefaultAssetMap;
import com.hypixel.hytale.server.core.asset.HytaleAssetStore;
import com.hypixel.hytale.server.core.asset.type.entityeffect.config.EntityEffect;
import com.hypixel.hytale.server.core.modules.entitystats.asset.EntityStatType;
import com.hypixel.hytale.server.core.plugin.registry.AssetRegistry;

import javax.annotation.Nonnull;

/**
 * Registers the mod's data files as Hytale asset types, so they load from every asset pack (this mod's, resource packs,
 * other mods), get validated with the rest of the game data, and hot-reload.
 *
 * <table>
 *   <tr><th>Asset</th><th>Folder</th><th>One file per</th></tr>
 *   <tr><td>{@link StatWeight} (Effect Codex)</td><td>{@code Server/Genetics/Stats/}</td><td>vanilla stat</td></tr>
 *   <tr><td>{@link EffectComplexity} (Effect Codex)</td><td>{@code Server/Genetics/Effects/}</td><td>vanilla effect</td></tr>
 *   <tr><td>{@link MutationDefinition} (Mutation Codex)</td><td>{@code Server/Genetics/Mutations/}</td><td>mutation</td></tr>
 *   <tr><td>{@link MobGeneProfile}</td><td>{@code Server/Genetics/MobProfiles/}</td><td>mob family</td></tr>
 * </table>
 */
public final class GeneticsData {
    private GeneticsData() {
    }

    public static void register(@Nonnull AssetRegistry registry) {
        registry.register(HytaleAssetStore.builder(StatWeight.class, new DefaultAssetMap<String, StatWeight>())
                .setPath(StatWeight.PATH)
                .setCodec(StatWeight.CODEC)
                .setKeyFunction(StatWeight::getId)
                .loadsAfter(EntityStatType.class)
                .build());
        registry.register(HytaleAssetStore.builder(EffectComplexity.class, new DefaultAssetMap<String, EffectComplexity>())
                .setPath(EffectComplexity.PATH)
                .setCodec(EffectComplexity.CODEC)
                .setKeyFunction(EffectComplexity::getId)
                .loadsAfter(EntityEffect.class)
                .build());
        registry.register(HytaleAssetStore.builder(MutationDefinition.class, new DefaultAssetMap<String, MutationDefinition>())
                .setPath(MutationDefinition.PATH)
                .setCodec(MutationDefinition.CODEC)
                .setKeyFunction(MutationDefinition::getId)
                .loadsAfter(EntityStatType.class, EntityEffect.class)
                .build());
        registry.register(HytaleAssetStore.builder(MobGeneProfile.class, new DefaultAssetMap<String, MobGeneProfile>())
                .setPath(MobGeneProfile.PATH)
                .setCodec(MobGeneProfile.CODEC)
                .setKeyFunction(MobGeneProfile::getId)
                .loadsAfter(EntityStatType.class, EntityEffect.class)
                .build());
    }
}
