package com.genetics.labstation;

import com.genetics.interaction.UseSyringeInteraction;
import com.hypixel.hytale.codec.builder.BuilderCodec;
import com.hypixel.hytale.component.CommandBuffer;
import com.hypixel.hytale.component.Ref;
import com.hypixel.hytale.component.Store;
import com.hypixel.hytale.protocol.InteractionType;
import com.hypixel.hytale.server.core.asset.type.blocktype.config.BlockType;
import com.hypixel.hytale.server.core.entity.InteractionContext;
import com.hypixel.hytale.server.core.entity.UUIDComponent;
import com.hypixel.hytale.server.core.entity.entities.Player;
import com.hypixel.hytale.server.core.entity.entities.player.windows.ContainerBlockWindow;
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
import java.util.Map;
import java.util.UUID;

/** Opens the Lab Station screen for the block being used. Its one container slot only accepts syringes. */
public class OpenLabStationInteraction extends SimpleBlockInteraction {
    /** Interaction type id, referenced from block JSON as {@code "Type": "OpenLabStation"}. */
    public static final String ID = "OpenLabStation";

    public static final BuilderCodec<OpenLabStationInteraction> CODEC = BuilderCodec.builder(
            OpenLabStationInteraction.class, OpenLabStationInteraction::new, SimpleBlockInteraction.CODEC
    ).documentation("Opens the Lab Station screen for the block being interacted with.").build();

    private static final short SAMPLE_SLOT = 0;
    private static final ItemSlotFilter SYRINGES_ONLY = item -> item != null && UseSyringeInteraction.ITEM_ID.equals(item.getId());

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
        UUIDComponent uuidComponent = commandBuffer.getComponent(ref, UUIDComponent.getComponentType());
        if (player == null || playerRef == null || uuidComponent == null) return;

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

        ContainerBlockWindow window = new ContainerBlockWindow(
                pos.x, pos.y, pos.z, section.getRotationIndex(pos.x, pos.y, pos.z), blockType, container
        );
        UUID uuid = uuidComponent.getUuid();
        Map<UUID, ContainerBlockWindow> windows = containerBlock.getWindows();
        if (windows.putIfAbsent(uuid, window) != null) return;

        LabStationPage page = new LabStationPage(playerRef, world, new Vector3i(pos), container, window);
        if (player.getPageManager().openCustomPageWithWindows(ref, store, page, window)) {
            window.registerCloseEvent(event -> windows.remove(uuid, window));
        } else {
            windows.remove(uuid, window);
        }
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
