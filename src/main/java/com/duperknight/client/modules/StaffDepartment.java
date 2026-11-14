package com.duperknight.client.modules;

import net.minecraft.network.chat.Component;

/** Staff departments whose internal rank is configured independently. */
public enum StaffDepartment {
    BANS("dmls.department.bans"),
    EVENTS("dmls.department.events"),
    WAR("dmls.department.war");

    private final String translationKey;

    StaffDepartment(String translationKey) {
        this.translationKey = translationKey;
    }

    public Component displayName() {
        return Component.translatable(translationKey);
    }
}
