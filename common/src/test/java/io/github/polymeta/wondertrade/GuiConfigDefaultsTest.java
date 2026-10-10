package io.github.polymeta.wondertrade;

import io.github.polymeta.wondertrade.configuration.BaseConfig;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.*;

class GuiConfigDefaultsTest {
    @Test
    void defaultsReproduceStockLook() {
        var gui = new BaseConfig().gui;
        assertEquals("minecraft:red_stained_glass_pane", gui.borderItem);
        assertEquals("minecraft:black_stained_glass_pane", gui.accentBorderItem);
        assertEquals("minecraft:white_stained_glass_pane", gui.fillerItem);
        assertFalse(gui.hideFillers);
        assertTrue(gui.generateBorders);
        assertEquals("minecraft:red_stained_glass_pane", gui.poolFooterItem);
        assertEquals("", gui.poolFooterName);
    }

    @Test
    void legacyConfigWithoutNewKeysKeepsDefaults() {
        var json = "{\"gui\":{\"generateBorders\":true,\"mainWindowTitle\":\"<red>Custom\"}}";
        var gui = BaseConfig.GSON.fromJson(json, BaseConfig.class).gui;
        assertEquals("<red>Custom", gui.mainWindowTitle);
        assertEquals("minecraft:red_stained_glass_pane", gui.borderItem);
        assertEquals("minecraft:white_stained_glass_pane", gui.fillerItem);
        assertFalse(gui.hideFillers);
        assertEquals("minecraft:red_stained_glass_pane", gui.poolFooterItem);
    }

    @Test
    void newKeysRoundTrip() {
        var json = "{\"gui\":{\"hideFillers\":true,\"fillerItem\":\"minecraft:glass_pane\"}}";
        var gui = BaseConfig.GSON.fromJson(json, BaseConfig.class).gui;
        assertTrue(gui.hideFillers);
        assertEquals("minecraft:glass_pane", gui.fillerItem);
    }
}
