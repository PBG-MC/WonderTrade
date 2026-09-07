package io.github.polymeta.wondertrade.commands;

import com.cobblemon.mod.common.Cobblemon;
import com.cobblemon.mod.common.api.permission.CobblemonPermission;
import com.cobblemon.mod.common.api.permission.PermissionLevel;
import com.mojang.brigadier.Command;
import com.mojang.brigadier.CommandDispatcher;
import com.mojang.brigadier.builder.LiteralArgumentBuilder;
import io.github.polymeta.wondertrade.WonderTrade;
import net.minecraft.commands.CommandSourceStack;
import net.minecraft.network.chat.Component;

/**
 * Re-reads config/wondertrade/pool.json into the live pool.
 *
 * <p>Upstream only ever loaded the pool once, during {@code init()}, while writing it back
 * after every trade. That made a hand-curated pool impossible to deploy without a full
 * server restart: the file was ignored until boot, and the next trade overwrote it with
 * the in-memory pool. This command closes that gap.
 */
public class ReloadPool {
    public static void register(CommandDispatcher<CommandSourceStack> dispatcher) {
        dispatcher.register(
                LiteralArgumentBuilder.<CommandSourceStack>literal("reloadpool")
                        .requires(req -> Cobblemon.INSTANCE.getPermissionValidator().hasPermission(req,
                                new CobblemonPermission("wondertrade.command.reloadpool", PermissionLevel.ALL_COMMANDS)))
                        .executes(ctx -> {
                            if(WonderTrade.regenerating.get()) {
                                ctx.getSource().sendSystemMessage(Component.literal(
                                        "The WonderTrade pool is being regenerated - try again once it finishes."));
                                return Command.SINGLE_SUCCESS;
                            }
                            var loaded = WonderTrade.reloadPoolFromDisk();
                            if(loaded < 0) {
                                ctx.getSource().sendSystemMessage(Component.literal(
                                        "Failed to reload the pool - see the console. The live pool was left untouched."));
                                return 0;
                            }
                            ctx.getSource().sendSystemMessage(Component.literal(
                                    "Reloaded the WonderTrade pool from disk: " + loaded + " Pokemon."));
                            return Command.SINGLE_SUCCESS;
                        })
        );
    }
}
