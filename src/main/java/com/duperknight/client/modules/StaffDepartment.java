package com.duperknight.client.modules;

import net.minecraft.text.Text;

/** Staff departments whose internal rank is configured independently. */
public enum StaffDepartment {
    BANS("dmls.department.bans"),
    EVENTS("dmls.department.events"),
    WAR("dmls.department.war");

    private final String translationKey;

    StaffDepartment(String translationKey) {
        this.translationKey = translationKey;
    }

    public Text displayName() {
        return Text.translatable(translationKey);
    }
}
