package madoku.craft.java.items;

import java.util.List;
import java.util.Set;

/** Shared category names, enablement fields, and category-priority helpers. */
public final class CategoriesConfigManager {
	private static final List<String> CATEGORY_FIELDS = List.of(
		ItemsConfigManager.FIELD_ARMOR_CATEGORY,
		ItemsConfigManager.FIELD_TOOL_CATEGORY,
		ItemsConfigManager.FIELD_WEAPON_CATEGORY,
		ItemsConfigManager.FIELD_FUEL_CATEGORY,
		ItemsConfigManager.FIELD_OTHER_CATEGORY
	);
	private static final List<String> PRIORITY = List.of(
		ItemsConfigManager.CATEGORY_ARMOR,
		ItemsConfigManager.CATEGORY_TOOL,
		ItemsConfigManager.CATEGORY_WEAPON,
		ItemsConfigManager.CATEGORY_FUEL,
		ItemsConfigManager.CATEGORY_OTHER
	);

	private CategoriesConfigManager() { }

	public static List<String> categoryFields() { return CATEGORY_FIELDS; }

	public static List<String> priority() { return PRIORITY; }

	public static String normalize(String category) {
		if (category == null) return "";
		return category.trim().toLowerCase(java.util.Locale.ROOT);
	}

	public static String dominantCategory(Set<String> categories) {
		if (categories == null || categories.isEmpty()) return null;
		for (String category : PRIORITY) if (categories.contains(category)) return category;
		return null;
	}

	public static boolean isRarityCategory(String category) {
		return ItemsConfigManager.CATEGORY_ARMOR.equals(category)
			|| ItemsConfigManager.CATEGORY_TOOL.equals(category)
			|| ItemsConfigManager.CATEGORY_WEAPON.equals(category);
	}
}
