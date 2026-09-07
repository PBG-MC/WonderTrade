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

public class Reload {
    public static void register(CommandDispatcher<CommandSourceStack> dispatcher) {
        dispatcher.register(
                LiteralArgumentBuilder.<CommandSourceStack>literal("reloadwondertrade")
                        .requires(req -> Cobblemon.INSTANCE.getPermissionValidator().hasPermission(req,
                                new CobblemonPermission("wondertrade.command.reload", PermissionLevel.ALL_COMMANDS)))
                        .executes(ctx -> {
                            WonderTrade.loadConfig();
                            ctx.getSource().sendSystemMessage(Component.literal("Config reloaded, check console for errors"));
                            return Command.SINGLE_SUCCESS;
                        })
        );
        // Upstream also registered "regeneratepool" here as a redirect to *this* command, which
        // (registering after RegeneratePool) silently replaced the real /regeneratepool with a
        // config reload. Removed - RegeneratePool owns that literal.
    }
}
