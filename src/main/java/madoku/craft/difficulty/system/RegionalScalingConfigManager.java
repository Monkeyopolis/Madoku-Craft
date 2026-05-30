package madoku.craft.difficulty.system;

import com.google.gson.JsonObject;

import java.util.LinkedHashMap;
import java.util.Map;

public final class RegionalScalingConfigManager {
	private RegionalScalingConfigManager() {
	}

	public static Map<String, JsonObject> buildDefaultMobScalingFileDefaults(
		double health,
		double movementSpeed,
		double armor,
		double damage,
		double knockbackResistance,
		double experienceDrop
	) {
		Map<String, JsonObject> defaults = new LinkedHashMap<>();
		defaults.put("bee", RegionalScalingConfigBee.buildDefaults(health, movementSpeed, armor, damage, knockbackResistance, experienceDrop));
		defaults.putAll(RegionalDifficultyConfigManager.buildDefaultMobScalingFileDefaults(
			health,
			movementSpeed,
			armor,
			damage,
			knockbackResistance,
			experienceDrop
		));
		return defaults;
	}

	public static JsonObject buildDynamicMobScalingDefaults(String fileKey) {
		String normalized = fileKey == null ? "" : fileKey.trim().toLowerCase();
		if ("bee".equals(normalized) || "minecraft:bee".equals(normalized)) {
			return RegionalScalingConfigBee.buildDefaults(
				RegionalDifficultyConfigManager.DEFAULT_HEALTH_INCREMENT,
				RegionalDifficultyConfigManager.DEFAULT_MOVEMENT_SPEED_INCREMENT,
				RegionalDifficultyConfigManager.DEFAULT_ARMOR_INCREMENT,
				RegionalDifficultyConfigManager.DEFAULT_DAMAGE_INCREMENT,
				RegionalDifficultyConfigManager.DEFAULT_KNOCKBACK_RESISTANCE_INCREMENT,
				RegionalDifficultyConfigManager.DEFAULT_EXPERIENCE_DROP_INCREMENT
			);
		}
		return RegionalDifficultyConfigManager.buildDynamicMobScalingDefaults(fileKey);
	}

	public static JsonObject buildMobScalingDefaults(
		String mobId,
		double health,
		double movementSpeed,
		double armor,
		double damage,
		double knockbackResistance,
		double experienceDrop,
		Double scale,
		Double explosionPower
	) {
		return RegionalDifficultyConfigManager.buildMobScalingDefaults(
			mobId,
			health,
			movementSpeed,
			armor,
			damage,
			knockbackResistance,
			experienceDrop,
			scale,
			explosionPower
		);
	}
}
