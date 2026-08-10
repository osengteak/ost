# Central Economy - Miner v0.6.1

Minecraft Java 26.2 / Fabric miner vertical slice for the central-planning economy project.

## v0.6.1 target

This revision is deliberately narrow: make the miner market usable before adding another profession.

- Chiseled quartz remains the miner workstation.
- Employment is a persisted 1:1 villager/workstation contract.
- Breaking the assigned workstation releases the auto-employed miner back to unemployed and removes the mod-owned `[광부]` badge.
- The market panel is responsive and centered; there is no horizontal scrolling requirement.
- Commodity rows use vertical mouse-wheel scrolling when the list is taller than the available screen area.
- The old fixed six-row page layout is gone.
- Raw copper, raw iron and raw gold are **not traded at all**.
- The miner catalog is exactly: coal, copper ingot, iron ingot, redstone, lapis lazuli, gold ingot, diamond.
- Buy/sell actions are server-authoritative. The client sends only a requested action; the server validates miner distance/contract, current plan membership, inventory, emeralds, quota and stock before committing.
- Every trade refreshes the authoritative market snapshot so quota, stock and status messages update on screen.

## Existing config migration

v0.6.0 may already have created `config/central_economy/miner_plan.json` with schema 1 and raw ores. v0.6.1 bundles schema 2.

On startup, if the existing file has an older schema, the mod backs it up as `miner_plan.schema1.backup.json` and installs the bundled schema-2 plan. A malformed existing plan is backed up as `miner_plan.invalid.backup.json` before recovery.

## Trade diagnostics

`latest.log` now marks the whole request path. A successful transaction should contain a chain similar to:

```text
[CE-TRADE] client sending ...
[CE-TRADE] server received ...
[CE-TRADE] execute start ...
[CE-TRADE] SELL committed ...   or   BUY committed ...
[CE-MARKET] snapshot sent ...
[CE-MARKET] client received snapshot ...
```

If the server has not declared the trade payload, the client shows an error instead of silently doing nothing. A request that receives no snapshot times out after about five seconds and tells the tester to inspect `[CE-TRADE]` in `latest.log`.

## Build

GitHub Actions runs, in order:

1. `python3 tools/validate_project.py`
2. `bash tools/run_core_self_test.sh`
3. a real Fabric/Loom `gradle clean build`
4. packaging of `central-economy-miner-0.6.1.jar`

The installable artifact is named `central-economy-miner-jar`.

## Runtime acceptance test

Use a new creative test world. Spawn an adult unemployed villager, place a chiseled quartz block, verify `[광부]`, open the miner market, then test both directions with enough inventory:

- sell one displayed procurement lot and verify the item count falls, emeralds rise, A/B quota changes, and shared state refreshes;
- buy one displayed retail lot and verify emeralds fall, the commodity arrives, and shared stock falls;
- resize/change GUI scale and verify the rightmost column stays on-screen;
- if more rows exist than fit, place the pointer over the list and use the mouse wheel;
- break the assigned chiseled quartz block and verify the badge disappears and the villager returns to unemployed.

A green GitHub Actions build proves compilation and the included deterministic tests; live Minecraft behavior still requires this runtime test.
