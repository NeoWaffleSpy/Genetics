package com.genetics.tree;

import com.hypixel.hytale.codec.Codec;
import com.hypixel.hytale.codec.KeyedCodec;
import com.hypixel.hytale.codec.builder.BuilderCodec;

import javax.annotation.Nonnull;
import javax.annotation.Nullable;
import java.util.List;
import java.util.Locale;

/**
 * One node of the Genome Tree (design doc §8.3), placed on the tree's grid and linked to its neighbours.
 * <pre>
 * { "Id": "N11_5", "Type": "Small", "X": 11, "Y": 5, "Stat": "Health", "Value": 2, "Links": [ "Origin", "N11_4" ] }
 * { "Id": "N9_2", "Type": "Core", "X": 9, "Y": 2, "Buff": "Hide", "Tiers": [ "Genetics_Hide_T1", "Genetics_Hide_T2" ] }
 * { "Id": "N13_2", "Type": "Notable", "X": 13, "Y": 2, "Mutation": "Undying" }
 * </pre>
 */
public final class TreeNode {
    /** What a node gives: the start, a basic stat, a special stat, a mutation, a tier of a permanent buff, or a repeatable stat. */
    public enum Kind { ORIGIN, SMALL, BIG, NOTABLE, CORE, SINK }

    public static final BuilderCodec<TreeNode> CODEC = BuilderCodec.builder(TreeNode.class, TreeNode::new)
            .append(new KeyedCodec<>("Id", Codec.STRING), (t, v) -> t.id = v, t -> t.id)
            .documentation("Unique id; saved in players' allocations, so keep it when moving a node.").add()
            .append(new KeyedCodec<>("Type", Codec.STRING), (t, v) -> t.type = v, t -> t.type)
            .documentation("Origin, Small (basic stat), Big (special stat), Notable (mutation), Core (permanent buff tier) or Sink (repeatable stat).").add()
            .append(new KeyedCodec<>("X", Codec.INTEGER), (t, v) -> t.x = v, t -> t.x)
            .documentation("Column on the tree's grid, from the left.").add()
            .append(new KeyedCodec<>("Y", Codec.INTEGER), (t, v) -> t.y = v, t -> t.y)
            .documentation("Row on the tree's grid, from the top.").add()
            .append(new KeyedCodec<>("Links", Codec.STRING_ARRAY), (t, v) -> t.links = v, t -> t.links)
            .documentation("Ids of linked nodes, in the same row or column. A link can be written on either node.").add()
            .append(new KeyedCodec<>("Stat", Codec.STRING), (t, v) -> t.stat = v, t -> t.stat)
            .documentation("Small, Big, Sink: the stat raised (a vanilla stat, or Genetics_DamageResistance / Genetics_KnockbackResistance).").add()
            .append(new KeyedCodec<>("Value", Codec.DOUBLE), (t, v) -> t.value = v, t -> t.value)
            .documentation("Small, Big: percent added. Sink: percent added per rank.").add()
            .append(new KeyedCodec<>("Mutation", Codec.STRING), (t, v) -> t.mutation = v, t -> t.mutation)
            .documentation("Notable: the mutation (Mutation Codex id).").add()
            .append(new KeyedCodec<>("Buff", Codec.STRING), (t, v) -> t.buff = v, t -> t.buff)
            .documentation("Core: the buff's name. Owning n Cores of a buff gives its tier n.").add()
            .append(new KeyedCodec<>("Tiers", Codec.STRING_ARRAY), (t, v) -> t.tiers = v, t -> t.tiers)
            .documentation("Core: the buff's effect id per tier, lowest first. A buff has at most this many Cores.").add()
            .append(new KeyedCodec<>("Cost", Codec.DOUBLE), (t, v) -> t.cost = v, t -> t.cost)
            .documentation("Base Complexity cost, instead of the type's default from Balance → GenomeTree. Sink: cost of the first rank.").add()
            .build();

    private String id = "";
    private String type = "Small";
    private int x;
    private int y;
    private String[] links = new String[0];
    @Nullable
    private String stat;
    private double value;
    @Nullable
    private String mutation;
    @Nullable
    private String buff;
    private String[] tiers = new String[0];
    private double cost;

    TreeNode() {
    }

    @Nonnull
    public String getId() {
        return id != null ? id : "";
    }

    /** The node's kind, or null if its Type is unknown. */
    @Nullable
    public Kind kind() {
        if (type == null) return null;
        try {
            return Kind.valueOf(type.trim().toUpperCase(Locale.ROOT));
        } catch (IllegalArgumentException e) {
            return null;
        }
    }

    public int getX() {
        return x;
    }

    public int getY() {
        return y;
    }

    @Nonnull
    public List<String> getLinks() {
        return links != null ? List.of(links) : List.of();
    }

    @Nullable
    public String getStat() {
        return stat;
    }

    public double getValue() {
        return value;
    }

    @Nullable
    public String getMutation() {
        return mutation;
    }

    @Nullable
    public String getBuff() {
        return buff;
    }

    @Nonnull
    public List<String> getTiers() {
        return tiers != null ? List.of(tiers) : List.of();
    }

    /** The node's own base cost, or 0 to use the type's default. */
    public double getCost() {
        return cost;
    }
}
