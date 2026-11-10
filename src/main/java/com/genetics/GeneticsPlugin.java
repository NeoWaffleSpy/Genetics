package com.genetics;

import com.genetics.codex.Codex;
import com.genetics.effect.NightVisionSystem;
import com.genetics.genome.ActiveGenome;
import com.genetics.genome.GenomeMaintenanceSystem;
import com.genetics.interaction.SelfInjectInteraction;
import com.genetics.interaction.UseSyringeInteraction;
import com.genetics.labstation.OpenLabStationInteraction;
import com.hypixel.hytale.component.ComponentRegistryProxy;
import com.hypixel.hytale.logger.HytaleLogger;
import com.hypixel.hytale.server.core.modules.interaction.interaction.config.Interaction;
import com.hypixel.hytale.server.core.plugin.JavaPlugin;
import com.hypixel.hytale.server.core.plugin.JavaPluginInit;
import com.hypixel.hytale.server.core.universe.world.storage.EntityStore;

import javax.annotation.Nonnull;

public class GeneticsPlugin extends JavaPlugin {
    private static final HytaleLogger LOGGER = HytaleLogger.forEnclosingClass();

    public GeneticsPlugin(@Nonnull JavaPluginInit init) {
        super(init);
    }

    @Override
    protected void setup() {
        Codex.register(getAssetRegistry());
        getCodecRegistry(Interaction.CODEC)
                .register(UseSyringeInteraction.ID, UseSyringeInteraction.class, UseSyringeInteraction.CODEC)
                .register(SelfInjectInteraction.ID, SelfInjectInteraction.class, SelfInjectInteraction.CODEC)
                .register(OpenLabStationInteraction.ID, OpenLabStationInteraction.class, OpenLabStationInteraction.CODEC);

        ComponentRegistryProxy<EntityStore> entities = getEntityStoreRegistry();
        ActiveGenome.setComponentType(entities.registerComponent(ActiveGenome.class, ActiveGenome.ID, ActiveGenome.CODEC));
        NightVisionSystem.Light.setComponentType(entities.registerComponent(NightVisionSystem.Light.class, NightVisionSystem.Light::new));
        entities.registerSystem(new GenomeMaintenanceSystem());
        entities.registerSystem(new NightVisionSystem());
        LOGGER.atInfo().log("Genetics set up.");
    }

    @Override
    protected void start() {
        Codex.validate();
    }
}
