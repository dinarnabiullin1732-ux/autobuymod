package ru.malfix.autobuy.mc;

import net.minecraft.client.MinecraftClient;
import net.minecraft.component.DataComponentTypes;
import net.minecraft.item.Item;
import net.minecraft.item.ItemStack;
import net.minecraft.item.tooltip.TooltipType;
import net.minecraft.registry.Registries;
import net.minecraft.text.Text;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

/**
 * Minecraft 1.21.4 compatibility helpers for ItemStack data/components.
 *
 * 1.16.5 stored most custom item identity in raw ItemStack NBT. Since 1.20.5+
 * the same data is exposed as data components. For matcher/search purposes we
 * stringify the important components and keep the old API boundary stable.
 */
public final class McItemStacks {
    private McItemStacks() {
    }

    public static String itemId(ItemStack stack) {
        try {
            if (stack == null || stack.isEmpty()) {
                return "minecraft:air";
            }
            return Registries.ITEM.getId(stack.getItem()).toString();
        } catch (Throwable ignored) {
            return "minecraft:air";
        }
    }

    public static List<String> tooltip(ItemStack stack, MinecraftClient client) {
        if (stack == null || stack.isEmpty()) {
            return Collections.emptyList();
        }
        List<String> result = new ArrayList<String>();
        try {
            List<Text> tooltip = stack.getTooltip(
                    Item.TooltipContext.create(client == null ? null : client.world),
                    client == null ? null : client.player,
                    TooltipType.BASIC
            );
            for (Text text : tooltip) {
                if (text != null) {
                    result.add(text.getString());
                }
            }
        } catch (Throwable ignored) {
            try {
                result.add(stack.getName().getString());
            } catch (Throwable ignored2) {
            }
        }
        return result;
    }

    public static String componentString(ItemStack stack) {
        if (stack == null || stack.isEmpty()) {
            return "";
        }
        StringBuilder out = new StringBuilder(512);
        append(out, "name", safeName(stack));
        append(out, "components", safeObject(stack.getComponents()));
        append(out, "componentChanges", safeObject(stack.getComponentChanges()));
        append(out, "customData", safeComponent(stack, DataComponentTypes.CUSTOM_DATA));
        append(out, "profile", safeComponent(stack, DataComponentTypes.PROFILE));
        append(out, "customName", safeComponent(stack, DataComponentTypes.CUSTOM_NAME));
        append(out, "itemName", safeComponent(stack, DataComponentTypes.ITEM_NAME));
        append(out, "lore", safeComponent(stack, DataComponentTypes.LORE));
        append(out, "enchantments", safeComponent(stack, DataComponentTypes.ENCHANTMENTS));
        append(out, "storedEnchantments", safeComponent(stack, DataComponentTypes.STORED_ENCHANTMENTS));
        append(out, "attributes", safeComponent(stack, DataComponentTypes.ATTRIBUTE_MODIFIERS));
        append(out, "potion", safeComponent(stack, DataComponentTypes.POTION_CONTENTS));
        append(out, "dyedColor", safeComponent(stack, DataComponentTypes.DYED_COLOR));
        append(out, "customModelData", safeComponent(stack, DataComponentTypes.CUSTOM_MODEL_DATA));
        return out.toString();
    }

    private static <T> String safeComponent(ItemStack stack, net.minecraft.component.ComponentType<T> type) {
        try {
            T value = stack.get(type);
            return value == null ? "" : String.valueOf(value);
        } catch (Throwable ignored) {
            return "";
        }
    }

    private static String safeName(ItemStack stack) {
        try {
            return stack.getName().getString();
        } catch (Throwable ignored) {
            return "";
        }
    }

    private static String safeObject(Object value) {
        try {
            return value == null ? "" : String.valueOf(value);
        } catch (Throwable ignored) {
            return "";
        }
    }

    private static void append(StringBuilder out, String key, String value) {
        if (value == null || value.isEmpty()) {
            return;
        }
        if (out.length() > 0) {
            out.append(' ');
        }
        out.append(key).append('=').append(value);
    }
}
