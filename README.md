# DMLS — Duper's Mod for Lazy Staff

DMLS is a client-only Fabric mod for Stoneworks staff workflows on Minecraft 1.21.11. It validates and coordinates staff commands, while the server remains authoritative.

## Install and configure

1. Install Fabric Loader, Fabric API, and the DMLS JAR on the client. Mod Menu is optional.
2. Start Minecraft once to create `config/dmls.properties`. `play.stoneworks.gg` is allowed by default.
3. Join through `play.stoneworks.gg`. DMLS reads and stores your staff rank from the hub scoreboard; an unrecognized rank locks the module menu.
4. Add alert words, one per line, to `config/dmls-alerts.txt`, then run `/dmls alerts reload`.

The first verified staff rank opens a welcome module grid. Later promotions open the same grid with newly unlocked modules highlighted.
Module messages intended for global chat temporarily switch to `public` when needed, then restore the previous channel; the channel-list probe is hidden from chat.

## Canonical commands ( its recommended that you use the GUI )

The supported command namespace is `/dmls`:

- `/dmls` — open the module menu.
- `/dmls help` — show in-game command help.
- `/dmls rank` — show the staff rank last detected in the Stoneworks hub.
- `/dmls dryrun [on|off]` — show or toggle session-only dry-run mode; changing it requires a detected Admin rank.
- `/dmls cancel` — cancel the active response-tracked operation.
- `/dmls lands <ign...>`
- `/dmls members <land>`
- `/dmls alts <ign>`
- `/dmls uuid <username...>` — accept up to ten comma/space-separated names.
- `/dmls xray <ign>` — stage an exact rollback plan.
- `/dmls xray confirm`
- `/dmls xray cancel`
- `/dmls prefix <ign> <limit> <prefixid> <prefixtext>`
- `/dmls donorpet <ign> <pet>`
- `/dmls promowave <rank> <ign...>` — stage a promotion batch.
- `/dmls promowave confirm`
- `/dmls promowave cancel`
- `/dmls demowave <rank> <ign...>` — stage a demotion batch.
- `/dmls demowave confirm`
- `/dmls demowave cancel`
- `/dmls activity <ign...>`
- `/dmls activity cancel`
- `/dmls co` — open the CoreProtect request builder.
- `/dmls containers <ign|*> <time> <radius>`
- `/dmls containers cancel`
- `/dmls griefs <ign|*> <time> <radius>`
- `/dmls griefs cancel`
- `/dmls punish` — open the rulebook and ban-log workflow.
- `/dmls alerts [on|off|reload]`
- `/dmls chatlog [filter]`
- `/dmls greet <ign>`
- `/dmls greeter [on|off]` — show or change the persisted automatic-greeter toggle.
- `/dmls loc [list]`
- `/dmls loc save <name>`
- `/dmls loc tp <name>`
- `/dmls loc del <name>`
- `/dmls brb [duration|off]` — durations accept forms such as `30s`, `5m`, or `1h30m`.
- `/dmls dnd [on|off]`
- `/dmls say [reply]`

## Development

Java 21 is required.

```text
./gradlew test --rerun-tasks
./gradlew build
```

CI validates the Gradle wrapper, builds and tests the project, and uploads test reports on failure. The produced mod is client-only and packages `LICENSE.txt`.
