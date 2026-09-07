package madoku.craft.java.compat.farming;

import java.util.List;

import madoku.craft.java.core.loot.LootTableCropsAPIManager;
import madoku.craft.java.farming.FarmingAPIManager;
import madoku.craft.java.farming.FarmingLootAdapter;
import net.minecraft.util.RandomSource;
import net.minecraft.world.item.ItemStack;

/** Connects the Farming module to Core's crop loot-table runtime. */
public final class MadokuFarmingFeatureAdapters {
	private MadokuFarmingFeatureAdapters() { }

	public static void initialize() {
		FarmingAPIManager.registerLootAdapter(new FarmingLootAdapter() {
			@Override
			public List<ItemStack> generateManagedLootForTable(String tableId, RandomSource random) {
				return LootTableCropsAPIManager.generateManagedLootForTable(tableId, random);
			}

			@Override
			public boolean hasManagedLootTable(String tableId) {
				return LootTableCropsAPIManager.hasManagedLootTable(tableId);
			}
		});
	}
}
