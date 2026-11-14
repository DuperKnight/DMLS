package com.duperknight.client.modules;

import com.duperknight.client.gui.modules.BackgroundsScreen;
import java.util.List;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.network.chat.Component;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;

/** Turns the optional animated DMLS menu backgrounds on and off. */
public final class BackgroundsModule extends DMLSModule {
    public BackgroundsModule() {
        super(StaffRank.HELPER);
    }

    @Override
    public Component displayName() {
        return Component.translatable("dmls.module.backgrounds.name");
    }

    @Override
    public ItemStack icon() {
        return new ItemStack(Items.GLASS_PANE);
    }

    @Override
    public List<Component> description() {
        return List.of(
                Component.translatable("dmls.module.backgrounds.description.1")
        );
    }

    @Override
    public ModuleCategory category() {
        return ModuleCategory.JOKE;
    }

    @Override
    public void openScreen(Minecraft client, Screen parent) {
        client.gui.setScreen(new BackgroundsScreen(parent, this));
    }

    @Override
    public void register() {
        // Nothing to hook: the background is drawn by the menu screens and reads BackgroundsSettings.
    }
}