package dev.centraleconomy.miner.villager;

import net.minecraft.ChatFormatting;
import net.minecraft.network.chat.Component;
import net.minecraft.world.entity.npc.villager.Villager;

import java.util.LinkedHashMap;
import java.util.Map;

/** Visible identity for any villager employed by a Central Economy workstation contract. */
public final class MinerVisualIdentity {
    private static final Map<String, String> BADGES = new LinkedHashMap<>();
    static {
        BADGES.put("farmer", "[농부]");
        BADGES.put("rancher", "[목축업자]");
        BADGES.put("fisher", "[어부]");
        BADGES.put("miner", "[광부]");
        BADGES.put("lumberjack", "[벌목꾼]");
        BADGES.put("mason", "[석공]");
        BADGES.put("fletcher", "[화살 제조인]");
        BADGES.put("librarian", "[사서]");
        BADGES.put("cleric", "[성직자]");
        BADGES.put("cartographer", "[지도제작자]");
    }

    private MinerVisualIdentity() {}

    /** Never overwrites a player-assigned custom name. */
    public static void ensureBadge(Villager villager, String marketId) {
        String text = BADGES.get(marketId);
        if (text == null) return;
        if (villager.getCustomName() == null || isOurBadge(villager.getCustomName())) {
            villager.setCustomName(Component.literal(text).withStyle(ChatFormatting.GOLD));
            villager.setCustomNameVisible(true);
        }
    }

    public static void clearIfOurs(Villager villager) {
        Component current = villager.getCustomName();
        if (current != null && isOurBadge(current)) {
            villager.setCustomName(null);
            villager.setCustomNameVisible(false);
        }
    }

    private static boolean isOurBadge(Component component) {
        String value = component.getString();
        return BADGES.containsValue(value) || "[Miner]".equals(value);
    }
}
