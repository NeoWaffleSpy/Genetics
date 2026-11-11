package com.genetics.process;

import com.genetics.tier.Materials;
import com.hypixel.hytale.component.ComponentAccessor;
import com.hypixel.hytale.component.Ref;
import com.hypixel.hytale.server.core.inventory.MaterialQuantity;
import com.hypixel.hytale.server.core.ui.builder.UICommandBuilder;
import com.hypixel.hytale.server.core.universe.world.storage.EntityStore;

import javax.annotation.Nonnull;
import java.util.List;

/** Writes a material cost into a {@code #CostList} group: one icon and "have / need" per material. */
public final class CostView {
    private static final String ENTRY = "Genetics/CostEntry.ui";

    private CostView() {
    }

    public static void write(@Nonnull UICommandBuilder commands, @Nonnull String list, @Nonnull List<MaterialQuantity> cost,
                      @Nonnull Ref<EntityStore> ref, @Nonnull ComponentAccessor<EntityStore> accessor) {
        commands.clear(list);
        boolean free = Materials.isFree(ref, accessor);
        for (int i = 0; i < cost.size(); i++) {
            MaterialQuantity material = cost.get(i);
            String entry = list + "[" + i + "] ";
            commands.append(list, ENTRY);
            commands.set(entry + "#Icon.Visible", material.getItemId() != null);
            if (material.getItemId() != null) commands.set(entry + "#Icon.ItemId", material.getItemId());
            int have = Materials.carried(ref, accessor, material);
            commands.set(entry + "#Amount.Text", (material.getItemId() == null ? material.getResourceTypeId() + " " : "")
                    + have + " / " + material.getQuantity());
            commands.set(entry + "#Amount.Style.TextColor", free || have >= material.getQuantity() ? "#8fd17a" : "#e06666");
        }
    }
}
