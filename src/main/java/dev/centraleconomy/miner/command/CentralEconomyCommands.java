package dev.centraleconomy.miner.command;

import com.mojang.brigadier.arguments.BoolArgumentType;
import com.mojang.brigadier.arguments.StringArgumentType;
import dev.centraleconomy.miner.market.MarketSavedData;
import dev.centraleconomy.miner.market.MinerMarketRuntime;
import net.fabricmc.fabric.api.command.v2.CommandRegistrationCallback;
import net.minecraft.commands.Commands;
import net.minecraft.network.chat.Component;
import net.minecraft.server.permissions.Permissions;

import java.util.Set;

/** Debug/admin hooks for progression structures whose physical definitions have not yet been designed. */
public final class CentralEconomyCommands {
    private static final Set<String> FLAGS = Set.of("market_warehouse", "regional_trade_route", "mineral_warehouse");
    private CentralEconomyCommands() {}

    public static void initialize() {
        CommandRegistrationCallback.EVENT.register((dispatcher, registryAccess, environment) -> dispatcher.register(
                Commands.literal("central_economy")
                        .requires(source -> source.permissions().hasPermission(Permissions.COMMANDS_MODERATOR))
                        .then(Commands.literal("reload").executes(ctx -> {
                            MinerMarketRuntime.reload();
                            MarketSavedData saved = MarketSavedData.get(ctx.getSource().getServer());
                            saved.state().initializedCycle(Long.MIN_VALUE);
                            saved.touch();
                            ctx.getSource().sendSuccess(() -> Component.literal("Central Economy: miner_plan.json reloaded; current cycle stock will reinitialize."), false);
                            return 1;
                        }))
                        .then(Commands.literal("status").executes(ctx -> {
                            MarketSavedData saved = MarketSavedData.get(ctx.getSource().getServer());
                            var s = saved.state();
                            ctx.getSource().sendSuccess(() -> Component.literal(
                                    "Central Economy | cycle=" + s.initializedCycle()
                                            + " | turnover=" + s.cumulativeTurnoverEmeralds() + "E"
                                            + " | flags=" + s.infrastructureFlags()
                                            + " | quotaKeys=" + s.quotaUsage().size()
                                            + " | claims=" + s.workstationClaims().size()), false);
                            return 1;
                        }))
                        .then(Commands.literal("flag")
                                .then(Commands.argument("name", StringArgumentType.word())
                                        .then(Commands.argument("enabled", BoolArgumentType.bool())
                                                .executes(ctx -> {
                                                    String flag = StringArgumentType.getString(ctx, "name");
                                                    boolean enabled = BoolArgumentType.getBool(ctx, "enabled");
                                                    if (!FLAGS.contains(flag)) {
                                                        ctx.getSource().sendFailure(Component.literal("Unknown flag. Use: " + FLAGS));
                                                        return 0;
                                                    }
                                                    MarketSavedData saved = MarketSavedData.get(ctx.getSource().getServer());
                                                    if (enabled) saved.state().infrastructureFlags().add(flag);
                                                    else saved.state().infrastructureFlags().remove(flag);
                                                    saved.touch();
                                                    ctx.getSource().sendSuccess(() -> Component.literal(flag + " = " + enabled), false);
                                                    return 1;
                                                }))))
                        .then(Commands.literal("clear_workstation_claims").executes(ctx -> {
                            MarketSavedData saved = MarketSavedData.get(ctx.getSource().getServer());
                            int count = saved.state().workstationClaims().size();
                            saved.state().workstationClaims().clear();
                            saved.touch();
                            ctx.getSource().sendSuccess(() -> Component.literal("Cleared " + count + " miner workstation fallback claims."), false);
                            return count;
                        }))
        ));
    }
}
