package io.github.polymeta.wondertrade;

import io.github.polymeta.wondertrade.util.TradeRules;
import io.github.polymeta.wondertrade.util.TradeRules.DepositCheck;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.*;

class TradeRulesTest {

    @Test
    void untradeableIsRefusedWithoutBypass() {
        assertEquals(DepositCheck.UNTRADEABLE, TradeRules.checkDeposit(false, false, false));
    }

    @Test
    void untradeableIsRefusedEvenWithBypass() {
        assertEquals(DepositCheck.UNTRADEABLE, TradeRules.checkDeposit(false, false, true));
        assertEquals(DepositCheck.UNTRADEABLE, TradeRules.checkDeposit(false, true, true));
    }

    @Test
    void blacklistedIsRefusedWithoutBypass() {
        assertEquals(DepositCheck.BLACKLISTED, TradeRules.checkDeposit(true, true, false));
    }

    @Test
    void bypassLiftsTheBlacklistOnly() {
        assertEquals(DepositCheck.ALLOWED, TradeRules.checkDeposit(true, true, true));
    }

    @Test
    void tradeableUnlistedPokemonIsAllowed() {
        assertEquals(DepositCheck.ALLOWED, TradeRules.checkDeposit(true, false, false));
        assertEquals(DepositCheck.ALLOWED, TradeRules.checkDeposit(true, false, true));
    }

    @Test
    void withTradeableWritesAnExplicitKeyForUntradeable() {
        assertEquals("species=pikachu level=50 tradeable=no",
                TradeRules.withTradeable("species=pikachu level=50", false));
    }

    @Test
    void withTradeableWritesAnExplicitKeyForTradeable() {
        assertEquals("species=pikachu level=50 tradeable=yes",
                TradeRules.withTradeable("species=pikachu level=50", true));
    }
}
