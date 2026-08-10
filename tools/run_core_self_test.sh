#!/usr/bin/env bash
set -euo pipefail
ROOT="$(cd "$(dirname "$0")/.." && pwd)"
OUT="$ROOT/build/core-self-test"
rm -rf "$OUT"
mkdir -p "$OUT"

javac -d "$OUT" \
  "$ROOT/src/main/java/dev/centraleconomy/miner/market/DeterministicRolls.java" \
  "$ROOT/src/main/java/dev/centraleconomy/miner/market/GateEvaluator.java" \
  "$ROOT/src/main/java/dev/centraleconomy/miner/market/MarketKeys.java" \
  "$ROOT/src/main/java/dev/centraleconomy/miner/market/MarketMutableState.java" \
  "$ROOT/src/main/java/dev/centraleconomy/miner/market/MinerMarketEngine.java" \
  "$ROOT/src/main/java/dev/centraleconomy/miner/market/ProcurementQuote.java" \
  "$ROOT/src/main/java/dev/centraleconomy/miner/market/QuotaKey.java" \
  "$ROOT/src/main/java/dev/centraleconomy/miner/market/QuotaUsage.java" \
  "$ROOT/src/main/java/dev/centraleconomy/miner/market/RetailQuote.java" \
  "$ROOT/src/main/java/dev/centraleconomy/miner/market/WorkstationClaim.java" \
  "$ROOT/src/main/java/dev/centraleconomy/miner/plan/CommodityPlan.java" \
  "$ROOT/src/main/java/dev/centraleconomy/miner/plan/MarketPlan.java" \
  "$ROOT/src/main/java/dev/centraleconomy/miner/plan/MinerPlan.java" \
  "$ROOT/src/main/java/dev/centraleconomy/miner/plan/RetailPlan.java" \
  "$ROOT/src/main/java/dev/centraleconomy/miner/plan/TierPlan.java" \
  "$ROOT/src/main/java/dev/centraleconomy/miner/net/MinerTradeRequest.java" \
  "$ROOT/src/main/java/dev/centraleconomy/miner/net/MarketSnapshotFraming.java" \
  "$ROOT/src/client/java/dev/centraleconomy/miner/client/MinerMarketLayout.java" \
  "$ROOT/tools/CoreSelfTest.java" \
  "$ROOT/tools/UiAndRequestSelfTest.java" \
  "$ROOT/tools/SnapshotFramingSelfTest.java"

java -cp "$OUT" CoreSelfTest
java -cp "$OUT" UiAndRequestSelfTest
java -cp "$OUT" SnapshotFramingSelfTest
