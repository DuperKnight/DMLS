package com.duperknight.client.utils;

import java.util.List;
import java.util.Locale;
import java.util.Optional;
import net.minecraft.client.Minecraft;

/** Exact-host (or explicit *.subdomain) allowlist for Stoneworks-specific commands. */
public final class ServerGuard {
    public static final List<String> DEFAULT_ALLOWED_SERVERS = List.of("play.stoneworks.gg");

    private ServerGuard() {
    }

    public static GuardResult check(Minecraft client) {
        if (client == null || client.isLocalServer()) return new GuardResult(false, "singleplayer", "");
        if (ClientUtils.isNotConnected(client) || client.getCurrentServer() == null) {
            return new GuardResult(false, "disconnected", "");
        }
        String address = normalizeAddress(client.getCurrentServer().ip);
        boolean allowed = isAllowed(address, DMLSConfig.allowedServers());
        return new GuardResult(allowed, allowed ? "allowed" : "not_allowed", address);
    }

    public static String connectionIdentity(Minecraft client) {
        GuardResult result = check(client);
        return result.allowed ? result.address : "";
    }

    public static boolean isAllowed(String address, List<String> rules) {
        HostPort candidate = HostPort.parse(address);
        if (candidate.host.isEmpty()) return false;
        for (String rawRule : rules) {
            Optional<String> validatedRule = normalizeRule(rawRule);
            if (validatedRule.isEmpty()) continue;
            String normalizedRule = validatedRule.get();
            boolean wildcard = normalizedRule.startsWith("*.");
            HostPort rule = HostPort.parse(wildcard ? normalizedRule.substring(2) : normalizedRule);
            if (rule.host.isEmpty() || (rule.port != -1 && rule.port != candidate.port)) continue;
            if (!wildcard && candidate.host.equals(rule.host)) return true;
            if (wildcard && candidate.host.endsWith("." + rule.host) && !candidate.host.equals(rule.host)) return true;
        }
        return false;
    }

    /** Validates and normalizes an exact host or explicit wildcard subdomain rule. */
    public static Optional<String> normalizeRule(String input) {
        String value = normalizeAddress(input);
        if (value.isEmpty() || value.contains("/") || value.contains("\\") || value.contains("://")
                || value.chars().anyMatch(Character::isWhitespace)) {
            return Optional.empty();
        }

        boolean wildcard = value.startsWith("*.");
        String hostAndPort = wildcard ? value.substring(2) : value;
        int colon = hostAndPort.lastIndexOf(':');
        String host = hostAndPort;
        String portSuffix = "";
        if (colon >= 0) {
            if (hostAndPort.indexOf(':') != colon) return Optional.empty();
            host = hostAndPort.substring(0, colon);
            try {
                int port = Integer.parseInt(hostAndPort.substring(colon + 1));
                if (port < 1 || port > 65535) return Optional.empty();
                portSuffix = ":" + port;
            } catch (NumberFormatException exception) {
                return Optional.empty();
            }
        }

        if (!isValidHost(host) || wildcard && !host.contains(".")) return Optional.empty();
        return Optional.of((wildcard ? "*." : "") + host + portSuffix);
    }

    private static boolean isValidHost(String host) {
        if (host.isEmpty() || host.length() > 253 || host.startsWith(".") || host.endsWith(".")) return false;
        for (String label : host.split("\\.", -1)) {
            if (label.isEmpty() || label.length() > 63 || label.startsWith("-") || label.endsWith("-")) return false;
            if (!label.chars().allMatch(character -> character >= 'a' && character <= 'z'
                    || character >= '0' && character <= '9' || character == '-')) return false;
        }
        return true;
    }

    public static String normalizeAddress(String address) {
        if (address == null) return "";
        String value = address.trim().toLowerCase(Locale.ROOT);
        while (value.endsWith(".")) value = value.substring(0, value.length() - 1);
        return value;
    }

    public record GuardResult(boolean allowed, String reason, String address) {
    }

    private record HostPort(String host, int port) {
        private static HostPort parse(String value) {
            String normalized = normalizeAddress(value);
            int colon = normalized.lastIndexOf(':');
            if (colon > 0 && normalized.indexOf(':') == colon) {
                try {
                    return new HostPort(normalized.substring(0, colon), Integer.parseInt(normalized.substring(colon + 1)));
                } catch (NumberFormatException ignored) {
                    return new HostPort("", -1);
                }
            }
            return new HostPort(normalized, 25565);
        }
    }
}
