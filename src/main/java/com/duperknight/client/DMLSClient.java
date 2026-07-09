package com.duperknight.client;

import com.duperknight.DMLS;
import com.duperknight.client.modules.CheckLandsModule;
import com.duperknight.client.modules.DMLSModule;
import net.fabricmc.api.ClientModInitializer;

import java.util.List;

public class DMLSClient implements ClientModInitializer {
    private final List<DMLSModule> modules = List.of(
            new CheckLandsModule()
    );

    @Override
    public void onInitializeClient() {
        DMLS.LOGGER.info("Initializing DMLS client, you are a lazy staff member!");
        modules.forEach(DMLSModule::register);
    }
}
