package dev.centraleconomy.miner.block;

import java.util.LinkedHashMap;
import java.util.Map;
import java.util.function.Function;

import net.fabricmc.fabric.api.creativetab.v1.CreativeModeTabEvents;
import net.minecraft.core.Registry;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.references.BlockItemId;
import net.minecraft.resources.ResourceKey;
import net.minecraft.world.item.BlockItem;
import net.minecraft.world.item.CreativeModeTabs;
import net.minecraft.world.item.Item;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.state.BlockBehaviour;
import net.minecraft.world.level.block.state.BlockState;

/** Ten intentional profession workstations. Wandering Trader deliberately has no workstation. */
public final class ModBlocks {
    public static final Block FARMER_WORKSTATION = register(ModBlockItemIds.FARMER_WORKSTATION, Block::new, BlockBehaviour.Properties.ofFullCopy(Blocks.CRAFTING_TABLE));
    public static final Block RANCHER_WORKSTATION = register(ModBlockItemIds.RANCHER_WORKSTATION, Block::new, BlockBehaviour.Properties.ofFullCopy(Blocks.CRAFTING_TABLE));
    public static final Block FISHER_WORKSTATION = register(ModBlockItemIds.FISHER_WORKSTATION, Block::new, BlockBehaviour.Properties.ofFullCopy(Blocks.BARREL));
    public static final Block MINER_WORKSTATION = register(ModBlockItemIds.MINER_WORKSTATION, Block::new, BlockBehaviour.Properties.ofFullCopy(Blocks.SMITHING_TABLE));
    public static final Block LUMBERJACK_WORKSTATION = register(ModBlockItemIds.LUMBERJACK_WORKSTATION, Block::new, BlockBehaviour.Properties.ofFullCopy(Blocks.CRAFTING_TABLE));
    public static final Block MASON_WORKSTATION = register(ModBlockItemIds.MASON_WORKSTATION, Block::new, BlockBehaviour.Properties.ofFullCopy(Blocks.STONECUTTER));
    public static final Block FLETCHER_WORKSTATION = register(ModBlockItemIds.FLETCHER_WORKSTATION, Block::new, BlockBehaviour.Properties.ofFullCopy(Blocks.FLETCHING_TABLE));
    public static final Block LIBRARIAN_WORKSTATION = register(ModBlockItemIds.LIBRARIAN_WORKSTATION, Block::new, BlockBehaviour.Properties.ofFullCopy(Blocks.LECTERN));
    public static final Block CLERIC_WORKSTATION = register(ModBlockItemIds.CLERIC_WORKSTATION, Block::new, BlockBehaviour.Properties.ofFullCopy(Blocks.BREWING_STAND));
    public static final Block CARTOGRAPHER_WORKSTATION = register(ModBlockItemIds.CARTOGRAPHER_WORKSTATION, Block::new, BlockBehaviour.Properties.ofFullCopy(Blocks.CARTOGRAPHY_TABLE));

    private static final Map<String, Block> BY_MARKET = new LinkedHashMap<>();

    static {
        BY_MARKET.put("farmer", FARMER_WORKSTATION);
        BY_MARKET.put("rancher", RANCHER_WORKSTATION);
        BY_MARKET.put("fisher", FISHER_WORKSTATION);
        BY_MARKET.put("miner", MINER_WORKSTATION);
        BY_MARKET.put("lumberjack", LUMBERJACK_WORKSTATION);
        BY_MARKET.put("mason", MASON_WORKSTATION);
        BY_MARKET.put("fletcher", FLETCHER_WORKSTATION);
        BY_MARKET.put("librarian", LIBRARIAN_WORKSTATION);
        BY_MARKET.put("cleric", CLERIC_WORKSTATION);
        BY_MARKET.put("cartographer", CARTOGRAPHER_WORKSTATION);
    }

    private ModBlocks() {}

    private static Block register(ResourceKey<Block> id, Function<BlockBehaviour.Properties, Block> factory, BlockBehaviour.Properties properties) {
        Block block = factory.apply(properties.setId(id));
        return Registry.register(BuiltInRegistries.BLOCK, id, block);
    }

    private static Block register(BlockItemId id, Function<BlockBehaviour.Properties, Block> factory, BlockBehaviour.Properties properties) {
        Block block = register(id.block(), factory, properties);
        BlockItem item = new BlockItem(block, new Item.Properties().useBlockDescriptionPrefix().setId(id.item()));
        Registry.register(BuiltInRegistries.ITEM, id.item(), item);
        return block;
    }

    public static void initialize() {
        CreativeModeTabEvents.modifyOutputEvent(CreativeModeTabs.BUILDING_BLOCKS).register(tab -> BY_MARKET.values().forEach(b -> tab.accept(b.asItem())));
    }

    public static Block blockForMarket(String marketId) { return BY_MARKET.get(marketId); }

    public static String marketForBlock(BlockState state) {
        if (state == null) return null;
        for (Map.Entry<String, Block> entry : BY_MARKET.entrySet()) {
            if (state.is(entry.getValue())) return entry.getKey();
        }
        return null;
    }

    public static Map<String, Block> allWorkstations() { return Map.copyOf(BY_MARKET); }
}
