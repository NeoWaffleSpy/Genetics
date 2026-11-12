package com.genetics.research;

import com.hypixel.hytale.assetstore.AssetExtraInfo;
import com.hypixel.hytale.assetstore.AssetRegistry;
import com.hypixel.hytale.assetstore.AssetStore;
import com.hypixel.hytale.assetstore.codec.AssetBuilderCodec;
import com.hypixel.hytale.assetstore.map.DefaultAssetMap;
import com.hypixel.hytale.assetstore.map.JsonAssetWithMap;
import com.hypixel.hytale.codec.Codec;

import com.hypixel.hytale.codec.KeyedCodec;

import javax.annotation.Nonnull;
import javax.annotation.Nullable;

/**
 * One node of the Research tree (design doc §9.5). One file per node in {@code Server/Genetics/Research/}:
 * <pre>
 * {
 *   "Name": "Splicing",
 *   "Description": "Unlocks the Splice tab and the Lab Station T2 upgrade.",
 *   "Branch": "Lab", "Order": 1, "Icon": "Helix",
 *   "Cost": 10,
 *   "Requires": [ "Processing" ],
 *   "Recipes": [ ],
 *   "Features": [ "Splicing" ]
 * }
 * </pre>
 * Researching a node teaches its recipes (vanilla recipe knowledge) and turns on its features. Starter nodes are
 * researched from the beginning.
 */
public final class ResearchNode implements JsonAssetWithMap<String, DefaultAssetMap<String, ResearchNode>> {
    public static final String PATH = "Genetics/Research";

    public static final AssetBuilderCodec<String, ResearchNode> CODEC = AssetBuilderCodec.builder(
                    ResearchNode.class, ResearchNode::new, Codec.STRING, (t, k) -> t.id = k, t -> t.id, (t, d) -> t.data = d, t -> t.data)
            .append(new KeyedCodec<>("Name", Codec.STRING), (t, v) -> t.name = v, t -> t.name)
            .documentation("Display name. Defaults to the id.").add()
            .append(new KeyedCodec<>("Description", Codec.STRING), (t, v) -> t.description = v, t -> t.description)
            .documentation("What researching it does, shown in the Research tab.").add()
            .append(new KeyedCodec<>("Branch", Codec.STRING), (t, v) -> t.branch = v, t -> t.branch)
            .documentation("Tree lane: Lab, Reagents, Syringes, Genome, or any new name (shown after the known ones).").add()
            .append(new KeyedCodec<>("Order", Codec.INTEGER), (t, v) -> t.order = v, t -> t.order)
            .documentation("Position in its lane, from the left.").add()
            .append(new KeyedCodec<>("Icon", Codec.STRING), (t, v) -> t.icon = v, t -> t.icon)
            .documentation("Icon shown on the node: Helix, Stat, Effect, Mutation or Syringe.").add()
            .append(new KeyedCodec<>("Cost", Codec.INTEGER), (t, v) -> t.cost = v, t -> t.cost)
            .documentation("Science spent to research it.").add()
            .append(new KeyedCodec<>("Starter", Codec.BOOLEAN), (t, v) -> t.starter = v, t -> t.starter)
            .documentation("Researched from the beginning.").add()
            .append(new KeyedCodec<>("Requires", Codec.STRING_ARRAY), (t, v) -> t.requires = v, t -> t.requires)
            .documentation("Ids of the nodes that must be researched first (any lane).").add()
            .append(new KeyedCodec<>("Recipes", Codec.STRING_ARRAY), (t, v) -> t.recipes = v, t -> t.recipes)
            .documentation("Item ids whose recipe is taught when researched (the recipes need KnowledgeRequired).").add()
            .append(new KeyedCodec<>("Features", Codec.STRING_ARRAY), (t, v) -> t.features = v, t -> t.features)
            .documentation("Lab features turned on: Splicing, MakePermanent.").add()
            .build();

    private static AssetStore<String, ResearchNode, DefaultAssetMap<String, ResearchNode>> store;

    private AssetExtraInfo.Data data;
    private String id;
    @Nullable
    private String name;
    @Nullable
    private String description;
    @Nullable
    private String branch;
    private int order;
    @Nullable
    private String icon;
    private int cost;
    private boolean starter;
    private String[] requires = new String[0];
    private String[] recipes = new String[0];
    private String[] features = new String[0];

    private ResearchNode() {
    }

    @Nonnull
    public static AssetStore<String, ResearchNode, DefaultAssetMap<String, ResearchNode>> getAssetStore() {
        if (store == null) store = AssetRegistry.getAssetStore(ResearchNode.class);
        return store;
    }

    @Nonnull
    public static DefaultAssetMap<String, ResearchNode> getAssetMap() {
        return getAssetStore().getAssetMap();
    }

    @Override
    public String getId() {
        return id;
    }

    @Nonnull
    public String getName() {
        return name != null ? name : id;
    }

    @Nonnull
    public String getDescription() {
        return description != null ? description : "";
    }

    @Nonnull
    public String getBranch() {
        return branch != null ? branch : "Other";
    }

    public int getOrder() {
        return order;
    }

    @Nonnull
    public String getIcon() {
        return icon != null ? icon : "Helix";
    }

    public int getCost() {
        return Math.max(0, cost);
    }

    public boolean isStarter() {
        return starter;
    }

    @Nonnull
    public String[] getRequires() {
        return requires != null ? requires : new String[0];
    }

    @Nonnull
    public String[] getRecipes() {
        return recipes != null ? recipes : new String[0];
    }

    @Nonnull
    public String[] getFeatures() {
        return features != null ? features : new String[0];
    }
}
