package com.genetics.labstation;

import com.genetics.codex.Balance;
import com.genetics.tier.Materials;
import com.hypixel.hytale.codec.Codec;
import com.hypixel.hytale.codec.KeyedCodec;
import com.hypixel.hytale.codec.builder.BuilderCodec;
import com.hypixel.hytale.component.Component;
import com.hypixel.hytale.component.ComponentAccessor;
import com.hypixel.hytale.component.ComponentType;
import com.hypixel.hytale.component.Ref;
import com.hypixel.hytale.component.Store;
import com.hypixel.hytale.protocol.SoundCategory;
import com.hypixel.hytale.server.core.asset.type.soundevent.config.SoundEvent;
import com.hypixel.hytale.server.core.modules.block.BlockModule;
import com.hypixel.hytale.server.core.universe.world.SoundUtil;
import com.hypixel.hytale.server.core.universe.world.World;
import com.hypixel.hytale.server.core.universe.world.storage.ChunkStore;
import com.hypixel.hytale.server.core.universe.world.storage.EntityStore;
import org.joml.Vector3i;

import javax.annotation.Nonnull;
import javax.annotation.Nullable;

/**
 * The Lab Station's tier, saved on the block like a vanilla bench's tier ({@code BenchBlock.TierLevel}). Tier 1
 * identifies samples; tier 2 adds processing and splicing (design doc §7). Stations placed before this component
 * existed have none, and count as tier 1 until upgraded.
 * <pre>
 * "BlockEntity": { "Components": { "Genetics_LabStation": { "Tier": 1 } } }
 * </pre>
 */
public class LabStationBlock implements Component<ChunkStore> {
    public static final String ID = "Genetics_LabStation";
    public static final int MAX_TIER = 3;

    public static final BuilderCodec<LabStationBlock> CODEC = BuilderCodec.builder(LabStationBlock.class, LabStationBlock::new)
            .append(new KeyedCodec<>("Tier", Codec.INTEGER), (t, v) -> t.tier = v, t -> t.tier).add()
            .build();

    private static ComponentType<ChunkStore, LabStationBlock> componentType;

    private int tier = 1;

    public LabStationBlock() {
    }

    private LabStationBlock(int tier) {
        this.tier = tier;
    }

    public static void setComponentType(@Nonnull ComponentType<ChunkStore, LabStationBlock> type) {
        componentType = type;
    }

    @Nonnull
    public static ComponentType<ChunkStore, LabStationBlock> getComponentType() {
        return componentType;
    }

    public int getTier() {
        return Math.max(1, tier);
    }

    @Override
    public Component<ChunkStore> clone() {
        return new LabStationBlock(tier);
    }

    // ---- The station at a position ----

    /** The tier of the station at this position (1 if it has no tier yet). */
    public static int tierAt(@Nonnull World world, @Nonnull Vector3i pos) {
        Ref<ChunkStore> block = blockEntity(world, pos);
        LabStationBlock station = block != null ? world.getChunkStore().getStore().getComponent(block, getComponentType()) : null;
        return station != null ? station.getTier() : 1;
    }

    /**
     * Upgrades the station at this position by one tier, taking the upgrade's materials from the player (free in
     * Creative). Returns a message saying why it failed, or null on success.
     */
    @Nullable
    public static String upgrade(@Nonnull World world, @Nonnull Vector3i pos, @Nonnull Ref<EntityStore> player, @Nonnull ComponentAccessor<EntityStore> accessor) {
        int current = tierAt(world, pos);
        Balance.StationUpgrade upgrade = Balance.get().stationUpgrade(current + 1);
        if (current >= MAX_TIER || upgrade == null) return "This Lab Station is already at its highest tier.";
        com.genetics.journal.GeneJournal journal = com.genetics.journal.Journal.of(player, accessor);
        String feature = com.genetics.research.Research.featureForStationTier(current + 1);
        if (feature != null && !com.genetics.research.Research.hasFeature(journal, feature)) {
            com.genetics.research.ResearchNode node = com.genetics.research.Research.grantingNode(feature);
            return "Research " + (node != null ? node.getName() : feature) + " first.";
        }
        Ref<ChunkStore> block = blockEntity(world, pos);
        if (block == null) return "This Lab Station can't be upgraded.";
        if (!Materials.pay(player, accessor, upgrade.getMaterials())) return "You don't have the materials for this upgrade.";

        Store<ChunkStore> chunks = world.getChunkStore().getStore();
        chunks.putComponent(block, getComponentType(), new LabStationBlock(current + 1));
        BlockModule.BlockStateInfo info = chunks.getComponent(block, BlockModule.BlockStateInfo.getComponentType());
        if (info != null) info.markNeedsSaving();

        int sound = SoundEvent.getAssetMap().getIndex("SFX_Workbench_Upgrade_Complete_Default");
        if (sound != Integer.MIN_VALUE) SoundUtil.playSoundEvent3d(sound, SoundCategory.SFX, pos.x + 0.5, pos.y + 0.5, pos.z + 0.5, accessor);
        return null;
    }

    @Nullable
    private static Ref<ChunkStore> blockEntity(@Nonnull World world, @Nonnull Vector3i pos) {
        Ref<ChunkStore> section = world.getChunkStore().getChunkSectionReferenceAtBlock(pos.x, pos.y, pos.z);
        if (section == null || !section.isValid()) return null;
        return BlockModule.getBlockEntity(world.getChunkStore().getStore(), section, pos.x, pos.y, pos.z);
    }
}
