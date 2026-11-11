package com.genetics.tier;

import com.hypixel.hytale.server.core.inventory.MaterialQuantity;

import javax.annotation.Nonnull;
import javax.annotation.Nullable;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;

/**
 * Complexity → tier ({@code TierResolver} in the design doc), tier → cost ({@code CostCalculator}), and whether a
 * Lab Station may work in a tier ({@code StationTierGate}).
 */
public final class Tiers {
    private Tiers() {
    }

    /** All tiers, lowest first. */
    @Nonnull
    public static List<ComplexityTier> all() {
        List<ComplexityTier> tiers = new ArrayList<>(ComplexityTier.getAssetMap().getAssetMap().values());
        tiers.sort(Comparator.comparingDouble(ComplexityTier::getMinComplexity));
        return tiers;
    }

    /** The tier this Complexity falls in: the highest tier whose minimum is at or below it (the lowest tier below all). */
    @Nullable
    public static ComplexityTier forComplexity(double complexity) {
        ComplexityTier found = null;
        for (ComplexityTier tier : all()) {
            if (found == null || tier.getMinComplexity() <= complexity) found = tier;
        }
        return found;
    }

    /** Position of a tier, lowest first, or -1 if unknown. */
    public static int rank(@Nullable String tierId) {
        if (tierId == null) return -1;
        List<ComplexityTier> tiers = all();
        for (int i = 0; i < tiers.size(); i++) {
            if (tiers.get(i).getId().equalsIgnoreCase(tierId)) return i;
        }
        return -1;
    }

    public static int rank(@Nullable ComplexityTier tier) {
        return tier != null ? rank(tier.getId()) : -1;
    }

    /** True if a Lab Station of this tier may process or splice in this Complexity tier ({@code StationTierGate}). */
    public static boolean allows(int stationTier, @Nonnull ComplexityTier tier) {
        return stationTier >= tier.getStationTier();
    }

    /**
     * What working at this Complexity costs in its tier. Each cost's Scaling gets the Complexity above the tier's
     * minimum. Amounts are rounded up; zero amounts are left out.
     */
    @Nonnull
    public static List<MaterialQuantity> cost(@Nonnull ComplexityTier tier, double complexity) {
        List<MaterialQuantity> out = new ArrayList<>();
        double points = Math.max(0, complexity - tier.getMinComplexity());
        for (ComplexityTier.Cost cost : tier.getCosts()) {
            if (cost.getScaling() == null || (cost.getItemId() == null && cost.getResourceTypeId() == null)) continue;
            int quantity = (int) Math.ceil(cost.getScaling().amount(points) - 1e-9);
            if (quantity <= 0) continue;
            out.add(new MaterialQuantity(cost.getItemId(), cost.getItemId() == null ? cost.getResourceTypeId() : null, null, quantity, null));
        }
        return out;
    }
}
