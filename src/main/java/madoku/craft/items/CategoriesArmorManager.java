package madoku.craft.items;

public final class CategoriesArmorManager {
	private final Integer durability;
	private final Double armor;
	private final Double armorToughness;

	public CategoriesArmorManager(Integer durability, Double armor, Double armorToughness) {
		this.durability = durability;
		this.armor = armor;
		this.armorToughness = armorToughness;
	}

	public Integer durability() {
		return durability;
	}

	public Double armor() {
		return armor;
	}

	public Double armorToughness() {
		return armorToughness;
	}

	public boolean hasDurability() {
		return durability != null && durability > 0;
	}

	public boolean hasArmor() {
		return armor != null;
	}

	public boolean hasArmorToughness() {
		return armorToughness != null;
	}
}

