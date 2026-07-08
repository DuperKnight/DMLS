package com.duperknight;

import net.fabricmc.api.DedicatedServerModInitializer;
import net.fabricmc.api.ModInitializer;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

public class DMLS implements ModInitializer, DedicatedServerModInitializer {
    public static final String MOD_ID = "dmls";
    public static final Logger LOGGER = LoggerFactory.getLogger(MOD_ID);

    @Override
    public void onInitialize() {
        // Nothing to do here :p
    }

    @Override
    public void onInitializeServer() {
        LOGGER.error("Why are you running this on a dedicated server??");
    }
}
