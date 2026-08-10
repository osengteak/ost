# Validation report - Miner v0.6.1

## Automated checks included

`tools/validate_project.py` checks source/config invariants including:

- stable employment-contract authority and workstation unemployment path;
- normal server-side villager interaction path;
- networking initialization;
- responsive market layout and removal of fixed six-row pagination;
- vertical mouse-wheel scroll implementation;
- client sendability check and staged `[CE-TRADE]` diagnostics;
- server commodity whitelist validation and authoritative transaction refresh;
- schema-1-to-schema-2 plan migration/backup path;
- exactly 7 miner commodities;
- raw copper/raw iron/raw gold absent from all miner trading;
- A procurement unit value not below B industrial unit value;
- retail unit price above both procurement unit prices for every commodity.

`tools/run_core_self_test.sh` compiles and executes pure Java tests without Minecraft:

- A -> B procurement transition;
- per-player quota separation;
- procurement adding physical shared stock;
- retail consuming shared stock;
- deterministic quota rolls;
- 7-day cycle reset;
- responsive layout bounds at 320x240 through 1648x928;
- compact-screen vertical scrolling math;
- BUY/SELL trade-request round trips and malformed-request rejection.

## Local result for this package

```text
PASS: project validation complete
PASS: central economy core invariants
PASS: responsive UI layout and trade request invariants
```

## Not claimed by local tests

This environment does not contain a working local Fabric/Loom toolchain, so Minecraft-linked classes are not compiled here. GitHub Actions performs the real Minecraft 26.2/Fabric compile. A successful build is still not a substitute for opening the game and exercising a real transaction.
