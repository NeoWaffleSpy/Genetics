package com.genetics.sickness;

import com.genetics.action.ActionRunner;
import com.hypixel.hytale.codec.Codec;
import com.hypixel.hytale.codec.KeyedCodec;
import com.hypixel.hytale.codec.builder.BuilderCodec;
import com.hypixel.hytale.component.Component;
import com.hypixel.hytale.component.ComponentType;
import com.hypixel.hytale.server.core.universe.world.storage.EntityStore;

import java.util.HashMap;
import java.util.Map;

/**
 * Per-entity bookkeeping for {@link SicknessSystem}. Only {@code HeldValue} is saved: the sickness an entity had when
 * it died, restored after it respawns (respawning resets every stat). The rest is rebuilt on load.
 */
public class SicknessState implements Component<EntityStore> {
    public static final String ID = "Genetics_SicknessState";

    public static final BuilderCodec<SicknessState> CODEC = BuilderCodec.builder(SicknessState.class, SicknessState::new)
            .append(new KeyedCodec<>("HeldValue", Codec.DOUBLE), (t, v) -> t.heldValue = v, t -> t.heldValue)
            .add()
            .build();

    private static ComponentType<EntityStore, SicknessState> componentType;

    /** Sickness kept while dead, or -1. */
    double heldValue = -1;
    /** Id of the band the entity was in at the last tick, or null before the first tick. */
    transient String band;
    /** Seconds until the next sickness event. */
    transient double nextEvent;
    /** Seconds until the next decay step and HUD refresh. */
    transient double nextStep;
    /** Remaining cooldown per event id. */
    final transient Map<String, Double> cooldowns = new HashMap<>();
    final transient ActionRunner actions = new ActionRunner();

    public static void setComponentType(ComponentType<EntityStore, SicknessState> type) {
        componentType = type;
    }

    public static ComponentType<EntityStore, SicknessState> getComponentType() {
        return componentType;
    }

    @Override
    public Component<EntityStore> clone() {
        SicknessState copy = new SicknessState();
        copy.heldValue = heldValue;
        return copy;
    }
}
