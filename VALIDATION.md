# Validation report - Miner v0.6.0

## Executed locally

`python3 tools/validate_project.py`

Result: **PASS**

Validated:
- server interaction hook is initialized
- client no longer intercepts villager clicks
- market open/trade authorization uses persisted employment claims
- valid claims do not re-run profession assignment every second
- workstation removal path clears claim, badge and miner profession
- 7-day planning cycle
- 10 procurement commodities
- exactly 7 retail commodities
- no raw ore retail
- A livelihood unit price is never below B industrial unit price
- no direct same-item procurement/retail arbitrage for overlapping commodities

`bash tools/run_core_self_test.sh`

Result: **PASS: central economy core invariants**

Validated:
- A -> B procurement transition
- player-specific quota state
- shared retail stock
- procurement adds physical retail stock where applicable
- retail decrements shared stock
- deterministic quota rolls
- cycle reset clears quota usage and restores planned retail stock
- 7 Minecraft days = 168000 ticks

## Static code review findings addressed

1. **0.5.x repeated hire loop:** fixed by making the persisted workstation claim authoritative after first hire.
2. **0.5.x market silently not opening:** fixed by initializing one server-side `UseEntityCallback` and validating the claim rather than the volatile profession holder.
3. **Client/server click ambiguity:** normal gameplay no longer requires a client click C2S packet. The client only receives the market snapshot.
4. **Workstation destruction:** invalid claim causes unemployment and `[광부]` badge removal.
5. **Diagnostics:** staged `[CE-EMPLOY]`, `[CE-MARKET]`, and `[CE-TRADE]` log markers were added.

## Not executable in this environment

The current environment cannot resolve Fabric/Minecraft Maven hosts, so an actual Loom/Minecraft 26.2 compile cannot be run here. The included GitHub Actions workflow performs the remaining authoritative compile using Java 25 and Gradle 9.5.1. A green Actions run is required before treating the JAR as installable.
