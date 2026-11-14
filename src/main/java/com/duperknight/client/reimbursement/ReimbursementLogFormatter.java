package com.duperknight.client.reimbursement;

import com.duperknight.client.utils.PrefixTextFormatter;
import java.math.RoundingMode;
import java.text.DecimalFormat;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import net.minecraft.core.Holder;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.core.registries.Registries;
import net.minecraft.resources.Identifier;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.enchantment.Enchantment;
import net.minecraft.world.level.Level;

/** Produces the forced-copy Stoneworks reimbursement log. */
public final class ReimbursementLogFormatter {
    private static final DecimalFormat MONEY = new DecimalFormat("#,##0.00");

    private ReimbursementLogFormatter() {
    }

    public static String format(ReimbursementPlan plan, Level world) {
        ReimbursementDraft.Snapshot draft = plan.draft();
        String discord = draft.discordUsername()
                + (draft.discordId().isBlank() ? "" : " / " + draft.discordId());
        List<String> itemLines = new ArrayList<>();
        for (ReimbursementEntry entry : draft.entries()) {
            if (entry instanceof MoneyEntry money) {
                MONEY.setRoundingMode(RoundingMode.UNNECESSARY);
                itemLines.add("$" + MONEY.format(money.amount()));
                continue;
            }
            ItemEntry item = (ItemEntry) entry;
            itemLines.add(item.amount() + "x " + displayName(item));
            for (Map.Entry<Identifier, Integer> enchantment : item.enchantments().entrySet()) {
                itemLines.add("- " + enchantmentName(world, enchantment.getKey())
                        + " " + roman(enchantment.getValue()));
            }
        }
        return "**IGN:** `" + draft.ign() + "`\n"
                + "**Discord:** " + discord + "\n"
                + "**Server:** Abexilas\n"
                + "**Items/Money:**\n"
                + "```\n" + String.join("\n", itemLines) + "\n```\n"
                + "**Reason:** " + draft.reason() + "\n"
                + "**Ticket:** " + draft.ticket();
    }

    private static String displayName(ItemEntry entry) {
        if (!entry.customName().isBlank()) {
            PrefixTextFormatter.PlainResult parsed = PrefixTextFormatter.plainText(entry.customName());
            if (parsed.valid()) return parsed.text();
        }
        Item item = BuiltInRegistries.ITEM.getValue(entry.itemId());
        return item.getDefaultInstance().getHoverName().getString();
    }

    private static String enchantmentName(Level world, Identifier id) {
        if (world != null) {
            var registry = world.registryAccess().lookup(Registries.ENCHANTMENT);
            if (registry.isPresent()) {
                Holder.Reference<Enchantment> entry = registry.get().get(id).orElse(null);
                if (entry != null) return Enchantment.getFullname(entry, 1).getString()
                        .replaceAll("\\s+[IVXLCDM]+$", "");
            }
        }
        String path = id.getPath().replace('_', ' ');
        StringBuilder result = new StringBuilder(path.length());
        boolean capitalize = true;
        for (char value : path.toCharArray()) {
            result.append(capitalize ? Character.toUpperCase(value) : value);
            capitalize = value == ' ';
        }
        return result.toString();
    }

    static String roman(int value) {
        if (value <= 0) return Integer.toString(value);
        int[] numbers = {1000, 900, 500, 400, 100, 90, 50, 40, 10, 9, 5, 4, 1};
        String[] numerals = {"M", "CM", "D", "CD", "C", "XC", "L", "XL", "X", "IX", "V", "IV", "I"};
        StringBuilder result = new StringBuilder();
        int remaining = value;
        for (int index = 0; index < numbers.length; index++) {
            while (remaining >= numbers[index]) {
                result.append(numerals[index]);
                remaining -= numbers[index];
            }
        }
        return result.toString();
    }
}
