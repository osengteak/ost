#!/usr/bin/env python3
from pathlib import Path
import json

ROOT = Path(__file__).resolve().parents[1]


def read(path: str) -> str:
    return (ROOT / path).read_text(encoding="utf-8")


def require(condition: bool, message: str) -> None:
    if not condition:
        raise SystemExit("FAIL: " + message)
    print("PASS:", message)


mod = read("src/main/java/dev/centraleconomy/miner/CentralEconomyMod.java")
client = read("src/client/java/dev/centraleconomy/miner/client/CentralEconomyClient.java")
screen = read("src/client/java/dev/centraleconomy/miner/client/MinerMarketScreen.java")
layout = read("src/client/java/dev/centraleconomy/miner/client/MinerMarketLayout.java")
interaction = read("src/main/java/dev/centraleconomy/miner/villager/MinerInteractionService.java")
employment = read("src/main/java/dev/centraleconomy/miner/villager/MinerEmploymentService.java")
transactions = read("src/main/java/dev/centraleconomy/miner/net/MinerMarketTransactions.java")
networking = read("src/main/java/dev/centraleconomy/miner/net/MinerMarketNetworking.java")
request = read("src/main/java/dev/centraleconomy/miner/net/MinerTradeRequest.java")
repository = read("src/main/java/dev/centraleconomy/miner/plan/MinerPlanRepository.java")
visual = read("src/main/java/dev/centraleconomy/miner/villager/MinerVisualIdentity.java")
props = read("gradle.properties")
workflow = read(".github/workflows/build.yml")

# Bootstrap / stable employment contract.
require("MinerInteractionService.initialize();" in mod, "server interaction hook is initialized")
require("MinerMarketNetworking.initialize();" in mod, "network payloads/receivers are initialized")
require("UseEntityCallback" not in client, "client does not intercept villager clicks")
require("OpenMinerMarketC2SPayload" not in client, "normal market open does not depend on a client C2S click packet")
require("MinerEmploymentService.isActiveMiner" in interaction, "interaction authority is the persisted employment contract")
require("MinerEmploymentService.isActiveMiner" in transactions, "trade authority is the persisted employment contract")
require("profession().is" not in transactions, "transactions do not depend on volatile vanilla profession state")
require("Stable state. Never re-run setVillagerData here." in employment, "valid claims do not re-hire every tick")
require("workstation removed" in employment and "VillagerProfession.NONE" in employment, "workstation removal releases miner to unemployed")
require("MinerVisualIdentity.clearIfOurs" in employment, "workstation removal clears the miner badge")
require("ensureBadge" in visual and "profession().is" not in visual, "badge follows employment contract rather than volatile profession holder")

# Version / CI.
require("mod_version=0.6.1" in props, "project version is 0.6.1")
require("Central Economy Miner 0.6.1 initialized" in mod, "runtime version log is 0.6.1")
require("central-economy-miner-0.6.1.jar" in workflow, "CI names the installable JAR as 0.6.1")
require("Validate source and economy invariants" in workflow, "CI runs project invariant validation")
require("Run pure core UI and request self-tests" in workflow, "CI runs pure economy/UI/request self-tests")
require("gradle --no-daemon --stacktrace clean build" in workflow, "CI performs a real Fabric/Loom build after self-tests")

# Responsive UI / scrolling.  No horizontal paging/scroll state should be necessary.
require("MinerMarketLayout.calculate(width, height)" in screen, "market screen uses responsive layout math")
require("mouseScrolled(double mouseX, double mouseY, double horizontalAmount, double verticalAmount)" in screen,
        "market list implements Minecraft 26.2 vertical mouse-wheel callback")
require("scrollOffset" in screen and "layout.maxScroll" in screen, "market list maintains bounded vertical scroll state")
require("ROWS_PER_PAGE" not in screen and "int page" not in screen, "old fixed six-row pagination was removed")
require("panelWidth = Math.min(720, availableWidth)" in layout, "market panel width is capped and centered instead of overflowing horizontally")
require("buyX + buyWidth" not in screen, "screen does not use old hard-coded off-screen buy column coordinates")

# Executable trade path and diagnostics.
require("ClientPlayNetworking.canSend(ExecuteMinerTradeC2SPayload.TYPE)" in screen, "client verifies server accepts the trade payload before sending")
require("[CE-TRADE] client sending" in screen, "client logs each outgoing trade request")
require("[CE-TRADE] server received" in networking, "server logs receipt of each trade request")
require("MinerTradeRequest.parse" in networking, "server parses a validated trade request object")
require("[CE-TRADE] execute start" in transactions, "transaction layer logs each execution start")
require("[CE-TRADE] SELL committed" in transactions and "[CE-TRADE] BUY committed" in transactions,
        "transaction layer logs successful buy and sell commits")
require("engine.plan().commodity(commodityId) == null" in transactions, "server rejects commodities outside the current miner plan")
require("player.getInventory().setChanged();" in transactions, "server marks direct inventory mutations dirty for synchronization")
require("sendSnapshot(player, villager, message);" in transactions, "server refreshes market state after a valid trade attempt")
require("[CE-MARKET] client received snapshot" in client, "client logs authoritative market snapshots")

# Config migration is important because old schema-1 configs otherwise keep raw ores forever.
require("existingSchema < bundledSchema" in repository, "older user miner plans are detected by schema")
require("miner_plan.schema" in repository and ".backup.json" in repository, "older user miner plans are backed up before migration")
require("writeConfig(bundled);" in repository, "older/invalid plans can be replaced by the bundled schema-2 plan")

plan = json.loads(read("src/main/resources/data/central_economy/economy/miner_plan.json"))
require(plan["schema"] == 2, "bundled miner plan schema is 2")
require(plan["planning_cycle_days"] == 7, "planning cycle remains 7 Minecraft days")
commodities = plan["commodities"]
expected = {
    "minecraft:coal",
    "minecraft:copper_ingot",
    "minecraft:iron_ingot",
    "minecraft:redstone",
    "minecraft:lapis_lazuli",
    "minecraft:gold_ingot",
    "minecraft:diamond",
}
raw_ores = {"minecraft:raw_copper", "minecraft:raw_iron", "minecraft:raw_gold"}
require(set(commodities) == expected, "miner catalog is exactly the agreed 7 processed/use-ready minerals")
require(raw_ores.isdisjoint(commodities), "raw copper/iron/gold are absent from all miner trading")
require(all("procurement_a" in v and "procurement_b" in v and "retail" in v for v in commodities.values()),
        "all 7 miner commodities support both procurement and retail")

for item, data in commodities.items():
    a = data["procurement_a"]["emeralds"] / data["procurement_a"]["lot_items"]
    b = data["procurement_b"]["emeralds"] / data["procurement_b"]["lot_items"]
    r = data["retail"]["emeralds"] / data["retail"]["lot_items"]
    require(a + 1e-12 >= b, f"{item}: A livelihood unit price is not below B industrial price")
    require(r > a and r > b, f"{item}: direct same-item buy/sell arbitrage is impossible")

# Transport framing stays deliberately small and rejects delimiter injection.
require("split(\"\\\\|\", 3)" in request, "trade request parser expects exactly three transport fields")
require("commodityId.indexOf('|')" in request, "trade request rejects delimiter injection in commodity ids")

print("PASS: project validation complete")
