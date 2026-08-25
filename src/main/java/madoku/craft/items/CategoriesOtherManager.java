package madoku.craft.items;

/** Shared helpers for the catch-all "other" category. */
public final class CategoriesOtherManager {
	private CategoriesOtherManager() { }

	public static boolean isOther(String category) {
		return ItemsConfigManager.CATEGORY_OTHER.equals(CategoriesConfigManager.normalize(category));
	}
}
