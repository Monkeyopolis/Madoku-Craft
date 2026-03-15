package madoku.craft.item.system;

final class MadokuToolProfile {
	private final Integer durability;
	private final Double attackDamage;
	private final Double attackSpeed;
	private final Double miningSpeed;
	private final Integer materialLevel;

	MadokuToolProfile(
		Integer durability,
		Double attackDamage,
		Double attackSpeed,
		Double miningSpeed,
		Integer materialLevel
	) {
		this.durability = durability;
		this.attackDamage = attackDamage;
		this.attackSpeed = attackSpeed;
		this.miningSpeed = miningSpeed;
		this.materialLevel = materialLevel;
	}

	Integer durability() {
		return durability;
	}

	Double attackDamage() {
		return attackDamage;
	}

	Double attackSpeed() {
		return attackSpeed;
	}

	Double miningSpeed() {
		return miningSpeed;
	}

	Integer materialLevel() {
		return materialLevel;
	}

	boolean hasDurability() {
		return durability != null && durability > 0;
	}

	boolean hasAttackDamage() {
		return attackDamage != null;
	}

	boolean hasAttackSpeed() {
		return attackSpeed != null;
	}

	boolean hasMiningSpeed() {
		return miningSpeed != null;
	}

	boolean hasMaterialLevel() {
		return materialLevel != null && materialLevel >= 0;
	}
}
