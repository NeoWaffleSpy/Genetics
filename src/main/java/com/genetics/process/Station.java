package com.genetics.process;

import com.genetics.labstation.LabStationBlock;
import com.genetics.labstation.LabStationPage;
import com.hypixel.hytale.component.Ref;
import com.hypixel.hytale.component.Store;
import com.hypixel.hytale.server.core.entity.entities.Player;
import com.hypixel.hytale.server.core.inventory.container.ItemContainer;
import com.hypixel.hytale.server.core.universe.PlayerRef;
import com.hypixel.hytale.server.core.universe.world.World;
import com.hypixel.hytale.server.core.universe.world.storage.EntityStore;
import org.joml.Vector3i;

import javax.annotation.Nonnull;

/** The Lab Station a screen was opened from: its world, position and sample slot. */
public record Station(@Nonnull World world, @Nonnull Vector3i pos, @Nonnull ItemContainer container) {
    public static final short SAMPLE_SLOT = 0;

    public int tier() {
        return LabStationBlock.tierAt(world, pos);
    }

    /** Opens the Lab Station screen again (used by the Back buttons and after processing or splicing). */
    public void reopen(@Nonnull Ref<EntityStore> ref, @Nonnull Store<EntityStore> store, @Nonnull PlayerRef playerRef) {
        Player player = store.getComponent(ref, Player.getComponentType());
        if (player != null) player.getPageManager().openCustomPage(ref, store, new LabStationPage(playerRef, this));
    }
}
