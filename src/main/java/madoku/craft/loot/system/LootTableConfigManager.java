package madoku.craft.loot.system;

import com.google.gson.JsonArray;
import com.google.gson.JsonObject;

public final class LootTableConfigManager {
	public static final String FIELD_ENABLED = "enabled";
	public static final String FIELD_TABLE_ID = "table-id";
	public static final String FIELD_ROLLS = "rolls";
	public static final String FIELD_MIN = "min";
	public static final String FIELD_MAX = "max";
	public static final String FIELD_GROUPS = "groups";
	public static final String FIELD_TAGS = "tags";
	public static final String FIELD_RARITY = "rarity";
	public static final String FIELD_WEIGHT = "weight";
	public static final String FIELD_ENTRIES = "entries";
	public static final String FIELD_ITEM = "item";
	public static final String FIELD_BLOCK = "block";
	public static final String FIELD_ITEM_RARITY = "item-rarity";
	public static final String FIELD_MIN_COUNT = "min-count";
	public static final String FIELD_MAX_COUNT = "max-count";
	public static final String FIELD_USE_MADOKU_LUCK = "use-madoku-luck";
	public static final String FIELD_ROLL_LUCK_MULTIPLIER = "roll-luck-multiplier";
	public static final String FIELD_RARITY_LUCK_MULTIPLIERS = "rarity-luck-multipliers";
	public static final String FIELD_LUCK_POINTS = "luck-points";
	public static final String FIELD_MULTIPLIERS = "multipliers";

	private LootTableConfigManager() {
	}

	public static JsonObject buildSettingsDefaults() {
		JsonObject root = new JsonObject();
		root.addProperty(FIELD_ENABLED, true);
		root.addProperty(FIELD_USE_MADOKU_LUCK, true);
		root.add(FIELD_ROLL_LUCK_MULTIPLIER, buildMultiplierCurve(new double[] { 1.0d, 1.25d, 1.5d, 1.75d, 2.0d }));
		root.add(FIELD_RARITY_LUCK_MULTIPLIERS, buildRarityLuckMultipliersDefaults());
		return root;
	}

	private static JsonObject buildRarityLuckMultipliersDefaults() {
		JsonObject root = new JsonObject();
		root.add(MadokuLootRarity.COMMON.id(), buildMultiplierCurve(new double[] { 1.0d, 0.875d, 0.75d, 0.625d, 0.5d }));
		root.add(MadokuLootRarity.RARE.id(), buildMultiplierCurve(new double[] { 1.0d, 0.9375d, 0.875d, 0.8125d, 0.75d }));
		root.add(MadokuLootRarity.EPIC.id(), buildMultiplierCurve(new double[] { 1.0d, 1.25d, 1.5d, 1.75d, 2.0d }));
		root.add(MadokuLootRarity.MYTHIC.id(), buildMultiplierCurve(new double[] { 1.0d, 1.5d, 2.0d, 3.0d, 4.0d }));
		return root;
	}

	private static JsonObject buildMultiplierCurve(double[] multipliers) {
		JsonObject root = new JsonObject();
		JsonArray points = new JsonArray();
		points.add(0.0d);
		points.add(25.0d);
		points.add(50.0d);
		points.add(75.0d);
		points.add(100.0d);

		JsonArray values = new JsonArray();
		for (double multiplier : multipliers) {
			values.add(multiplier);
		}

		root.add(FIELD_LUCK_POINTS, points);
		root.add(FIELD_MULTIPLIERS, values);
		return root;
	}
}
