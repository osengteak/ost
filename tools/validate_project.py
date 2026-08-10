#!/usr/bin/env python3
from pathlib import Path
import json
import re

ROOT = Path(__file__).resolve().parents[1]

def read(path):
    return (ROOT / path).read_text(encoding="utf-8")

def require(condition, message):
    if not condition:
        raise SystemExit("FAIL: " + message)
    print("PASS:", message)

mod = read("src/main/java/dev/centraleconomy/miner/CentralEconomyMod.java")
client = read("src/client/java/dev/centraleconomy/miner/client/CentralEconomyClient.java")
interaction = read("src/main/java/dev/centraleconomy/miner/villager/MinerInteractionService.java")
employment = read("src/main/java/dev/centraleconomy/miner/villager/MinerEmploymentService.java")
transactions = read("src/main/java/dev/centraleconomy/miner/net/MinerMarketTransactions.java")
visual = read("src/main/java/dev/centraleconomy/miner/villager/MinerVisualIdentity.java")
props = read("gradle.properties")
workflow = read(".github/workflows/build.yml")

require("MinerInteractionService.initialize();" in mod, "server interaction hook is initialized")
require("UseEntityCallback" not in client, "client does not intercept villager clicks")
require("OpenMinerMarketC2SPayload" not in client, "normal market open does not depend on a client C2S click packet")
require("MinerEmploymentService.isActiveMiner" in interaction, "interaction authority is the persisted employment contract")
require("MinerEmploymentService.isActiveMiner" in transactions, "trade authority is the persisted employment contract")
require("profession().is" not in transactions, "transactions do not depend on volatile vanilla profession state")
require("Stable state. Never re-run setVillagerData here." in employment, "valid claims do not re-hire every tick")
require("workstation removed" in employment and "VillagerProfession.NONE" in employment, "workstation removal releases miner to unemployed")
require("MinerVisualIdentity.clearIfOurs" in employment, "workstation removal clears the miner badge")
require("ensureBadge" in visual and "profession().is" not in visual, "badge follows employment rather than volatile profession holder")
require("mod_version=0.6.0" in props, "project version is 0.6.0")
require("Validate source and economy invariants" in workflow, "CI runs project invariant validation")
require("Run pure economy core self-test" in workflow, "CI runs pure economy self-test")

plan = json.loads(read("src/main/resources/data/central_economy/economy/miner_plan.json"))
require(plan["planning_cycle_days"] == 7, "planning cycle remains 7 Minecraft days")
commodities = plan["commodities"]
require(len(commodities) == 10, "miner procurement catalog has 10 commodities")
retail_ids = {k for k,v in commodities.items() if "retail" in v}
expected_retail = {
    "minecraft:coal", "minecraft:copper_ingot", "minecraft:iron_ingot",
    "minecraft:redstone", "minecraft:lapis_lazuli", "minecraft:gold_ingot", "minecraft:diamond"
}
require(retail_ids == expected_retail, "miner retail catalog is exactly the agreed 7 processed/use-ready minerals")
require(not ({"minecraft:raw_copper", "minecraft:raw_iron", "minecraft:raw_gold"} & retail_ids), "raw ores are never retailed")

for item, data in commodities.items():
    a = data["procurement_a"]["emeralds"] / data["procurement_a"]["lot_items"]
    b = data["procurement_b"]["emeralds"] / data["procurement_b"]["lot_items"]
    require(a + 1e-12 >= b, f"{item}: A livelihood unit price is not below B industrial price")
    if "retail" in data:
        r = data["retail"]["emeralds"] / data["retail"]["lot_items"]
        require(r > a and r > b, f"{item}: direct same-item buy/sell arbitrage is impossible")

print("PASS: project validation complete")
