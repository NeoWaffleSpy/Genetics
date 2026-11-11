package com.genetics.tier;

import com.hypixel.hytale.component.ComponentAccessor;
import com.hypixel.hytale.component.Ref;
import com.hypixel.hytale.protocol.GameMode;
import com.hypixel.hytale.server.core.Message;
import com.hypixel.hytale.server.core.asset.type.item.config.Item;
import com.hypixel.hytale.server.core.entity.entities.Player;
import com.hypixel.hytale.server.core.inventory.InventoryComponent;
import com.hypixel.hytale.server.core.inventory.MaterialQuantity;
import com.hypixel.hytale.server.core.inventory.container.CombinedItemContainer;
import com.hypixel.hytale.server.core.universe.world.storage.EntityStore;

import javax.annotation.Nonnull;
import java.util.List;

/**
 * Paying material costs from a player's backpack, storage and hotbar, the same way vanilla bench upgrades do.
 * Players in Creative mode pay nothing, also like vanilla.
 */
public final class Materials {
    private Materials() {
    }

    public static boolean isFree(@Nonnull Ref<EntityStore> ref, @Nonnull ComponentAccessor<EntityStore> accessor) {
        Player player = accessor.getComponent(ref, Player.getComponentType());
        return player != null && player.getGameMode() == GameMode.Creative;
    }

    /** How many of this material the player carries. */
    public static int carried(@Nonnull Ref<EntityStore> ref, @Nonnull ComponentAccessor<EntityStore> accessor, @Nonnull MaterialQuantity material) {
        return inventory(ref, accessor).countRemovableMaterial(material);
    }

    public static boolean canPay(@Nonnull Ref<EntityStore> ref, @Nonnull ComponentAccessor<EntityStore> accessor, @Nonnull List<MaterialQuantity> cost) {
        return cost.isEmpty() || isFree(ref, accessor) || inventory(ref, accessor).canRemoveMaterials(cost);
    }

    /** Removes the cost from the player's inventory. Returns false, removing nothing, if they can't pay it. */
    public static boolean pay(@Nonnull Ref<EntityStore> ref, @Nonnull ComponentAccessor<EntityStore> accessor, @Nonnull List<MaterialQuantity> cost) {
        if (cost.isEmpty() || isFree(ref, accessor)) return true;
        CombinedItemContainer inventory = inventory(ref, accessor);
        if (!inventory.canRemoveMaterials(cost)) return false;
        return inventory.removeMaterials(cost).succeeded();
    }

    /** The material's display name: the item's name, or the resource type's id. */
    @Nonnull
    public static Message name(@Nonnull MaterialQuantity material) {
        if (material.getItemId() != null) {
            Item item = Item.getAssetMap().getAsset(material.getItemId());
            return item != null ? item.getTranslationMessage() : Message.raw(material.getItemId());
        }
        return Message.raw(material.getResourceTypeId() != null ? material.getResourceTypeId().replace('_', ' ') : "?");
    }

    @Nonnull
    private static CombinedItemContainer inventory(@Nonnull Ref<EntityStore> ref, @Nonnull ComponentAccessor<EntityStore> accessor) {
        return InventoryComponent.getCombined(accessor, ref, InventoryComponent.BACKPACK_STORAGE_HOTBAR);
    }
}
