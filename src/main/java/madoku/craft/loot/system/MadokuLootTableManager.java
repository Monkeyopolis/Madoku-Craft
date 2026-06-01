package madoku.craft.loot.system;

import net.minecraft.resources.ResourceKey;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.Container;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.storage.loot.LootContext;
import net.minecraft.world.level.storage.loot.LootTable;

import java.util.List;

public final class MadokuLootTableManager {
	private MadokuLootTableManager() {
	}

	public static void initialize() {
		MadokuLootTableStructures.initialize();
		MadokuLootTableEntities.initialize();
		MadokuLootTableEquipments.initialize();
	}

	public static boolean applyManagedLootTable(
		Container container,
		ResourceKey<LootTable> lootTableKey,
		long lootTableSeed,
		ServerLevel level,
		ServerPlayer player
	) {
		return MadokuLootTableStructures.applyManagedLootTable(container, lootTableKey, lootTableSeed, level, player);
	}

	public static List<ItemStack> generateManagedLootForContext(LootContext lootContext) {
		List<ItemStack> generated = MadokuLootTableStructures.generateManagedLootForContext(lootContext);
		if (generated != null) {
			return generated;
		}
		return MadokuLootTableEntities.generateManagedLootForContext(lootContext);
	}
}
