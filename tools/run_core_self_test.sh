#!/usr/bin/env bash
set -euo pipefail
ROOT="$(cd "$(dirname "$0")/.." && pwd)"
OUT="$ROOT/build/core-self-test"
rm -rf "$OUT"
mkdir -p "$OUT"

javac -d "$OUT" \
  "$ROOT/src/main/java/dev/centraleconomy/miner/market/DeterministicRolls.java" \
  "$ROOT/src/main/java/dev/centraleconomy/miner/market/GateEvaluator.java" \
  "$ROOT/src/main/java/dev/centraleconomy/miner/market/MarketMutableState.java" \
  "$ROOT/src/main/java/dev/centraleconomy/miner/market/MinerMarketEngine.java" \
  "$ROOT/src/main/java/dev/centraleconomy/miner/market/ProcurementQuote.java" \
  "$ROOT/src/main/java/dev/centraleconomy/miner/market/QuotaKey.java" \
  "$ROOT/src/main/java/dev/centraleconomy/miner/market/QuotaUsage.java" \
  "$ROOT/src/main/java/dev/centraleconomy/miner/market/RetailQuote.java" \
  "$ROOT/src/main/java/dev/centraleconomy/miner/market/WorkstationClaim.java" \
  "$ROOT/src/main/java/dev/centraleconomy/miner/plan/CommodityPlan.java" \
  "$ROOT/src/main/java/dev/centraleconomy/miner/plan/MinerPlan.java" \
  "$ROOT/src/main/java/dev/centraleconomy/miner/plan/RetailPlan.java" \
  "$ROOT/src/main/java/dev/centraleconomy/miner/plan/TierPlan.java" \
  "$ROOT/tools/CoreSelfTest.java"

java -cp "$OUT" CoreSelfTest
