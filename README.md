# DMLS — Duper's Mod for Lazy Staff

DMLS is a client-only Fabric mod for Stoneworks staff workflows on Minecraft 1.21.11. It sends privileged commands but never grants or detects server permission.

## Install and configure

1. Install Fabric Loader, Fabric API, and this mod's JAR on the client. Mod Menu is optional.
2. Start Minecraft once to create `config/dmls.properties`. `play.stoneworks.gg` is allowed by default.
3. Select a staff rank in Mod Options or with `/dmls rank`. This selection only controls which modules DMLS displays. The server remains authoritative and may reject every command.
4. Put alert words, one per line, in `config/dmls-alerts.txt`, then run `/dmls alerts reload`.

Allowlist matching is case-insensitive, normalizes the default port, supports exact hosts, and supports only explicit `*.` subdomain rules. Substring matching is not used. Privileged queues re-check the current connection before each command and cancel on disconnect or server change.

## Commands

The canonical command root is `/dmls`:

- `/dmls lands <ign...>`
- `/dmls members <land>`
- `/dmls alts <ign>`
- `/dmls uuid <username...>` — accepts up to 10 comma/space-separated names through Mojang's bulk API
- `/dmls xray <ign>` and `/dmls xray cancel`
- `/dmls prefix <ign> <limit> <prefixid> <prefixtext>`
- `/dmls donorpet <ign> <pet>`
- `/dmls promowave <rank> <ign...>`
- `/dmls demowave <rank> <ign...>` — removes the given staff rank from every listed player
- `/dmls activity <ign...>` — runs `/activity` for every listed staff member and summarizes the hours, sorted
- `/dmls alerts [on|off|reload]`
- `/dmls brb <duration|off>` — auto-replies to private messages while you are AFK, e.g. `5m`, `30s`, `1h`
- `/dmls dnd <on|off>` — auto-replies that you are busy until turned off
- `/dmls say [reply]`
- `/dmls rank [rank]`
- `/dmls help`


## Development

Java 21 is required. Run:

```text
./gradlew clean test
./gradlew build
```

CI validates the Gradle wrapper, builds, tests, and uploads test reports on failure. The produced mod is client-only and packages `LICENSE.txt`.
