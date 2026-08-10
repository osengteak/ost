# Validation report — Central Economy v1.0.1

## Automated checks included

`tools/validate_project.py` checks the final multi-market project, including:

- Minecraft 26.2 / Fabric Loader 0.19.3 / Fabric API 0.156.0+26.2 metadata;
- ten custom profession workstation blocks and their blockstate/model/item/recipe/loot resources;
- exact 16×16 workstation textures;
- persistent 1:1 workstation employment contracts and break-to-unemployed path;
- server-authoritative market interaction and proximity checks;
- Wandering Trader detection by `minecraft:wandering_trader` registry id without a concrete NPC class import;
- per-player A/B quota keys qualified by market, commodity and planning cycle;
- shared market retail stock and cycle reset;
- server stack construction paths for normal items, enchanted books, potions and tipped arrows;
- all configured market catalogs and special-product variant counts;
- no raw copper/iron/gold in the miner market;
- direct same-item buy→sell arbitrage checks across markets;
- responsive market layout, vertical wheel scrolling and trade-network diagnostics.

`tools/run_core_self_test.sh` compiles and executes Minecraft-independent Java tests for the economy engine, responsive UI math, and generic trade-request serialization/validation.

## Local result for this package

```text
PASS: full Central Economy 1.0.1 project validation complete
PASS: full central economy engine invariants
PASS: responsive UI and generic trade request invariants
```

## Compile issue fixed from v1.0.0

The first v1.0.0 GitHub Actions run failed with four `cannot find symbol` errors because production source imported and type-checked `net.minecraft.world.entity.npc.WanderingTrader`. v1.0.1 removes that Java-class dependency entirely. Both the interaction hook and transaction endpoint resolver now inspect the entity registry identity and accept only `minecraft:wandering_trader`.

## Not claimed yet

This environment cannot perform the real Fabric/Loom-linked Minecraft compile. The next hard gate is a green GitHub Actions `clean build`. After that, live Minecraft testing is still required for all profession workstations and special-product trades.
