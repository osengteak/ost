# Central Economy - Miner v0.6.0

Minecraft Java Edition 26.2 / Fabric miner-only vertical slice.

## What changed in 0.6.0

The 0.5.x implementation treated the vanilla `VillagerProfession` holder as the authority for miner employment. In real play, Minecraft's villager brain could rewrite that holder because the fallback employment system had not originated from vanilla JOB_SITE memory. The symptom was a `became miner` log every second and market interaction silently failing whenever the holder had already returned to NONE.

v0.6.0 uses a persisted **employment contract** as the gameplay authority:

- key: villager UUID
- value: dimension + claimed chiseled quartz block position
- one workstation is claimed by at most one miner
- a valid claim keeps the villager employed without re-running profession assignment
- breaking the claimed chiseled quartz block removes the contract, clears `[광부]`, and returns the registered miner profession to NONE
- market interaction and transaction validation use the same persisted contract

The registered `central_economy:miner` profession is still assigned once on hire, but the economy no longer breaks if vanilla later rewrites that internal holder.

## Expected gameplay

1. Place a **Chiseled Quartz Block** near an adult unemployed villager.
2. Within about one second, the villager receives a visible gold `[광부]` badge.
3. Right-click the miner.
4. The **광부 중앙시장** screen opens.
5. Selling uses player UUID + commodity + planning cycle A/B procurement quotas.
6. Buying uses server-wide shared retail stock.
7. Break that miner's claimed Chiseled Quartz Block.
8. Within about one second, `[광부]` disappears and the villager becomes unemployed again.

## Economic scope

State procurement (10): coal, raw copper, copper ingot, raw iron, iron ingot, redstone, lapis lazuli, raw gold, gold ingot, diamond.

State retail (7): coal, copper ingot, iron ingot, redstone, lapis lazuli, gold ingot, diamond. Raw ores are not retailed.

The planning cycle is 7 Minecraft days. Prices, lot sizes, quotas, retail stock, activation probability and gates are loaded from `miner_plan.json`.

## Diagnostics

`latest.log` now contains staged markers:

- `[CE-EMPLOY] hired ...`
- `[CE-MARKET] interact ... activeMiner=true`
- `[CE-MARKET] open request ...`
- `[CE-MARKET] snapshot built ...`
- `[CE-MARKET] snapshot sent ...`
- `[CE-MARKET] client received snapshot ...`
- `[CE-MARKET] client opened miner market screen ...`
- `[CE-TRADE] BUY/SELL ...`
- `[CE-EMPLOY] miner ... became unemployed: workstation removed`

This makes a failed interaction localizable from one log file.

## Build

Push the project to GitHub. `Build Miner Mod` runs automatically and performs:

1. Java 25 setup
2. source/economy invariant validation
3. pure central-economy core self-test
4. actual Fabric 26.2 Loom/Gradle build
5. installable JAR artifact creation

On success download artifact `central-economy-miner-jar` and put `central-economy-miner-0.6.0.jar` in the same mods directory as Fabric API and Essential.

## Test checklist

- [ ] Mod loads on Minecraft 26.2 Fabric + Essential
- [ ] Adult unemployed villager claims one chiseled quartz workstation
- [ ] `[광부]` appears once; `latest.log` does not spam re-hiring every second
- [ ] Second villager cannot claim the same workstation
- [ ] Right-click logs `activeMiner=true`
- [ ] Market snapshot is sent and client screen opens
- [ ] A quota transitions to B where applicable
- [ ] A/B quota is shared across all miner NPCs for the same player
- [ ] Retail stock is shared across all miner NPCs
- [ ] Restart preserves quotas, stock and workstation claims
- [ ] Breaking the claimed workstation clears `[광부]` and returns villager to unemployed
