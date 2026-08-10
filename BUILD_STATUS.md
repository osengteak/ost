# v0.6.0 build status

## Locally verified in this generation environment

- Source contract validator: PASS
- Pure economy core self-test: PASS
- Miner plan shape: PASS (10 procurement / 7 retail / 7-day cycle)
- Raw ores excluded from retail: PASS
- A livelihood unit price >= B industrial unit price: PASS for all 10 commodities
- Direct same-item procurement -> retail arbitrage: PASS (none) for all 7 overlapping commodities
- Interaction hook initialization present: PASS
- Client-side villager click interception removed: PASS
- Interaction/trade authority moved from volatile profession holder to persisted workstation claim: PASS
- Workstation-removal release path present: PASS
- `[광부]` badge removal path present: PASS

## Requires GitHub Actions / real game

This environment cannot resolve Fabric/Minecraft Maven hosts, so the actual Minecraft 26.2 Loom compilation must be performed by the included GitHub Actions workflow. The workflow runs the same local validations first, then `gradle clean build` with Java 25.

After a green build, the remaining validation is in-game integration:

1. hire once, no repeated hire log
2. market opens on right click
3. actual inventory/emerald transactions
4. shared stock and per-player quota behavior
5. persistence after restart
6. workstation break -> unemployment and badge removal
