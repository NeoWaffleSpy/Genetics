package com.genetics;

import com.genetics.labstation.LabStationBlock;
import com.genetics.mutation.Behavior;
import com.genetics.mutation.MutationSystem;
import com.genetics.tier.Scaling;
import com.genetics.action.Action;
import com.genetics.codex.Codex;
import com.genetics.command.GeneticsCommand;
import com.genetics.sickness.SicknessState;
import com.genetics.sickness.SicknessSystem;
import com.genetics.effect.MorphSystem;
import com.genetics.effect.NightVisionSystem;
import com.genetics.genome.ActiveGenome;
import com.genetics.genome.GenomeMaintenanceSystem;
import com.genetics.interaction.SelfInjectInteraction;
import com.genetics.interaction.UseExpansionInteraction;
import com.genetics.journal.GeneJournal;
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
        Action.registerBuiltIns();
        Behavior.registerBuiltIns();
        Scaling.registerBuiltIns();
        Codex.register(getAssetRegistry());
        getCodecRegistry(Interaction.CODEC)
                .register(UseSyringeInteraction.ID, UseSyringeInteraction.class, UseSyringeInteraction.CODEC)
                .register(SelfInjectInteraction.ID, SelfInjectInteraction.class, SelfInjectInteraction.CODEC)
                .register(UseExpansionInteraction.ID, UseExpansionInteraction.class, UseExpansionInteraction.CODEC)
                .register(OpenLabStationInteraction.ID, OpenLabStationInteraction.class, OpenLabStationInteraction.CODEC);

        ComponentRegistryProxy<EntityStore> entities = getEntityStoreRegistry();
        ActiveGenome.setComponentType(entities.registerComponent(ActiveGenome.class, ActiveGenome.ID, ActiveGenome.CODEC));
        NightVisionSystem.Light.setComponentType(entities.registerComponent(NightVisionSystem.Light.class, NightVisionSystem.Light::new));
        MorphSystem.State.setComponentType(entities.registerComponent(MorphSystem.State.class, MorphSystem.State::new));
        SicknessState.setComponentType(entities.registerComponent(SicknessState.class, SicknessState.ID, SicknessState.CODEC));
        GeneJournal.setComponentType(entities.registerComponent(GeneJournal.class, GeneJournal.ID, GeneJournal.CODEC));
        MutationSystem.State.setComponentType(entities.registerComponent(MutationSystem.State.class, MutationSystem.State::new));
        LabStationBlock.setComponentType(getChunkStoreRegistry().registerComponent(LabStationBlock.class, LabStationBlock.ID, LabStationBlock.CODEC));
        entities.registerSystem(new GenomeMaintenanceSystem());
        entities.registerSystem(new SicknessSystem());
        getCommandRegistry().registerCommand(new GeneticsCommand());
        entities.registerSystem(new NightVisionSystem());
        entities.registerSystem(new MorphSystem());
        entities.registerSystem(new MutationSystem());
        LOGGER.atInfo().log("Genetics set up.");
    }

    @Override
    protected void start() {
        Codex.validate();
    }
}
