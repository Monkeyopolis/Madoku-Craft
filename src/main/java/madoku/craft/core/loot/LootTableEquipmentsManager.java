package madoku.craft.core.loot;

public final class LootTableEquipmentsManager {

	private LootTableEquipmentsManager() {
	}

	public static void initialize() {
		EquipmentsConfigManager.reloadConfig();
	}

	public static void reset() {
		EquipmentsConfigManager.reset();
	}
}

