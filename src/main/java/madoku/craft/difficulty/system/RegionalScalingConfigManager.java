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
		defaults.putAll(RegionalDifficultyConfigManager.buildDefaultMobScalingFileDefaults(
			health,
			movementSpeed,
			armor,
			damage,
			knockbackResistance,
			experienceDrop
		));
		defaults.put("bee", RegionalScalingConfigBee.buildDefaults(health, movementSpeed, armor, damage, knockbackResistance, experienceDrop));
		defaults.put("creeper", RegionalScalingConfigCreeper.buildDefaults(health, movementSpeed, armor, damage, knockbackResistance, experienceDrop));
		defaults.put("skeleton", RegionalScalingConfigSkeleton.buildDefaults(health, movementSpeed, armor, damage, knockbackResistance, experienceDrop));
		defaults.put("stray", RegionalScalingConfigStray.buildDefaults(health, movementSpeed, armor, damage, knockbackResistance, experienceDrop));
		defaults.put("bogged", RegionalScalingConfigBogged.buildDefaults(health, movementSpeed, armor, damage, knockbackResistance, experienceDrop));
		defaults.put("parched", RegionalScalingConfigParched.buildDefaults(health, movementSpeed, armor, damage, knockbackResistance, experienceDrop));
		defaults.put("spider", RegionalScalingConfigSpider.buildDefaults(health, movementSpeed, armor, damage, knockbackResistance, experienceDrop));
		defaults.put("cave-spider", RegionalScalingConfigCaveSpider.buildDefaults(health, movementSpeed, armor, damage, knockbackResistance, experienceDrop));
		defaults.put("zombie", RegionalScalingConfigZombie.buildDefaults(health, movementSpeed, armor, damage, knockbackResistance, experienceDrop));
		defaults.put("husk", RegionalScalingConfigHusk.buildDefaults(health, movementSpeed, armor, damage, knockbackResistance, experienceDrop));
		defaults.put("drowned", RegionalScalingConfigDrowned.buildDefaults(health, movementSpeed, armor, damage, knockbackResistance, experienceDrop));
		defaults.put("zombie-villager", RegionalScalingConfigZombieVillager.buildDefaults(health, movementSpeed, armor, damage, knockbackResistance, experienceDrop));
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
		if ("creeper".equals(normalized) || "minecraft:creeper".equals(normalized)) {
			return RegionalScalingConfigCreeper.buildDefaults(
				RegionalDifficultyConfigManager.DEFAULT_HEALTH_INCREMENT,
				RegionalDifficultyConfigManager.DEFAULT_MOVEMENT_SPEED_INCREMENT,
				RegionalDifficultyConfigManager.DEFAULT_ARMOR_INCREMENT,
				RegionalDifficultyConfigManager.DEFAULT_DAMAGE_INCREMENT,
				RegionalDifficultyConfigManager.DEFAULT_KNOCKBACK_RESISTANCE_INCREMENT,
				RegionalDifficultyConfigManager.DEFAULT_EXPERIENCE_DROP_INCREMENT
			);
		}
		if ("skeleton".equals(normalized) || "minecraft:skeleton".equals(normalized)) {
			return RegionalScalingConfigSkeleton.buildDefaults(
				RegionalDifficultyConfigManager.DEFAULT_HEALTH_INCREMENT,
				RegionalDifficultyConfigManager.DEFAULT_MOVEMENT_SPEED_INCREMENT,
				RegionalDifficultyConfigManager.DEFAULT_ARMOR_INCREMENT,
				RegionalDifficultyConfigManager.DEFAULT_DAMAGE_INCREMENT,
				RegionalDifficultyConfigManager.DEFAULT_KNOCKBACK_RESISTANCE_INCREMENT,
				RegionalDifficultyConfigManager.DEFAULT_EXPERIENCE_DROP_INCREMENT
			);
		}
		if ("stray".equals(normalized) || "minecraft:stray".equals(normalized)) {
			return RegionalScalingConfigStray.buildDefaults(
				RegionalDifficultyConfigManager.DEFAULT_HEALTH_INCREMENT,
				RegionalDifficultyConfigManager.DEFAULT_MOVEMENT_SPEED_INCREMENT,
				RegionalDifficultyConfigManager.DEFAULT_ARMOR_INCREMENT,
				RegionalDifficultyConfigManager.DEFAULT_DAMAGE_INCREMENT,
				RegionalDifficultyConfigManager.DEFAULT_KNOCKBACK_RESISTANCE_INCREMENT,
				RegionalDifficultyConfigManager.DEFAULT_EXPERIENCE_DROP_INCREMENT
			);
		}
		if ("bogged".equals(normalized) || "minecraft:bogged".equals(normalized)) {
			return RegionalScalingConfigBogged.buildDefaults(
				RegionalDifficultyConfigManager.DEFAULT_HEALTH_INCREMENT,
				RegionalDifficultyConfigManager.DEFAULT_MOVEMENT_SPEED_INCREMENT,
				RegionalDifficultyConfigManager.DEFAULT_ARMOR_INCREMENT,
				RegionalDifficultyConfigManager.DEFAULT_DAMAGE_INCREMENT,
				RegionalDifficultyConfigManager.DEFAULT_KNOCKBACK_RESISTANCE_INCREMENT,
				RegionalDifficultyConfigManager.DEFAULT_EXPERIENCE_DROP_INCREMENT
			);
		}
		if ("parched".equals(normalized) || "minecraft:parched".equals(normalized)) {
			return RegionalScalingConfigParched.buildDefaults(
				RegionalDifficultyConfigManager.DEFAULT_HEALTH_INCREMENT,
				RegionalDifficultyConfigManager.DEFAULT_MOVEMENT_SPEED_INCREMENT,
				RegionalDifficultyConfigManager.DEFAULT_ARMOR_INCREMENT,
				RegionalDifficultyConfigManager.DEFAULT_DAMAGE_INCREMENT,
				RegionalDifficultyConfigManager.DEFAULT_KNOCKBACK_RESISTANCE_INCREMENT,
				RegionalDifficultyConfigManager.DEFAULT_EXPERIENCE_DROP_INCREMENT
			);
		}
		if ("drowned".equals(normalized) || "minecraft:drowned".equals(normalized)) {
			return RegionalScalingConfigDrowned.buildDefaults(
				RegionalDifficultyConfigManager.DEFAULT_HEALTH_INCREMENT,
				RegionalDifficultyConfigManager.DEFAULT_MOVEMENT_SPEED_INCREMENT,
				RegionalDifficultyConfigManager.DEFAULT_ARMOR_INCREMENT,
				RegionalDifficultyConfigManager.DEFAULT_DAMAGE_INCREMENT,
				RegionalDifficultyConfigManager.DEFAULT_KNOCKBACK_RESISTANCE_INCREMENT,
				RegionalDifficultyConfigManager.DEFAULT_EXPERIENCE_DROP_INCREMENT
			);
		}
		if ("spider".equals(normalized) || "minecraft:spider".equals(normalized)) {
			return RegionalScalingConfigSpider.buildDefaults(
				RegionalDifficultyConfigManager.DEFAULT_HEALTH_INCREMENT,
				RegionalDifficultyConfigManager.DEFAULT_MOVEMENT_SPEED_INCREMENT,
				RegionalDifficultyConfigManager.DEFAULT_ARMOR_INCREMENT,
				RegionalDifficultyConfigManager.DEFAULT_DAMAGE_INCREMENT,
				RegionalDifficultyConfigManager.DEFAULT_KNOCKBACK_RESISTANCE_INCREMENT,
				RegionalDifficultyConfigManager.DEFAULT_EXPERIENCE_DROP_INCREMENT
			);
		}
		if ("cave-spider".equals(normalized) || "minecraft:cave_spider".equals(normalized)) {
			return RegionalScalingConfigCaveSpider.buildDefaults(
				RegionalDifficultyConfigManager.DEFAULT_HEALTH_INCREMENT,
				RegionalDifficultyConfigManager.DEFAULT_MOVEMENT_SPEED_INCREMENT,
				RegionalDifficultyConfigManager.DEFAULT_ARMOR_INCREMENT,
				RegionalDifficultyConfigManager.DEFAULT_DAMAGE_INCREMENT,
				RegionalDifficultyConfigManager.DEFAULT_KNOCKBACK_RESISTANCE_INCREMENT,
				RegionalDifficultyConfigManager.DEFAULT_EXPERIENCE_DROP_INCREMENT
			);
		}
		if ("zombie".equals(normalized) || "minecraft:zombie".equals(normalized)) {
			return RegionalScalingConfigZombie.buildDefaults(
				RegionalDifficultyConfigManager.DEFAULT_HEALTH_INCREMENT,
				RegionalDifficultyConfigManager.DEFAULT_MOVEMENT_SPEED_INCREMENT,
				RegionalDifficultyConfigManager.DEFAULT_ARMOR_INCREMENT,
				RegionalDifficultyConfigManager.DEFAULT_DAMAGE_INCREMENT,
				RegionalDifficultyConfigManager.DEFAULT_KNOCKBACK_RESISTANCE_INCREMENT,
				RegionalDifficultyConfigManager.DEFAULT_EXPERIENCE_DROP_INCREMENT
			);
		}
		if ("husk".equals(normalized) || "minecraft:husk".equals(normalized)) {
			return RegionalScalingConfigHusk.buildDefaults(
				RegionalDifficultyConfigManager.DEFAULT_HEALTH_INCREMENT,
				RegionalDifficultyConfigManager.DEFAULT_MOVEMENT_SPEED_INCREMENT,
				RegionalDifficultyConfigManager.DEFAULT_ARMOR_INCREMENT,
				RegionalDifficultyConfigManager.DEFAULT_DAMAGE_INCREMENT,
				RegionalDifficultyConfigManager.DEFAULT_KNOCKBACK_RESISTANCE_INCREMENT,
				RegionalDifficultyConfigManager.DEFAULT_EXPERIENCE_DROP_INCREMENT
			);
		}
		if ("zombie-villager".equals(normalized) || "minecraft:zombie_villager".equals(normalized)) {
			return RegionalScalingConfigZombieVillager.buildDefaults(
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
		Double health,
		Double movementSpeed,
		Double swimmingSpeed,
		Double flyingSpeed,
		Double scale,
		Double armor,
		Double damage,
		Double knockbackResistance,
		Double experienceDrop,
		Double rangedDamage,
		Double attackAccuracy,
		Double explosionPower
	) {
		return RegionalDifficultyConfigManager.buildMobScalingDefaults(
			mobId,
			health,
			movementSpeed,
			swimmingSpeed,
			flyingSpeed,
			scale,
			armor,
			damage,
			knockbackResistance,
			experienceDrop,
			rangedDamage,
			attackAccuracy,
			explosionPower
		);
	}
}
