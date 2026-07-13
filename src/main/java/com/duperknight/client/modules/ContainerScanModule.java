package com.duperknight.client.modules;

import com.duperknight.client.gui.modules.ContainerScanScreen;
import com.duperknight.client.modules.session.AbstractCoreProtectScanModule;
import com.duperknight.client.parser.CoreProtectLookupParser;
import net.minecraft.client.MinecraftClient;
import net.minecraft.client.gui.screen.Screen;
import net.minecraft.item.ItemStack;
import net.minecraft.item.Items;
import net.minecraft.text.Text;

import java.util.List;

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
    public Text displayName() {
        return Text.translatable("dmls.module.containers.name");
    }

    @Override
    public ItemStack icon() {
        return new ItemStack(Items.CHEST);
    }

    @Override
    public List<Text> description() {
        return List.of(
                Text.translatable("dmls.module.containers.description.1"),
                Text.translatable("dmls.module.containers.description.2")
        );
    }

    @Override
    public void openScreen(MinecraftClient client, Screen parent) {
        client.setScreen(new ContainerScanScreen(parent, this));
    }
}
