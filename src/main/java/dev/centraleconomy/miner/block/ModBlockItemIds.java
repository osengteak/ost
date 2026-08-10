package dev.centraleconomy.miner.block;

import dev.centraleconomy.miner.CentralEconomyMod;
import net.minecraft.references.BlockItemId;
import net.minecraft.resources.Identifier;

public final class ModBlockItemIds {
    public static final BlockItemId FARMER_WORKSTATION = create("farmer_workstation");
    public static final BlockItemId RANCHER_WORKSTATION = create("rancher_workstation");
    public static final BlockItemId FISHER_WORKSTATION = create("fisher_workstation");
    public static final BlockItemId MINER_WORKSTATION = create("miner_workstation");
    public static final BlockItemId LUMBERJACK_WORKSTATION = create("lumberjack_workstation");
    public static final BlockItemId MASON_WORKSTATION = create("mason_workstation");
    public static final BlockItemId FLETCHER_WORKSTATION = create("fletcher_workstation");
    public static final BlockItemId LIBRARIAN_WORKSTATION = create("librarian_workstation");
    public static final BlockItemId CLERIC_WORKSTATION = create("cleric_workstation");
    public static final BlockItemId CARTOGRAPHER_WORKSTATION = create("cartographer_workstation");

    private ModBlockItemIds() {}

    private static BlockItemId create(String name) {
        Identifier id = Identifier.fromNamespaceAndPath(CentralEconomyMod.MOD_ID, name);
        return BlockItemId.create(id, id);
    }
}
