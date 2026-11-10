package com.genetics.interaction;

import com.genetics.genome.ActiveGenome;
import com.genetics.genome.ComplexityCalculator;
import com.genetics.genome.Gene;
import com.genetics.genome.GeneText;
import com.genetics.genome.Genome;
import com.genetics.genome.GenomeApplier;
import com.genetics.genome.InjectionRoller;
import com.hypixel.hytale.codec.builder.BuilderCodec;
import com.hypixel.hytale.component.CommandBuffer;
import com.hypixel.hytale.component.Ref;
import com.hypixel.hytale.protocol.InteractionState;
import com.hypixel.hytale.protocol.InteractionType;
import com.hypixel.hytale.protocol.WaitForDataFrom;
import com.hypixel.hytale.server.core.Message;
import com.hypixel.hytale.server.core.asset.type.item.config.metadata.ItemDisplayMetadata;
import com.hypixel.hytale.server.core.entity.InteractionContext;
import com.hypixel.hytale.server.core.inventory.ItemStack;
import com.hypixel.hytale.server.core.modules.interaction.interaction.CooldownHandler;
import com.hypixel.hytale.server.core.modules.interaction.interaction.config.SimpleInstantInteraction;
import com.hypixel.hytale.server.core.universe.PlayerRef;
import com.hypixel.hytale.server.core.universe.world.storage.EntityStore;
import org.bson.BsonValue;

import javax.annotation.Nonnull;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.ThreadLocalRandom;

/**
 * Used by the Syringe on its user (secondary use):
 * <ul>
 *   <li>a filled syringe is <b>injected</b>: the sample is rolled ({@link InjectionRoller}), temporary effects start,
 *   and the lasting genes replace the user's active genome. The syringe is emptied and can be used again.</li>
 *   <li>an empty syringe <b>copies</b> the user's active genome into it, as a raw sample (design doc §9.1).</li>
 * </ul>
 */
public class SelfInjectInteraction extends SimpleInstantInteraction {
    public static final String ID = "Genetics_SelfInject";
    /** Source id written into copies of a player's genome. */
    public static final String PLAYER_SOURCE_ID = "Player";

    public static final BuilderCodec<SelfInjectInteraction> CODEC = BuilderCodec.builder(
            SelfInjectInteraction.class, SelfInjectInteraction::new, SimpleInstantInteraction.CODEC
    ).documentation("Injects the held syringe's sample into the user, or copies the user's genome into an empty syringe.").build();

    @Nonnull
    @Override
    public WaitForDataFrom getWaitForDataFrom() {
        return WaitForDataFrom.Server;
    }

    @Override
    protected void firstRun(@Nonnull InteractionType type, @Nonnull InteractionContext context, @Nonnull CooldownHandler cooldownHandler) {
        CommandBuffer<EntityStore> commandBuffer = context.getCommandBuffer();
        ItemStack syringe = context.getHeldItem();
        Ref<EntityStore> self = context.getEntity();
        PlayerRef user = commandBuffer != null ? commandBuffer.getComponent(self, PlayerRef.getComponentType()) : null;
        if (commandBuffer == null || syringe == null || user == null || !UseSyringeInteraction.ITEM_ID.equals(syringe.getItemId())) {
            fail(context);
            return;
        }

        ItemStack result = Genome.isPresentOn(syringe)
                ? inject(self, user, syringe, commandBuffer)
                : copy(self, user, syringe, commandBuffer);
        if (result == null || !context.getHeldItemContainer().replaceItemStackInSlot(context.getHeldItemSlot(), syringe, result).succeeded()) {
            fail(context);
            return;
        }
        context.setHeldItem(result);
    }

    /** Injects the syringe's sample. Returns the emptied syringe, or null if nothing happened. */
    private static ItemStack inject(@Nonnull Ref<EntityStore> self, @Nonnull PlayerRef user, @Nonnull ItemStack syringe,
                                    @Nonnull CommandBuffer<EntityStore> commandBuffer) {
        Genome sample = Genome.read(syringe);
        if (sample == null) {
            user.sendMessage(Message.raw("This sample comes from a newer version of Genetics and can't be injected."));
            return null;
        }

        String language = user.getLanguage();
        List<Gene> lasting = new ArrayList<>();
        List<String> report = new ArrayList<>();
        List<String> lost = new ArrayList<>();
        for (InjectionRoller.Roll roll : InjectionRoller.roll(sample, ThreadLocalRandom.current())) {
            Gene gene = roll.result();
            if (gene == null) {
                lost.add(GeneText.describe(roll.original(), language));
                continue;
            }
            if (gene instanceof Gene.Effect effect && !effect.permanent()) {
                GenomeApplier.startTemporary(self, commandBuffer, effect);
            } else {
                lasting.add(gene);
            }
            String text = GeneText.describe(gene, language);
            report.add(switch (roll.outcome()) {
                case WEAKENED -> text + " [weakened]";
                case INVERTED -> text + " [turned against you]";
                default -> text;
            });
        }

        ActiveGenome current = commandBuffer.getComponent(self, ActiveGenome.getComponentType());
        Genome previous = current != null ? current.getGenome() : null;
        Genome next = new Genome(Genome.FORMAT_VERSION, sample.processed(), ComplexityCalculator.compute(lasting), sample.source(), lasting);
        // Update the existing component in place, so the maintenance system never re-applies the previous genome.
        if (current != null) current.setGenome(next);
        else commandBuffer.putComponent(self, ActiveGenome.getComponentType(), new ActiveGenome(next));
        GenomeApplier.replace(self, commandBuffer, previous, next);

        String from = sample.source() != null ? sample.source().mobName() + "'s" : "the";
        StringBuilder message = new StringBuilder("You injected " + from + " genes.");
        message.append(report.isEmpty() ? " Nothing took hold." : " You gained: " + String.join(", ", report) + ".");
        if (!lost.isEmpty()) message.append(" Lost: ").append(String.join(", ", lost)).append(".");
        if (previous != null && !previous.genes().isEmpty()) message.append(" Your previous genome was replaced.");
        user.sendMessage(Message.raw(message.toString()));

        return syringe.withMetadata(Genome.METADATA_KEY, (BsonValue) null)
                .withMetadata(Genome.LEGACY_METADATA_KEY, (BsonValue) null)
                .withMetadata(ItemDisplayMetadata.KEYED_CODEC.getKey(), (BsonValue) null);
    }

    /** Copies the user's active genome into the empty syringe. Returns the filled syringe, or null if there is nothing to copy. */
    private static ItemStack copy(@Nonnull Ref<EntityStore> self, @Nonnull PlayerRef user, @Nonnull ItemStack syringe,
                                  @Nonnull CommandBuffer<EntityStore> commandBuffer) {
        ActiveGenome active = commandBuffer.getComponent(self, ActiveGenome.getComponentType());
        Genome genome = active != null ? active.getGenome() : null;
        if (genome == null || genome.genes().isEmpty()) {
            user.sendMessage(Message.raw("You have no active genome to copy."));
            return null;
        }

        String name = user.getUsername();
        Genome copy = Genome.raw(new Genome.Source(PLAYER_SOURCE_ID, name, null, System.currentTimeMillis()), genome.genes());
        user.sendMessage(Message.raw("Copied your genome (" + copy.genes().size() + " genes, Complexity " + GeneText.number(copy.complexity()) + ")."));
        return copy.writeTo(syringe)
                .withMetadata(ItemDisplayMetadata.KEYED_CODEC, new ItemDisplayMetadata(Message.raw(name + "'s Genes"), null));
    }

    private static void fail(@Nonnull InteractionContext context) {
        context.getState().state = InteractionState.Failed;
    }

    @Override
    public String toString() {
        return "SelfInjectInteraction{}" + super.toString();
    }
}
