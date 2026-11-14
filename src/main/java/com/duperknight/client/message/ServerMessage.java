package com.duperknight.client.message;

import net.minecraft.network.chat.Component;

public record ServerMessage(Component text, String cleanText, MessageOrigin origin, boolean overlay, long receivedAtNanos) {
}
