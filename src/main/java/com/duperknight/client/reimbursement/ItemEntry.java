package com.duperknight.client.reimbursement;

import net.minecraft.util.Identifier;

import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;

/** Immutable item request; the screen replaces entries as fields are edited. */
public record ItemEntry(
        Identifier itemId,
        String customName,
        List<String> lore,
        int amount,
        Map<Identifier, Integer> enchantments,
        Destination destination,
        String playerIgn,
        List<ContainerTarget> containers
) implements ReimbursementEntry {
    public ItemEntry {
        customName = Objects.requireNonNullElse(customName, "");
        lore = Objects.requireNonNullElse(lore, List.<String>of()).stream()
                .filter(Objects::nonNull)
                .filter(line -> !line.isBlank())
                .toList();
        enchantments = Map.copyOf(new LinkedHashMap<>(
                Objects.requireNonNullElse(enchantments, Map.of())));
        destination = Objects.requireNonNullElse(destination, Destination.ME);
        playerIgn = Objects.requireNonNullElse(playerIgn, "");
        containers = List.copyOf(Objects.requireNonNullElse(containers, List.of()));
    }

    public static ItemEntry empty() {
        return new ItemEntry(null, "", List.of(), 1, Map.of(),
                Destination.ME, "", List.of());
    }

    public ItemEntry withItemId(Identifier value) {
        return new ItemEntry(value, customName, lore, amount, enchantments, destination, playerIgn, containers);
    }

    public ItemEntry withCustomName(String value) {
        return new ItemEntry(itemId, value, lore, amount, enchantments, destination, playerIgn, containers);
    }

    public ItemEntry withLore(List<String> value) {
        return new ItemEntry(itemId, customName, value, amount, enchantments, destination, playerIgn, containers);
    }

    public ItemEntry withAmount(int value) {
        return new ItemEntry(itemId, customName, lore, value, enchantments, destination, playerIgn, containers);
    }

    public ItemEntry withEnchantments(Map<Identifier, Integer> value) {
        return new ItemEntry(itemId, customName, lore, amount, value, destination, playerIgn, containers);
    }

    @Override
    public ItemEntry withDestination(Destination value) {
        return new ItemEntry(itemId, customName, lore, amount, enchantments, value, playerIgn, containers);
    }

    @Override
    public ItemEntry withPlayerIgn(String value) {
        return new ItemEntry(itemId, customName, lore, amount, enchantments, destination, value, containers);
    }

    public ItemEntry withContainers(List<ContainerTarget> value) {
        return new ItemEntry(itemId, customName, lore, amount, enchantments, destination, playerIgn, value);
    }
}
