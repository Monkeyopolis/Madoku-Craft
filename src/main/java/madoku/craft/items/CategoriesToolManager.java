package madoku.craft.items;

public final class CategoriesToolManager {
	private final Integer durability;
	private final Double attackDamage;
	private final Double attackSpeed;
	private final Double miningSpeed;
	private final Integer materialLevel;
	private final ReachProfile reach;

	public CategoriesToolManager(
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

	public Integer durability() {
		return durability;
	}

	public Double attackDamage() {
		return attackDamage;
	}

	public Double attackSpeed() {
		return attackSpeed;
	}

	public Double miningSpeed() {
		return miningSpeed;
	}

	public Integer materialLevel() {
		return materialLevel;
	}

	public ReachProfile reach() {
		return reach;
	}

	public boolean hasDurability() {
		return durability != null && durability > 0;
	}

	public boolean hasAttackDamage() {
		return attackDamage != null;
	}

	public boolean hasAttackSpeed() {
		return attackSpeed != null;
	}

	public boolean hasMiningSpeed() {
		return miningSpeed != null;
	}

	public boolean hasMaterialLevel() {
		return materialLevel != null && materialLevel >= 0;
	}

	public boolean hasReach() {
		return reach != null && reach.hasValues();
	}

	public static final class ReachProfile {
		private final Double minRange;
		private final Double maxRange;
		private final Double minCreativeRange;
		private final Double maxCreativeRange;
		private final Double hitboxMargin;
		private final Double mobFactor;

		public ReachProfile(
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

		public Double minRange() {
			return minRange;
		}

		public Double maxRange() {
			return maxRange;
		}

		public Double minCreativeRange() {
			return minCreativeRange;
		}

		public Double maxCreativeRange() {
			return maxCreativeRange;
		}

		public Double hitboxMargin() {
			return hitboxMargin;
		}

		public Double mobFactor() {
			return mobFactor;
		}

		public boolean hasValues() {
			return minRange != null
				|| maxRange != null
				|| minCreativeRange != null
				|| maxCreativeRange != null
				|| hitboxMargin != null
				|| mobFactor != null;
		}
	}
}

