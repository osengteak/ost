# Validation report - Central Economy v1.0.2

## Added regression coverage

`tools/validate_project.py` now checks that:

- the project and uploaded artifact target v1.0.2;
- employment registers the Fabric server living-entity death event;
- death removes the persisted workstation claim;
- mob conversion also removes the old villager claim;
- ordinary entity unload is not used as an employment-release signal;
- saved market state uses schema 2 and contains the legacy ghost-claim recovery path;
- the server does not send `new MinerMarketSnapshotS2CPayload(json)` with the whole catalog;
- snapshots pass through `MarketSnapshotFraming.frame(json)`;
- framing has explicit chunk, UTF-8 packet and total-assembly bounds;
- the client uses the snapshot assembler before parsing market JSON.

`tools/SnapshotFramingSelfTest.java` executes pure Java regression tests for:

- one-frame small snapshots;
- a large synthetic Korean catalog split across many frames;
- every wire frame staying under the explicit UTF-8 ceiling;
- exact reassembly after reversed/out-of-order delivery;
- cleanup after completion;
- malformed-frame rejection;
- conflicting duplicate-frame rejection;
- oversized total snapshot rejection.

The existing core tests still cover A/B procurement transition, player quota isolation, market-qualified shared stock, retail decrement, deterministic rolls, 7-day reset, responsive layout bounds, vertical scrolling, and trade-request validation.

## Local result

```text
PASS: full Central Economy 1.0.2 project validation complete
PASS: full central economy engine invariants
PASS: responsive UI and generic trade request invariants
PASS: chunked market snapshot framing invariants
```

## External hard gate

The local container does not have network access or a Minecraft 26.2/Fabric dependency cache, so it cannot perform the Loom compilation here. The included GitHub Actions workflow remains the real Minecraft-linked compile gate.
