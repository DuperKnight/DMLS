package com.duperknight.client.gui;

import com.duperknight.client.DMLSClient;
import com.terraformersmc.modmenu.api.ConfigScreenFactory;
import com.terraformersmc.modmenu.api.ModMenuApi;

public class ModMenuIntegration implements ModMenuApi {
    @Override
    public ConfigScreenFactory<?> getModConfigScreenFactory() {
        return parent -> new DMLSHomeScreen(DMLSClient.modules(), parent);
    }
}
