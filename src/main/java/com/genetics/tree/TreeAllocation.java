package com.genetics.tree;

import javax.annotation.Nonnull;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.Set;

/**
 * The nodes a player owns on the Genome Tree, with each Sink's rank (other nodes have rank 1). The Origin is always
 * owned and never stored. Saved as "Id" or "Id:Rank" strings.
 */
public final class TreeAllocation {
    private final Map<String, Integer> ranks = new LinkedHashMap<>();

    public TreeAllocation() {
    }

    public TreeAllocation(@Nonnull TreeAllocation other) {
        ranks.putAll(other.ranks);
    }

    public boolean owns(@Nonnull String id) {
        return ranks.containsKey(id);
    }

    /** The node's rank: 0 when not owned. */
    public int rank(@Nonnull String id) {
        return ranks.getOrDefault(id, 0);
    }

    @Nonnull
    public Set<String> ids() {
        return Collections.unmodifiableSet(ranks.keySet());
    }

    public void set(@Nonnull String id, int rank) {
        if (rank <= 0) ranks.remove(id);
        else ranks.put(id, rank);
    }

    public void remove(@Nonnull String id) {
        ranks.remove(id);
    }

    public void clear() {
        ranks.clear();
    }

    public boolean isEmpty() {
        return ranks.isEmpty();
    }

    @Nonnull
    public String[] save() {
        return ranks.entrySet().stream().map(e -> e.getValue() > 1 ? e.getKey() + ":" + e.getValue() : e.getKey()).toArray(String[]::new);
    }

    @Nonnull
    public static TreeAllocation load(String[] saved) {
        TreeAllocation out = new TreeAllocation();
        if (saved == null) return out;
        for (String entry : saved) {
            if (entry == null || entry.isBlank()) continue;
            int colon = entry.lastIndexOf(':');
            int rank = 1;
            String id = entry;
            if (colon > 0) {
                try {
                    rank = Integer.parseInt(entry.substring(colon + 1).trim());
                    id = entry.substring(0, colon);
                } catch (NumberFormatException ignored) {
                }
            }
            out.set(id.trim(), Math.max(1, rank));
        }
        return out;
    }
}
