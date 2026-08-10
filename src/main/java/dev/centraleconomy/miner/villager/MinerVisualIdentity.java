package dev.centraleconomy.miner.villager;

import net.minecraft.ChatFormatting;
import net.minecraft.network.chat.Component;
import net.minecraft.world.entity.npc.villager.Villager;

/**
 * Server-side visual identity for the custom miner profession.
 *
 * We deliberately use a visible name badge instead of relying on a custom
 * villager profession texture. That makes profession state obvious even if
 * a resource pack changes villager textures.
 */
public final class MinerVisualIdentity {
    private static final Component BADGE = Component.literal("[광부]").withStyle(ChatFormatting.GOLD);

    private MinerVisualIdentity() {}

    public static void ensure(Villager villager) {
        if (!isMiner(villager)) return;

        // Do not destroy a player's own name-tag name. If no custom name is
        // present (the normal case), the profession itself is shown visibly.
        if (villager.getCustomName() == null || isOurBadge(villager.getCustomName())) {
            villager.setCustomName(BADGE.copy());
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

    private static boolean isMiner(Villager villager) {
        return villager.getVillagerData().profession().is(ModVillagerProfessions.MINER_KEY);
    }

    private static boolean isOurBadge(Component component) {
        String value = component.getString();
        return "[광부]".equals(value) || "[Miner]".equals(value);
    }
}
