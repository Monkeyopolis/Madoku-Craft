package madoku.craft.armor;

import madoku.craft.attributes.MadokuAttributesManager;
import net.minecraft.tags.DamageTypeTags;
import net.minecraft.world.damagesource.DamageSource;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.ai.attributes.Attributes;

public final class MadokuArmorManager {
	private static final double DAMAGE_ROUND_INCREMENT = 0.05d;
	private static final double ARMOR_POINT_STEP = 0.25d;
	private static final double ARMOR_POINT_DAMAGE_REDUCTION = 0.05d;
	private static final double TOUGHNESS_POINT_STEP = 0.25d;
	private static final double TOUGHNESS_PERCENT_REDUCTION = 0.01d;

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

		double armorPoints = clampToLimit(entity.getAttributeValue(Attributes.ARMOR), settings.armorPointsClampLimit);
		double armorToughnessPoints = clampToLimit(
			entity.getAttributeValue(Attributes.ARMOR_TOUGHNESS),
			settings.armorToughnessPointsClampLimit
		);

		long armorPointSteps = getStepCount(armorPoints, ARMOR_POINT_STEP);
		long toughnessPointSteps = getStepCount(armorToughnessPoints, TOUGHNESS_POINT_STEP);

		double reducedDamage = Math.max(0.0d, amount - (armorPointSteps * ARMOR_POINT_DAMAGE_REDUCTION));
		double toughnessMultiplier = 1.0d - (toughnessPointSteps * TOUGHNESS_PERCENT_REDUCTION);
		toughnessMultiplier = Math.max(0.0d, toughnessMultiplier);

		double finalDamage = reducedDamage * toughnessMultiplier;
		if (source.is(DamageTypeTags.IS_FALL)) {
			double mitigatedDamage = Math.max(0.0d, amount - finalDamage);
			finalDamage = amount - (mitigatedDamage * settings.fallDamageArmorEffectiveness);
		}

		return (float) Math.max(0.0d, roundToDamageIncrement(finalDamage));
	}

	private static void loadStaticConfig() {
		settings = ArmorConfigManager.loadSettings(MadokuAttributesManager.isEnabled());
	}

	private static double clampToLimit(double value, int limit) {
		if (value < 0) {
			return 0.0d;
		}
		return Math.min(value, Math.max(0, limit));
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
