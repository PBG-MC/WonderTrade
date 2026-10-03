package io.github.polymeta.wondertrade;

import io.github.polymeta.wondertrade.configuration.BaseConfig;
import io.github.polymeta.wondertrade.configuration.Pool;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.io.File;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import java.util.stream.Stream;

import static org.junit.jupiter.api.Assertions.*;

class WonderTradePersistenceTest {
    @TempDir Path dir;

    @BeforeEach
    void setUp() {
        WonderTrade.configDir = dir.toFile();
        WonderTrade.pool = new Pool();
        WonderTrade.config = new BaseConfig();
    }

    private long count(String regex) throws Exception {
        try (Stream<Path> s = Files.list(dir)) {
            return s.filter(p -> p.getFileName().toString().matches(regex)).count();
        }
    }

    // F6
    @Test
    void savePoolWritesFileAndLeavesNoTempFile() throws Exception {
        WonderTrade.pool.pokemon.addAll(List.of("species=pikachu level=5", "species=eevee level=7"));
        WonderTrade.savePool();
        var loaded = BaseConfig.GSON.fromJson(Files.readString(dir.resolve("pool.json")), Pool.class);
        assertEquals(WonderTrade.pool.pokemon, loaded.pokemon);
        assertEquals(0, count(".*[.]tmp"));
    }

    @Test
    void savePoolReplacesExistingFile() throws Exception {
        Files.writeString(dir.resolve("pool.json"), "old");
        WonderTrade.pool.pokemon.add("species=mew");
        WonderTrade.savePool();
        assertTrue(Files.readString(dir.resolve("pool.json")).contains("mew"));
    }

    @Test
    void saveConfigWritesAtomically() throws Exception {
        WonderTrade.config.poolSize = 77;
        WonderTrade.saveConfig();
        var loaded = BaseConfig.GSON.fromJson(Files.readString(dir.resolve("main.json")), BaseConfig.class);
        assertEquals(77, loaded.poolSize);
        assertEquals(0, count(".*[.]tmp"));
    }

    @Test
    void loadPoolMovesUnparseableFileAsideInsteadOfOverwriting() throws Exception {
        String garbage = "{ not json at all [[[";
        Files.writeString(dir.resolve("pool.json"), garbage);
        WonderTrade.loadPool();
        assertTrue(WonderTrade.pool.pokemon.isEmpty());
        File[] broken = dir.toFile().listFiles((d, n) -> n.startsWith("pool.json.broken-"));
        assertNotNull(broken);
        assertEquals(1, broken.length);
        assertEquals(garbage, Files.readString(broken[0].toPath()));
        // a fresh, valid pool.json is written in its place
        assertNotNull(BaseConfig.GSON.fromJson(Files.readString(dir.resolve("pool.json")), Pool.class));
    }

    @Test
    void loadPoolKeepsValidFile() throws Exception {
        Files.writeString(dir.resolve("pool.json"), "{\"pokemon\":[\"species=mew\"]}");
        WonderTrade.loadPool();
        assertEquals(List.of("species=mew"), WonderTrade.pool.pokemon);
        assertEquals(0, count("pool[.]json[.]broken-.*"));
    }

    // F7
    @Test
    void loadConfigWithEmptyFileFallsBackToDefaults() throws Exception {
        Files.writeString(dir.resolve("main.json"), "");
        WonderTrade.config = null;
        assertDoesNotThrow(WonderTrade::loadConfig);
        assertNotNull(WonderTrade.config);
        assertEquals(new BaseConfig().poolSize, WonderTrade.config.poolSize);
    }

    @Test
    void loadConfigWithWhitespaceOnlyFileFallsBackToDefaults() throws Exception {
        Files.writeString(dir.resolve("main.json"), "   " + System.lineSeparator() + "  ");
        assertDoesNotThrow(WonderTrade::loadConfig);
        assertNotNull(WonderTrade.config);
    }

    @Test
    void loadConfigClampsNonPositivePoolSize() throws Exception {
        Files.writeString(dir.resolve("main.json"), "{\"poolSize\":0}");
        WonderTrade.loadConfig();
        assertEquals(new BaseConfig().poolSize, WonderTrade.config.poolSize);
    }

    // F17
    @Test
    void drawAndDepositOnEmptyPoolReturnsNullAndDepositsNothing() {
        assertNull(WonderTrade.drawAndDeposit("species=a"));
        assertTrue(WonderTrade.pool.pokemon.isEmpty());
    }

    @Test
    void drawAndDepositSwapsEntries() {
        WonderTrade.pool.pokemon.add("species=a");
        assertEquals("species=a", WonderTrade.drawAndDeposit("species=b"));
        assertEquals(List.of("species=b"), WonderTrade.pool.pokemon);
    }

    @Test
    void drawAndDepositKeepsPoolSize() {
        WonderTrade.pool.pokemon.addAll(List.of("a", "b", "c", "d"));
        for (int i = 0; i < 20; i++) {
            assertNotNull(WonderTrade.drawAndDeposit("x" + i));
            assertEquals(4, WonderTrade.pool.pokemon.size());
        }
    }

    @Test
    void rollbackDepositRemovesDeposit() {
        WonderTrade.pool.pokemon.addAll(List.of("a", "dep"));
        WonderTrade.rollbackDeposit("dep");
        assertEquals(List.of("a"), WonderTrade.pool.pokemon);
    }

    @Test
    void restoreDrawnUndoesDrawAndDeposit() {
        WonderTrade.pool.pokemon.add("a");
        String drawn = WonderTrade.drawAndDeposit("dep");
        WonderTrade.restoreDrawn(drawn, "dep");
        assertEquals(List.of("a"), WonderTrade.pool.pokemon);
    }
}
