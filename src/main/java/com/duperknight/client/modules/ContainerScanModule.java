package com.duperknight.client.modules;

import com.duperknight.client.gui.modules.ContainerScanScreen;
import com.duperknight.client.modules.session.AbstractCoreProtectScanModule;
import com.duperknight.client.parser.CoreProtectLookupParser;
import java.util.List;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.network.chat.Component;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;

/** Scans container activity with CoreProtect and summarizes who took and added what. */
public final class ContainerScanModule extends AbstractCoreProtectScanModule {
    public ContainerScanModule() {
        super(
                "§8[§6DMLS - Containers§8] §7",
                "container_scan",
                "Container Scan",
                "dmls.chat.containers",
                "/dmls containers cancel",
                CoreProtectLookupParser.ScanKind.CONTAINER
        );
    }

    @Override
    public Component displayName() {
        return Component.translatable("dmls.module.containers.name");
    }

    @Override
    public ItemStack icon() {
        return new ItemStack(Items.CHEST);
    }

    @Override
    public List<Component> description() {
        return List.of(
                Component.translatable("dmls.module.containers.description.1"),
                Component.translatable("dmls.module.containers.description.2")
        );
    }

    @Override
    public ModuleCategory category() {
        return ModuleCategory.GENERAL;
    }

    @Override
    public void openScreen(Minecraft client, Screen parent) {
        client.gui.setScreen(new ContainerScanScreen(parent, this));
    }
}
