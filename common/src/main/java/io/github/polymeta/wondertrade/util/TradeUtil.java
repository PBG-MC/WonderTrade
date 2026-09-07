package io.github.polymeta.wondertrade.util;

import com.cobblemon.mod.common.Cobblemon;
import com.cobblemon.mod.common.api.permission.CobblemonPermission;
import com.cobblemon.mod.common.api.permission.PermissionLevel;
import com.cobblemon.mod.common.api.pokemon.PokemonProperties;
import com.cobblemon.mod.common.api.pokemon.PokemonPropertyExtractor;
import com.cobblemon.mod.common.pokemon.Pokemon;
import io.github.polymeta.wondertrade.WonderTrade;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerPlayer;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;

import java.util.UUID;
import java.util.concurrent.ConcurrentSkipListSet;
import java.util.concurrent.TimeUnit;

public class TradeUtil
{
    private static final Logger logger = LogManager.getLogger();
    private static final ConcurrentSkipListSet<UUID> playersOnCooldown = new ConcurrentSkipListSet<>();

    public static boolean isPlayerOnCooldown(UUID playerId, boolean canBypass)
    {
        return playersOnCooldown.contains(playerId) && !canBypass && WonderTrade.config.cooldownEnabled;
    }

    public static void doWonderTrade(ServerPlayer player, Pokemon slot)
    {
        var canBypass = Cobblemon.INSTANCE.getPermissionValidator()
                                .hasPermission(player, new CobblemonPermission("wondertrade.command.trade.bypass",
                                                                               PermissionLevel.CHEAT_COMMANDS_AND_COMMAND_BLOCKS));
        if(isPlayerOnCooldown(player.getUUID(), canBypass)) {
            player.sendSystemMessage(WonderTrade.config.messages.cooldownFeedback(player.registryAccess()));
            return;
        }
        if(isPokemonForbidden(slot) && !canBypass) {
            player.sendSystemMessage(WonderTrade.config.messages.pokemonNotAllowed(player.registryAccess()));
            return;
        }

        // Clamp before snapshotting, so what we bank in the pool is what we advertise.
        if(WonderTrade.config.adjustNewPokemonToLevelRange) {
            var level = slot.getLevel();
            if(level > WonderTrade.config.poolMaxLevel) {
                slot.setLevel(WonderTrade.config.poolMaxLevel);
            }
            else if (level < WonderTrade.config.poolMinLevel) {
                slot.setLevel(WonderTrade.config.poolMinLevel);
            }
        }
        var deposit = slot.createPokemonProperties(PokemonPropertyExtractor.ALL).asString(" ");

        // Draw and deposit as one atomic step so two players trading in the same tick
        // can never draw the same entry, and so an empty pool is reported rather than
        // thrown (upstream called rng.nextInt(0) here -> "bound must be positive").
        var wonderPoke = WonderTrade.drawAndDeposit(deposit);
        if(wonderPoke == null) {
            player.sendSystemMessage(WonderTrade.config.messages.poolEmpty(player.registryAccess()));
            logger.warn("A player tried to WonderTrade but the pool is empty; triggering a regeneration.");
            WonderTrade.regeneratePool(WonderTrade.config.poolSize);
            return;
        }

        // Build the reward before touching the player's party. A malformed pool entry
        // must not cost them the Pokemon they put in.
        Pokemon received;
        try {
            received = PokemonProperties.Companion.parse(wonderPoke).create();
        } catch (Exception e) {
            logger.error("Discarding unparseable WonderTrade pool entry '" + wonderPoke + "'; the trade was rolled back.", e);
            WonderTrade.rollbackDeposit(deposit);
            player.sendSystemMessage(WonderTrade.config.messages.tradeFailed(player.registryAccess()));
            return;
        }

        var playerParty = Cobblemon.INSTANCE.getStorage().getParty(player);
        playerParty.remove(slot);
        if(!playerParty.add(received)) {
            // Party had no room for the reward: undo everything rather than void it.
            logger.error("Could not add the traded Pokemon to {}'s party; rolling the trade back.", player.getGameProfile().getName());
            playerParty.add(slot);
            WonderTrade.restoreDrawn(wonderPoke, deposit);
            player.sendSystemMessage(WonderTrade.config.messages.tradeFailed(player.registryAccess()));
            return;
        }
        WonderTrade.savePool();

        if(WonderTrade.config.cooldownEnabled && !canBypass) {
            playersOnCooldown.add(player.getUUID());
            WonderTrade.scheduler.schedule(() -> {playersOnCooldown.remove(player.getUUID());},
                                           WonderTrade.config.cooldown, TimeUnit.MINUTES);
        }
        player.sendSystemMessage(WonderTrade.config.messages.successFeedback(player.registryAccess()));
        var server = player.getServer();
        var broadcastMessage = WonderTrade.config.messages.broadcastPokemon(slot, player.registryAccess());
        if(server != null && !broadcastMessage.equals(Component.empty())) {
            server.getPlayerList().broadcastSystemMessage(broadcastMessage, false);
        }
    }

    public static boolean isPokemonForbidden(Pokemon pokemon) {
        for (String property : WonderTrade.config.blacklist) {
            if(PokemonProperties.Companion.parse(property).matches(pokemon)) {
                return true;
            }
        }
        return false;
    }
}
