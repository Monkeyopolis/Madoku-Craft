package madoku.craft.java.compat;

import net.fabricmc.loader.api.FabricLoader;

/** Centralizes optional module detection for the Compat runtime. */
public final class MadokuCompatModuleState {
	public static final String UNIFIED_BUNDLE_ID = "madoku-craft";
	public static final String CORE_ID = "madoku-craft-core";
	public static final String ATTRIBUTES_ID = "madoku-craft-attributes";
	public static final String FARMING_ID = "madoku-craft-farming";
	public static final String HUD_ID = "madoku-craft-hud";
	public static final String ITEMS_ID = "madoku-craft-items";
	public static final String LEVELS_ID = "madoku-craft-levels";
	public static final String MOBS_ID = "madoku-craft-mobs";
	public static final String PETS_ID = "madoku-craft-pets";
	public static final String UTILITY_ID = "madoku-craft-utility";

	private MadokuCompatModuleState() { }

	public static boolean isUnifiedBundle() {
		return FabricLoader.getInstance().isModLoaded(UNIFIED_BUNDLE_ID);
	}

	public static boolean isLoaded(String modId) {
		return isUnifiedBundle() || (modId != null && FabricLoader.getInstance().isModLoaded(modId));
	}

	public static boolean hasAll(String... modIds) {
		if (modIds == null) return false;
		for (String modId : modIds) {
			if (!isLoaded(modId)) return false;
		}
		return true;
	}

	public static boolean hasAny(String... modIds) {
		if (modIds == null) return false;
		for (String modId : modIds) {
			if (isLoaded(modId)) return true;
		}
		return false;
	}
}
