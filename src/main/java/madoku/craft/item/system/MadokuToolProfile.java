package madoku.craft.item.system;

final class MadokuToolProfile {
	private final Integer durability;
	private final Double attackDamage;
	private final Double attackSpeed;
	private final Double miningSpeed;
	private final Integer materialLevel;
	private final ReachProfile reach;

	MadokuToolProfile(
		Integer durability,
		Double attackDamage,
		Double attackSpeed,
		Double miningSpeed,
		Integer materialLevel,
		ReachProfile reach
	) {
		this.durability = durability;
		this.attackDamage = attackDamage;
		this.attackSpeed = attackSpeed;
		this.miningSpeed = miningSpeed;
		this.materialLevel = materialLevel;
		this.reach = reach;
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

	ReachProfile reach() {
		return reach;
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

	boolean hasReach() {
		return reach != null && reach.hasValues();
	}

	static final class ReachProfile {
		private final Double minRange;
		private final Double maxRange;
		private final Double minCreativeRange;
		private final Double maxCreativeRange;
		private final Double hitboxMargin;
		private final Double mobFactor;

		ReachProfile(
			Double minRange,
			Double maxRange,
			Double minCreativeRange,
			Double maxCreativeRange,
			Double hitboxMargin,
			Double mobFactor
		) {
			this.minRange = minRange;
			this.maxRange = maxRange;
			this.minCreativeRange = minCreativeRange;
			this.maxCreativeRange = maxCreativeRange;
			this.hitboxMargin = hitboxMargin;
			this.mobFactor = mobFactor;
		}

		Double minRange() {
			return minRange;
		}

		Double maxRange() {
			return maxRange;
		}

		Double minCreativeRange() {
			return minCreativeRange;
		}

		Double maxCreativeRange() {
			return maxCreativeRange;
		}

		Double hitboxMargin() {
			return hitboxMargin;
		}

		Double mobFactor() {
			return mobFactor;
		}

		boolean hasValues() {
			return minRange != null
				|| maxRange != null
				|| minCreativeRange != null
				|| maxCreativeRange != null
				|| hitboxMargin != null
				|| mobFactor != null;
		}
	}
}
