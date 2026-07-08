package com.duperknight.client;

import net.fabricmc.api.ClientModInitializer;
import com.duperknight.DMLS;

public class DMLSClient implements ClientModInitializer {

    @Override
    public void onInitializeClient() {
        DMLS.LOGGER.info("Initializing DMLS client, you are a lazy staff member!");
        CheckLandsCommand.register();
    }
}
