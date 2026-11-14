package com.genetics.action;

import com.hypixel.hytale.codec.Codec;
import com.hypixel.hytale.codec.KeyedCodec;
import com.hypixel.hytale.codec.builder.BuilderCodec;
import com.hypixel.hytale.server.core.entity.ItemUtils;
import com.hypixel.hytale.server.core.inventory.ItemStack;
import org.joml.Vector3d;

import javax.annotation.Nonnull;
import javax.annotation.Nullable;

/** {@code { "Type": "DropItem", "ItemId": "Food_Egg", "Quantity": 1 }}: drops an item at the entity's feet. */
public class DropItemAction extends Action {
    public static final String TYPE = "DropItem";

    public static final BuilderCodec<DropItemAction> CODEC = BuilderCodec.builder(DropItemAction.class, DropItemAction::new, BASE_CODEC)
            .append(new KeyedCodec<>("ItemId", Codec.STRING), (t, v) -> t.itemId = v, t -> t.itemId).add()
            .append(new KeyedCodec<>("Quantity", Codec.INTEGER), (t, v) -> t.quantity = v, t -> t.quantity).add()
            .build();

    private String itemId;
    private int quantity = 1;

    @Nullable
    public String getItemId() {
        return itemId;
    }

    @Override
    public void run(@Nonnull ActionContext context) {
        if (itemId == null || quantity <= 0) return;
        ItemUtils.throwItem(context.entity(), context.buffer(), new ItemStack(itemId, quantity, null), new Vector3d(0, -1, 0), 0.5F);
    }
}
