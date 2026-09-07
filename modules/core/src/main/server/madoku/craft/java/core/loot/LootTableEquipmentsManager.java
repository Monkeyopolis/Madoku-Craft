package madoku.craft.java.core.loot;

public final class LootTableEquipmentsManager {

	private LootTableEquipmentsManager() {
	}

	public static void initialize() {
		EquipmentsConfigAPIManager.reloadConfig();
	}

	public static void reset() {
		EquipmentsConfigAPIManager.reset();
	}
}


