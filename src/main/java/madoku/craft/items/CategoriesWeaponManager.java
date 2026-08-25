package madoku.craft.items;

/** Shared helpers for weapon-category behavior. Weapon profiles reuse tool combat metadata. */
public final class CategoriesWeaponManager {
	private CategoriesWeaponManager() { }

	public static boolean isWeapon(String category) {
		return ItemsConfigManager.CATEGORY_WEAPON.equals(CategoriesConfigManager.normalize(category));
	}
}
