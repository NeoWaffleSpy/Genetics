package com.Genetics;

import com.hypixel.hytale.logger.HytaleLogger;
import com.hypixel.hytale.server.core.plugin.JavaPlugin;
import com.hypixel.hytale.server.core.plugin.JavaPluginInit;

public class GeneticsPlugin extends JavaPlugin {
    private static final HytaleLogger LOGGER = HytaleLogger.forEnclosingClass();

    public GeneticsPlugin(JavaPluginInit init) {
        super(init);
        LOGGER.atInfo().log("Genetics Plugin Initialized!");
    }

    @Override
    protected void setup() {
        LOGGER.atInfo().log("Setting up Genetics Plugin...");
    }
}
