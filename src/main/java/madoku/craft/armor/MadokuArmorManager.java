package madoku.craft.armor;

import madoku.craft.attributes.MadokuAttributesManager;
import net.minecraft.tags.DamageTypeTags;
import net.minecraft.world.damagesource.DamageSource;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.ai.attributes.Attributes;

public final class MadokuArmorManager {
	private static final double DAMAGE_ROUND_INCREMENT = 0.05d;
	private static final double POINT_STEP = 0.25d;

	private static volatile ArmorConfigManager.Settings settings = ArmorConfigManager.Settings.defaults();

	private MadokuArmorManager() {
	}

	public static void initialize() {
		loadStaticConfig();
	}

	public static boolean isEnabled() {
		return settings.enabled;
	}

	public static float applyCustomArmorDamage(LivingEntity entity, DamageSource source, float amount) {
		if (entity == null || source == null || amount <= 0.0f || !settings.enabled) {
			return amount;
		}

		double armorPoints = clampToRange(
			entity.getAttributeValue(Attributes.ARMOR),
			settings.armorPoints.startingArmor,
			settings.armorPoints.maxPoints
		);
		double armorToughnessPoints = clampToRange(
			entity.getAttributeValue(Attributes.ARMOR_TOUGHNESS),
			settings.armorToughnessPoints.startingArmorToughness,
			settings.armorToughnessPoints.maxPoints
		);

		double damageAfterArmor = applyDamageReduction(amount, armorPoints, settings.armorPoints.damageReduction);
		double damageAfterToughness = applyDamageReduction(
			damageAfterArmor,
			armorToughnessPoints,
			settings.armorToughnessPoints.damageReduction
		);

		double finalDamage = damageAfterToughness;
		if (source.is(DamageTypeTags.IS_FALL)) {
			double mitigatedDamage = Math.max(0.0d, amount - finalDamage);
			finalDamage = amount - (mitigatedDamage * settings.main.fallDamageReduction);
		}

		return (float) Math.max(0.0d, roundToDamageIncrement(finalDamage));
	}

	private static void loadStaticConfig() {
		settings = ArmorConfigManager.loadSettings(MadokuAttributesManager.isEnabled());
	}

	private static double applyDamageReduction(double amount, double points, ArmorConfigManager.DamageReduction reduction) {
		if (amount <= 0.0d || reduction == null) {
			return amount;
		}

		long pointSteps = getStepCount(points, POINT_STEP);
		if (pointSteps <= 0L) {
			return amount;
		}

		return switch (reduction.type) {
			case FLAT -> Math.max(0.0d, amount - (pointSteps * reduction.value));
			case PERCENTAGE -> {
				double multiplier = Math.max(0.0d, 1.0d - (pointSteps * reduction.value));
				yield amount * multiplier;
			}
		};
	}

	private static double clampToRange(double value, double min, double max) {
		double lowerBound = Math.min(min, max);
		double upperBound = Math.max(min, max);
		if (value < lowerBound) {
			return lowerBound;
		}
		return Math.min(value, upperBound);
	}

	private static long getStepCount(double value, double stepSize) {
		if (value <= 0.0d || stepSize <= 0.0d) {
			return 0L;
		}
		return Math.max(0L, (long) Math.floor(value / stepSize));
	}

	private static double roundToDamageIncrement(double value) {
		return Math.round(value / DAMAGE_ROUND_INCREMENT) * DAMAGE_ROUND_INCREMENT;
	}
}
