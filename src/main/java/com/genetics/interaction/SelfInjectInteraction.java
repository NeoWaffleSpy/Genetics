package com.genetics.interaction;

import com.genetics.genome.Genome;
import com.hypixel.hytale.codec.builder.BuilderCodec;
import com.hypixel.hytale.component.CommandBuffer;
import com.hypixel.hytale.component.Ref;
import com.hypixel.hytale.protocol.InteractionState;
import com.hypixel.hytale.protocol.InteractionType;
import com.hypixel.hytale.protocol.WaitForDataFrom;
import com.hypixel.hytale.server.core.entity.InteractionContext;
import com.hypixel.hytale.server.core.entity.entities.Player;
import com.hypixel.hytale.server.core.inventory.ItemStack;
import com.hypixel.hytale.server.core.modules.interaction.interaction.CooldownHandler;
import com.hypixel.hytale.server.core.modules.interaction.interaction.config.SimpleInstantInteraction;
import com.hypixel.hytale.server.core.universe.PlayerRef;
import com.hypixel.hytale.server.core.universe.world.storage.EntityStore;

import javax.annotation.Nonnull;

/**
 * Used by every syringe on its user (secondary use):
 * <ul>
 *   <li>a filled syringe is <b>injected</b> ({@link Injection#inject}). If the sample's genome is over the user's
 *   Genome Capacity, a warning screen asks for confirmation first ({@link ConfirmInjectionPage}).</li>
 *   <li>an empty syringe <b>copies</b> the user's active genome into it, as a raw sample (design doc §9.1).</li>
 * </ul>
 */
public class SelfInjectInteraction extends SimpleInstantInteraction {
    public static final String ID = "Genetics_SelfInject";

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
        if (commandBuffer == null || user == null || !Syringes.isSyringe(syringe)) {
            fail(context);
            return;
        }

        Genome sample = Genome.read(syringe);
        if (sample != null && Injection.isOverCapacity(sample, self, commandBuffer)) {
            Player player = commandBuffer.getComponent(self, Player.getComponentType());
            if (player != null) {
                player.getPageManager().openCustomPage(self, self.getStore(),
                        new ConfirmInjectionPage(user, sample, context.getHeldItemContainer(), context.getHeldItemSlot(), syringe));
            }
            return;
        }

        ItemStack result = Genome.isPresentOn(syringe)
                ? Injection.inject(self, user, syringe, commandBuffer)
                : Injection.copy(self, user, syringe, commandBuffer);
        if (result == null || !context.getHeldItemContainer().replaceItemStackInSlot(context.getHeldItemSlot(), syringe, result).succeeded()) {
            fail(context);
            return;
        }
        context.setHeldItem(result);
    }

    private static void fail(@Nonnull InteractionContext context) {
        context.getState().state = InteractionState.Failed;
    }

    @Override
    public String toString() {
        return "SelfInjectInteraction{}" + super.toString();
    }
}
