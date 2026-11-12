package com.genetics.labstation;

import com.genetics.genome.GeneText;
import com.genetics.genome.Genome;
import com.genetics.genome.Translations;
import com.hypixel.hytale.server.core.Message;
import com.hypixel.hytale.server.core.asset.type.item.config.Item;
import com.hypixel.hytale.server.core.asset.type.item.config.ItemQuality;
import com.hypixel.hytale.server.core.inventory.ItemStack;
import com.hypixel.hytale.server.core.ui.Anchor;
import com.hypixel.hytale.server.core.ui.Value;
import com.hypixel.hytale.server.core.ui.builder.UICommandBuilder;

import javax.annotation.Nonnull;
import javax.annotation.Nullable;
import java.util.Set;

/**
 * Fills and places the Lab Station's own item tooltip ({@code #TooltipLayer} in {@code LabStation.ui}), a copy of
 * the game's: name in the rarity's colour, rarity label, description and durability, on the rarity's background.
 * <p>
 * The positions below mirror the layout of {@code LabStation.ui}, in the tooltip layer's coordinates: x from its
 * left edge, y from its bottom edge (the bottom of the inventory panel). Change them together with the layout.
 */
final class ItemTooltip {
    private static final String SKINS = "Genetics/TooltipSkins.ui";
    /** Rarity skins that exist in {@code TooltipSkins.ui}; any other rarity uses Default. */
    private static final Set<String> SKIN_NAMES = Set.of("Default", "Common", "Uncommon", "Rare", "Epic", "Legendary", "Junk", "Technical");

    private static final int LAYER_WIDTH = 1000;
    private static final int TOOLTIP_WIDTH = 320;
    private static final int ARROW_WIDTH = 33;
    private static final int SLOT = 64;
    private static final int SLOT_STEP = 68;
    private static final int SLOTS_PER_ROW = 9;
    /** Left edge of the inventory grids: (1000 - 642) / 2 panel offset + 17 padding. */
    private static final int GRID_X = 196;
    /** Top edge of the storage grid's first row: 420 panel - 38 title - 17 padding. */
    private static final int STORAGE_TOP = 365;
    /** Top edge of the hotbar: storage top - 268 storage - 12 margin. */
    private static final int HOTBAR_TOP = 85;
    /**
     * The sample slot: 17 padding from the station panel's left, and its top edge from the layer's bottom
     * (966 - 38 title - 17 padding - 2 line - 38 tabs - 1 line - 8 gap - 20 label). Slot B is 94 to its right.
     */
    private static final int SAMPLE_X = 17;
    private static final int SPLICE_X = SAMPLE_X + 94;
    private static final int SAMPLE_TOP = 842;
    /** The tooltip's bottom sits this far above the slot's top edge; the arrow hangs into the gap. */
    private static final int GAP_ABOVE_SLOT = 6;

    private ItemTooltip() {
    }

    static void write(@Nonnull UICommandBuilder commands, @Nonnull ItemStack stack, @Nonnull String grid, int slot, @Nullable String language) {
        Item item = stack.getItem();
        ItemQuality quality = ItemQuality.getAssetMap().getAsset(item.getQualityIndex());
        if (quality == null) quality = ItemQuality.DEFAULT_ITEM_QUALITY;

        // Background and arrow in the rarity's skin.
        String skin = skinName(quality);
        commands.set("#ItemTooltip.Background", Value.<String>ref(SKINS, "ItemTooltip" + skin));
        commands.set("#TooltipArrow.Background", Value.<String>ref(SKINS, "ItemTooltip" + skin + "Arrow"));

        // Header: name in the rarity's colour, rarity label on the right.
        String color = hex(quality.getTextColor());
        Message name = stack.getDisplayName();
        if (color != null) name = name.color(color);
        commands.set("#TooltipName.TextSpans", name);
        boolean showQuality = quality.isVisibleQualityLabel() && quality.getLocalizationKey() != null;
        commands.set("#TooltipQuality.Visible", showQuality);
        if (showQuality) {
            Message label = Message.translation(quality.getLocalizationKey());
            if (color != null) label = label.color(color);
            commands.set("#TooltipQuality.TextSpans", label);
        }

        // Description: a sample's summary for filled syringes, else the item's own description if it has one.
        Message description = description(stack, item, language);
        commands.set("#TooltipDescription.Visible", description != null);
        if (description != null) commands.set("#TooltipDescriptionText.TextSpans", description);

        // Footer: durability, for items that have it.
        boolean durable = stack.getMaxDurability() > 0;
        commands.set("#TooltipFooter.Visible", durable);
        if (durable) {
            commands.set("#TooltipDurability.Text", "Durability " + Math.round(stack.getDurability()) + " / " + Math.round(stack.getMaxDurability()));
        }

        place(commands, grid, slot);
        commands.set("#TooltipLayer.Visible", true);
    }

    @Nullable
    private static String hex(@Nullable com.hypixel.hytale.protocol.Color color) {
        return color == null ? null : String.format("#%02x%02x%02x", color.red & 0xFF, color.green & 0xFF, color.blue & 0xFF);
    }

    @Nonnull
    private static String skinName(@Nonnull ItemQuality quality) {
        String texture = quality.getItemTooltipTexture();
        if (texture != null) {
            String file = texture.substring(texture.lastIndexOf('/') + 1).replace(".png", "");
            String name = file.startsWith("ItemTooltip") ? file.substring("ItemTooltip".length()) : file;
            if (SKIN_NAMES.contains(name)) return name;
        }
        return "Default";
    }

    @Nullable
    private static Message description(@Nonnull ItemStack stack, @Nonnull Item item, @Nullable String language) {
        Genome genome = Genome.read(stack);
        if (genome != null) {
            if (!genome.identified()) return Message.raw("Unidentified sample. Process it at a Lab Station to see its genes.");
            return Message.raw((genome.processed() ? "Genetic material" : "Raw sample") + "  ·  Complexity " + GeneText.number(genome.complexity())
                    + "  ·  " + genome.genes().size() + (genome.genes().size() == 1 ? " gene" : " genes"));
        }
        String key = item.getDescriptionTranslationKey();
        if (key == null || Translations.resolve(language, key, "").isEmpty()) return null;
        return stack.getDisplayDescription();
    }

    /**
     * Puts the tooltip above the hovered inventory slot, centred on it with the arrow pointing at it, like the
     * game's. The station's slot is near the top of the screen, so its tooltip goes to the right of it instead.
     */
    private static void place(@Nonnull UICommandBuilder commands, @Nonnull String grid, int slot) {
        int indent;
        int gap;
        boolean arrow = true;
        int centerX;
        switch (grid) {
            case LabStationPage.STORAGE_GRID -> {
                centerX = GRID_X + (slot % SLOTS_PER_ROW) * SLOT_STEP + SLOT / 2;
                gap = STORAGE_TOP - (slot / SLOTS_PER_ROW) * SLOT_STEP + GAP_ABOVE_SLOT;
            }
            case LabStationPage.HOTBAR_GRID -> {
                centerX = GRID_X + (slot % SLOTS_PER_ROW) * SLOT_STEP + SLOT / 2;
                gap = HOTBAR_TOP + GAP_ABOVE_SLOT;
            }
            case LabStationPage.SPLICE_GRID -> {
                centerX = SPLICE_X + SLOT + 12 + TOOLTIP_WIDTH / 2;
                gap = SAMPLE_TOP - SLOT - 130;
                arrow = false;
            }
            default -> {
                centerX = SAMPLE_X + SLOT + 12 + TOOLTIP_WIDTH / 2;
                gap = SAMPLE_TOP - SLOT - 130;
                arrow = false;
            }
        }
        indent = Math.clamp(centerX - TOOLTIP_WIDTH / 2, 0, LAYER_WIDTH - TOOLTIP_WIDTH);

        commands.setObject("#TooltipGap.Anchor", anchor(null, Math.max(0, gap), null, null));
        commands.setObject("#TooltipIndent.Anchor", anchor(indent, null, null, null));
        commands.set("#TooltipArrow.Visible", arrow);
        if (arrow) {
            int arrowLeft = Math.clamp(centerX - indent - ARROW_WIDTH / 2, 12, TOOLTIP_WIDTH - ARROW_WIDTH - 12);
            commands.setObject("#TooltipArrow.Anchor", anchor(ARROW_WIDTH, 24, arrowLeft, -8));
        }
    }

    @Nonnull
    private static Anchor anchor(@Nullable Integer width, @Nullable Integer height, @Nullable Integer left, @Nullable Integer bottom) {
        Anchor anchor = new Anchor();
        if (width != null) anchor.setWidth(Value.of(width));
        if (height != null) anchor.setHeight(Value.of(height));
        if (left != null) anchor.setLeft(Value.of(left));
        if (bottom != null) anchor.setBottom(Value.of(bottom));
        return anchor;
    }
}
