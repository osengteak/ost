# Validation report — Central Economy 1.0.0

## What was validated locally

`tools/validate_project.py` performs static/data validation over the complete project. It verifies project versions and CI wiring, all ten workstation registrations/resources, exact 16×16 PNG dimensions, POI/profession integration, persisted employment invariants, workstation-break release behavior, Wandering Trader routing, server-authoritative transaction checks, special-stack constructors, schema migration, catalog coverage and economic no-direct-arbitrage rules.

`tools/run_core_self_test.sh` compiles only Minecraft-independent Java classes and executes two deterministic test programs. The engine test exercises multiple markets, market-qualified shared stock, UUID-specific A/B quotas, market isolation, A→B transition, retail stock consumption, progression gates and 7-day cycle reset. The UI/request test exercises responsive layouts at multiple resolutions, large-catalog vertical scroll ranges, request round-tripping and malformed/delimiter-injection rejection.

## Catalog acceptance snapshot

- Farmer: 20 rows
- Rancher: 13 rows
- Fisher: 7 rows
- Miner: 7 rows
- Lumberjack: 11 rows
- Mason: 21 rows
- Fletcher: 47 rows
- Librarian: 128 rows
- Cleric: 152 rows
- Wandering Trader: 20 rows
- Cartographer: 4 rows
- Total: 430 rows

Special exact-stack rows are retail-only because plain-item procurement cannot safely identify arbitrary enchantment/potion components without a component-aware sell matcher. Normal item rows retain A/B procurement where configured.

## What remains external

This environment does not contain the Minecraft/Fabric dependency graph or a Java 25 Gradle toolchain, so it cannot honestly certify a Fabric 26.2 Loom compile here. The included GitHub Actions workflow is the actual compile gate. A green workflow is then followed by live runtime verification in Minecraft 26.2.
