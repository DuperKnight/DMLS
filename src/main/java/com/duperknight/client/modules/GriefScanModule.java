package com.duperknight.client.modules;

import com.duperknight.client.gui.modules.GriefScanScreen;
import com.duperknight.client.modules.session.AbstractCoreProtectScanModule;
import com.duperknight.client.parser.CoreProtectLookupParser;
import net.minecraft.client.MinecraftClient;
import net.minecraft.client.gui.screen.Screen;
import net.minecraft.item.ItemStack;
import net.minecraft.item.Items;
import net.minecraft.text.Text;

import java.util.List;

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
    public Text displayName() {
        return Text.translatable("dmls.module.griefs.name");
    }

    @Override
    public ItemStack icon() {
        return new ItemStack(Items.IRON_PICKAXE);
    }

    @Override
    public List<Text> description() {
        return List.of(
                Text.translatable("dmls.module.griefs.description.1"),
                Text.translatable("dmls.module.griefs.description.2")
        );
    }

    @Override
    public void openScreen(MinecraftClient client, Screen parent) {
        client.setScreen(new GriefScanScreen(parent, this));
    }
}
