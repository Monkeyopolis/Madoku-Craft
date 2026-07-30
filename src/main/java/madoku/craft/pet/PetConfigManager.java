package madoku.craft.pet;

import com.google.gson.JsonObject;
import madoku.craft.api.json.JSONFormatManager;
import madoku.craft.api.json.MadokuJSONManager;
import net.minecraft.world.item.ItemStack;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.resources.Identifier;
import net.minecraft.world.entity.EntityType;
import net.minecraft.sounds.SoundEvent;
import net.minecraft.sounds.SoundEvents;

import java.io.IOException;
import java.nio.file.Path;
import java.util.Map;

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

	static String canonicalPetId(String value) {
		String normalized = normalizeKey(value);
		if (!normalized.contains(":")) normalized = "minecraft:" + normalized;
		return normalized.replace('-', '_');
	}

	static String normalizeAbilityId(String value) {
		return normalizeKey(value).replace('-', '_');
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
		if (entity == null) {
			return null;
		}
		String syncedItemId = normalizeKey(PetPayloadManager.soundItemId(entity.getUUID()));
		if (!syncedItemId.isBlank()) {
			return resolvePetRule(syncedItemId);
		}
		String itemId = PetEntitiesManager.getManagedPetItemId(entity);
		return itemId.isBlank() ? null : resolvePetRule(itemId);
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
		if ("minecraft:bat".equals(normalizedItemId)) return 0.4D;
		if ("minecraft:bee".equals(normalizedItemId)) return 0.5D;
		if ("minecraft:chicken".equals(normalizedItemId)) return 0.3D;
		return 0.25D;
	}

	static String defaultRarityForItem(String itemId) {
		String normalizedItemId = normalizePetId(itemId);
		if ("minecraft:bee".equals(normalizedItemId) || "minecraft:bat".equals(normalizedItemId)) return MadokuPetManager.PET_RARITY_EPIC;
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

	static EntityType<?> resolvePetEntityType(String petId) {
		Identifier identifier = Identifier.tryParse(canonicalPetId(petId));
		return identifier == null ? null : BuiltInRegistries.ENTITY_TYPE.getValue(identifier);
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
			case MadokuPetManager.PET_RARITY_COMMON, MadokuPetManager.PET_RARITY_RARE, MadokuPetManager.PET_RARITY_EPIC, MadokuPetManager.PET_RARITY_LEGENDARY -> normalizeKey(rawRarity);
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
			final long schedulerTickInterval;
			final int petRarityCommonChanceWeight;
			final int petRarityRareChanceWeight;
			final int petRarityEpicChanceWeight;
		final int petRarityLegendaryChanceWeight;
		final int maxLevel;

			PetSettings(
				boolean enabled,
				boolean entitiesEnabled,
				long schedulerTickInterval,
				int petRarityCommonChanceWeight,
				int petRarityRareChanceWeight,
				int petRarityEpicChanceWeight,
				int petRarityLegendaryChanceWeight,
				int maxLevel
			) {
				this.enabled = enabled;
				this.entitiesEnabled = entitiesEnabled;
				this.schedulerTickInterval = schedulerTickInterval;
				this.petRarityCommonChanceWeight = petRarityCommonChanceWeight;
				this.petRarityRareChanceWeight = petRarityRareChanceWeight;
				this.petRarityEpicChanceWeight = petRarityEpicChanceWeight;
				this.petRarityLegendaryChanceWeight = petRarityLegendaryChanceWeight;
				this.maxLevel = maxLevel;
			}

			static PetSettings defaults() {
				return new PetSettings(
					true,
					true,
					5L,
					67,
					24,
					8,
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
				long schedulerTickInterval = clampLong(
					getLong(source, "scheduler-tick-interval", defaults.schedulerTickInterval),
					1L,
					20L
				);
				return new PetSettings(
					enabled,
					entitiesEnabled,
					schedulerTickInterval,
					petRarityCommonChanceWeight,
					petRarityRareChanceWeight,
					petRarityEpicChanceWeight,
					petRarityLegendaryChanceWeight,
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
						.put("legendary", petRarityLegendaryChanceWeight))
					.put("scheduler-tick-interval", schedulerTickInterval)
					.build();
			}

			static long clampLong(long value, long min, long max) {
				return Math.max(min, Math.min(max, value));
			}

			static double clampDouble(double value, double min, double max) {
				return Math.max(min, Math.min(max, value));
			}
	}

	static final class PetRule {
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
				this.petId = itemId != null && itemId.startsWith("madoku:")
					? normalizePetId(itemId.substring("madoku:".length()).replaceFirst("-pet$", ""))
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

			static JsonObject defaultsForEntity(String petId, String abilityType) {
				String resolvedPetId = normalizePetId(petId);
				String resolvedAbilityType = normalizeAbilityId(abilityType);
				return madoku.craft.api.json.JSONFormatManager.object()
					.object("pet-id", pet -> pet
						.put("id", resolvedPetId)
						.put("rarity", defaultRarityForItem(resolvedPetId))
						.put("ability-id", resolvedAbilityType.isBlank() ? MadokuPetManager.PET_ABILITY_NONE : abilityConfigId(resolvedAbilityType))
						.put("pet-scale", defaultPetScaleForItem(resolvedPetId))
						.put("follow-speed", 1.2D)
						.put("teleport-distance", 8.0D)
						.put("cooldown", defaultCooldownSeconds(resolvedAbilityType)))
					.build();
			}

			static JsonObject defaultsForAbility(String abilityType) {
				String resolvedAbilityType = normalizeAbilityId(abilityType);
				boolean usesRangedHomingArrow = MadokuPetManager.PET_ABILITY_RANGED_HOMING_ARROW.equals(resolvedAbilityType);
				boolean usesWebProjectile = MadokuPetManager.PET_ABILITY_WEB_PROJECTILE.equals(resolvedAbilityType);
				boolean usesExplosiveProjectile = MadokuPetManager.PET_ABILITY_EXPLOSIVE_PROJECTILE.equals(resolvedAbilityType);
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
					ability.addProperty("effect-duration-ticks", 100L);
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
				if (usesDamageBlock) {
					ability.addProperty("damage-block", 4.0D);
					ability.addProperty("cooldown", 30.0D);
				}
				if (usesMobScan) {
					ability.addProperty("cooldown", 180.0D);
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
					default -> 0.0D;
				};
			}

			private static double defaultCooldownSeconds(String abilityType) {
				return switch (normalizeAbilityId(abilityType)) {
					case MadokuPetManager.PET_ABILITY_RANGED_HOMING_ARROW -> 5.0D;
					case MadokuPetManager.PET_ABILITY_WEB_PROJECTILE -> 15.0D;
					case MadokuPetManager.PET_ABILITY_EXPLOSIVE_PROJECTILE -> 60.0D;
					case MadokuPetManager.PET_ABILITY_DAMAGE_BLOCK -> 30.0D;
					case MadokuPetManager.PET_ABILITY_MOB_SCAN -> 180.0D;
					default -> 0.0D;
				};
			}

			static PetRule fromJson(JsonObject source, String fileKey) {
				return fromJson(source, fileKey, null);
			}

			static PetRule fromJson(JsonObject source, String fileKey, JsonObject abilityDefinition) {
				if (source == null) {
					return null;
				}

				String petId = resolvePetId(fileKey, source);
				if (petId == null || petId.isBlank()) {
					return null;
				}
				JsonObject petGroup = objectField(source, "pet-id");
				JsonObject abilityGroup = objectField(abilityDefinition, "ability-id");
				String abilityType = normalizeAbilityId(getString(petGroup, "ability-id", getString(abilityGroup, "id", MadokuPetManager.PET_ABILITY_NONE)));
				JsonObject flattened = flattenRuleSource(source, abilityGroup, abilityType);
				source = flattened;
				String itemId = "madoku:" + petItemPath(petId);
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
				int ambientSoundIntervalMultiplier = (int) PetSettings.clampLong(getLong(source, "ambient-sound-interval-multiplier", DEFAULT_AMBIENT_SOUND_INTERVAL_MULTIPLIER), 1L, 20L);
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
				String soundEventId = getString(
					source,
					"sound-event",
					defaultSoundEventIdForAbility(abilityType)
				);
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
					ambientSoundIntervalMultiplier,
					soundVolumeMultiplier,
					abilityType.isBlank() ? MadokuPetManager.PET_ABILITY_NONE : abilityType,
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

			private static JsonObject flattenRuleSource(JsonObject source, JsonObject abilityGroup, String abilityType) {
				JsonObject petGroup = objectField(source, "pet-id");
				JsonObject flattened = source.deepCopy();
				flattened.remove("pet-id");
				for (Map.Entry<String, com.google.gson.JsonElement> entry : petGroup.entrySet()) {
					if ("id".equals(entry.getKey()) || "ability-id".equals(entry.getKey())) continue;
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
				double cooldownSeconds = getDouble(petGroup, "cooldown", getDouble(abilityGroup, "cooldown", 0.0D));
				flattened.addProperty("cooldown-ticks", Math.max(0L, Math.round(cooldownSeconds * 20.0D)));
				flattened.addProperty("ability", abilityType);
				return flattened;
			}

			boolean canPerformReactiveAttack() {
				if (!enabled || attackSpeed <= 0.0F || cooldownTicks <= 0L) {
					return false;
				}
				if (MadokuPetManager.PET_ABILITY_RANGED_HOMING_ARROW.equals(abilityType)) {
					return attackDamage > 0.0F;
				}
				if (MadokuPetManager.PET_ABILITY_WEB_PROJECTILE.equals(abilityType)) {
					return true;
				}
				return MadokuPetManager.PET_ABILITY_EXPLOSIVE_PROJECTILE.equals(abilityType) && explosionRadius > 0.0F && attackDamage > 0.0F;
			}

			double playerDamageBonus() {
				return enabled && MadokuPetManager.PET_ABILITY_PLAYER_DAMAGE_BONUS.equals(abilityType) ? playerDamageBonusAmount : 0.0D;
			}

			double fallDamageReduction() {
				return enabled && MadokuPetManager.PET_ABILITY_FALL_DAMAGE_REDUCTION.equals(abilityType) ? fallDamageReductionAmount : 0.0D;
			}

			double maxHealthBonus() {
				return enabled && MadokuPetManager.PET_ABILITY_MAX_HEALTH_BONUS.equals(abilityType) ? maxHealthBonusAmount : 0.0D;
			}

			double armorBonus() {
				return enabled && MadokuPetManager.PET_ABILITY_ARMOR_BONUS.equals(abilityType) ? armorBonusAmount : 0.0D;
			}

			double damageBlockAmount() {
				return enabled && MadokuPetManager.PET_ABILITY_DAMAGE_BLOCK.equals(abilityType) ? damageBlockAmount : 0.0D;
			}

			boolean canBlockIncomingDamage() {
				return enabled && MadokuPetManager.PET_ABILITY_DAMAGE_BLOCK.equals(abilityType) && damageBlockAmount > 0.0D && cooldownTicks > 0L;
			}

			String abilityDescription() {
				if (!enabled) {
					return "";
				}
				if (MadokuPetManager.PET_ABILITY_RANGED_HOMING_ARROW.equals(abilityType) && attackDamage > 0.0F) {
					return "Active: Fires a homing arrow at your target for " + MadokuPetManager.formatAbilityAmount(attackDamage) + " damage.";
				}
				if (MadokuPetManager.PET_ABILITY_WEB_PROJECTILE.equals(abilityType) && attackDamage > 0.0F) {
					return "Active: Launches a web projectile for " + MadokuPetManager.formatAbilityAmount(attackDamage) + " damage that slows enemies.";
				}
				if (MadokuPetManager.PET_ABILITY_EXPLOSIVE_PROJECTILE.equals(abilityType) && attackDamage > 0.0F && explosionRadius > 0.0F) {
					return "Active: Fires an explosive projectile for " + MadokuPetManager.formatAbilityAmount(attackDamage)
						+ " damage within " + MadokuPetManager.formatAbilityAmount(explosionRadius) + " blocks.";
				}
				if (MadokuPetManager.PET_ABILITY_PLAYER_DAMAGE_BONUS.equals(abilityType) && playerDamageBonusAmount > 0.0D) {
					return "Passive: Increases player damage by " + MadokuPetManager.formatAbilityAmount(playerDamageBonusAmount) + ".";
				}
				if (MadokuPetManager.PET_ABILITY_FALL_DAMAGE_REDUCTION.equals(abilityType) && fallDamageReductionAmount > 0.0D) {
					return "Passive: Reduces fall damage by " + MadokuPetManager.formatPercent(fallDamageReductionAmount) + ".";
				}
				if (MadokuPetManager.PET_ABILITY_MAX_HEALTH_BONUS.equals(abilityType) && maxHealthBonusAmount > 0.0D) {
					return "Passive: Increases max health by " + MadokuPetManager.formatPercent(maxHealthBonusAmount) + ".";
				}
				if (MadokuPetManager.PET_ABILITY_ARMOR_BONUS.equals(abilityType) && armorBonusAmount > 0.0D) {
					return "Passive: Increases armor by " + MadokuPetManager.formatAbilityAmount(armorBonusAmount) + ".";
				}
				if (MadokuPetManager.PET_ABILITY_DAMAGE_BLOCK.equals(abilityType) && damageBlockAmount > 0.0D) {
					return "Active: Blocks " + MadokuPetManager.formatAbilityAmount(damageBlockAmount) + " incoming damage.";
				}
				if (MadokuPetManager.PET_ABILITY_MOB_SCAN.equals(abilityType)) {
					return "Automatic: Periodically reveals nearby mobs.";
				}
				if (MadokuPetManager.PET_ABILITY_BEE_SWARM.equals(abilityType) && attackDamage > 0.0F) {
					return "Automatic: Swarms nearby hostile mobs for " + MadokuPetManager.formatAbilityAmount(attackDamage) + " damage per hit.";
				}
				return "";
			}

			String cooldownDescription() {
				if (!enabled || cooldownTicks <= 0L || MadokuPetManager.PET_ABILITY_NONE.equals(abilityType)) {
					return "";
				}
				if (MadokuPetManager.PET_ABILITY_MOB_SCAN.equals(abilityType)) {
					return "Cooldown: " + MadokuPetManager.formatCooldownSeconds(cooldownTicks) + "s";
				}
				return "Cooldown: " + MadokuPetManager.formatCooldownSeconds(cooldownTicks) + "s";
			}

			boolean hasAbility() {
				return enabled && !MadokuPetManager.PET_ABILITY_NONE.equals(abilityType);
			}

			PetRule atLevel(int level) {
				int safeLevel = Math.max(1, Math.min(PetConfigManager.maxPetLevel(), level));
				if (safeLevel == 1) return this;
				double multiplier = 1.0D + ((safeLevel - 1) * 0.5D);
				return new PetRule(
					enabled, itemId, rarity, petScale, followSpeed, idleMoveSpeed, idleDistance, teleportDistance,
					idleWanderRadius, idleMinIntervalTicks, idleMaxIntervalTicks, ambientSoundIntervalMultiplier,
					soundVolumeMultiplier, abilityType, (float) (attackDamage * multiplier), attackSpeed,
					effectDurationTicks, playerDamageBonusAmount * multiplier, fallDamageReductionAmount * multiplier,
					maxHealthBonusAmount * multiplier, armorBonusAmount * multiplier, damageBlockAmount * multiplier,
					cooldownTicks, shotDelayTicks, attackArcStepDegrees, attackRearOffset, attackRearSpread,
					attackLateralRadius, attackVerticalOffset, (float) (explosionRadius * multiplier), soundEventId
				);
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
				if (MadokuPetManager.PET_ABILITY_MOB_SCAN.equals(abilityType)) {
					return SoundEvents.BEACON_ACTIVATE;
				}
				if (MadokuPetManager.PET_ABILITY_WEB_PROJECTILE.equals(abilityType)) {
					return SoundEvents.LLAMA_SPIT;
				}
				if (MadokuPetManager.PET_ABILITY_EXPLOSIVE_PROJECTILE.equals(abilityType)) {
					return SoundEvents.CREEPER_PRIMED;
				}
				if (MadokuPetManager.PET_ABILITY_BEE_SWARM.equals(abilityType)) {
					return SoundEvents.BEE_LOOP;
				}
				return SoundEvents.SKELETON_SHOOT;
			}

			private static String defaultSoundEventIdForAbility(String abilityType) {
				if (MadokuPetManager.PET_ABILITY_MOB_SCAN.equals(abilityType)) {
					return BuiltInRegistries.SOUND_EVENT.getKey(SoundEvents.BEACON_ACTIVATE).toString();
				}
				if (MadokuPetManager.PET_ABILITY_WEB_PROJECTILE.equals(abilityType)) {
					return BuiltInRegistries.SOUND_EVENT.getKey(SoundEvents.LLAMA_SPIT).toString();
				}
				if (MadokuPetManager.PET_ABILITY_EXPLOSIVE_PROJECTILE.equals(abilityType)) {
					return BuiltInRegistries.SOUND_EVENT.getKey(SoundEvents.CREEPER_PRIMED).toString();
				}
				if (MadokuPetManager.PET_ABILITY_BEE_SWARM.equals(abilityType)) {
					return BuiltInRegistries.SOUND_EVENT.getKey(SoundEvents.BEE_LOOP).toString();
				}
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
