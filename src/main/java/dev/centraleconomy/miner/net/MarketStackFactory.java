package dev.centraleconomy.miner.net;

import dev.centraleconomy.miner.plan.CommodityPlan;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.core.registries.Registries;
import net.minecraft.resources.Identifier;
import net.minecraft.resources.ResourceKey;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.item.alchemy.Potion;
import net.minecraft.world.item.alchemy.PotionContents;
import net.minecraft.world.item.enchantment.Enchantment;

/** Builds exact server-authoritative ItemStacks for normal and configured variant commodities. */
public final class MarketStackFactory {
    private MarketStackFactory() {}

    public static ItemStack create(ServerPlayer player, CommodityPlan cp, int count) {
        if (count <= 0) throw new IllegalArgumentException("count must be positive");
        return switch (cp.kind()) {
            case "item" -> plain(cp.itemId(), count);
            case "enchanted_book" -> enchantedBook(player, cp, count);
            case "potion" -> potion(player, cp, count);
            case "tipped_arrow" -> tippedArrow(player, cp, count);
            default -> throw new IllegalArgumentException("unsupported commodity kind: " + cp.kind());
        };
    }

    public static Item resolvePlainItem(String itemId) {
        Identifier id = parseId(itemId);
        Item item = BuiltInRegistries.ITEM.getValue(id);
        return item == null ? Items.AIR : item;
    }

    public static String translationKey(CommodityPlan cp) {
        Item item = resolvePlainItem(cp.itemId());
        return item == Items.AIR ? "" : item.getDescriptionId();
    }

    private static ItemStack plain(String itemId, int count) {
        Item item = resolvePlainItem(itemId);
        if (item == Items.AIR) throw new IllegalArgumentException("unknown item: " + itemId);
        return new ItemStack(item, count);
    }

    private static ItemStack enchantedBook(ServerPlayer player, CommodityPlan cp, int count) {
        var registry = player.registryAccess().lookupOrThrow(Registries.ENCHANTMENT);
        ResourceKey<Enchantment> key = ResourceKey.create(Registries.ENCHANTMENT, parseId(cp.variantId()));
        var holder = registry.get(key).orElseThrow(() -> new IllegalArgumentException("unknown enchantment: " + cp.variantId()));
        ItemStack stack = new ItemStack(Items.ENCHANTED_BOOK, count);
        stack.enchant(holder, cp.level());
        return stack;
    }

    private static ItemStack potion(ServerPlayer player, CommodityPlan cp, int count) {
        Item item = resolvePlainItem(cp.itemId());
        if (item == Items.AIR) throw new IllegalArgumentException("unknown potion item: " + cp.itemId());
        var registry = player.registryAccess().lookupOrThrow(Registries.POTION);
        ResourceKey<Potion> key = ResourceKey.create(Registries.POTION, parseId(cp.variantId()));
        var holder = registry.get(key).orElseThrow(() -> new IllegalArgumentException("unknown potion: " + cp.variantId()));
        ItemStack stack = PotionContents.createItemStack(item, holder);
        stack.setCount(count);
        return stack;
    }

    private static ItemStack tippedArrow(ServerPlayer player, CommodityPlan cp, int count) {
        var registry = player.registryAccess().lookupOrThrow(Registries.POTION);
        ResourceKey<Potion> key = ResourceKey.create(Registries.POTION, parseId(cp.variantId()));
        var holder = registry.get(key).orElseThrow(() -> new IllegalArgumentException("unknown potion: " + cp.variantId()));
        ItemStack stack = PotionContents.createItemStack(Items.TIPPED_ARROW, holder);
        stack.setCount(count);
        return stack;
    }

    private static Identifier parseId(String value) {
        int colon = value.indexOf(':');
        return colon < 0
                ? Identifier.fromNamespaceAndPath("minecraft", value)
                : Identifier.fromNamespaceAndPath(value.substring(0, colon), value.substring(colon + 1));
    }
}
