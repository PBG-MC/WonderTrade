package io.github.polymeta.wondertrade.util;

/**
 * The pure decisions behind WonderTrade deposits, kept free of Cobblemon types so they can be
 * unit-tested without a Minecraft bootstrap.
 */
public final class TradeRules {
    private TradeRules() {}

    public enum DepositCheck { ALLOWED, UNTRADEABLE, BLACKLISTED }

    /**
     * Decides whether a Pokemon may enter the pool.
     *
     * An untradeable Pokemon is refused for everyone, including holders of the bypass permission:
     * the bypass only lifts the config blacklist, never the in-game lock.
     */
    public static DepositCheck checkDeposit(boolean tradeable, boolean blacklisted, boolean canBypass) {
        if(!tradeable) {
            return DepositCheck.UNTRADEABLE;
        }
        if(blacklisted && !canBypass) {
            return DepositCheck.BLACKLISTED;
        }
        return DepositCheck.ALLOWED;
    }

    /**
     * Appends an explicit {@code tradeable=} key to a serialized Pokemon. Cobblemon's
     * {@code PokemonPropertyExtractor.ALL} has no extractor for the flag, so without this the
     * pooled copy silently becomes tradeable again.
     */
    public static String withTradeable(String properties, boolean tradeable) {
        return properties + " tradeable=" + (tradeable ? "yes" : "no");
    }
}
