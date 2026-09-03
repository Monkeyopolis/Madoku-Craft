package madoku.craft.core.loot;

/** Orchestrates the managed loot-table subsystem through its public API contract. */
public final class MadokuLootTableManager {
	private MadokuLootTableManager() {
	}

	public static void initialize() { LootTableAPIManager.initialize(); }
	public static void reset() { LootTableAPIManager.reset(); }
}
