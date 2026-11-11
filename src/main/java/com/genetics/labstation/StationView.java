package com.genetics.labstation;

import com.genetics.codex.Balance;
import com.genetics.genome.Genome;
import com.genetics.process.CostView;
import com.genetics.tier.Materials;
import com.hypixel.hytale.component.ComponentAccessor;
import com.hypixel.hytale.component.Ref;
import com.hypixel.hytale.server.core.inventory.MaterialQuantity;
import com.hypixel.hytale.server.core.ui.builder.UICommandBuilder;
import com.hypixel.hytale.server.core.universe.world.storage.EntityStore;

import javax.annotation.Nonnull;
import javax.annotation.Nullable;
import java.util.List;

/**
 * The station part of the Lab Station screen: its tier and upgrade (left column), and the Process and Splice buttons
 * under the analysis, with a hint when they can't be used yet.
 */
final class StationView {
    private StationView() {
    }

    static void write(@Nonnull UICommandBuilder commands, int tier, @Nullable Genome sample,
                      @Nonnull Ref<EntityStore> ref, @Nonnull ComponentAccessor<EntityStore> accessor) {
        commands.set("#StationTier.Text", "Lab Station T" + tier);
        Balance.StationUpgrade upgrade = tier < LabStationBlock.MAX_TIER ? Balance.get().stationUpgrade(tier + 1) : null;
        commands.set("#UpgradePanel.Visible", upgrade != null);
        if (upgrade != null) {
            List<MaterialQuantity> cost = upgrade.getMaterials();
            commands.set("#UpgradeButton.Text", "Upgrade to T" + upgrade.getTier());
            commands.set("#UpgradeButton.Disabled", !Materials.canPay(ref, accessor, cost));
            CostView.write(commands, "#UpgradeCost", cost, ref, accessor);
        }

        boolean raw = sample != null && !sample.processed();
        boolean material = sample != null && sample.processed();
        commands.set("#ProcessButton.Visible", raw);
        commands.set("#SpliceButton.Visible", material);
        commands.set("#ProcessButton.Disabled", false);
        commands.set("#SpliceButton.Disabled", tier < 2);
        String hint = sample == null ? ""
                : raw && !sample.identified() ? "Process it to reveal its genes, choose which to keep and turn it into genetic material."
                : raw ? "Choose which genes to keep and turn this sample into genetic material."
                : tier < 2 ? "Upgrade the station to T2 to splice genetic material."
                : "Splice it with another genetic material from your inventory.";
        commands.set("#ActionHint.Text", hint);
    }
}
