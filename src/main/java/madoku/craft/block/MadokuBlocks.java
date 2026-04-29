package madoku.craft.block;

import madoku.craft.MadokuCraft;
import net.fabricmc.fabric.api.creativetab.v1.CreativeModeTabEvents;
import net.minecraft.core.Registry;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.core.registries.Registries;
import net.minecraft.resources.Identifier;
import net.minecraft.resources.ResourceKey;
import net.minecraft.world.item.BlockItem;
import net.minecraft.world.item.CreativeModeTabs;
import net.minecraft.world.item.Item;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.state.BlockBehaviour;

public final class MadokuBlocks {
	public static final Identifier NETHER_IRON_ORE_ID = Identifier.fromNamespaceAndPath(MadokuCraft.MOD_ID, "nether_iron_ore");

	public static final Block NETHER_IRON_ORE = Registry.register(
		BuiltInRegistries.BLOCK,
		NETHER_IRON_ORE_ID,
		new Block(
			BlockBehaviour.Properties.ofFullCopy(Blocks.NETHER_GOLD_ORE)
				.setId(ResourceKey.create(Registries.BLOCK, NETHER_IRON_ORE_ID))
		)
	);

	public static final Item NETHER_IRON_ORE_ITEM = Registry.register(
		BuiltInRegistries.ITEM,
		NETHER_IRON_ORE_ID,
		new BlockItem(
			NETHER_IRON_ORE,
			new Item.Properties()
				.setId(ResourceKey.create(Registries.ITEM, NETHER_IRON_ORE_ID))
		)
	);

	private MadokuBlocks() {
	}

	public static void initialize() {
		// static registration side effects
		CreativeModeTabEvents.modifyOutputEvent(CreativeModeTabs.NATURAL_BLOCKS).register(output ->
			output.insertAfter(Blocks.NETHER_GOLD_ORE, NETHER_IRON_ORE)
		);
	}
}
