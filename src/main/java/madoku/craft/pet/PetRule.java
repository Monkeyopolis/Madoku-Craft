package madoku.craft.pet;

import com.google.gson.JsonObject;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.resources.Identifier;
import net.minecraft.sounds.SoundEvent;
import net.minecraft.sounds.SoundEvents;

final class PetRule {
	private static final double DEFAULT_IDLE_MOVE_SPEED = 0.8D;
	private static final double DEFAULT_IDLE_DISTANCE = 4.0D;
	private static final double DEFAULT_IDLE_WANDER_RADIUS = 2.0D;
	private static final long DEFAULT_IDLE_MIN_INTERVAL_TICKS = 20L;
	private static final long DEFAULT_IDLE_MAX_INTERVAL_TICKS = 60L;
	private static final int DEFAULT_AMBIENT_SOUND_INTERVAL_MULTIPLIER = 3;
	private static final float DEFAULT_SOUND_VOLUME_MULTIPLIER = 0.2F;
	private static final double DEFAULT_ATTACK_ARC_STEP_DEGREES = 18.0D;
	private static final double DEFAULT_ATTACK_REAR_OFFSET = 0.58D;
	private static final double DEFAULT_ATTACK_REAR_SPREAD = 0.20D;
	private static final double DEFAULT_ATTACK_LATERAL_RADIUS = 0.45D;
	private static final double DEFAULT_ATTACK_VERTICAL_OFFSET = -0.34D;

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
		boolean usesBeeSwarm = PlayerEntitiesSystem.PET_ABILITY_BEE_SWARM.equals(resolvedAbilityType);
		madoku.craft.config.JsonFormatBuilder.ObjectBuilder root = madoku.craft.config.JsonFormatBuilder.object()
			.put("enabled", true)
			.put("item-id", resolvedItemId)
			.put("rarity", PlayerEntitiesSystem.defaultRarityForItem(resolvedItemId))
			.put("pet-scale", PlayerEntitiesSystem.defaultPetScaleForItem(resolvedItemId))
			.put("follow-speed", 1.2D)
			.put("teleport-distance", 8.0D)
			.put("ability", resolvedAbilityType.isBlank() ? PlayerEntitiesSystem.PET_ABILITY_NONE : resolvedAbilityType);
		if (usesRangedHomingArrow) {
			root.put("attack-damage", 3.0D)
				.put("attack-speed", 1.5D)
				.put("cooldown-ticks", 5L * 20L)
				.put("shot-delay-ticks", 10L);
		}
		if (usesWebProjectile) {
			root.put("follow-speed", 1.0D)
				.put("attack-damage", 1.5D)
				.put("attack-speed", 1.0D)
				.put("effect-duration-ticks", 100L)
				.put("cooldown-ticks", 15L * 20L)
				.put("shot-delay-ticks", 10L);
		}
		if (usesExplosiveProjectile) {
			root.put("follow-speed", 1.2D)
				.put("attack-damage", 12.0D)
				.put("attack-speed", 2.0D)
				.put("cooldown-ticks", 60L * 20L)
				.put("shot-delay-ticks", 10L)
				.put("explosion-radius", 4D);
		}
		if (usesPlayerDamageBonus) {
			root.put("player-damage-bonus", 1.25D);
		}
		if (usesFallDamageReduction) {
			root.put("fall-damage-reduction", 0.20D);
		}
		if (usesMaxHealthBonus) {
			root.put("max-health-bonus", 0.125D);
		}
		if (usesArmorBonus) {
			root.put("armor-bonus", 2.0D);
		}
		if (usesDamageBlock) {
			root.put("damage-block", 4.0D)
				.put("cooldown-ticks", 30L * 20L);
		}
		if (usesMobScan) {
			root.put("cooldown-ticks", 3L * 60L * 20L);
		}
		if (usesBeeSwarm) {
			root.put("follow-speed", 1.5D)
				.put("idle-move-speed", 1.25D)
				.put("idle-wander-radius", 4.0D)
				.put("attack-damage", 1.5D)
				.put("cooldown-ticks", 0L);
		}
		return root.build();
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
			PlayerEntitiesSystem.getDouble(source, "pet-scale", PlayerEntitiesSystem.defaultPetScaleForItem(itemId)),
			0.01D,
			4.0D
		);
		String abilityType = PlayerEntitiesSystem.normalizeKey(PlayerEntitiesSystem.getString(source, "ability", PlayerEntitiesSystem.PET_ABILITY_NONE));
		double followSpeed = PetSettings.clampDouble(PlayerEntitiesSystem.getDouble(source, "follow-speed", 1.2D), 0.05D, 4.0D);
		double idleMoveSpeed = PetSettings.clampDouble(
			PlayerEntitiesSystem.getDouble(source, "idle-move-speed", defaultIdleMoveSpeedForAbility(abilityType)),
			0.05D,
			4.0D
		);
		double idleDistance = PetSettings.clampDouble(PlayerEntitiesSystem.getDouble(source, "idle-distance", DEFAULT_IDLE_DISTANCE), 0.5D, 32.0D);
		double teleportDistance = PetSettings.clampDouble(PlayerEntitiesSystem.getDouble(source, "teleport-distance", 8.0D), idleDistance, 64.0D);
		double idleWanderRadius = PetSettings.clampDouble(PlayerEntitiesSystem.getDouble(source, "idle-wander-radius", DEFAULT_IDLE_WANDER_RADIUS), 0.0D, 16.0D);
		long idleMinIntervalTicks = PetSettings.clampLong(PlayerEntitiesSystem.getLong(source, "idle-min-interval-ticks", DEFAULT_IDLE_MIN_INTERVAL_TICKS), 1L, 20L * 60L);
		long idleMaxIntervalTicks = PetSettings.clampLong(PlayerEntitiesSystem.getLong(source, "idle-max-interval-ticks", DEFAULT_IDLE_MAX_INTERVAL_TICKS), idleMinIntervalTicks, 20L * 60L);
		int ambientSoundIntervalMultiplier = (int) PetSettings.clampLong(PlayerEntitiesSystem.getLong(source, "ambient-sound-interval-multiplier", DEFAULT_AMBIENT_SOUND_INTERVAL_MULTIPLIER), 1L, 20L);
		float soundVolumeMultiplier = (float) PetSettings.clampDouble(PlayerEntitiesSystem.getDouble(source, "sound-volume-multiplier", DEFAULT_SOUND_VOLUME_MULTIPLIER), 0.0D, 4.0D);
		float attackDamage = (float) PetSettings.clampDouble(PlayerEntitiesSystem.getDouble(source, "attack-damage", 0.0D), 0.0D, 1024.0D);
		float attackSpeed = (float) PetSettings.clampDouble(PlayerEntitiesSystem.getDouble(source, "attack-speed", 0.0D), 0.05D, 8.0D);
		long effectDurationTicks = PetSettings.clampLong(PlayerEntitiesSystem.getLong(source, "effect-duration-ticks", 0L), 0L, 20L * 60L);
		double playerDamageBonusAmount = PetSettings.clampDouble(PlayerEntitiesSystem.getDouble(source, "player-damage-bonus", 0.0D), 0.0D, 1024.0D);
		double fallDamageReductionAmount = PetSettings.clampDouble(PlayerEntitiesSystem.getDouble(source, "fall-damage-reduction", 0.0D), 0.0D, 1.0D);
		double maxHealthBonusAmount = PetSettings.clampDouble(PlayerEntitiesSystem.getDouble(source, "max-health-bonus", 0.0D), 0.0D, 10.0D);
		double armorBonusAmount = PetSettings.clampDouble(PlayerEntitiesSystem.getDouble(source, "armor-bonus", 0.0D), 0.0D, 1024.0D);
		double damageBlockAmount = PetSettings.clampDouble(PlayerEntitiesSystem.getDouble(source, "damage-block", 0.0D), 0.0D, 1024.0D);
		long cooldownTicks = PetSettings.clampLong(PlayerEntitiesSystem.getLong(source, "cooldown-ticks", 0L), 0L, 20L * 60L * 60L);
		long shotDelayTicks = PetSettings.clampLong(PlayerEntitiesSystem.getLong(source, "shot-delay-ticks", 0L), 0L, 20L * 60L);
		double attackArcStepDegrees = PetSettings.clampDouble(
			PlayerEntitiesSystem.getDouble(source, "attack-arc-step-degrees", defaultAttackArcStepDegreesForAbility(abilityType)),
			0.0D,
			90.0D
		);
		double attackRearOffset = PetSettings.clampDouble(
			PlayerEntitiesSystem.getDouble(source, "attack-rear-offset", defaultAttackRearOffsetForAbility(abilityType)),
			0.0D,
			4.0D
		);
		double attackRearSpread = PetSettings.clampDouble(
			PlayerEntitiesSystem.getDouble(source, "attack-rear-spread", defaultAttackRearSpreadForAbility(abilityType)),
			0.0D,
			4.0D
		);
		double attackLateralRadius = PetSettings.clampDouble(
			PlayerEntitiesSystem.getDouble(source, "attack-lateral-radius", defaultAttackLateralRadiusForAbility(abilityType)),
			0.0D,
			4.0D
		);
		double attackVerticalOffset = PetSettings.clampDouble(
			PlayerEntitiesSystem.getDouble(source, "attack-vertical-offset", defaultAttackVerticalOffsetForAbility(abilityType)),
			-4.0D,
			4.0D
		);
		float explosionRadius = (float) PetSettings.clampDouble(PlayerEntitiesSystem.getDouble(source, "explosion-radius", 4.0D), 0.25D, 12.0D);
		String soundEventId = PlayerEntitiesSystem.getString(
			source,
			"sound-event",
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
			return "Automatic: Periodically reveals nearby mobs.";
		}
		if (PlayerEntitiesSystem.PET_ABILITY_BEE_SWARM.equals(abilityType) && attackDamage > 0.0F) {
			return "Automatic: Swarms nearby hostile mobs.";
		}
		return "";
	}

	String cooldownDescription() {
		if (!enabled || cooldownTicks <= 0L || PlayerEntitiesSystem.PET_ABILITY_NONE.equals(abilityType)) {
			return "";
		}
		if (PlayerEntitiesSystem.PET_ABILITY_MOB_SCAN.equals(abilityType)) {
			return "Cooldown: " + PlayerEntitiesSystem.formatCooldownSeconds(cooldownTicks) + "s";
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
		if (PlayerEntitiesSystem.PET_ABILITY_BEE_SWARM.equals(abilityType)) {
			return SoundEvents.BEE_LOOP;
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
		if (PlayerEntitiesSystem.PET_ABILITY_BEE_SWARM.equals(abilityType)) {
			return BuiltInRegistries.SOUND_EVENT.getKey(SoundEvents.BEE_LOOP).toString();
		}
		return BuiltInRegistries.SOUND_EVENT.getKey(SoundEvents.SKELETON_SHOOT).toString();
	}

	private static double defaultIdleMoveSpeedForAbility(String abilityType) {
		if (PlayerEntitiesSystem.PET_ABILITY_WEB_PROJECTILE.equals(abilityType)) {
			return 0.75D;
		}
		if (PlayerEntitiesSystem.PET_ABILITY_BEE_SWARM.equals(abilityType)) {
			return 1.2D;
		}
		return DEFAULT_IDLE_MOVE_SPEED;
	}

	private static double defaultAttackArcStepDegreesForAbility(String abilityType) {
		if (PlayerEntitiesSystem.PET_ABILITY_WEB_PROJECTILE.equals(abilityType)) {
			return 12.0D;
		}
		if (PlayerEntitiesSystem.PET_ABILITY_EXPLOSIVE_PROJECTILE.equals(abilityType)) {
			return 10.0D;
		}
		return DEFAULT_ATTACK_ARC_STEP_DEGREES;
	}

	private static double defaultAttackRearOffsetForAbility(String abilityType) {
		if (PlayerEntitiesSystem.PET_ABILITY_WEB_PROJECTILE.equals(abilityType)) {
			return 0.50D;
		}
		if (PlayerEntitiesSystem.PET_ABILITY_EXPLOSIVE_PROJECTILE.equals(abilityType)) {
			return 0.46D;
		}
		return DEFAULT_ATTACK_REAR_OFFSET;
	}

	private static double defaultAttackRearSpreadForAbility(String abilityType) {
		if (PlayerEntitiesSystem.PET_ABILITY_WEB_PROJECTILE.equals(abilityType)) {
			return 0.15D;
		}
		if (PlayerEntitiesSystem.PET_ABILITY_EXPLOSIVE_PROJECTILE.equals(abilityType)) {
			return 0.12D;
		}
		return DEFAULT_ATTACK_REAR_SPREAD;
	}

	private static double defaultAttackLateralRadiusForAbility(String abilityType) {
		if (PlayerEntitiesSystem.PET_ABILITY_WEB_PROJECTILE.equals(abilityType)) {
			return 0.38D;
		}
		if (PlayerEntitiesSystem.PET_ABILITY_EXPLOSIVE_PROJECTILE.equals(abilityType)) {
			return 0.35D;
		}
		return DEFAULT_ATTACK_LATERAL_RADIUS;
	}

	private static double defaultAttackVerticalOffsetForAbility(String abilityType) {
		if (PlayerEntitiesSystem.PET_ABILITY_WEB_PROJECTILE.equals(abilityType)) {
			return -0.28D;
		}
		if (PlayerEntitiesSystem.PET_ABILITY_EXPLOSIVE_PROJECTILE.equals(abilityType)) {
			return -0.26D;
		}
		return DEFAULT_ATTACK_VERTICAL_OFFSET;
	}
}

