# Central Economy v1.0.2

Minecraft Java 26.2 / Fabric central-planning villager economy mod.

## v1.0.2 hotfix scope

This revision keeps the 1.0.1 economy, workstations, textures and market UI, and fixes two runtime defects found during live testing:

1. **Large market snapshot disconnect**
   - v1.0.1 serialized an entire market into one JSON String custom payload.
   - Large catalogs such as Librarian and Cleric could fail during packet encoding and disconnect the player.
   - v1.0.2 splits a snapshot into bounded frames (`4096` chars, hard UTF-8 frame ceiling `16384` bytes), sends them in order, and reassembles them client-side before parsing the JSON.
   - Each transfer has a unique id, part index/count validation, duplicate-conflict rejection, a total snapshot size limit, and stale-transfer cleanup.

2. **Dead villager ghost workstation claim**
   - v1.0.1 only reconciled claims for villagers found in the nearby scan. A dead villager therefore vanished from the scan while its persisted workstation claim remained.
   - v1.0.2 listens for server-side villager death and immediately removes the UUID -> workstation contract.
   - Mob conversion also releases the old villager contract.
   - Ordinary chunk/entity unload does **not** release employment.
   - Saved market-state schema is bumped to 2. On first load from a pre-fix save, legacy workstation claims are discarded once so already-created ghost claims cannot survive the upgrade. Living Central Economy villagers reattach to a nearby matching free workstation on the next employment scan.

## Markets

The full build retains 10 custom workstation professions plus the vanilla Wandering Trader miscellaneous market:

- Farmer
- Rancher
- Fisher
- Miner
- Lumberjack
- Mason
- Fletcher
- Librarian
- Cleric
- Cartographer
- Wandering Trader market (no workstation block)

The market engine retains per-player A/B procurement quota, shared retail stock, the 7-Minecraft-day planning cycle, search/favorites/vertical scrolling, server-authoritative buy/sell validation, persistence, special enchanted-book/potion/tipped-arrow construction, and the existing profession workstation textures.

## v1.0.2 diagnostics

A large market open should now produce a sequence similar to:

```text
[CE-MARKET] snapshot built ... chars=... utf8Bytes=... parts=...
[CE-MARKET] snapshot sent ... parts=...
[CE-MARKET] client reassembled snapshot parts=... chars=...
[CE-MARKET] client opened market=...
```

Killing an employed villager should produce:

```text
[CE-EMPLOY] released <market> workstation claim for <uuid>: villager died
```

A new unemployed villager near the now-free workstation should then be able to take that profession on the next employment scan.

## Build

GitHub Actions runs:

1. `python3 tools/validate_project.py`
2. `bash tools/run_core_self_test.sh`
3. real Minecraft 26.2 Fabric/Loom `gradle clean build`
4. packaging of `central-economy-1.0.2.jar`

The artifact is named `central-economy-jar`.

## Runtime acceptance test

After installing only the new Central Economy JAR (remove older Central Economy JARs):

1. Open a large catalog market, preferably Librarian or Cleric. It must open without disconnecting.
2. Perform one buy and one sell where available and verify inventory/emerald/quota/stock refresh.
3. Employ a villager with a workstation, kill that villager, then place/spawn another unemployed villager near the same workstation. The new villager must be able to claim it.
4. Break an assigned workstation and verify the living villager returns to unemployed.
5. Re-enter the world and verify state persists.

Local pure-Java/static validation is included, but a green GitHub Actions build and this live test are still the final Minecraft-linked gates.
