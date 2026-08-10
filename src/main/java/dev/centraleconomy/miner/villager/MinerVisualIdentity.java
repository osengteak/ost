package dev.centraleconomy.miner.villager;

import net.minecraft.ChatFormatting;
import net.minecraft.network.chat.Component;
import net.minecraft.world.entity.npc.villager.Villager;

/** Visible identity for a villager employed by the Central Economy miner contract. */
public final class MinerVisualIdentity {
    private static final Component BADGE = Component.literal("[광부]").withStyle(ChatFormatting.GOLD);

    private MinerVisualIdentity() {}

    /**
     * Employment state, not the vanilla profession holder, is authoritative.
     * This is intentional: vanilla villager brain scheduling may rewrite a
     * custom profession if its own JOB_SITE memory is not the source of the job.
     */
    public static void ensureBadge(Villager villager) {
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

    private static boolean isOurBadge(Component component) {
        String value = component.getString();
        return "[광부]".equals(value) || "[Miner]".equals(value);
    }
}
