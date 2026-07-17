package com.duperknight.client.modules;

import com.duperknight.client.gui.modules.MiniMeHudScreen;
import com.duperknight.client.hud.MiniMeHudOverlay;
import net.minecraft.client.MinecraftClient;
import net.minecraft.client.gui.screen.Screen;
import net.minecraft.item.ItemStack;
import net.minecraft.item.Items;
import net.minecraft.text.Text;

import java.util.List;

/** Configures the optional animated Mini Mes shown on the in-game HUD. */
public final class MiniMeHudModule extends DMLSModule {
    public MiniMeHudModule() {
        super(StaffRank.HELPER);
    }

    @Override
    public Text displayName() {
        return Text.translatable("dmls.module.mini_me_hud.name");
    }

    @Override
    public ItemStack icon() {
        return new ItemStack(Items.PLAYER_HEAD);
    }

    @Override
    public List<Text> description() {
        return List.of(
                Text.translatable("dmls.module.mini_me_hud.description.1"),
                Text.translatable("dmls.module.mini_me_hud.description.2")
        );
    }

    @Override
    public ModuleCategory category() {
        return ModuleCategory.JOKE;
    }

    @Override
    public void openScreen(MinecraftClient client, Screen parent) {
        client.setScreen(new MiniMeHudScreen(parent, this));
    }

    @Override
    public void register() {
        MiniMeHudOverlay.register();
    }
}
