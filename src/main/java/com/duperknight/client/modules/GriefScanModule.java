package com.duperknight.client.modules;

import com.duperknight.client.gui.modules.GriefScanScreen;
import com.duperknight.client.modules.session.AbstractCoreProtectScanModule;
import com.duperknight.client.parser.CoreProtectLookupParser;
import java.util.List;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.network.chat.Component;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;

/** Scans block activity with CoreProtect and summarizes who broke and placed what. */
public final class GriefScanModule extends AbstractCoreProtectScanModule {
    public GriefScanModule() {
        super(
                "§8[§6DMLS - Griefs§8] §7",
                "grief_scan",
                "Grief Scan",
                "dmls.chat.griefs",
                "/dmls griefs cancel",
                CoreProtectLookupParser.ScanKind.BLOCK
        );
    }

    @Override
    public Component displayName() {
        return Component.translatable("dmls.module.griefs.name");
    }

    @Override
    public ItemStack icon() {
        return new ItemStack(Items.IRON_PICKAXE);
    }

    @Override
    public List<Component> description() {
        return List.of(
                Component.translatable("dmls.module.griefs.description.1"),
                Component.translatable("dmls.module.griefs.description.2")
        );
    }

    @Override
    public ModuleCategory category() {
        return ModuleCategory.GENERAL;
    }

    @Override
    public void openScreen(Minecraft client, Screen parent) {
        client.gui.setScreen(new GriefScanScreen(parent, this));
    }
}
