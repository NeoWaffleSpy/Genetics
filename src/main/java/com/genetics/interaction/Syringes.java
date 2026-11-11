package com.genetics.interaction;

import com.genetics.genome.Genome;
import com.hypixel.hytale.server.core.Message;
import com.hypixel.hytale.server.core.asset.type.item.config.metadata.ItemDisplayMetadata;
import com.hypixel.hytale.server.core.inventory.ItemStack;
import org.bson.BsonValue;

import javax.annotation.Nonnull;
import javax.annotation.Nullable;

/**
 * Helpers shared by every syringe tier ({@code Genetics_Syringe}, {@code Genetics_Syringe_Copper}, …): recognising one,
 * filling it, naming it and emptying it.
 */
public final class Syringes {
    /** Item ids of all syringe tiers start with this. */
    public static final String ITEM_PREFIX = "Genetics_Syringe";

    private Syringes() {
    }

    public static boolean isSyringe(@Nullable String itemId) {
        return itemId != null && itemId.startsWith(ITEM_PREFIX);
    }

    public static boolean isSyringe(@Nullable ItemStack stack) {
        return stack != null && !stack.isEmpty() && isSyringe(stack.getItemId());
    }

    /** The syringe holding this genome, named after it: "Cow's Genes", or "Unidentified Cow Sample". */
    @Nonnull
    public static ItemStack fill(@Nonnull ItemStack syringe, @Nonnull Genome genome) {
        String name = genome.displayName();
        return genome.writeTo(syringe).withMetadata(ItemDisplayMetadata.KEYED_CODEC, new ItemDisplayMetadata(Message.raw(name), null));
    }

    /** The syringe, empty and with its normal name, ready to be used again. */
    @Nonnull
    public static ItemStack empty(@Nonnull ItemStack syringe) {
        return syringe.withMetadata(Genome.METADATA_KEY, (BsonValue) null)
                .withMetadata(Genome.LEGACY_METADATA_KEY, (BsonValue) null)
                .withMetadata(ItemDisplayMetadata.KEYED_CODEC.getKey(), (BsonValue) null);
    }
}
