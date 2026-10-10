package io.github.polymeta.wondertrade.util;

import io.github.polymeta.wondertrade.WonderTrade;
import io.github.polymeta.wondertrade.configuration.BaseConfig;
import net.minecraft.core.RegistryAccess;
import net.minecraft.core.component.DataComponents;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.Container;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;

public class GuiUtil
{
    public static void placeButton(BaseConfig.ButtonConfig input, Container container, RegistryAccess access)
    {
        if(input == null)
        {
            return;
        }
        var stack = new ItemStack(BuiltInRegistries.ITEM.get(ResourceLocation.parse(input.item)));
        if(input.customName != null && !input.customName.isBlank())
        {
            stack.set(DataComponents.CUSTOM_NAME, TextUtil.styledText(input.customName, access));
        }
        container.setItem(input.position, stack);
    }

    /** A pane-like decoration stack: configured item (falls back to {@code fallback} if unknown), blank name unless given. */
    public static ItemStack decoration(String itemId, String fallback, String name, RegistryAccess access)
    {
        var item = Items.AIR;
        var id = itemId == null ? null : ResourceLocation.tryParse(itemId);
        if(id != null)
        {
            item = BuiltInRegistries.ITEM.getOptional(id).orElse(Items.AIR);
        }
        if(item == Items.AIR)
        {
            item = BuiltInRegistries.ITEM.get(ResourceLocation.parse(fallback));
        }
        var stack = new ItemStack(item);
        stack.set(DataComponents.CUSTOM_NAME,
                name == null || name.isBlank() ? Component.empty() : TextUtil.styledText(name, access));
        return stack;
    }

    public static void checkAndPlaceBorders(Container container, RegistryAccess access)
    {
        var gui = WonderTrade.config.gui;
        if(!gui.generateBorders || gui.hideFillers)
        {
            return;
        }
        var border = decoration(gui.borderItem, "minecraft:red_stained_glass_pane", null, access);
        var accent = decoration(gui.accentBorderItem, "minecraft:black_stained_glass_pane", null, access);
        var filler = decoration(gui.fillerItem, "minecraft:white_stained_glass_pane", null, access);

        for(int i = 0; i < 9; i++)
        {
            container.setItem(i, border);
        }
        for(int i = 9; i < 18; i++)
        {
            container.setItem(i, accent);
        }
        for(int i = 18; i < 27; i++)
        {
            container.setItem(i, filler);
        }
    }
}
