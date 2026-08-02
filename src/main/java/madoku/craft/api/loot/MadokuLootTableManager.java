package madoku.craft.api.loot;

import madoku.craft.api.MadokuAPIManager;
import net.minecraft.resources.ResourceKey;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.Container;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.storage.loot.LootContext;
import net.minecraft.world.level.storage.loot.LootTable;

import java.util.List;

public final class MadokuLootTableManager {
	static final String CONFIG_ROOT_FOLDER_NAME = MadokuAPIManager.API_FOLDER_NAME + "/madoku-loot-tables";

	private MadokuLootTableManager() {
	}

	public static void initialize() {
		LootTableConfigManager.initialize();
		LootTableStructuresManager.initialize();
		LootTableEntitiesManager.initialize();
		LootTableEquipmentsManager.initialize();
	}

	public static boolean applyManagedLootTable(
		Container container,
		ResourceKey<LootTable> lootTableKey,
		long lootTableSeed,
		ServerLevel level,
		ServerPlayer player
	) {
		return LootTableStructuresManager.applyManagedLootTable(container, lootTableKey, lootTableSeed, level, player);
	}

	public static List<ItemStack> generateManagedLootForContext(LootContext lootContext) {
		List<ItemStack> generated = LootTableStructuresManager.generateManagedLootForContext(lootContext);
		if (generated != null) {
			return generated;
		}
		return LootTableEntitiesManager.generateManagedLootForContext(lootContext);
	}

	public static void reset() {
		LootTableEquipmentsManager.reset();
		LootTableStructuresManager.reset();
		LootTableEntitiesManager.reset();
		LootTableConfigManager.reset();
	}

	static long resolveReloadIntervalMillis(net.minecraft.server.MinecraftServer server) {
		long ticks = madoku.craft.api.scheduler.SchedulerAdaptiveIntervalManager.resolve("loot-table-config", server, 30L, 600L);
		return ticks * 50L;
	}
}

