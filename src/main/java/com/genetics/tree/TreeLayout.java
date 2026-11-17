package com.genetics.tree;

import com.hypixel.hytale.assetstore.AssetExtraInfo;
import com.hypixel.hytale.assetstore.AssetRegistry;
import com.hypixel.hytale.assetstore.AssetStore;
import com.hypixel.hytale.assetstore.codec.AssetBuilderCodec;
import com.hypixel.hytale.assetstore.map.DefaultAssetMap;
import com.hypixel.hytale.assetstore.map.JsonAssetWithMap;
import com.hypixel.hytale.codec.Codec;
import com.hypixel.hytale.codec.KeyedCodec;
import com.hypixel.hytale.codec.codecs.array.ArrayCodec;

import javax.annotation.Nonnull;
import javax.annotation.Nullable;
import java.util.ArrayList;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

/**
 * The Genome Tree's nodes (design doc §8.3), read from {@code Server/Genetics/GenomeTree/Default.json}. A pack can
 * replace the whole tree by overriding that file. Links are undirected: a link written on one node works both ways.
 */
public final class TreeLayout implements JsonAssetWithMap<String, DefaultAssetMap<String, TreeLayout>> {
    public static final String PATH = "Genetics/GenomeTree";
    public static final String DEFAULT_ID = "Default";

    public static final AssetBuilderCodec<String, TreeLayout> CODEC = AssetBuilderCodec.builder(
                    TreeLayout.class, TreeLayout::new, Codec.STRING, (t, k) -> t.id = k, t -> t.id, (t, d) -> t.data = d, t -> t.data)
            .append(new KeyedCodec<>("Cell", Codec.INTEGER), (t, v) -> t.cell = v, t -> t.cell)
            .documentation("Size of one grid cell on screen, in pixels.").add()
            .append(new KeyedCodec<>("Nodes", new ArrayCodec<>(TreeNode.CODEC, TreeNode[]::new)), (t, v) -> t.nodes = v, t -> t.nodes)
            .documentation("Every node of the tree. Exactly one has Type Origin: the start, always owned.").add()
            .build();

    private static final TreeLayout EMPTY = new TreeLayout();
    private static AssetStore<String, TreeLayout, DefaultAssetMap<String, TreeLayout>> store;

    private AssetExtraInfo.Data data;
    private String id = DEFAULT_ID;
    private int cell = 28;
    private TreeNode[] nodes = new TreeNode[0];
    @Nullable
    private transient Map<String, TreeNode> byId;
    @Nullable
    private transient Map<String, Set<String>> neighbours;

    TreeLayout() {
    }

    @Nonnull
    public static AssetStore<String, TreeLayout, DefaultAssetMap<String, TreeLayout>> getAssetStore() {
        if (store == null) store = AssetRegistry.getAssetStore(TreeLayout.class);
        return store;
    }

    @Nonnull
    public static DefaultAssetMap<String, TreeLayout> getAssetMap() {
        return getAssetStore().getAssetMap();
    }

    /** The active tree: the Default file, or an empty tree if it is missing. */
    @Nonnull
    public static TreeLayout get() {
        TreeLayout loaded = getAssetMap().getAsset(DEFAULT_ID);
        return loaded != null ? loaded : EMPTY;
    }

    @Override
    public String getId() {
        return id;
    }

    public int getCell() {
        return cell > 0 ? cell : 28;
    }

    @Nonnull
    public List<TreeNode> getNodes() {
        index();
        return List.copyOf(byId.values());
    }

    @Nullable
    public TreeNode node(@Nullable String nodeId) {
        if (nodeId == null) return null;
        index();
        return byId.get(nodeId);
    }

    /** The start node, or null if the tree has none. */
    @Nullable
    public TreeNode origin() {
        for (TreeNode node : getNodes()) if (node.kind() == TreeNode.Kind.ORIGIN) return node;
        return null;
    }

    /** Ids of the nodes linked to this one, in either direction. */
    @Nonnull
    public Set<String> neighbours(@Nonnull String nodeId) {
        index();
        Set<String> out = neighbours.get(nodeId);
        return out != null ? Collections.unmodifiableSet(out) : Set.of();
    }

    /** Every link once, as [a, b] with a before b in file order. */
    @Nonnull
    public List<String[]> links() {
        index();
        List<String[]> out = new ArrayList<>();
        Set<String> seen = new LinkedHashSet<>();
        for (TreeNode node : byId.values()) {
            for (String other : neighbours(node.getId())) {
                String key = node.getId().compareTo(other) < 0 ? node.getId() + "|" + other : other + "|" + node.getId();
                if (seen.add(key) && byId.containsKey(other)) out.add(new String[]{node.getId(), other});
            }
        }
        return out;
    }

    private void index() {
        if (byId != null) return;
        Map<String, TreeNode> ids = new LinkedHashMap<>();
        if (nodes != null) for (TreeNode node : nodes) if (node != null && !node.getId().isEmpty()) ids.putIfAbsent(node.getId(), node);
        Map<String, Set<String>> links = new LinkedHashMap<>();
        for (TreeNode node : ids.values()) {
            for (String other : node.getLinks()) {
                if (other.equals(node.getId())) continue;
                links.computeIfAbsent(node.getId(), k -> new LinkedHashSet<>()).add(other);
                links.computeIfAbsent(other, k -> new LinkedHashSet<>()).add(node.getId());
            }
        }
        neighbours = links;
        byId = ids;
    }
}
