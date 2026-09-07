package madoku.craft.java.core.loot;

import net.minecraft.resources.ResourceKey;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.Container;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.storage.loot.LootContext;
import net.minecraft.world.level.storage.loot.LootTable;

import java.util.List;

/** Orchestrates the managed loot-table subsystem through its public API contract. */
public final class MadokuLootTableManager {
	private MadokuLootTableManager() {
	}

	public static void initialize() {
		LootTableConfigManager.initialize();
		LootTableStructuresManager.initialize();
		LootTableEntitiesAPIManager.initialize();
		LootTableEquipmentsManager.initialize();
		LootTableCropsAPIManager.initialize();
	}
	public static void reset() {
		LootTableEquipmentsManager.reset();
		LootTableStructuresManager.reset();
		LootTableEntitiesAPIManager.reset();
		LootTableConfigManager.reset();
		LootTableCropsAPIManager.reset();
	}
	public static boolean applyManagedLootTable(Container container, ResourceKey<LootTable> lootTableKey, long lootTableSeed, ServerLevel level, ServerPlayer player) {
		return LootTableStructuresManager.applyManagedLootTable(container, lootTableKey, lootTableSeed, level, player);
	}
	public static List<ItemStack> generateManagedLootForContext(LootContext lootContext) {
		List<ItemStack> generated = LootTableStructuresManager.generateManagedLootForContext(lootContext);
		if (generated != null) return generated;
		generated = LootTableEntitiesAPIManager.generateManagedLootForContext(lootContext);
		return generated != null ? generated : LootTableCropsAPIManager.generateManagedLootForContext(lootContext);
	}
}
