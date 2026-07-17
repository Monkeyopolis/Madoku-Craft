package madoku.craft.item.system;

final class MadokuArmorProfile {
	private final Integer durability;
	private final Double armor;
	private final Double armorToughness;

	MadokuArmorProfile(Integer durability, Double armor, Double armorToughness) {
		this.durability = durability;
		this.armor = armor;
		this.armorToughness = armorToughness;
	}

	Integer durability() {
		return durability;
	}

	Double armor() {
		return armor;
	}

	Double armorToughness() {
		return armorToughness;
	}

	boolean hasDurability() {
		return durability != null && durability > 0;
	}

	boolean hasArmor() {
		return armor != null;
	}

	boolean hasArmorToughness() {
		return armorToughness != null;
	}
}

