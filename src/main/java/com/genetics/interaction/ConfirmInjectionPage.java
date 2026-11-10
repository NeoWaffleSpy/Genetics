package com.genetics.interaction;

import com.genetics.capacity.GenomeCapacity;
import com.genetics.genome.GeneText;
import com.genetics.genome.Genome;
import com.hypixel.hytale.codec.Codec;
import com.hypixel.hytale.codec.KeyedCodec;
import com.hypixel.hytale.codec.builder.BuilderCodec;
import com.hypixel.hytale.component.Ref;
import com.hypixel.hytale.component.Store;
import com.hypixel.hytale.protocol.packets.interface_.CustomPageLifetime;
import com.hypixel.hytale.protocol.packets.interface_.CustomUIEventBindingType;
import com.hypixel.hytale.server.core.entity.entities.player.pages.InteractiveCustomUIPage;
import com.hypixel.hytale.server.core.inventory.ItemStack;
import com.hypixel.hytale.server.core.inventory.container.ItemContainer;
import com.hypixel.hytale.server.core.ui.builder.EventData;
import com.hypixel.hytale.server.core.ui.builder.UICommandBuilder;
import com.hypixel.hytale.server.core.ui.builder.UIEventBuilder;
import com.hypixel.hytale.server.core.universe.PlayerRef;
import com.hypixel.hytale.server.core.universe.world.storage.EntityStore;

import javax.annotation.Nonnull;

/**
 * The warning shown before injecting a genome that is over the user's Genome Capacity (design doc §3.4: "overflow
 * should be a choice the player makes, not a surprise"). Injecting anyway runs the normal injection, which degrades
 * the genome and causes massive sickness.
 */
public class ConfirmInjectionPage extends InteractiveCustomUIPage<ConfirmInjectionPage.EventPayload> {
    private static final String LAYOUT = "Genetics/ConfirmInjection.ui";

    private final Genome sample;
    private final ItemContainer container;
    private final short slot;
    private final ItemStack syringe;
    private boolean done;

    public ConfirmInjectionPage(@Nonnull PlayerRef playerRef, @Nonnull Genome sample, @Nonnull ItemContainer container, short slot, @Nonnull ItemStack syringe) {
        super(playerRef, CustomPageLifetime.CanDismiss, EventPayload.CODEC);
        this.sample = sample;
        this.container = container;
        this.slot = slot;
        this.syringe = syringe;
    }

    @Override
    public void build(@Nonnull Ref<EntityStore> ref, @Nonnull UICommandBuilder commands, @Nonnull UIEventBuilder events, @Nonnull Store<EntityStore> store) {
        commands.append(LAYOUT);
        events.addEventBinding(CustomUIEventBindingType.Activating, "#InjectButton", new EventData().append("Action", "Inject"), false);
        events.addEventBinding(CustomUIEventBindingType.Activating, "#CancelButton", new EventData().append("Action", "Cancel"), false);

        String mob = sample.source() != null ? sample.source().mobName() + "'s genes" : "This sample";
        double carried = Injection.lastingComplexity(sample);
        float capacity = GenomeCapacity.capacity(ref, store);
        commands.set("#Numbers.Text", mob + ":  Complexity " + GeneText.number(carried) + "  /  your capacity " + GeneText.number(capacity));
        commands.set("#Details.Text", "Your body can't hold this genome. If you inject it, it will degrade until it fits, destroying "
                + "its most valuable genes first, and you will be struck by severe genetic sickness.");
    }

    @Override
    public void handleDataEvent(@Nonnull Ref<EntityStore> ref, @Nonnull Store<EntityStore> store, @Nonnull EventPayload data) {
        if (done || data.action == null) return;
        done = true;
        if ("Inject".equals(data.action)) {
            // Only inject the syringe that opened this screen, if it is still where it was.
            ItemStack current = container.getItemStack(slot);
            if (current != null && current.equals(syringe)) {
                ItemStack result = Injection.inject(ref, playerRef, current, store);
                if (result != null) container.replaceItemStackInSlot(slot, current, result);
            }
        }
        close();
    }

    public static class EventPayload {
        public static final BuilderCodec<EventPayload> CODEC = BuilderCodec.builder(EventPayload.class, EventPayload::new)
                .append(new KeyedCodec<>("Action", Codec.STRING), (p, v) -> p.action = v, p -> p.action).add()
                .build();

        private String action;
    }
}
