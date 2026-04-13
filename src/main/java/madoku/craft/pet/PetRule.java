package madoku.craft.pet;

import com.google.gson.JsonObject;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.resources.Identifier;
import net.minecraft.sounds.SoundEvent;
import net.minecraft.sounds.SoundEvents;

final class PetRule {
	final boolean enabled;
	final String itemId;
	final String rarity;
	final double petScale;
	final double followSpeed;
	final double idleMoveSpeed;
	final double idleDistance;
	final double teleportDistance;
	final double idleWanderRadius;
	final long idleMinIntervalTicks;
	final long idleMaxIntervalTicks;
	final int ambientSoundIntervalMultiplier;
	final float soundVolumeMultiplier;
	final String abilityType;
	final float attackDamage;
	final float attackSpeed;
	final long effectDurationTicks;
	final double playerDamageBonusAmount;
	final double fallDamageReductionAmount;
	final double maxHealthBonusAmount;
	final double armorBonusAmount;
	final double damageBlockAmount;
	final long cooldownTicks;
	final long shotDelayTicks;
	final double attackArcStepDegrees;
	final double attackRearOffset;
	final double attackRearSpread;
	final double attackLateralRadius;
	final double attackVerticalOffset;
	final float explosionRadius;
	final String soundEventId;

	PetRule(
		boolean enabled,
		String itemId,
		String rarity,
		double petScale,
		double followSpeed,
		double idleMoveSpeed,
		double idleDistance,
		double teleportDistance,
		double idleWanderRadius,
		long idleMinIntervalTicks,
		long idleMaxIntervalTicks,
		int ambientSoundIntervalMultiplier,
		float soundVolumeMultiplier,
		String abilityType,
		float attackDamage,
		float attackSpeed,
		long effectDurationTicks,
		double playerDamageBonusAmount,
		double fallDamageReductionAmount,
		double maxHealthBonusAmount,
		double armorBonusAmount,
		double damageBlockAmount,
		long cooldownTicks,
		long shotDelayTicks,
		double attackArcStepDegrees,
		double attackRearOffset,
		double attackRearSpread,
		double attackLateralRadius,
		double attackVerticalOffset,
		float explosionRadius,
		String soundEventId
	) {
		this.enabled = enabled;
		this.itemId = itemId;
		this.rarity = rarity;
		this.petScale = petScale;
		this.followSpeed = followSpeed;
		this.idleMoveSpeed = idleMoveSpeed;
		this.idleDistance = idleDistance;
		this.teleportDistance = teleportDistance;
		this.idleWanderRadius = idleWanderRadius;
		this.idleMinIntervalTicks = idleMinIntervalTicks;
		this.idleMaxIntervalTicks = idleMaxIntervalTicks;
		this.ambientSoundIntervalMultiplier = ambientSoundIntervalMultiplier;
		this.soundVolumeMultiplier = soundVolumeMultiplier;
		this.abilityType = abilityType;
		this.attackDamage = attackDamage;
		this.attackSpeed = attackSpeed;
		this.effectDurationTicks = effectDurationTicks;
		this.playerDamageBonusAmount = playerDamageBonusAmount;
		this.fallDamageReductionAmount = fallDamageReductionAmount;
		this.maxHealthBonusAmount = maxHealthBonusAmount;
		this.armorBonusAmount = armorBonusAmount;
		this.damageBlockAmount = damageBlockAmount;
		this.cooldownTicks = cooldownTicks;
		this.shotDelayTicks = shotDelayTicks;
		this.attackArcStepDegrees = attackArcStepDegrees;
		this.attackRearOffset = attackRearOffset;
		this.attackRearSpread = attackRearSpread;
		this.attackLateralRadius = attackLateralRadius;
		this.attackVerticalOffset = attackVerticalOffset;
		this.explosionRadius = explosionRadius;
		this.soundEventId = soundEventId;
	}

	static JsonObject defaultsForItem(String itemId, String abilityType) {
		JsonObject root = new JsonObject();
		String resolvedItemId = itemId == null ? "" : itemId.trim();
		String resolvedAbilityType = PlayerEntitiesSystem.normalizeKey(abilityType);
		boolean usesRangedHomingArrow = PlayerEntitiesSystem.PET_ABILITY_RANGED_HOMING_ARROW.equals(resolvedAbilityType);
		boolean usesWebProjectile = PlayerEntitiesSystem.PET_ABILITY_WEB_PROJECTILE.equals(resolvedAbilityType);
		boolean usesExplosiveProjectile = PlayerEntitiesSystem.PET_ABILITY_EXPLOSIVE_PROJECTILE.equals(resolvedAbilityType);
		boolean usesPlayerDamageBonus = PlayerEntitiesSystem.PET_ABILITY_PLAYER_DAMAGE_BONUS.equals(resolvedAbilityType);
		boolean usesFallDamageReduction = PlayerEntitiesSystem.PET_ABILITY_FALL_DAMAGE_REDUCTION.equals(resolvedAbilityType);
		boolean usesMaxHealthBonus = PlayerEntitiesSystem.PET_ABILITY_MAX_HEALTH_BONUS.equals(resolvedAbilityType);
		boolean usesArmorBonus = PlayerEntitiesSystem.PET_ABILITY_ARMOR_BONUS.equals(resolvedAbilityType);
		boolean usesDamageBlock = PlayerEntitiesSystem.PET_ABILITY_DAMAGE_BLOCK.equals(resolvedAbilityType);
		boolean usesMobScan = PlayerEntitiesSystem.PET_ABILITY_MOB_SCAN.equals(resolvedAbilityType);
		root.addProperty("enabled", true);
		root.addProperty("item_id", resolvedItemId);
		root.addProperty("rarity", PlayerEntitiesSystem.defaultRarityForItem(resolvedItemId));
		root.addProperty("pet_scale", PlayerEntitiesSystem.defaultPetScaleForItem(resolvedItemId));
		root.addProperty("follow_speed", 1.2D);
		root.addProperty("idle_move_speed", 0.8D);
		root.addProperty("idle_distance", 4.0D);
		root.addProperty("teleport_distance", 8.0D);
		root.addProperty("idle_wander_radius", 2.0D);
		root.addProperty("idle_min_interval_ticks", 20L);
		root.addProperty("idle_max_interval_ticks", 60L);
		root.addProperty("ambient_sound_interval_multiplier", 3);
		root.addProperty("sound_volume_multiplier", 0.2D);
		root.addProperty("ability", resolvedAbilityType.isBlank() ? PlayerEntitiesSystem.PET_ABILITY_NONE : resolvedAbilityType);
		if (usesRangedHomingArrow) {
			root.addProperty("attack_damage", 3.0D);
			root.addProperty("attack_speed", 1.5D);
			root.addProperty("cooldown_ticks", 5L * 20L);
			root.addProperty("shot_delay_ticks", 10L);
			root.addProperty("attack_arc_step_degrees", 18.0D);
			root.addProperty("attack_rear_offset", 0.58D);
			root.addProperty("attack_rear_spread", 0.20D);
			root.addProperty("attack_lateral_radius", 0.45D);
			root.addProperty("attack_vertical_offset", -0.34D);
			root.addProperty("sound_event", BuiltInRegistries.SOUND_EVENT.getKey(SoundEvents.SKELETON_SHOOT).toString());
		}
		if (usesWebProjectile) {
			root.addProperty("follow_speed", 1.0D);
			root.addProperty("idle_move_speed", 0.75D);
			root.addProperty("attack_damage", 1.5D);
			root.addProperty("attack_speed", 1.0D);
			root.addProperty("effect_duration_ticks", 100L);
			root.addProperty("cooldown_ticks", 15L * 20L);
			root.addProperty("shot_delay_ticks", 10L);
			root.addProperty("attack_arc_step_degrees", 12.0D);
			root.addProperty("attack_rear_offset", 0.50D);
			root.addProperty("attack_rear_spread", 0.15D);
			root.addProperty("attack_lateral_radius", 0.38D);
			root.addProperty("attack_vertical_offset", -0.28D);
			root.addProperty("sound_event", BuiltInRegistries.SOUND_EVENT.getKey(SoundEvents.LLAMA_SPIT).toString());
		}
		if (usesExplosiveProjectile) {
			root.addProperty("follow_speed", 1.2D);
			root.addProperty("idle_move_speed", 0.8D);
			root.addProperty("attack_damage", 12.0D);
			root.addProperty("attack_speed", 2.0D);
			root.addProperty("cooldown_ticks", 60L * 20L);
			root.addProperty("shot_delay_ticks", 10L);
			root.addProperty("attack_arc_step_degrees", 10.0D);
			root.addProperty("attack_rear_offset", 0.46D);
			root.addProperty("attack_rear_spread", 0.12D);
			root.addProperty("attack_lateral_radius", 0.35D);
			root.addProperty("attack_vertical_offset", -0.26D);
			root.addProperty("explosion_radius", 4D);
			root.addProperty("sound_event", BuiltInRegistries.SOUND_EVENT.getKey(SoundEvents.CREEPER_PRIMED).toString());
		}
		if (usesPlayerDamageBonus) {
			root.addProperty("player_damage_bonus", 1.0D);
		}
		if (usesFallDamageReduction) {
			root.addProperty("fall_damage_reduction", 0.20D);
		}
		if (usesMaxHealthBonus) {
			root.addProperty("max_health_bonus", 0.10D);
		}
		if (usesArmorBonus) {
			root.addProperty("armor_bonus", 2.0D);
		}
		if (usesDamageBlock) {
			root.addProperty("damage_block", 4.0D);
			root.addProperty("cooldown_ticks", 30L * 20L);
		}
		if (usesMobScan) {
			root.addProperty("cooldown_ticks", 3L * 60L * 20L);
			root.addProperty("sound_event", BuiltInRegistries.SOUND_EVENT.getKey(SoundEvents.BEACON_ACTIVATE).toString());
		}
		return root;
	}

	static PetRule fromJson(JsonObject source, String fileKey) {
		if (source == null) {
			return null;
		}

		String itemId = PlayerEntitiesSystem.resolvePetItemId(fileKey, source);
		if (itemId == null || itemId.isBlank()) {
			return null;
		}
		String rarity = PlayerEntitiesSystem.normalizePetRarity(
			PlayerEntitiesSystem.getString(source, "rarity", PlayerEntitiesSystem.PET_RARITY_COMMON)
		);
		double petScale = PetSettings.clampDouble(
			PlayerEntitiesSystem.getDouble(source, "pet_scale", PlayerEntitiesSystem.defaultPetScaleForItem(itemId)),
			0.01D,
			4.0D
		);
		double followSpeed = PetSettings.clampDouble(PlayerEntitiesSystem.getDouble(source, "follow_speed", 1.2D), 0.05D, 4.0D);
		double idleMoveSpeed = PetSettings.clampDouble(PlayerEntitiesSystem.getDouble(source, "idle_move_speed", 0.8D), 0.05D, 4.0D);
		double idleDistance = PetSettings.clampDouble(PlayerEntitiesSystem.getDouble(source, "idle_distance", 4.0D), 0.5D, 32.0D);
		double teleportDistance = PetSettings.clampDouble(PlayerEntitiesSystem.getDouble(source, "teleport_distance", 8.0D), idleDistance, 64.0D);
		double idleWanderRadius = PetSettings.clampDouble(PlayerEntitiesSystem.getDouble(source, "idle_wander_radius", 2.0D), 0.0D, 16.0D);
		long idleMinIntervalTicks = PetSettings.clampLong(PlayerEntitiesSystem.getLong(source, "idle_min_interval_ticks", 20L), 1L, 20L * 60L);
		long idleMaxIntervalTicks = PetSettings.clampLong(PlayerEntitiesSystem.getLong(source, "idle_max_interval_ticks", 60L), idleMinIntervalTicks, 20L * 60L);
		int ambientSoundIntervalMultiplier = (int) PetSettings.clampLong(PlayerEntitiesSystem.getLong(source, "ambient_sound_interval_multiplier", 3L), 1L, 20L);
		float soundVolumeMultiplier = (float) PetSettings.clampDouble(PlayerEntitiesSystem.getDouble(source, "sound_volume_multiplier", 0.2D), 0.0D, 4.0D);
		String abilityType = PlayerEntitiesSystem.normalizeKey(PlayerEntitiesSystem.getString(source, "ability", PlayerEntitiesSystem.PET_ABILITY_NONE));
		float attackDamage = (float) PetSettings.clampDouble(PlayerEntitiesSystem.getDouble(source, "attack_damage", 0.0D), 0.0D, 1024.0D);
		float attackSpeed = (float) PetSettings.clampDouble(PlayerEntitiesSystem.getDouble(source, "attack_speed", 0.0D), 0.05D, 8.0D);
		long effectDurationTicks = PetSettings.clampLong(PlayerEntitiesSystem.getLong(source, "effect_duration_ticks", 0L), 0L, 20L * 60L);
		double playerDamageBonusAmount = PetSettings.clampDouble(PlayerEntitiesSystem.getDouble(source, "player_damage_bonus", 0.0D), 0.0D, 1024.0D);
		double fallDamageReductionAmount = PetSettings.clampDouble(PlayerEntitiesSystem.getDouble(source, "fall_damage_reduction", 0.0D), 0.0D, 1.0D);
		double maxHealthBonusAmount = PetSettings.clampDouble(PlayerEntitiesSystem.getDouble(source, "max_health_bonus", 0.0D), 0.0D, 10.0D);
		double armorBonusAmount = PetSettings.clampDouble(PlayerEntitiesSystem.getDouble(source, "armor_bonus", 0.0D), 0.0D, 1024.0D);
		double damageBlockAmount = PetSettings.clampDouble(PlayerEntitiesSystem.getDouble(source, "damage_block", 0.0D), 0.0D, 1024.0D);
		long cooldownTicks = PetSettings.clampLong(PlayerEntitiesSystem.getLong(source, "cooldown_ticks", 0L), 0L, 20L * 60L * 60L);
		long shotDelayTicks = PetSettings.clampLong(PlayerEntitiesSystem.getLong(source, "shot_delay_ticks", 0L), 0L, 20L * 60L);
		double attackArcStepDegrees = PetSettings.clampDouble(PlayerEntitiesSystem.getDouble(source, "attack_arc_step_degrees", 18.0D), 0.0D, 90.0D);
		double attackRearOffset = PetSettings.clampDouble(PlayerEntitiesSystem.getDouble(source, "attack_rear_offset", 0.58D), 0.0D, 4.0D);
		double attackRearSpread = PetSettings.clampDouble(PlayerEntitiesSystem.getDouble(source, "attack_rear_spread", 0.20D), 0.0D, 4.0D);
		double attackLateralRadius = PetSettings.clampDouble(PlayerEntitiesSystem.getDouble(source, "attack_lateral_radius", 0.45D), 0.0D, 4.0D);
		double attackVerticalOffset = PetSettings.clampDouble(PlayerEntitiesSystem.getDouble(source, "attack_vertical_offset", -0.34D), -4.0D, 4.0D);
		float explosionRadius = (float) PetSettings.clampDouble(PlayerEntitiesSystem.getDouble(source, "explosion_radius", 4.0D), 0.25D, 12.0D);
		String soundEventId = PlayerEntitiesSystem.getString(
			source,
			"sound_event",
			defaultSoundEventIdForAbility(abilityType)
		);
		return new PetRule(
			PlayerEntitiesSystem.getBoolean(source, "enabled", true),
			itemId.trim(),
			rarity,
			petScale,
			followSpeed,
			idleMoveSpeed,
			idleDistance,
			teleportDistance,
			idleWanderRadius,
			idleMinIntervalTicks,
			idleMaxIntervalTicks,
			ambientSoundIntervalMultiplier,
			soundVolumeMultiplier,
			abilityType.isBlank() ? PlayerEntitiesSystem.PET_ABILITY_NONE : abilityType,
			attackDamage,
			attackSpeed,
			effectDurationTicks,
			playerDamageBonusAmount,
			fallDamageReductionAmount,
			maxHealthBonusAmount,
			armorBonusAmount,
			damageBlockAmount,
			cooldownTicks,
			shotDelayTicks,
			attackArcStepDegrees,
			attackRearOffset,
			attackRearSpread,
			attackLateralRadius,
			attackVerticalOffset,
			explosionRadius,
			soundEventId
		);
	}

	boolean canPerformReactiveAttack() {
		if (!enabled || attackSpeed <= 0.0F || cooldownTicks <= 0L) {
			return false;
		}
		if (PlayerEntitiesSystem.PET_ABILITY_RANGED_HOMING_ARROW.equals(abilityType)) {
			return attackDamage > 0.0F;
		}
		if (PlayerEntitiesSystem.PET_ABILITY_WEB_PROJECTILE.equals(abilityType)) {
			return true;
		}
		return PlayerEntitiesSystem.PET_ABILITY_EXPLOSIVE_PROJECTILE.equals(abilityType) && explosionRadius > 0.0F && attackDamage > 0.0F;
	}

	double playerDamageBonus() {
		return enabled && PlayerEntitiesSystem.PET_ABILITY_PLAYER_DAMAGE_BONUS.equals(abilityType) ? playerDamageBonusAmount : 0.0D;
	}

	double fallDamageReduction() {
		return enabled && PlayerEntitiesSystem.PET_ABILITY_FALL_DAMAGE_REDUCTION.equals(abilityType) ? fallDamageReductionAmount : 0.0D;
	}

	double maxHealthBonus() {
		return enabled && PlayerEntitiesSystem.PET_ABILITY_MAX_HEALTH_BONUS.equals(abilityType) ? maxHealthBonusAmount : 0.0D;
	}

	double armorBonus() {
		return enabled && PlayerEntitiesSystem.PET_ABILITY_ARMOR_BONUS.equals(abilityType) ? armorBonusAmount : 0.0D;
	}

	double damageBlockAmount() {
		return enabled && PlayerEntitiesSystem.PET_ABILITY_DAMAGE_BLOCK.equals(abilityType) ? damageBlockAmount : 0.0D;
	}

	boolean canBlockIncomingDamage() {
		return enabled && PlayerEntitiesSystem.PET_ABILITY_DAMAGE_BLOCK.equals(abilityType) && damageBlockAmount > 0.0D && cooldownTicks > 0L;
	}

	String abilityDescription() {
		if (!enabled) {
			return "";
		}
		if (PlayerEntitiesSystem.PET_ABILITY_RANGED_HOMING_ARROW.equals(abilityType)) {
			return "Active: Fires a homing arrow at your target.";
		}
		if (PlayerEntitiesSystem.PET_ABILITY_WEB_PROJECTILE.equals(abilityType)) {
			return "Active: Launches a web projectile that slows enemies.";
		}
		if (PlayerEntitiesSystem.PET_ABILITY_EXPLOSIVE_PROJECTILE.equals(abilityType)) {
			return "Active: Fires an explosive projectile at your target.";
		}
		if (PlayerEntitiesSystem.PET_ABILITY_PLAYER_DAMAGE_BONUS.equals(abilityType) && playerDamageBonusAmount > 0.0D) {
			return "Passive: Increases player damage by " + PlayerEntitiesSystem.formatAbilityAmount(playerDamageBonusAmount) + ".";
		}
		if (PlayerEntitiesSystem.PET_ABILITY_FALL_DAMAGE_REDUCTION.equals(abilityType) && fallDamageReductionAmount > 0.0D) {
			return "Passive: Reduces fall damage by " + PlayerEntitiesSystem.formatPercent(fallDamageReductionAmount) + ".";
		}
		if (PlayerEntitiesSystem.PET_ABILITY_MAX_HEALTH_BONUS.equals(abilityType) && maxHealthBonusAmount > 0.0D) {
			return "Passive: Increases max health by " + PlayerEntitiesSystem.formatPercent(maxHealthBonusAmount) + ".";
		}
		if (PlayerEntitiesSystem.PET_ABILITY_ARMOR_BONUS.equals(abilityType) && armorBonusAmount > 0.0D) {
			return "Passive: Increases armor by " + PlayerEntitiesSystem.formatAbilityAmount(armorBonusAmount) + ".";
		}
		if (PlayerEntitiesSystem.PET_ABILITY_DAMAGE_BLOCK.equals(abilityType) && damageBlockAmount > 0.0D) {
			return "Active: Blocks " + PlayerEntitiesSystem.formatAbilityAmount(damageBlockAmount) + " incoming damage.";
		}
		if (PlayerEntitiesSystem.PET_ABILITY_MOB_SCAN.equals(abilityType)) {
			return "Automatic: Periodically scans nearby mobs, makes them glow for 90s, and scales with extra bats.";
		}
		return "";
	}

	String cooldownDescription() {
		if (!enabled || cooldownTicks <= 0L || PlayerEntitiesSystem.PET_ABILITY_NONE.equals(abilityType)) {
			return "";
		}
		if (PlayerEntitiesSystem.PET_ABILITY_MOB_SCAN.equals(abilityType)) {
			return "Cooldown: " + PlayerEntitiesSystem.formatCooldownSeconds(cooldownTicks) + "s base, -40s per extra bat";
		}
		return "Cooldown: " + PlayerEntitiesSystem.formatCooldownSeconds(cooldownTicks) + "s";
	}

	boolean hasAbility() {
		return enabled && !PlayerEntitiesSystem.PET_ABILITY_NONE.equals(abilityType);
	}

	SoundEvent resolveSoundEvent() {
		Identifier identifier = Identifier.tryParse(soundEventId == null ? "" : soundEventId.trim());
		if (identifier == null) {
			return defaultSoundEvent();
		}
		SoundEvent soundEvent = BuiltInRegistries.SOUND_EVENT.getValue(identifier);
		return soundEvent == null ? defaultSoundEvent() : soundEvent;
	}

	SoundEvent defaultSoundEvent() {
		if (PlayerEntitiesSystem.PET_ABILITY_MOB_SCAN.equals(abilityType)) {
			return SoundEvents.BEACON_ACTIVATE;
		}
		if (PlayerEntitiesSystem.PET_ABILITY_WEB_PROJECTILE.equals(abilityType)) {
			return SoundEvents.LLAMA_SPIT;
		}
		if (PlayerEntitiesSystem.PET_ABILITY_EXPLOSIVE_PROJECTILE.equals(abilityType)) {
			return SoundEvents.CREEPER_PRIMED;
		}
		return SoundEvents.SKELETON_SHOOT;
	}

	private static String defaultSoundEventIdForAbility(String abilityType) {
		if (PlayerEntitiesSystem.PET_ABILITY_MOB_SCAN.equals(abilityType)) {
			return BuiltInRegistries.SOUND_EVENT.getKey(SoundEvents.BEACON_ACTIVATE).toString();
		}
		if (PlayerEntitiesSystem.PET_ABILITY_WEB_PROJECTILE.equals(abilityType)) {
			return BuiltInRegistries.SOUND_EVENT.getKey(SoundEvents.LLAMA_SPIT).toString();
		}
		if (PlayerEntitiesSystem.PET_ABILITY_EXPLOSIVE_PROJECTILE.equals(abilityType)) {
			return BuiltInRegistries.SOUND_EVENT.getKey(SoundEvents.CREEPER_PRIMED).toString();
		}
		return BuiltInRegistries.SOUND_EVENT.getKey(SoundEvents.SKELETON_SHOOT).toString();
	}
}
