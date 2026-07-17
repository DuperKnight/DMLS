package com.duperknight.client.modules;

import net.minecraft.text.Text;

/** Stable display groups for modules on the DMLS home screen. */
public enum ModuleCategory {
    GENERAL("dmls.module_category.general"),
    EVENTS("dmls.module_category.events"),
    WAR("dmls.module_category.war"),
    BANS("dmls.module_category.bans"),
    SERVER_MANAGEMENT("dmls.module_category.server_management"),
    JOKE("dmls.module_category.joke");

    private final String translationKey;

    ModuleCategory(String translationKey) {
        this.translationKey = translationKey;
    }

    public Text displayName() {
        return Text.translatable(translationKey);
    }
}
