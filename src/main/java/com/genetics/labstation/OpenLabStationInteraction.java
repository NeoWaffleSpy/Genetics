package com.genetics.labstation;

import com.genetics.interaction.Syringes;
import com.hypixel.hytale.codec.builder.BuilderCodec;
import com.hypixel.hytale.component.CommandBuffer;
import com.hypixel.hytale.component.Ref;
import com.hypixel.hytale.component.Store;
import com.hypixel.hytale.protocol.InteractionType;
import com.hypixel.hytale.server.core.asset.type.blocktype.config.BlockType;
import com.hypixel.hytale.server.core.entity.InteractionContext;
import com.hypixel.hytale.server.core.entity.entities.Player;
import com.hypixel.hytale.server.core.inventory.ItemStack;
import com.hypixel.hytale.server.core.inventory.container.ItemContainer;
import com.hypixel.hytale.server.core.inventory.container.filter.FilterActionType;
import com.hypixel.hytale.server.core.inventory.container.filter.ItemSlotFilter;
import com.hypixel.hytale.server.core.modules.block.BlockModule;
import com.hypixel.hytale.server.core.modules.block.components.ItemContainerBlock;
import com.hypixel.hytale.server.core.modules.interaction.interaction.CooldownHandler;
import com.hypixel.hytale.server.core.modules.interaction.interaction.config.client.SimpleBlockInteraction;
import com.hypixel.hytale.server.core.universe.PlayerRef;
import com.hypixel.hytale.server.core.universe.world.World;
import com.hypixel.hytale.server.core.universe.world.chunk.section.BlockSection;
import com.hypixel.hytale.server.core.universe.world.storage.ChunkStore;
import com.hypixel.hytale.server.core.universe.world.storage.EntityStore;
import org.joml.Vector3i;

import javax.annotation.Nonnull;
import javax.annotation.Nullable;

/** Opens the fully custom Lab Station screen ({@link LabStationPage}). Its one container slot only accepts syringes. */
public class OpenLabStationInteraction extends SimpleBlockInteraction {
    /** Interaction type id, referenced from block JSON as {@code "Type": "Genetics_OpenLabStation"}. */
    public static final String ID = "Genetics_OpenLabStation";

    public static final BuilderCodec<OpenLabStationInteraction> CODEC = BuilderCodec.builder(
            OpenLabStationInteraction.class, OpenLabStationInteraction::new, SimpleBlockInteraction.CODEC
    ).documentation("Opens the Lab Station for the block being interacted with.").build();

    private static final short SAMPLE_SLOT = 0;
    private static final ItemSlotFilter SYRINGES_ONLY = item -> item != null && Syringes.isSyringe(item.getId());

    @Override
    protected void interactWithBlock(
            @Nonnull World world,
            @Nonnull CommandBuffer<EntityStore> commandBuffer,
            @Nonnull InteractionType type,
            @Nonnull InteractionContext context,
            @Nullable ItemStack itemInHand,
            @Nonnull Vector3i pos,
            @Nonnull CooldownHandler cooldownHandler
    ) {
        Ref<EntityStore> ref = context.getEntity();
        Store<EntityStore> store = ref.getStore();
        Player player = commandBuffer.getComponent(ref, Player.getComponentType());
        PlayerRef playerRef = commandBuffer.getComponent(ref, PlayerRef.getComponentType());
        if (player == null || playerRef == null) return;

        ChunkStore chunkStore = world.getChunkStore();
        Ref<ChunkStore> sectionRef = chunkStore.getChunkSectionReferenceAtBlock(pos.x, pos.y, pos.z);
        if (sectionRef == null || !sectionRef.isValid()) return;

        Store<ChunkStore> chunkComponents = chunkStore.getStore();
        Ref<ChunkStore> blockRef = BlockModule.getBlockEntity(chunkComponents, sectionRef, pos.x, pos.y, pos.z);
        ItemContainerBlock containerBlock = blockRef != null ? chunkComponents.getComponent(blockRef, ItemContainerBlock.getComponentType()) : null;
        BlockSection section = chunkComponents.getComponent(sectionRef, BlockSection.getComponentType());
        if (containerBlock == null || section == null) return;

        BlockType blockType = BlockType.getAssetMap().getAsset(section.get(pos.x, pos.y, pos.z));
        if (blockType == null) return;

        ItemContainer container = containerBlock.getItemContainer();
        container.setSlotFilter(FilterActionType.ADD, SAMPLE_SLOT, SYRINGES_ONLY);

        player.getPageManager().openCustomPage(ref, store, new LabStationPage(playerRef, world, container));
    }

    @Override
    protected void simulateInteractWithBlock(
            @Nonnull InteractionType type, @Nonnull InteractionContext context, @Nullable ItemStack itemInHand, @Nonnull World world, @Nonnull Vector3i targetBlock
    ) {
    }

    @Override
    public String toString() {
        return "OpenLabStationInteraction{}" + super.toString();
    }
}
