package com.genetics;

import com.genetics.interaction.UseSyringeInteraction;
import com.hypixel.hytale.logger.HytaleLogger;
import com.hypixel.hytale.server.core.modules.interaction.interaction.config.Interaction;
import com.hypixel.hytale.server.core.plugin.JavaPlugin;
import com.hypixel.hytale.server.core.plugin.JavaPluginInit;

import javax.annotation.Nonnull;

public class GeneticsPlugin extends JavaPlugin {
    private static final HytaleLogger LOGGER = HytaleLogger.forEnclosingClass();

    public GeneticsPlugin(@Nonnull JavaPluginInit init) {
        super(init);
    }

    @Override
    protected void setup() {
        getCodecRegistry(Interaction.CODEC).register(
                UseSyringeInteraction.ID, UseSyringeInteraction.class, UseSyringeInteraction.CODEC
        );
        LOGGER.atInfo().log("Genetics set up.");
    }
}
