package madoku.craft.pet;

import com.google.gson.JsonObject;
import com.google.gson.JsonArray;
import com.google.gson.JsonElement;
import madoku.craft.api.json.JSONFormatManager;
import madoku.craft.api.json.MadokuJSONManager;
import net.minecraft.world.item.ItemStack;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.resources.Identifier;
import net.minecraft.sounds.SoundEvent;
import net.minecraft.sounds.SoundEvents;

import java.io.IOException;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.Map;
import java.util.List;
import java.util.LinkedHashSet;

/** Owns the configured Madoku Pet definitions and configuration lifecycle. */
public final class PetConfigManager {
	public static final String ROOT_FOLDER = "madoku-craft";
	public static final String PET_FOLDER = "madoku-craft-pets";
	public static final String ENTITY_FOLDER = "madoku-entities";
	public static final String ABILITY_FOLDER = "madoku-abilities";

	private PetConfigManager() {
	}

	public static void initialize() {
		reload();
	}

	public static boolean isEnabled() {
		return settings.enabled;
	}

	public static boolean areEntitiesEnabled() {
		return settings.entitiesEnabled;
	}

	public static boolean isValidPet(ItemStack stack) {
		if (!PetEntitiesManager.isPetItem(stack)) return false;
		PetRule rule = resolvePetRule(stack);
		return rule != null && rule.enabled;
	}

	public static String petRarity(ItemStack stack) {
		PetRule rule = resolvePetRule(stack);
		return rule == null ? MadokuPetManager.PET_RARITY_COMMON : rule.rarity;
	}

	public static String normalizeKey(String value) {
		return value == null ? "" : value.trim().toLowerCase();
	}

	static String normalizePetId(String value) {
		String normalized = normalizeKey(value).replace('_', '-');
		return normalized.contains(":") ? normalized : "minecraft:" + normalized;
	}

	static String normalizeAbilityId(String value) {
		return normalizeKey(value).replace('-', '_');
	}

	static List<String> resolveAbilityTypes(JsonObject sourceRoot) {
		JsonObject petGroup = objectField(sourceRoot, "pet-id");
		LinkedHashSet<String> resolved = new LinkedHashSet<>();
		JsonElement abilityIdsElement = petGroup.get("ability-ids");
		if (abilityIdsElement != null && abilityIdsElement.isJsonArray()) {
			JsonArray abilityIds = abilityIdsElement.getAsJsonArray();
			for (JsonElement element : abilityIds) {
				if (element != null && element.isJsonPrimitive()) {
					String abilityType = normalizeAbilityId(element.getAsString());
					if (!abilityType.isBlank()) resolved.add(abilityType);
				}
				if (resolved.size() >= 3) break;
			}
		}
		return List.copyOf(resolved);
	}

	static String abilityConfigId(String value) {
		return normalizeAbilityId(value).replace('_', '-');
	}

	static String petItemPath(String petId) {
		String normalized = normalizePetId(petId);
		int separator = normalized.indexOf(':');
		String path = separator < 0 ? normalized : normalized.substring(separator + 1);
		return path + "-pet";
	}

	static int maxPetLevel() {
		return settings.maxLevel;
	}

	private static final org.slf4j.Logger LOGGER = org.slf4j.LoggerFactory.getLogger(PetConfigManager.class);
	private static volatile PetSettings settings = PetSettings.defaults();
	private static final String CONFIG_FILE_NAME = "madoku-pets";

	static void reload() {
		loadStaticConfig();
		PetAbilitiesManager.AbilitiesConfigManager.reload();
		PetEntitiesManager.EntitiesConfigManager.reload();
	}

	static PetSettings settings() { return settings; }
	static Map<String, PetRule> rules() { return PetEntitiesManager.EntitiesConfigManager.rules(); }

	static PetRule resolvePetRule(ItemStack stack) {
		if (stack == null || stack.isEmpty()) {
			return null;
		}
		String petId = PetEntitiesManager.petId(stack);
		PetRule rule = petId.isBlank() ? null : resolvePetRule(petId);
		return rule == null ? null : rule.atLevel(PetEntitiesManager.petLevel(stack));
	}

	static PetRule resolvePetRule(net.minecraft.world.entity.Entity entity) {
		if (!(entity instanceof MadokuPetEntity pet)) {
			return null;
		}
		String petId = PetEntitiesManager.getManagedPetItemId(pet);
		PetRule rule = petId.isBlank() ? null : resolvePetRule(petId);
		return rule == null ? null : rule.atLevel(pet.petLevel());
	}

	static PetRule resolvePetRule(String itemId) {
		return PetEntitiesManager.EntitiesConfigManager.resolve(itemId);
	}

	static void loadStaticConfig() {
		try {
			Path rootDirectory = MadokuJSONManager.getOrCreateGlobalSystemDirectory(PET_FOLDER);
			Path configFile = resolveJsonFile(rootDirectory, CONFIG_FILE_NAME);
			JsonObject defaults = PetSettings.defaults().toConfigJson();
			JsonObject normalized = JSONFormatManager.ensureManagedFile(configFile, defaults);
			PetSettings configured = PetSettings.fromJson(normalized);
			JSONFormatManager.writeManagedFile(configFile, configured.toConfigJson(), defaults);
			settings = configured;
		} catch (IOException exception) {
			settings = PetSettings.defaults();
			LOGGER.error("Failed to load Madoku pet settings; using defaults.", exception);
		}
	}

	static Path petDirectory() throws IOException {
		return MadokuJSONManager.getOrCreateGlobalSystemDirectory(PET_FOLDER);
	}

	static void logFailure(String message, Throwable cause) {
		LOGGER.error(message, cause);
	}

	static String defaultAbilityForItem(String itemId) {
		String normalizedItemId = normalizePetId(itemId);
		if ("minecraft:bat".equals(normalizedItemId)) return MadokuPetManager.PET_ABILITY_MOB_SCAN;
		if ("minecraft:bee".equals(normalizedItemId)) return MadokuPetManager.PET_ABILITY_BEE_SWARM;
		if ("minecraft:chicken".equals(normalizedItemId)) return MadokuPetManager.PET_ABILITY_FALL_DAMAGE_REDUCTION;
		if ("minecraft:cow".equals(normalizedItemId)) return MadokuPetManager.PET_ABILITY_DAMAGE_BLOCK;
		if ("minecraft:pig".equals(normalizedItemId)) return MadokuPetManager.PET_ABILITY_MAX_HEALTH_BONUS;
		if ("minecraft:sheep".equals(normalizedItemId)) return MadokuPetManager.PET_ABILITY_ARMOR_BONUS;
		if ("minecraft:skeleton".equals(normalizedItemId)) return MadokuPetManager.PET_ABILITY_RANGED_HOMING_ARROW;
		if ("minecraft:spider".equals(normalizedItemId)) return MadokuPetManager.PET_ABILITY_WEB_PROJECTILE;
		if ("minecraft:creeper".equals(normalizedItemId)) return MadokuPetManager.PET_ABILITY_EXPLOSIVE_PROJECTILE;
		if ("minecraft:zombie".equals(normalizedItemId)) return MadokuPetManager.PET_ABILITY_PLAYER_DAMAGE_BONUS;
		return MadokuPetManager.PET_ABILITY_NONE;
	}

	static double defaultPetScaleForItem(String itemId) {
		String normalizedItemId = normalizePetId(itemId);
		if ("minecraft:bat".equals(normalizedItemId)) return 0.3D;
		if ("minecraft:bee".equals(normalizedItemId)) return 0.3D;
		if ("minecraft:chicken".equals(normalizedItemId)) return 0.3D;
		return 0.25D;
	}

	static String defaultRarityForItem(String itemId) {
		String normalizedItemId = normalizePetId(itemId);
		if ("minecraft:bee".equals(normalizedItemId) || "minecraft:bat".equals(normalizedItemId)) return MadokuPetManager.PET_RARITY_EPIC;
		if ("minecraft:chicken".equals(normalizedItemId)) return MadokuPetManager.PET_RARITY_LEGENDARY;
		if ("minecraft:cow".equals(normalizedItemId)
			|| "minecraft:creeper".equals(normalizedItemId)
			|| "minecraft:skeleton".equals(normalizedItemId)
			|| "minecraft:spider".equals(normalizedItemId)) return MadokuPetManager.PET_RARITY_RARE;
		return MadokuPetManager.PET_RARITY_COMMON;
	}

	static String resolvePetId(String fileKey, JsonObject sourceRoot) {
		JsonObject petGroup = objectField(sourceRoot, "pet-id");
		String configured = getString(petGroup, "id", "");
		if (configured.isBlank()) configured = normalizeFileKey(fileKey);
		if (configured.isBlank()) return null;
		String petId = normalizePetId(configured);
		return PetEntitiesManager.petItem(petId) == null ? null : petId;
	}

	static JsonObject objectField(JsonObject source, String key) {
		if (source == null || key == null || !source.has(key) || !source.get(key).isJsonObject()) return new JsonObject();
		return source.getAsJsonObject(key);
	}

	static Path resolveJsonFile(Path directory, String fileName) {
		String normalized = fileName == null ? "" : fileName.trim();
		if (normalized.isEmpty()) throw new IllegalArgumentException("JSON file name must not be blank.");
		if (!normalized.endsWith(".json")) normalized += ".json";
		return directory.resolve(normalized);
	}

	static String normalizeFileKey(String rawKey) {
		return rawKey == null ? "" : rawKey.trim().toLowerCase();
	}

	static String normalizePetRarity(String rawRarity) {
		return switch (normalizeKey(rawRarity)) {
			case MadokuPetManager.PET_RARITY_COMMON, MadokuPetManager.PET_RARITY_RARE, MadokuPetManager.PET_RARITY_EPIC, MadokuPetManager.PET_RARITY_LEGENDARY, MadokuPetManager.PET_RARITY_MYTHIC -> normalizeKey(rawRarity);
			default -> MadokuPetManager.PET_RARITY_COMMON;
		};
	}

	static String getString(JsonObject source, String key, String fallback) {
		if (source == null || key == null || !source.has(key) || !source.get(key).isJsonPrimitive()) return fallback;
		try { return source.get(key).getAsString(); } catch (RuntimeException exception) { return fallback; }
	}

	static long getLong(JsonObject source, String key, long fallback) {
		if (source == null || key == null || !source.has(key) || !source.get(key).isJsonPrimitive()) return fallback;
		try { return source.get(key).getAsLong(); } catch (RuntimeException exception) { return fallback; }
	}

	static int getInt(JsonObject source, String key, int fallback) {
		long value = getLong(source, key, fallback);
		return value < Integer.MIN_VALUE || value > Integer.MAX_VALUE ? fallback : (int) value;
	}

	static double getDouble(JsonObject source, String key, double fallback) {
		if (source == null || key == null || !source.has(key) || !source.get(key).isJsonPrimitive()) return fallback;
		try { return source.get(key).getAsDouble(); } catch (RuntimeException exception) { return fallback; }
	}

	static boolean getBoolean(JsonObject source, String key, boolean fallback) {
		if (source == null || key == null || !source.has(key) || !source.get(key).isJsonPrimitive()) return fallback;
		try { return source.get(key).getAsBoolean(); } catch (RuntimeException exception) { return fallback; }
	}


	static final class PetSettings {
		final boolean enabled;
			final boolean entitiesEnabled;
			final int petRarityCommonChanceWeight;
			final int petRarityRareChanceWeight;
			final int petRarityEpicChanceWeight;
			final int petRarityLegendaryChanceWeight;
			final int petRarityMythicChanceWeight;
		final int maxLevel;

			PetSettings(
				boolean enabled,
				boolean entitiesEnabled,
				int petRarityCommonChanceWeight,
				int petRarityRareChanceWeight,
				int petRarityEpicChanceWeight,
				int petRarityLegendaryChanceWeight,
				int petRarityMythicChanceWeight,
				int maxLevel
			) {
				this.enabled = enabled;
				this.entitiesEnabled = entitiesEnabled;
				this.petRarityCommonChanceWeight = petRarityCommonChanceWeight;
				this.petRarityRareChanceWeight = petRarityRareChanceWeight;
				this.petRarityEpicChanceWeight = petRarityEpicChanceWeight;
				this.petRarityLegendaryChanceWeight = petRarityLegendaryChanceWeight;
				this.petRarityMythicChanceWeight = petRarityMythicChanceWeight;
				this.maxLevel = maxLevel;
			}

			static PetSettings defaults() {
				return new PetSettings(
					true,
					true,
					334,
					120,
					40,
					5,
					1,
					5
				);
			}

			static PetSettings fromJson(JsonObject source) {
				PetSettings defaults = defaults();
				JsonObject petEntity = objectField(source, "pet-entity");
				JsonObject petRarity = objectField(source, "pet-rarity");
				boolean enabled = getBoolean(source, "enabled", defaults.enabled);
				boolean entitiesEnabled = getBoolean(petEntity, "enabled", defaults.entitiesEnabled);
				int maxLevel = (int) clampLong(getLong(petEntity, "max-level", defaults.maxLevel), 1L, 100L);
				int petRarityCommonChanceWeight = (int) clampLong(
					getLong(petRarity, "common", defaults.petRarityCommonChanceWeight),
					0,
					100000
				);
				int petRarityRareChanceWeight = (int) clampLong(
					getLong(petRarity, "rare", defaults.petRarityRareChanceWeight),
					0,
					100000
				);
				int petRarityEpicChanceWeight = (int) clampLong(
					getLong(petRarity, "epic", defaults.petRarityEpicChanceWeight),
					0,
					100000
				);
				int petRarityLegendaryChanceWeight = (int) clampLong(
					getLong(petRarity, "legendary", defaults.petRarityLegendaryChanceWeight),
					0,
					100000
				);
				int petRarityMythicChanceWeight = (int) clampLong(
					getLong(petRarity, "mythic", defaults.petRarityMythicChanceWeight),
					0,
					100000
				);
				return new PetSettings(
					enabled,
					entitiesEnabled,
					petRarityCommonChanceWeight,
					petRarityRareChanceWeight,
					petRarityEpicChanceWeight,
					petRarityLegendaryChanceWeight,
					petRarityMythicChanceWeight,
					maxLevel
				);
			}

			JsonObject toConfigJson() {
				return madoku.craft.api.json.JSONFormatManager.object()
					.object("pet-entity", child -> child
						.put("enabled", entitiesEnabled)
						.put("max-level", maxLevel))
					.object("pet-rarity", child -> child
						.put("common", petRarityCommonChanceWeight)
						.put("rare", petRarityRareChanceWeight)
						.put("epic", petRarityEpicChanceWeight)
						.put("legendary", petRarityLegendaryChanceWeight)
						.put("mythic", petRarityMythicChanceWeight))
					.build();
			}

			static long clampLong(long value, long min, long max) {
				return Math.max(min, Math.min(max, value));
			}

			static double clampDouble(double value, double min, double max) {
				return Math.max(min, Math.min(max, value));
			}
	}

	static final class PetAbilityRule {
		final String abilityType;
		final float soundVolumeMultiplier;
		final float attackDamage;
		final float attackSpeed;
		final long effectDurationTicks;
		final long stunDurationTicks;
		final long slowDurationTicks;
		final double slowPercentage;
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

		PetAbilityRule(
			String abilityType,
			float soundVolumeMultiplier,
			float attackDamage,
			float attackSpeed,
			long effectDurationTicks,
			long stunDurationTicks,
			long slowDurationTicks,
			double slowPercentage,
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
			this.abilityType = normalizeAbilityId(abilityType);
			this.soundVolumeMultiplier = soundVolumeMultiplier;
			this.attackDamage = attackDamage;
			this.attackSpeed = attackSpeed;
			this.effectDurationTicks = effectDurationTicks;
			this.stunDurationTicks = stunDurationTicks;
			this.slowDurationTicks = slowDurationTicks;
			this.slowPercentage = slowPercentage;
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

		boolean canPerformReactiveAttack() {
			if (attackSpeed <= 0.0F || cooldownTicks <= 0L) return false;
			if (MadokuPetManager.PET_ABILITY_RANGED_HOMING_ARROW.equals(abilityType)) return attackDamage > 0.0F;
			if (MadokuPetManager.PET_ABILITY_WEB_PROJECTILE.equals(abilityType)) return true;
			return MadokuPetManager.PET_ABILITY_EXPLOSIVE_PROJECTILE.equals(abilityType)
				&& explosionRadius > 0.0F
				&& attackDamage > 0.0F;
		}

		PetAbilityRule atLevel(int level, String petId) {
			double multiplier = 1.0D + ((Math.max(1, level) - 1) * 0.5D);
			double resolvedAttackDamage = attackDamage * multiplier;
			double resolvedPlayerDamageBonus = playerDamageBonusAmount * multiplier;
			double resolvedFallDamageReduction = fallDamageReductionAmount * multiplier;
			double resolvedMaxHealthBonus = maxHealthBonusAmount * multiplier;
			double resolvedArmorBonus = armorBonusAmount * multiplier;
			double resolvedDamageBlock = damageBlockAmount * multiplier;
			double resolvedExplosionRadius = explosionRadius * multiplier;
			long resolvedStunDurationTicks = stunDurationTicks;
			long resolvedSlowDurationTicks = slowDurationTicks;
			double resolvedSlowPercentage = slowPercentage;
			String normalizedPetId = normalizePetId(petId);
			if (MadokuPetManager.PET_ABILITY_WEB_PROJECTILE.equals(abilityType)) {
				resolvedStunDurationTicks += (Math.max(1, level) - 1) * 10L;
				resolvedSlowDurationTicks += (Math.max(1, level) - 1) * 40L;
				resolvedSlowPercentage += (Math.max(1, level) - 1) * 0.05D;
			}
			if ("minecraft:chicken".equals(normalizedPetId)) {
				if (MadokuPetManager.PET_ABILITY_EGG_PROJECTILE.equals(abilityType)) {
					resolvedAttackDamage = attackDamage + ((Math.max(1, level) - 1) * 0.5D);
					resolvedExplosionRadius = explosionRadius + ((Math.max(1, level) - 1) * 0.5D);
				}
				if (MadokuPetManager.PET_ABILITY_FALL_DAMAGE_REDUCTION.equals(abilityType)) {
					resolvedFallDamageReduction = fallDamageReductionAmount + ((Math.max(1, level) - 1) * 0.025D);
				}
			} else if ("minecraft:creeper".equals(normalizedPetId)
				&& MadokuPetManager.PET_ABILITY_EXPLOSIVE_PROJECTILE.equals(abilityType)) {
				resolvedExplosionRadius = explosionRadius + (Math.max(1, level) - 1);
			}
			return new PetAbilityRule(
				abilityType,
				soundVolumeMultiplier,
				(float) resolvedAttackDamage,
				attackSpeed,
				effectDurationTicks,
				resolvedStunDurationTicks,
				resolvedSlowDurationTicks,
				resolvedSlowPercentage,
				resolvedPlayerDamageBonus,
				resolvedFallDamageReduction,
				resolvedMaxHealthBonus,
				resolvedArmorBonus,
				resolvedDamageBlock,
				cooldownTicks,
				shotDelayTicks,
				attackArcStepDegrees,
				attackRearOffset,
				attackRearSpread,
				attackLateralRadius,
				attackVerticalOffset,
				(float) resolvedExplosionRadius,
				soundEventId
			);
		}

		PetAbilityRule withoutCooldown() {
			return new PetAbilityRule(
				abilityType,
				soundVolumeMultiplier,
				attackDamage,
				attackSpeed,
				effectDurationTicks,
				stunDurationTicks,
				slowDurationTicks,
				slowPercentage,
				playerDamageBonusAmount,
				fallDamageReductionAmount,
				maxHealthBonusAmount,
				armorBonusAmount,
				damageBlockAmount,
				0L,
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
	}

	static final class PetRule {
		private static final double DEFAULT_IDLE_MOVE_SPEED = 0.8D;
			private static final double DEFAULT_IDLE_DISTANCE = 4.0D;
			private static final double DEFAULT_IDLE_WANDER_RADIUS = 2.0D;
			private static final long DEFAULT_IDLE_MIN_INTERVAL_TICKS = 20L;
			private static final long DEFAULT_IDLE_MAX_INTERVAL_TICKS = 60L;
			private static final float DEFAULT_SOUND_VOLUME_MULTIPLIER = 0.2F;
			private static final double DEFAULT_ATTACK_ARC_STEP_DEGREES = 18.0D;
			private static final double DEFAULT_ATTACK_REAR_OFFSET = 0.58D;
			private static final double DEFAULT_ATTACK_REAR_SPREAD = 0.20D;
			private static final double DEFAULT_ATTACK_LATERAL_RADIUS = 0.45D;
			private static final double DEFAULT_ATTACK_VERTICAL_OFFSET = -0.34D;

			final boolean enabled;
			final String petId;
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
			final float soundVolumeMultiplier;
			final String abilityType;
			final List<String> abilityTypes;
			final List<PetAbilityRule> abilities;
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
				float soundVolumeMultiplier,
				String abilityType,
				List<String> abilityTypes,
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
				String soundEventId,
				List<PetAbilityRule> abilities
			) {
				this.enabled = enabled;
				this.petId = itemId != null && itemId.startsWith(PetEntitiesManager.PET_ITEM_NAMESPACE + ":")
					? normalizePetId(itemId.substring((PetEntitiesManager.PET_ITEM_NAMESPACE + ":").length()).replaceFirst("-pet$", ""))
					: normalizePetId(itemId);
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
				this.soundVolumeMultiplier = soundVolumeMultiplier;
				this.abilityType = abilityType;
				LinkedHashSet<String> normalizedAbilities = new LinkedHashSet<>();
				if (abilityTypes != null) {
					for (String configuredAbility : abilityTypes) {
						String normalizedAbility = normalizeAbilityId(configuredAbility);
						if (!normalizedAbility.isBlank() && normalizedAbilities.size() < 3) normalizedAbilities.add(normalizedAbility);
					}
				}
				this.abilityTypes = List.copyOf(normalizedAbilities);
				this.abilities = normalizeCooldownAbilities(abilities, itemId);
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

			private static List<PetAbilityRule> normalizeCooldownAbilities(List<PetAbilityRule> configuredAbilities, String itemId) {
				if (configuredAbilities == null || configuredAbilities.isEmpty()) return List.of();
				List<PetAbilityRule> normalized = new ArrayList<>();
				boolean cooldownAssigned = false;
				for (PetAbilityRule ability : configuredAbilities) {
					if (ability == null) continue;
					if (ability.cooldownTicks > 0L) {
						if (cooldownAssigned) {
							LOGGER.warn("Pet {} defines more than one cooldown ability; disabling the cooldown for {}.", itemId, ability.abilityType);
							normalized.add(ability.withoutCooldown());
							continue;
						}
						cooldownAssigned = true;
					}
					normalized.add(ability);
				}
				return List.copyOf(normalized);
			}

			static JsonObject defaultsForEntity(String petId, String... configuredAbilityTypes) {
				String resolvedPetId = normalizePetId(petId);
				LinkedHashSet<String> resolvedAbilities = new LinkedHashSet<>();
				if (configuredAbilityTypes != null) {
					for (String configuredAbilityType : configuredAbilityTypes) {
						String resolvedAbilityType = normalizeAbilityId(configuredAbilityType);
						if (!resolvedAbilityType.isBlank() && resolvedAbilities.size() < 3) resolvedAbilities.add(resolvedAbilityType);
					}
				}
				if (resolvedAbilities.isEmpty()) resolvedAbilities.add(MadokuPetManager.PET_ABILITY_NONE);
				String resolvedAbilityType = resolvedAbilities.iterator().next();
				return madoku.craft.api.json.JSONFormatManager.object()
					.object("pet-id", pet -> {
						pet.put("id", resolvedPetId)
							.put("rarity", defaultRarityForItem(resolvedPetId))
							.put("pet-scale", defaultPetScaleForItem(resolvedPetId))
							.put("follow-speed", 1.2D)
							.put("teleport-distance", 8.0D)
							.put("cooldown", defaultCooldownSecondsForPet(resolvedPetId, resolvedAbilityType));
						pet.array("ability-ids", abilities -> {
							for (String abilityType : resolvedAbilities) abilities.add(abilityConfigId(abilityType));
						});
					})
					.build();
			}

			static JsonObject defaultsForAbility(String abilityType) {
				String resolvedAbilityType = normalizeAbilityId(abilityType);
				boolean usesRangedHomingArrow = MadokuPetManager.PET_ABILITY_RANGED_HOMING_ARROW.equals(resolvedAbilityType);
				boolean usesWebProjectile = MadokuPetManager.PET_ABILITY_WEB_PROJECTILE.equals(resolvedAbilityType);
				boolean usesExplosiveProjectile = MadokuPetManager.PET_ABILITY_EXPLOSIVE_PROJECTILE.equals(resolvedAbilityType);
				boolean usesEggProjectile = MadokuPetManager.PET_ABILITY_EGG_PROJECTILE.equals(resolvedAbilityType);
				boolean usesDamageBlock = MadokuPetManager.PET_ABILITY_DAMAGE_BLOCK.equals(resolvedAbilityType);
				boolean usesMobScan = MadokuPetManager.PET_ABILITY_MOB_SCAN.equals(resolvedAbilityType);
				boolean usesBeeSwarm = MadokuPetManager.PET_ABILITY_BEE_SWARM.equals(resolvedAbilityType);
				String valueType = switch (resolvedAbilityType) {
					case MadokuPetManager.PET_ABILITY_PLAYER_DAMAGE_BONUS, MadokuPetManager.PET_ABILITY_FALL_DAMAGE_REDUCTION, MadokuPetManager.PET_ABILITY_MAX_HEALTH_BONUS -> "percentage";
					default -> "flat";
				};
				JsonObject ability = madoku.craft.api.json.JSONFormatManager.object()
					.put("id", abilityConfigId(resolvedAbilityType))
					.object("value-type", value -> value.put("type", valueType).put("value", defaultAbilityValue(resolvedAbilityType)))
					.build();
				if (usesRangedHomingArrow) {
					ability.addProperty("attack-damage", 3.0D);
					ability.addProperty("attack-speed", 1.5D);
					ability.addProperty("cooldown", 5.0D);
					ability.addProperty("shot-delay-ticks", 10L);
				}
				if (usesWebProjectile) {
					ability.addProperty("follow-speed", 1.0D);
					ability.addProperty("attack-damage", 1.5D);
					ability.addProperty("attack-speed", 1.0D);
					ability.addProperty("effect-duration-ticks", 240L);
					ability.addProperty("stun-duration-ticks", 60L);
					ability.addProperty("slow-duration-ticks", 240L);
					ability.addProperty("slow-percentage", 0.40D);
					ability.addProperty("cooldown", 15.0D);
					ability.addProperty("shot-delay-ticks", 10L);
				}
				if (usesExplosiveProjectile) {
					ability.addProperty("follow-speed", 1.2D);
					ability.addProperty("attack-damage", 12.0D);
					ability.addProperty("attack-speed", 2.0D);
					ability.addProperty("cooldown", 60.0D);
					ability.addProperty("shot-delay-ticks", 10L);
					ability.addProperty("explosion-radius", 4D);
				}
				if (usesEggProjectile) {
					ability.addProperty("follow-speed", 1.0D);
					ability.addProperty("attack-damage", 2.0D);
					ability.addProperty("attack-speed", 1.5D);
					ability.addProperty("cooldown", 30.0D);
					ability.addProperty("shot-delay-ticks", 5L);
					ability.addProperty("explosion-radius", 1.0D);
				}
				if (usesDamageBlock) {
					ability.addProperty("damage-block", 4.0D);
					ability.addProperty("cooldown", 30.0D);
				}
				if (usesMobScan) {
					ability.addProperty("cooldown", 150.0D);
				}
				if (usesBeeSwarm) {
					ability.addProperty("follow-speed", 1.5D);
					ability.addProperty("idle-move-speed", 1.25D);
					ability.addProperty("idle-wander-radius", 4.0D);
					ability.addProperty("attack-damage", 1.5D);
					ability.addProperty("cooldown", 0.0D);
				}
				return madoku.craft.api.json.JSONFormatManager.object().put("ability-id", ability).build();
			}

			private static double defaultAbilityValue(String abilityType) {
				return switch (abilityType) {
					case MadokuPetManager.PET_ABILITY_PLAYER_DAMAGE_BONUS -> 1.25D;
					case MadokuPetManager.PET_ABILITY_FALL_DAMAGE_REDUCTION -> 0.20D;
					case MadokuPetManager.PET_ABILITY_MAX_HEALTH_BONUS -> 0.125D;
					case MadokuPetManager.PET_ABILITY_ARMOR_BONUS -> 2.0D;
					case MadokuPetManager.PET_ABILITY_DAMAGE_BLOCK -> 4.0D;
					case MadokuPetManager.PET_ABILITY_BEE_SWARM -> 1.5D;
					case MadokuPetManager.PET_ABILITY_EGG_PROJECTILE -> 2.0D;
					default -> 0.0D;
				};
			}

			private static double defaultCooldownSeconds(String abilityType) {
				return switch (normalizeAbilityId(abilityType)) {
					case MadokuPetManager.PET_ABILITY_RANGED_HOMING_ARROW -> 5.0D;
					case MadokuPetManager.PET_ABILITY_WEB_PROJECTILE -> 15.0D;
					case MadokuPetManager.PET_ABILITY_EXPLOSIVE_PROJECTILE -> 60.0D;
					case MadokuPetManager.PET_ABILITY_EGG_PROJECTILE -> 30.0D;
					case MadokuPetManager.PET_ABILITY_DAMAGE_BLOCK -> 30.0D;
					case MadokuPetManager.PET_ABILITY_MOB_SCAN -> 150.0D;
					default -> 0.0D;
				};
			}

			private static double defaultCooldownSecondsForPet(String petId, String abilityType) {
				if ("minecraft:chicken".equals(normalizePetId(petId))) return 30.0D;
				return defaultCooldownSeconds(abilityType);
			}

			static PetRule fromJson(JsonObject source, String fileKey, Map<String, JsonObject> abilityDefinitions) {
				if (source == null) {
					return null;
				}

				String petId = resolvePetId(fileKey, source);
				if (petId == null || petId.isBlank()) {
					return null;
				}

				List<String> abilityTypes = resolveAbilityTypes(source);
				String abilityType = abilityTypes.isEmpty() ? MadokuPetManager.PET_ABILITY_NONE : abilityTypes.get(0);
				JsonObject primaryDefinition = abilityDefinitions == null ? null : abilityDefinitions.get(normalizeAbilityId(abilityType));
				JsonObject abilityGroup = objectField(primaryDefinition, "ability-id");
				JsonObject resolvedSource = resolveAbilitySource(source, abilityGroup, abilityType);
				source = resolvedSource;
				String itemId = PetEntitiesManager.PET_ITEM_NAMESPACE + ":" + petItemPath(petId);
				String rarity = normalizePetRarity(
					getString(source, "rarity", MadokuPetManager.PET_RARITY_COMMON)
				);
				double petScale = PetSettings.clampDouble(
					getDouble(source, "pet-scale", defaultPetScaleForItem(itemId)),
					0.01D,
					4.0D
				);
				abilityType = normalizeAbilityId(getString(source, "ability", abilityType));
				double followSpeed = PetSettings.clampDouble(getDouble(source, "follow-speed", 1.2D), 0.05D, 4.0D);
				double idleMoveSpeed = PetSettings.clampDouble(
					getDouble(source, "idle-move-speed", defaultIdleMoveSpeedForAbility(abilityType)),
					0.05D,
					4.0D
				);
				double idleDistance = PetSettings.clampDouble(getDouble(source, "idle-distance", DEFAULT_IDLE_DISTANCE), 0.5D, 32.0D);
				double teleportDistance = PetSettings.clampDouble(getDouble(source, "teleport-distance", 8.0D), idleDistance, 64.0D);
				double idleWanderRadius = PetSettings.clampDouble(getDouble(source, "idle-wander-radius", DEFAULT_IDLE_WANDER_RADIUS), 0.0D, 16.0D);
				long idleMinIntervalTicks = PetSettings.clampLong(getLong(source, "idle-min-interval-ticks", DEFAULT_IDLE_MIN_INTERVAL_TICKS), 1L, 20L * 60L);
				long idleMaxIntervalTicks = PetSettings.clampLong(getLong(source, "idle-max-interval-ticks", DEFAULT_IDLE_MAX_INTERVAL_TICKS), idleMinIntervalTicks, 20L * 60L);
				float soundVolumeMultiplier = (float) PetSettings.clampDouble(getDouble(source, "sound-volume-multiplier", DEFAULT_SOUND_VOLUME_MULTIPLIER), 0.0D, 4.0D);
				float attackDamage = (float) PetSettings.clampDouble(getDouble(source, "attack-damage", 0.0D), 0.0D, 1024.0D);
				float attackSpeed = (float) PetSettings.clampDouble(getDouble(source, "attack-speed", 0.0D), 0.05D, 8.0D);
				long effectDurationTicks = PetSettings.clampLong(getLong(source, "effect-duration-ticks", 0L), 0L, 20L * 60L);
				double playerDamageBonusAmount = PetSettings.clampDouble(getDouble(source, "player-damage-bonus", 0.0D), 0.0D, 1024.0D);
				double fallDamageReductionAmount = PetSettings.clampDouble(getDouble(source, "fall-damage-reduction", 0.0D), 0.0D, 1.0D);
				double maxHealthBonusAmount = PetSettings.clampDouble(getDouble(source, "max-health-bonus", 0.0D), 0.0D, 10.0D);
				double armorBonusAmount = PetSettings.clampDouble(getDouble(source, "armor-bonus", 0.0D), 0.0D, 1024.0D);
				double damageBlockAmount = PetSettings.clampDouble(getDouble(source, "damage-block", 0.0D), 0.0D, 1024.0D);
				long cooldownTicks = PetSettings.clampLong(getLong(source, "cooldown-ticks", 0L), 0L, 20L * 60L * 60L);
				long shotDelayTicks = PetSettings.clampLong(getLong(source, "shot-delay-ticks", 0L), 0L, 20L * 60L);
				double attackArcStepDegrees = PetSettings.clampDouble(
					getDouble(source, "attack-arc-step-degrees", defaultAttackArcStepDegreesForAbility(abilityType)),
					0.0D,
					90.0D
				);
				double attackRearOffset = PetSettings.clampDouble(
					getDouble(source, "attack-rear-offset", defaultAttackRearOffsetForAbility(abilityType)),
					0.0D,
					4.0D
				);
				double attackRearSpread = PetSettings.clampDouble(
					getDouble(source, "attack-rear-spread", defaultAttackRearSpreadForAbility(abilityType)),
					0.0D,
					4.0D
				);
				double attackLateralRadius = PetSettings.clampDouble(
					getDouble(source, "attack-lateral-radius", defaultAttackLateralRadiusForAbility(abilityType)),
					0.0D,
					4.0D
				);
				double attackVerticalOffset = PetSettings.clampDouble(
					getDouble(source, "attack-vertical-offset", defaultAttackVerticalOffsetForAbility(abilityType)),
					-4.0D,
					4.0D
				);
				float explosionRadius = (float) PetSettings.clampDouble(getDouble(source, "explosion-radius", 4.0D), 0.25D, 12.0D);
				String soundEventId = getString(source, "sound-event", defaultSoundEventIdForAbility(abilityType));
				List<PetAbilityRule> abilities = parseAbilityRules(source, petId, abilityTypes, abilityDefinitions);
				return new PetRule(
					getBoolean(source, "enabled", true),
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
					soundVolumeMultiplier,
					abilityType.isBlank() ? MadokuPetManager.PET_ABILITY_NONE : abilityType,
					abilityTypes,
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
					soundEventId,
					abilities
				);
			}

			private static List<PetAbilityRule> parseAbilityRules(
				JsonObject source,
				String petId,
				List<String> abilityTypes,
				Map<String, JsonObject> abilityDefinitions
			) {
				List<PetAbilityRule> abilities = new ArrayList<>();
				for (String abilityType : abilityTypes) {
					JsonObject definition = abilityDefinitions == null ? null : abilityDefinitions.get(normalizeAbilityId(abilityType));
					JsonObject abilityGroup = objectField(definition, "ability-id");
					JsonObject resolvedSource = resolveAbilitySource(source, abilityGroup, abilityType);
					abilities.add(new PetAbilityRule(
						abilityType,
						(float) PetSettings.clampDouble(getDouble(resolvedSource, "sound-volume-multiplier", 0.2D), 0.0D, 4.0D),
						(float) PetSettings.clampDouble(getDouble(resolvedSource, "attack-damage", 0.0D), 0.0D, 1024.0D),
						(float) PetSettings.clampDouble(getDouble(resolvedSource, "attack-speed", 0.0D), 0.05D, 8.0D),
						PetSettings.clampLong(getLong(resolvedSource, "effect-duration-ticks", 0L), 0L, 20L * 60L),
						PetSettings.clampLong(getLong(resolvedSource, "stun-duration-ticks", defaultStunDurationTicksForAbility(abilityType)), 0L, 20L * 60L),
						PetSettings.clampLong(getLong(resolvedSource, "slow-duration-ticks", defaultSlowDurationTicksForAbility(abilityType)), 0L, 20L * 60L * 10L),
						PetSettings.clampDouble(getDouble(resolvedSource, "slow-percentage", defaultSlowPercentageForAbility(abilityType)), 0.0D, 1.0D),
						PetSettings.clampDouble(getDouble(resolvedSource, "player-damage-bonus", 0.0D), 0.0D, 1024.0D),
						PetSettings.clampDouble(getDouble(resolvedSource, "fall-damage-reduction", 0.0D), 0.0D, 1.0D),
						PetSettings.clampDouble(getDouble(resolvedSource, "max-health-bonus", 0.0D), 0.0D, 10.0D),
						PetSettings.clampDouble(getDouble(resolvedSource, "armor-bonus", 0.0D), 0.0D, 1024.0D),
						PetSettings.clampDouble(getDouble(resolvedSource, "damage-block", 0.0D), 0.0D, 1024.0D),
						PetSettings.clampLong(getLong(resolvedSource, "cooldown-ticks", 0L), 0L, 20L * 60L * 60L),
						PetSettings.clampLong(getLong(resolvedSource, "shot-delay-ticks", 0L), 0L, 20L * 60L),
						PetSettings.clampDouble(getDouble(resolvedSource, "attack-arc-step-degrees", defaultAttackArcStepDegreesForAbility(abilityType)), 0.0D, 90.0D),
						PetSettings.clampDouble(getDouble(resolvedSource, "attack-rear-offset", defaultAttackRearOffsetForAbility(abilityType)), 0.0D, 4.0D),
						PetSettings.clampDouble(getDouble(resolvedSource, "attack-rear-spread", defaultAttackRearSpreadForAbility(abilityType)), 0.0D, 4.0D),
						PetSettings.clampDouble(getDouble(resolvedSource, "attack-lateral-radius", defaultAttackLateralRadiusForAbility(abilityType)), 0.0D, 4.0D),
						PetSettings.clampDouble(getDouble(resolvedSource, "attack-vertical-offset", defaultAttackVerticalOffsetForAbility(abilityType)), -4.0D, 4.0D),
						(float) PetSettings.clampDouble(getDouble(resolvedSource, "explosion-radius", defaultExplosionRadiusForAbility(abilityType)), 0.0D, 12.0D),
						getString(resolvedSource, "sound-event", defaultSoundEventIdForAbility(abilityType))
					));
				}
				return List.copyOf(abilities);
			}

			private static double defaultExplosionRadiusForAbility(String abilityType) {
				if (MadokuPetManager.PET_ABILITY_EXPLOSIVE_PROJECTILE.equals(abilityType)) return 4.0D;
				if (MadokuPetManager.PET_ABILITY_EGG_PROJECTILE.equals(abilityType)) return 1.0D;
				return 0.0D;
			}

			private static long defaultStunDurationTicksForAbility(String abilityType) {
				return MadokuPetManager.PET_ABILITY_WEB_PROJECTILE.equals(abilityType) ? 60L : 0L;
			}

			private static long defaultSlowDurationTicksForAbility(String abilityType) {
				return MadokuPetManager.PET_ABILITY_WEB_PROJECTILE.equals(abilityType) ? 240L : 0L;
			}

			private static double defaultSlowPercentageForAbility(String abilityType) {
				return MadokuPetManager.PET_ABILITY_WEB_PROJECTILE.equals(abilityType) ? 0.40D : 0.0D;
			}

			private static JsonObject resolveAbilitySource(JsonObject source, JsonObject abilityGroup, String abilityType) {
				JsonObject petGroup = objectField(source, "pet-id");
				JsonObject flattened = source.deepCopy();
				flattened.remove("pet-id");
				for (Map.Entry<String, com.google.gson.JsonElement> entry : petGroup.entrySet()) {
					if ("id".equals(entry.getKey()) || "ability-ids".equals(entry.getKey())) continue;
					flattened.add(entry.getKey(), entry.getValue().deepCopy());
				}
				for (Map.Entry<String, com.google.gson.JsonElement> entry : abilityGroup.entrySet()) {
					if ("id".equals(entry.getKey()) || "value-type".equals(entry.getKey())) continue;
					if (!flattened.has(entry.getKey())) flattened.add(entry.getKey(), entry.getValue().deepCopy());
				}
				JsonObject valueType = objectField(abilityGroup, "value-type");
				double value = getDouble(valueType, "value", 0.0D);
				String valueKey = switch (abilityType) {
					case MadokuPetManager.PET_ABILITY_PLAYER_DAMAGE_BONUS -> "player-damage-bonus";
					case MadokuPetManager.PET_ABILITY_FALL_DAMAGE_REDUCTION -> "fall-damage-reduction";
					case MadokuPetManager.PET_ABILITY_MAX_HEALTH_BONUS -> "max-health-bonus";
					case MadokuPetManager.PET_ABILITY_ARMOR_BONUS -> "armor-bonus";
					case MadokuPetManager.PET_ABILITY_DAMAGE_BLOCK -> "damage-block";
					case MadokuPetManager.PET_ABILITY_BEE_SWARM -> "attack-damage";
					default -> null;
				};
				if (valueKey != null && !flattened.has(valueKey)) flattened.addProperty(valueKey, value);
				double abilityCooldownSeconds = getDouble(abilityGroup, "cooldown", 0.0D);
				double petCooldownSeconds = getDouble(petGroup, "cooldown", -1.0D);
				double cooldownSeconds = abilityCooldownSeconds > 0.0D && petCooldownSeconds >= 0.0D
					? petCooldownSeconds
					: abilityCooldownSeconds;
				flattened.addProperty("cooldown-ticks", Math.max(0L, Math.round(cooldownSeconds * 20.0D)));
				flattened.addProperty("ability", abilityType);
				return flattened;
			}

			boolean canPerformReactiveAttack() {
			if (!enabled) return false;
			for (PetAbilityRule ability : abilities) {
				if (ability.canPerformReactiveAttack()) return true;
			}
			return false;
		}

		List<PetAbilityRule> reactiveAbilities() {
			List<PetAbilityRule> reactive = new ArrayList<>();
			for (PetAbilityRule ability : abilities) {
				if (ability.canPerformReactiveAttack()) reactive.add(ability);
			}
			return List.copyOf(reactive);
		}

			PetAbilityRule ability(String requestedAbilityType) {
				String normalized = normalizeAbilityId(requestedAbilityType);
				for (PetAbilityRule ability : abilities) {
					if (ability.abilityType.equals(normalized)) return ability;
				}
				return null;
			}

			double playerDamageBonus() {
			PetAbilityRule ability = ability(MadokuPetManager.PET_ABILITY_PLAYER_DAMAGE_BONUS);
			return ability == null ? 0.0D : ability.playerDamageBonusAmount;
			}

			double fallDamageReduction() {
			PetAbilityRule ability = ability(MadokuPetManager.PET_ABILITY_FALL_DAMAGE_REDUCTION);
			return ability == null ? 0.0D : ability.fallDamageReductionAmount;
			}

			double maxHealthBonus() {
			PetAbilityRule ability = ability(MadokuPetManager.PET_ABILITY_MAX_HEALTH_BONUS);
			return ability == null ? 0.0D : ability.maxHealthBonusAmount;
			}

			double armorBonus() {
			PetAbilityRule ability = ability(MadokuPetManager.PET_ABILITY_ARMOR_BONUS);
			return ability == null ? 0.0D : ability.armorBonusAmount;
			}

			double damageBlockAmount() {
			PetAbilityRule ability = ability(MadokuPetManager.PET_ABILITY_DAMAGE_BLOCK);
			return ability == null ? 0.0D : ability.damageBlockAmount;
			}

			boolean canBlockIncomingDamage() {
			PetAbilityRule ability = ability(MadokuPetManager.PET_ABILITY_DAMAGE_BLOCK);
			return ability != null && ability.damageBlockAmount > 0.0D && ability.cooldownTicks > 0L;
			}

			List<String> abilityDescriptions() {
				if (!enabled) {
					return List.of();
				}
				List<String> descriptions = new ArrayList<>();
				for (PetAbilityRule ability : abilities) {
					String configuredAbility = ability.abilityType;
					if (MadokuPetManager.PET_ABILITY_RANGED_HOMING_ARROW.equals(configuredAbility) && ability.attackDamage > 0.0F) {
						descriptions.add("Active: Fires a homing arrow at a target for " + MadokuPetManager.formatAbilityAmount(ability.attackDamage) + " damage.");
					} else if (MadokuPetManager.PET_ABILITY_WEB_PROJECTILE.equals(configuredAbility) && ability.attackDamage > 0.0F) {
						descriptions.add("Active: Launches a web projectile that stuns enemies for "
							+ MadokuPetManager.formatCooldownSeconds(ability.stunDurationTicks) + "s and deals "
							+ MadokuPetManager.formatAbilityAmount(ability.attackDamage) + " damage.");
					} else if (MadokuPetManager.PET_ABILITY_EXPLOSIVE_PROJECTILE.equals(configuredAbility) && ability.attackDamage > 0.0F && ability.explosionRadius > 0.0F) {
						descriptions.add("Active: Fires an explosive projectile for " + MadokuPetManager.formatAbilityAmount(ability.attackDamage)
							+ " damage within a " + MadokuPetManager.formatAbilityAmount(ability.explosionRadius) + " radius.");
					} else if (MadokuPetManager.PET_ABILITY_EGG_PROJECTILE.equals(configuredAbility) && ability.attackDamage > 0.0F && ability.explosionRadius > 0.0F) {
						descriptions.add("Active: Fires egg projectiles for " + MadokuPetManager.formatAbilityAmount(ability.attackDamage)
							+ " damage within " + MadokuPetManager.formatAbilityAmount(ability.explosionRadius) + " radius.");
					} else if (MadokuPetManager.PET_ABILITY_PLAYER_DAMAGE_BONUS.equals(configuredAbility) && ability.playerDamageBonusAmount > 0.0D) {
						descriptions.add("Passive: Increases damage by " + MadokuPetManager.formatAbilityAmount(ability.playerDamageBonusAmount) + ".");
					} else if (MadokuPetManager.PET_ABILITY_FALL_DAMAGE_REDUCTION.equals(configuredAbility) && ability.fallDamageReductionAmount > 0.0D) {
						descriptions.add("Passive: Reduces fall damage by " + MadokuPetManager.formatPercent(ability.fallDamageReductionAmount) + ".");
					} else if (MadokuPetManager.PET_ABILITY_MAX_HEALTH_BONUS.equals(configuredAbility) && ability.maxHealthBonusAmount > 0.0D) {
						descriptions.add("Passive: Increases max health by " + MadokuPetManager.formatPercent(ability.maxHealthBonusAmount) + ".");
					} else if (MadokuPetManager.PET_ABILITY_ARMOR_BONUS.equals(configuredAbility) && ability.armorBonusAmount > 0.0D) {
						descriptions.add("Passive: Increases armor by " + MadokuPetManager.formatAbilityAmount(ability.armorBonusAmount) + ".");
					} else if (MadokuPetManager.PET_ABILITY_DAMAGE_BLOCK.equals(configuredAbility) && ability.damageBlockAmount > 0.0D) {
						descriptions.add("Active: Blocks " + MadokuPetManager.formatAbilityAmount(ability.damageBlockAmount) + " incoming damage.");
					} else if (MadokuPetManager.PET_ABILITY_MOB_SCAN.equals(configuredAbility)) {
						descriptions.add("Automatic: Periodically reveals nearby mobs.");
					} else if (MadokuPetManager.PET_ABILITY_BEE_SWARM.equals(configuredAbility) && ability.attackDamage > 0.0F) {
						descriptions.add("Automatic: Swarms nearby hostile mobs for " + MadokuPetManager.formatAbilityAmount(ability.attackDamage) + " damage per hit.");
					}
				}
				return List.copyOf(descriptions);
			}

		String cooldownDescription() {
			List<String> descriptions = cooldownDescriptions();
			return descriptions.isEmpty() ? "" : descriptions.get(0);
		}

		List<String> cooldownDescriptions() {
			if (!enabled) return List.of();
			for (PetAbilityRule ability : abilities) {
				if (ability.cooldownTicks > 0L) {
					return List.of("Cooldown: " + MadokuPetManager.formatCooldownSeconds(ability.cooldownTicks) + "s");
				}
			}
			return List.of();
		}

			String cooldownDescription(long resolvedCooldownTicks) {
				if (!enabled || resolvedCooldownTicks <= 0L || MadokuPetManager.PET_ABILITY_NONE.equals(abilityType)) {
					return "";
				}
				return "Cooldown: " + MadokuPetManager.formatCooldownSeconds(resolvedCooldownTicks) + "s";
			}

		boolean hasAbility() {
			return enabled && !abilities.isEmpty() && !MadokuPetManager.PET_ABILITY_NONE.equals(abilities.get(0).abilityType);
		}

		boolean hasAbility(String requestedAbilityType) {
				String normalized = normalizeAbilityId(requestedAbilityType);
			return enabled && !normalized.isBlank() && abilityTypes.contains(normalized);
		}

		long minimumCooldownTicks() {
			long minimum = Long.MAX_VALUE;
			for (PetAbilityRule ability : abilities) {
				if (ability.cooldownTicks > 0L) minimum = Math.min(minimum, ability.cooldownTicks);
			}
			return minimum == Long.MAX_VALUE ? 0L : minimum;
		}

			PetRule atLevel(int level) {
				int safeLevel = Math.max(1, Math.min(PetConfigManager.maxPetLevel(), level));
				if (safeLevel == 1) return this;
				double multiplier = 1.0D + ((safeLevel - 1) * 0.5D);
				double resolvedAttackDamage = attackDamage * multiplier;
				double resolvedFallDamageReduction = fallDamageReductionAmount * multiplier;
				double resolvedExplosionRadius = explosionRadius * multiplier;
				if ("minecraft:chicken".equals(petId)) {
					resolvedAttackDamage = attackDamage + ((safeLevel - 1) * 0.5D);
					resolvedFallDamageReduction = fallDamageReductionAmount + ((safeLevel - 1) * 0.025D);
					resolvedExplosionRadius = explosionRadius + ((safeLevel - 1) * 0.5D);
				} else if ("minecraft:creeper".equals(petId)) {
					resolvedExplosionRadius = explosionRadius + (safeLevel - 1);
				}
				List<PetAbilityRule> resolvedAbilities = new ArrayList<>();
				for (PetAbilityRule ability : abilities) {
					resolvedAbilities.add(ability.atLevel(safeLevel, petId));
				}
				return new PetRule(
					enabled, itemId, rarity, petScale, followSpeed, idleMoveSpeed, idleDistance, teleportDistance,
					idleWanderRadius, idleMinIntervalTicks, idleMaxIntervalTicks,
					soundVolumeMultiplier, abilityType, abilityTypes, (float) resolvedAttackDamage, attackSpeed,
					effectDurationTicks, playerDamageBonusAmount * multiplier, resolvedFallDamageReduction,
					maxHealthBonusAmount * multiplier, armorBonusAmount * multiplier, damageBlockAmount * multiplier,
					cooldownTicks, shotDelayTicks, attackArcStepDegrees, attackRearOffset, attackRearSpread,
					attackLateralRadius, attackVerticalOffset, (float) resolvedExplosionRadius, soundEventId,
					resolvedAbilities
				);
			}

		SoundEvent resolveSoundEvent() {
		return resolveSoundEvent(abilityType);
		}

		SoundEvent resolveSoundEvent(String requestedAbilityType) {
		PetAbilityRule ability = ability(requestedAbilityType);
		String configuredSoundEventId = ability == null ? soundEventId : ability.soundEventId;
		Identifier identifier = Identifier.tryParse(configuredSoundEventId == null ? "" : configuredSoundEventId.trim());
		if (identifier == null) return defaultSoundEvent(requestedAbilityType);
		SoundEvent soundEvent = BuiltInRegistries.SOUND_EVENT.getValue(identifier);
		return soundEvent == null ? defaultSoundEvent(requestedAbilityType) : soundEvent;
		}

		SoundEvent defaultSoundEvent() {
			return defaultSoundEvent(abilityType);
		}

		SoundEvent defaultSoundEvent(String requestedAbilityType) {
			String resolvedAbilityType = normalizeAbilityId(requestedAbilityType);
			if (MadokuPetManager.PET_ABILITY_MOB_SCAN.equals(resolvedAbilityType)) return SoundEvents.BEACON_ACTIVATE;
			if (MadokuPetManager.PET_ABILITY_WEB_PROJECTILE.equals(resolvedAbilityType)) return SoundEvents.LLAMA_SPIT;
			if (MadokuPetManager.PET_ABILITY_EXPLOSIVE_PROJECTILE.equals(resolvedAbilityType)) return SoundEvents.CREEPER_PRIMED;
			if (MadokuPetManager.PET_ABILITY_BEE_SWARM.equals(resolvedAbilityType)) return SoundEvents.BEE_LOOP;
			if (MadokuPetManager.PET_ABILITY_EGG_PROJECTILE.equals(resolvedAbilityType)) return SoundEvents.EGG_THROW;
			return SoundEvents.SKELETON_SHOOT;
		}

		private static String defaultSoundEventIdForAbility(String abilityType) {
			if (MadokuPetManager.PET_ABILITY_MOB_SCAN.equals(abilityType)) return BuiltInRegistries.SOUND_EVENT.getKey(SoundEvents.BEACON_ACTIVATE).toString();
			if (MadokuPetManager.PET_ABILITY_WEB_PROJECTILE.equals(abilityType)) return BuiltInRegistries.SOUND_EVENT.getKey(SoundEvents.LLAMA_SPIT).toString();
			if (MadokuPetManager.PET_ABILITY_EXPLOSIVE_PROJECTILE.equals(abilityType)) return BuiltInRegistries.SOUND_EVENT.getKey(SoundEvents.CREEPER_PRIMED).toString();
			if (MadokuPetManager.PET_ABILITY_BEE_SWARM.equals(abilityType)) return BuiltInRegistries.SOUND_EVENT.getKey(SoundEvents.BEE_LOOP).toString();
			if (MadokuPetManager.PET_ABILITY_EGG_PROJECTILE.equals(abilityType)) return BuiltInRegistries.SOUND_EVENT.getKey(SoundEvents.EGG_THROW).toString();
			return BuiltInRegistries.SOUND_EVENT.getKey(SoundEvents.SKELETON_SHOOT).toString();
		}

			private static double defaultIdleMoveSpeedForAbility(String abilityType) {
				if (MadokuPetManager.PET_ABILITY_WEB_PROJECTILE.equals(abilityType)) {
					return 0.75D;
				}
				if (MadokuPetManager.PET_ABILITY_BEE_SWARM.equals(abilityType)) {
					return 1.2D;
				}
				return DEFAULT_IDLE_MOVE_SPEED;
			}

			private static double defaultAttackArcStepDegreesForAbility(String abilityType) {
				if (MadokuPetManager.PET_ABILITY_WEB_PROJECTILE.equals(abilityType)) {
					return 12.0D;
				}
				if (MadokuPetManager.PET_ABILITY_EXPLOSIVE_PROJECTILE.equals(abilityType)) {
					return 10.0D;
				}
				return DEFAULT_ATTACK_ARC_STEP_DEGREES;
			}

			private static double defaultAttackRearOffsetForAbility(String abilityType) {
				if (MadokuPetManager.PET_ABILITY_WEB_PROJECTILE.equals(abilityType)) {
					return 0.50D;
				}
				if (MadokuPetManager.PET_ABILITY_EXPLOSIVE_PROJECTILE.equals(abilityType)) {
					return 0.46D;
				}
				return DEFAULT_ATTACK_REAR_OFFSET;
			}

			private static double defaultAttackRearSpreadForAbility(String abilityType) {
				if (MadokuPetManager.PET_ABILITY_WEB_PROJECTILE.equals(abilityType)) {
					return 0.15D;
				}
				if (MadokuPetManager.PET_ABILITY_EXPLOSIVE_PROJECTILE.equals(abilityType)) {
					return 0.12D;
				}
				return DEFAULT_ATTACK_REAR_SPREAD;
			}

			private static double defaultAttackLateralRadiusForAbility(String abilityType) {
				if (MadokuPetManager.PET_ABILITY_WEB_PROJECTILE.equals(abilityType)) {
					return 0.38D;
				}
				if (MadokuPetManager.PET_ABILITY_EXPLOSIVE_PROJECTILE.equals(abilityType)) {
					return 0.35D;
				}
				return DEFAULT_ATTACK_LATERAL_RADIUS;
			}

			private static double defaultAttackVerticalOffsetForAbility(String abilityType) {
				if (MadokuPetManager.PET_ABILITY_WEB_PROJECTILE.equals(abilityType)) {
					return -0.28D;
				}
				if (MadokuPetManager.PET_ABILITY_EXPLOSIVE_PROJECTILE.equals(abilityType)) {
					return -0.26D;
				}
				return DEFAULT_ATTACK_VERTICAL_OFFSET;
			}
	}
}
