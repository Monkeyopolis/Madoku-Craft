package madoku.craft.java.pet;

import com.google.gson.JsonObject;

import madoku.craft.java.core.json.JSONFormatAPIManager;
import madoku.craft.java.core.json.JSONAPIManager;
import madoku.craft.java.core.rarity.RarityAPIManager;
import madoku.craft.java.core.rarity.RarityAPIManager.Tier;
import madoku.craft.java.core.sync.SyncConfigAPIManager;

import com.google.gson.JsonArray;
import com.google.gson.JsonElement;
import com.google.gson.JsonParser;

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
		PetConfigAPIManager.registerProvider(new MadokuPetConfigProvider());
		reload();
		SyncConfigAPIManager.register(
			"pet",
			PetConfigManager::createClientSyncSnapshot,
			PetConfigManager::applyClientSyncSnapshot,
			PetConfigManager::resetClientSyncState
		);
	}

	public static boolean isEnabled() {
		return settings().enabled;
	}

	public static boolean areEntitiesEnabled() {
		return settings().entitiesEnabled;
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
		return JSONAPIManager.normalizeRegistryIdentifierForJson(value);
	}

	static String normalizeAbilityId(String value) {
		return normalizeKey(value).replace('-', '_');
	}

	static String cooldownLabel(String abilityType) {
		return switch (normalizeAbilityId(abilityType)) {
			case MadokuPetManager.PET_ABILITY_DAMAGE_BLOCK -> "Block:";
			case MadokuPetManager.PET_ABILITY_HEALTH_REGENERATION -> "Heal:";
			case MadokuPetManager.PET_ABILITY_EGG_PROJECTILE -> "Egg Volley:";
			case MadokuPetManager.PET_ABILITY_WEB_PROJECTILE -> "Web Projectile:";
			case MadokuPetManager.PET_ABILITY_EXPLOSIVE_PROJECTILE -> "Explosion:";
			case MadokuPetManager.PET_ABILITY_RANGED_HOMING_ARROW -> "Homing Arrow:";
			case MadokuPetManager.PET_ABILITY_MOB_SCAN -> "Mob Scan:";
			default -> abilityType == null || abilityType.isBlank() ? "Cooldown:" : abilityType + ":";
		};
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
		return settings().maxLevel;
	}

	private static final org.slf4j.Logger LOGGER = org.slf4j.LoggerFactory.getLogger(PetConfigManager.class);
	private static volatile PetSettings settings = PetSettings.defaults();
	private static volatile PetSettings clientSynchronizedSettings;
	private static Map<String, JsonObject> savedClientAbilityDefinitions = Map.of();
	private static Map<String, JsonObject> savedClientEntityDefinitions = Map.of();
	private static boolean clientSynchronized;
	private static final String CONFIG_FILE_NAME = "madoku-pets";

	static void reload() {
		loadStaticConfig();
		PetAbilitiesManager.AbilitiesConfigManager.reload();
		PetEntitiesManager.EntitiesConfigManager.reload();
	}

	static PetSettings settings() {
		PetSettings synchronizedSettings = clientSynchronizedSettings;
		return synchronizedSettings == null ? settings : synchronizedSettings;
	}
	static Map<String, PetRule> rules() { return PetEntitiesManager.EntitiesConfigManager.rules(); }

	public static String createClientSyncSnapshot() {
		JsonObject settingsRoot = JSONFormatAPIManager.object()
			.put("enabled", settings.enabled)
			.object("pet-entity", entity -> entity
				.put("enabled", settings.entitiesEnabled)
				.put("max-level", settings.maxLevel))
			.build();
		JsonObject snapshot = JSONFormatAPIManager.object()
			.put("settings", settingsRoot)
			.object("abilities", abilities -> PetAbilitiesManager.AbilitiesConfigManager.snapshotDefinitions()
				.forEach(abilities::put))
			.object("entities", entities -> PetEntitiesManager.EntitiesConfigManager.snapshotSourceFiles()
				.forEach(entities::put))
			.build();
		return snapshot.toString();
	}

	public static void applyClientSyncSnapshot(String snapshot) {
		JsonElement parsed = JsonParser.parseString(snapshot == null ? "" : snapshot);
		if (!parsed.isJsonObject()) return;
		JsonObject root = parsed.getAsJsonObject();
		captureClientSyncState();
		clientSynchronizedSettings = PetSettings.fromJson(readObject(root, "settings"));
		PetAbilitiesManager.AbilitiesConfigManager.applyClientSynchronizedDefinitions(readObjectMap(root, "abilities"));
		PetEntitiesManager.EntitiesConfigManager.applyClientSynchronizedSourceFiles(readObjectMap(root, "entities"));
		clientSynchronized = true;
	}

	public static void resetClientSyncState() {
		if (!clientSynchronized) return;
		clientSynchronizedSettings = null;
		PetAbilitiesManager.AbilitiesConfigManager.applyClientSynchronizedDefinitions(savedClientAbilityDefinitions);
		PetEntitiesManager.EntitiesConfigManager.applyClientSynchronizedSourceFiles(savedClientEntityDefinitions);
		clientSynchronized = false;
		savedClientAbilityDefinitions = Map.of();
		savedClientEntityDefinitions = Map.of();
	}

	private static void captureClientSyncState() {
		if (clientSynchronized) return;
		savedClientAbilityDefinitions = PetAbilitiesManager.AbilitiesConfigManager.snapshotDefinitions();
		savedClientEntityDefinitions = PetEntitiesManager.EntitiesConfigManager.snapshotSourceFiles();
	}

	private static JsonObject readObject(JsonObject source, String key) {
		JsonElement element = source == null ? null : source.get(key);
		return element != null && element.isJsonObject() ? element.getAsJsonObject() : new JsonObject();
	}

	private static Map<String, JsonObject> readObjectMap(JsonObject source, String key) {
		Map<String, JsonObject> values = new java.util.LinkedHashMap<>();
		for (Map.Entry<String, JsonElement> entry : readObject(source, key).entrySet()) {
			if (entry.getValue().isJsonObject()) values.put(entry.getKey(), entry.getValue().getAsJsonObject());
		}
		return values;
	}

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
			Path rootDirectory = JSONAPIManager.getOrCreateGlobalSystemDirectory(PET_FOLDER);
			Path configFile = resolveJsonFile(rootDirectory, CONFIG_FILE_NAME);
			JsonObject defaults = PetSettings.defaults().toConfigJson();
			JsonObject normalized = JSONFormatAPIManager.ensureManagedFile(configFile, defaults);
			PetSettings configured = PetSettings.fromJson(normalized);
			JSONFormatAPIManager.writeManagedFile(configFile, configured.toConfigJson(), defaults);
			settings = configured;
		} catch (IOException exception) {
			settings = PetSettings.defaults();
			LOGGER.error("Failed to load Madoku pet settings; using defaults.", exception);
		}
	}

	static Path petDirectory() throws IOException {
		return JSONAPIManager.getOrCreateGlobalSystemDirectory(PET_FOLDER);
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

	static double defaultTeleportDistanceForItem(String itemId) {
		String normalizedItemId = normalizePetId(itemId);
		return "minecraft:bat".equals(normalizedItemId) || "minecraft:bee".equals(normalizedItemId) ? 12.0D : 8.0D;
	}

	static double defaultIdleDistanceForItem(String itemId) {
		String normalizedItemId = normalizePetId(itemId);
		return "minecraft:bat".equals(normalizedItemId) || "minecraft:bee".equals(normalizedItemId) ? 6.0D : 4.0D;
	}

	static String defaultRarityForItem(String itemId) {
		String normalizedItemId = normalizePetId(itemId);
		if ("minecraft:bee".equals(normalizedItemId) || "minecraft:bat".equals(normalizedItemId)) return MadokuPetManager.PET_RARITY_EPIC;
		if ("minecraft:chicken".equals(normalizedItemId)) return MadokuPetManager.PET_RARITY_LEGENDARY;
		if ("minecraft:cow".equals(normalizedItemId)) return MadokuPetManager.PET_RARITY_LEGENDARY;
		if ("minecraft:creeper".equals(normalizedItemId)
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
		Tier tier = RarityAPIManager.fromString(rawRarity);
		return tier == null ? Tier.COMMON.id() : tier.id();
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
		final int maxLevel;

		PetSettings(
				boolean enabled,
				boolean entitiesEnabled,
				int maxLevel
			) {
				this.enabled = enabled;
				this.entitiesEnabled = entitiesEnabled;
				this.maxLevel = maxLevel;
			}

			static PetSettings defaults() {
				return new PetSettings(
					true,
					true,
					5
				);
			}

			static PetSettings fromJson(JsonObject source) {
				PetSettings defaults = defaults();
				JsonObject petEntity = objectField(source, "pet-entity");
				boolean enabled = getBoolean(source, "enabled", defaults.enabled);
				boolean entitiesEnabled = getBoolean(petEntity, "enabled", defaults.entitiesEnabled);
				int maxLevel = (int) clampLong(getLong(petEntity, "max-level", defaults.maxLevel), 1L, 100L);
				return new PetSettings(
					enabled,
					entitiesEnabled,
					maxLevel
				);
			}

			JsonObject toConfigJson() {
				return madoku.craft.java.core.json.JSONFormatAPIManager.object()
					.object("pet-entity", child -> child
						.put("enabled", entitiesEnabled)
						.put("max-level", maxLevel))
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
		final double projectileCount;
		final long projectileIntervalTicks;
		final long effectDurationTicks;
		final long stunDurationTicks;
		final long slowDurationTicks;
		final double slowPercentage;
		final double vulnerabilityAmount;
		final long vulnerabilityDurationTicks;
		final double mobScanVulnerabilityAmount;
		final double playerDamageBonusAmount;
		final double fallDamageReductionAmount;
		final double maxHealthBonusAmount;
		final double armorBonusAmount;
		final double damageBlockAmount;
		final double healthRegenerationAmount;
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
			double projectileCount,
			long projectileIntervalTicks,
			long effectDurationTicks,
			long stunDurationTicks,
			long slowDurationTicks,
			double slowPercentage,
			double vulnerabilityAmount,
			long vulnerabilityDurationTicks,
			double mobScanVulnerabilityAmount,
			double playerDamageBonusAmount,
			double fallDamageReductionAmount,
			double maxHealthBonusAmount,
			double armorBonusAmount,
			double damageBlockAmount,
			double healthRegenerationAmount,
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
			this.projectileCount = projectileCount;
			this.projectileIntervalTicks = projectileIntervalTicks;
			this.effectDurationTicks = effectDurationTicks;
			this.stunDurationTicks = stunDurationTicks;
			this.slowDurationTicks = slowDurationTicks;
			this.slowPercentage = slowPercentage;
			this.vulnerabilityAmount = vulnerabilityAmount;
			this.vulnerabilityDurationTicks = vulnerabilityDurationTicks;
			this.mobScanVulnerabilityAmount = mobScanVulnerabilityAmount;
			this.playerDamageBonusAmount = playerDamageBonusAmount;
			this.fallDamageReductionAmount = fallDamageReductionAmount;
			this.maxHealthBonusAmount = maxHealthBonusAmount;
			this.armorBonusAmount = armorBonusAmount;
			this.damageBlockAmount = damageBlockAmount;
			this.healthRegenerationAmount = healthRegenerationAmount;
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
			double resolvedAttackDamage = attackDamage;
			double resolvedProjectileCount = projectileCount;
			double resolvedMobScanVulnerability = mobScanVulnerabilityAmount;
			if (MadokuPetManager.PET_ABILITY_MOB_SCAN.equals(abilityType)) {
				resolvedMobScanVulnerability += (Math.max(1, level) - 1) * 0.025D;
			}
			double resolvedPlayerDamageBonus = playerDamageBonusAmount;
			if (MadokuPetManager.PET_ABILITY_PLAYER_DAMAGE_BONUS.equals(abilityType)) {
				resolvedPlayerDamageBonus += (Math.max(1, level) - 1) * 0.25D;
			}
			double resolvedFallDamageReduction = fallDamageReductionAmount;
			double resolvedMaxHealthBonus = maxHealthBonusAmount;
			if (MadokuPetManager.PET_ABILITY_MAX_HEALTH_BONUS.equals(abilityType)) {
				resolvedMaxHealthBonus += (Math.max(1, level) - 1) * 0.025D;
			}
			double resolvedArmorBonus = armorBonusAmount;
			double resolvedDamageBlock = damageBlockAmount;
			double resolvedHealthRegeneration = healthRegenerationAmount;
			long resolvedEffectDurationTicks = effectDurationTicks;
			if (MadokuPetManager.PET_ABILITY_DAMAGE_BLOCK.equals(abilityType)) {
				resolvedDamageBlock += (Math.max(1, level) - 1) * 2.0D;
			}
			if (MadokuPetManager.PET_ABILITY_HEALTH_REGENERATION.equals(abilityType)) {
				resolvedHealthRegeneration += (Math.max(1, level) - 1) * 0.0125D;
				resolvedEffectDurationTicks += (Math.max(1, level) - 1) * 10L;
			}
			double resolvedExplosionRadius = explosionRadius;
			long resolvedStunDurationTicks = stunDurationTicks;
			long resolvedSlowDurationTicks = slowDurationTicks;
			double resolvedSlowPercentage = slowPercentage;
			double resolvedVulnerabilityAmount = vulnerabilityAmount;
			long resolvedVulnerabilityDurationTicks = vulnerabilityDurationTicks;
			String normalizedPetId = normalizePetId(petId);
			if ("minecraft:sheep".equals(normalizedPetId)
				&& MadokuPetManager.PET_ABILITY_ARMOR_BONUS.equals(abilityType)) {
				resolvedArmorBonus = armorBonusAmount + ((Math.max(1, level) - 1) * 0.25D);
			}
			if (MadokuPetManager.PET_ABILITY_WEB_PROJECTILE.equals(abilityType)) {
				resolvedAttackDamage = attackDamage + ((Math.max(1, level) - 1) * 0.5D);
				resolvedStunDurationTicks += (Math.max(1, level) - 1) * 5L;
				resolvedSlowDurationTicks += (Math.max(1, level) - 1) * 40L;
				resolvedSlowPercentage += (Math.max(1, level) - 1) * 0.05D;
			}
			if (MadokuPetManager.PET_ABILITY_EXPLOSIVE_PROJECTILE.equals(abilityType)) {
				resolvedAttackDamage = attackDamage + ((Math.max(1, level) - 1) * 3.0D);
				resolvedVulnerabilityAmount += (Math.max(1, level) - 1) * 0.0125D;
				resolvedVulnerabilityDurationTicks += (Math.max(1, level) - 1) * 25L;
			}
			if ("minecraft:chicken".equals(normalizedPetId)) {
				if (MadokuPetManager.PET_ABILITY_EGG_PROJECTILE.equals(abilityType)) {
					resolvedAttackDamage = attackDamage + ((Math.max(1, level) - 1) * 0.25D);
					resolvedProjectileCount = projectileCount + ((Math.max(1, level) - 1) * 0.5D);
					resolvedExplosionRadius = explosionRadius + ((Math.max(1, level) - 1) * 0.25D);
				}
				if (MadokuPetManager.PET_ABILITY_FALL_DAMAGE_REDUCTION.equals(abilityType)) {
					resolvedFallDamageReduction = fallDamageReductionAmount + ((Math.max(1, level) - 1) * 0.05D);
				}
			} else if ("minecraft:creeper".equals(normalizedPetId)
				&& MadokuPetManager.PET_ABILITY_EXPLOSIVE_PROJECTILE.equals(abilityType)) {
				resolvedExplosionRadius = explosionRadius + (Math.max(1, level) - 1);
			} else if ("minecraft:skeleton".equals(normalizedPetId)
				&& MadokuPetManager.PET_ABILITY_RANGED_HOMING_ARROW.equals(abilityType)) {
				int levelDelta = Math.max(1, level) - 1;
				resolvedAttackDamage = attackDamage + (levelDelta * 0.5D);
				resolvedProjectileCount = projectileCount + (levelDelta * 0.125D);
			} else if ("minecraft:bee".equals(normalizedPetId)
				&& MadokuPetManager.PET_ABILITY_BEE_SWARM.equals(abilityType)) {
				resolvedAttackDamage = attackDamage + ((Math.max(1, level) - 1) * 0.2D);
			}
			return new PetAbilityRule(
				abilityType,
				soundVolumeMultiplier,
				(float) resolvedAttackDamage,
				attackSpeed,
				resolvedProjectileCount,
				projectileIntervalTicks,
				resolvedEffectDurationTicks,
				resolvedStunDurationTicks,
				resolvedSlowDurationTicks,
				resolvedSlowPercentage,
				resolvedVulnerabilityAmount,
				resolvedVulnerabilityDurationTicks,
				resolvedMobScanVulnerability,
				resolvedPlayerDamageBonus,
				resolvedFallDamageReduction,
				resolvedMaxHealthBonus,
				resolvedArmorBonus,
				resolvedDamageBlock,
				resolvedHealthRegeneration,
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
				projectileCount,
				projectileIntervalTicks,
				effectDurationTicks,
				stunDurationTicks,
				slowDurationTicks,
				slowPercentage,
				vulnerabilityAmount,
				vulnerabilityDurationTicks,
				mobScanVulnerabilityAmount,
				playerDamageBonusAmount,
				fallDamageReductionAmount,
				maxHealthBonusAmount,
				armorBonusAmount,
				damageBlockAmount,
				healthRegenerationAmount,
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
				return configuredAbilities.stream().filter(ability -> ability != null).toList();
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
				return madoku.craft.java.core.json.JSONFormatAPIManager.object()
					.object("pet-id", pet -> {
						pet.put("id", resolvedPetId)
							.put("rarity", defaultRarityForItem(resolvedPetId))
							.put("pet-scale", defaultPetScaleForItem(resolvedPetId))
							.put("follow-speed", 1.2D)
							.put("teleport-distance", defaultTeleportDistanceForItem(resolvedPetId));
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
				boolean usesHealthRegeneration = MadokuPetManager.PET_ABILITY_HEALTH_REGENERATION.equals(resolvedAbilityType);
				boolean usesMobScan = MadokuPetManager.PET_ABILITY_MOB_SCAN.equals(resolvedAbilityType);
				boolean usesBeeSwarm = MadokuPetManager.PET_ABILITY_BEE_SWARM.equals(resolvedAbilityType);
				JsonObject ability = madoku.craft.java.core.json.JSONFormatAPIManager.object()
					.put("id", abilityConfigId(resolvedAbilityType))
					.build();
				if (usesRangedHomingArrow) {
					ability.addProperty("attack-damage", 3.0D);
					ability.addProperty("attack-speed", 3.0D);
					ability.addProperty("projectile-count", 1.0D);
					ability.addProperty("projectile-interval-ticks", 10L);
					ability.addProperty("cooldown", 5.0D);
					ability.addProperty("shot-delay-ticks", 10L);
				}
				if (usesWebProjectile) {
					ability.addProperty("follow-speed", 1.5D);
					ability.addProperty("attack-damage", 6.0D);
					ability.addProperty("attack-speed", 2.0D);
					ability.addProperty("projectile-count", 1.0D);
					ability.addProperty("projectile-interval-ticks", 10L);
					ability.addProperty("effect-duration-ticks", 240L);
					ability.addProperty("stun-duration-ticks", 50L);
					ability.addProperty("slow-duration-ticks", 240L);
					ability.addProperty("slow-percentage", 0.40D);
					ability.addProperty("cooldown", 30.0D);
					ability.addProperty("shot-delay-ticks", 10L);
				}
				if (usesExplosiveProjectile) {
					ability.addProperty("follow-speed", 1.2D);
					ability.addProperty("attack-damage", 12.0D);
					ability.addProperty("attack-speed", 2.5D);
					ability.addProperty("projectile-count", 1.0D);
					ability.addProperty("projectile-interval-ticks", 10L);
					ability.addProperty("vulnerability", 0.10D);
					ability.addProperty("vulnerability-duration-ticks", 100L);
					ability.addProperty("cooldown", 60.0D);
					ability.addProperty("shot-delay-ticks", 10L);
					ability.addProperty("explosion-radius", 4D);
				}
				if (usesEggProjectile) {
					ability.addProperty("follow-speed", 1.0D);
					ability.addProperty("attack-damage", 4.0D);
					ability.addProperty("attack-speed", 2.5D);
					ability.addProperty("projectile-count", 3.0D);
					ability.addProperty("projectile-interval-ticks", 4L);
					ability.addProperty("cooldown", 15.0D);
					ability.addProperty("shot-delay-ticks", 5L);
					ability.addProperty("explosion-radius", 1.5D);
				}
				if (usesDamageBlock) {
					ability.addProperty("damage-block", 5.0D);
					ability.addProperty("cooldown", 30.0D);
				}
				if (usesHealthRegeneration) {
					ability.addProperty("health-regeneration", 0.05D);
					ability.addProperty("effect-duration-ticks", 60L);
					ability.addProperty("cooldown", 60.0D);
				}
				if (usesMobScan) {
					ability.addProperty("mob-scan-vulnerability", 0.15D);
					ability.addProperty("cooldown", 120.0D);
				}
				if (usesBeeSwarm) {
					ability.addProperty("follow-speed", 1.5D);
					ability.addProperty("idle-move-speed", 1.25D);
					ability.addProperty("idle-wander-radius", 4.0D);
					ability.addProperty("attack-damage", 1.6D);
					ability.addProperty("cooldown", 0.0D);
				}
				if (MadokuPetManager.PET_ABILITY_PLAYER_DAMAGE_BONUS.equals(resolvedAbilityType)) {
					ability.addProperty("player-damage-bonus", 1.0D);
				}
				if (MadokuPetManager.PET_ABILITY_FALL_DAMAGE_REDUCTION.equals(resolvedAbilityType)) {
					ability.addProperty("fall-damage-reduction", 0.30D);
				}
				if (MadokuPetManager.PET_ABILITY_MAX_HEALTH_BONUS.equals(resolvedAbilityType)) {
					ability.addProperty("max-health-bonus", 0.15D);
				}
				if (MadokuPetManager.PET_ABILITY_ARMOR_BONUS.equals(resolvedAbilityType)) {
					ability.addProperty("armor-bonus", 1.5D);
				}
				return madoku.craft.java.core.json.JSONFormatAPIManager.object().put("ability-id", ability).build();
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
				double idleDistance = PetSettings.clampDouble(getDouble(source, "idle-distance", defaultIdleDistanceForItem(itemId)), 0.5D, 32.0D);
				double teleportDistance = PetSettings.clampDouble(getDouble(source, "teleport-distance", defaultTeleportDistanceForItem(itemId)), idleDistance, 64.0D);
				double idleWanderRadius = PetSettings.clampDouble(getDouble(source, "idle-wander-radius", DEFAULT_IDLE_WANDER_RADIUS), 0.0D, 16.0D);
				long idleMinIntervalTicks = PetSettings.clampLong(getLong(source, "idle-min-interval-ticks", DEFAULT_IDLE_MIN_INTERVAL_TICKS), 1L, 20L * 60L);
				long idleMaxIntervalTicks = PetSettings.clampLong(getLong(source, "idle-max-interval-ticks", DEFAULT_IDLE_MAX_INTERVAL_TICKS), idleMinIntervalTicks, 20L * 60L);
				float soundVolumeMultiplier = (float) PetSettings.clampDouble(getDouble(source, "sound-volume-multiplier", DEFAULT_SOUND_VOLUME_MULTIPLIER), 0.0D, 4.0D);
				float attackDamage = (float) PetSettings.clampDouble(getDouble(source, "attack-damage", 0.0D), 0.0D, 1024.0D);
				float attackSpeed = (float) PetSettings.clampDouble(getDouble(source, "attack-speed", 0.0D), 0.05D, 8.0D);
				long effectDurationTicks = PetSettings.clampLong(
					getLong(source, "effect-duration-ticks", 0L),
					0L,
					MadokuPetManager.PET_ABILITY_HEALTH_REGENERATION.equals(abilityType) ? Long.MAX_VALUE : 20L * 60L
				);
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
				float explosionRadius = (float) PetSettings.clampDouble(getDouble(source, "explosion-radius", defaultExplosionRadiusForAbility(abilityType)), 0.25D, 12.0D);
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
						PetSettings.clampDouble(getDouble(resolvedSource, "projectile-count", defaultProjectileCountForAbility(abilityType)), 1.0D, 64.0D),
						PetSettings.clampLong(
							getLong(resolvedSource, "projectile-interval-ticks", defaultProjectileIntervalTicksForAbility(abilityType, resolvedSource)),
							0L,
							20L * 60L
						),
						PetSettings.clampLong(
							getLong(resolvedSource, "effect-duration-ticks", 0L),
							0L,
							MadokuPetManager.PET_ABILITY_HEALTH_REGENERATION.equals(abilityType) ? Long.MAX_VALUE : 20L * 60L
						),
						PetSettings.clampLong(getLong(resolvedSource, "stun-duration-ticks", defaultStunDurationTicksForAbility(abilityType)), 0L, 20L * 60L),
						PetSettings.clampLong(getLong(resolvedSource, "slow-duration-ticks", defaultSlowDurationTicksForAbility(abilityType)), 0L, 20L * 60L * 10L),
						PetSettings.clampDouble(getDouble(resolvedSource, "slow-percentage", defaultSlowPercentageForAbility(abilityType)), 0.0D, 1.0D),
						PetSettings.clampDouble(getDouble(resolvedSource, "vulnerability", defaultVulnerabilityForAbility(abilityType)), 0.0D, 10.0D),
						PetSettings.clampLong(getLong(resolvedSource, "vulnerability-duration-ticks", defaultVulnerabilityDurationTicksForAbility(abilityType)), 0L, 20L * 60L * 10L),
						PetSettings.clampDouble(getDouble(
							resolvedSource,
							"mob-scan-vulnerability",
							MadokuPetManager.PET_ABILITY_MOB_SCAN.equals(abilityType) ? 0.05D : 0.0D
						), 0.0D, 10.0D),
						PetSettings.clampDouble(getDouble(resolvedSource, "player-damage-bonus", 0.0D), 0.0D, 1024.0D),
						PetSettings.clampDouble(getDouble(resolvedSource, "fall-damage-reduction", 0.0D), 0.0D, 1.0D),
						PetSettings.clampDouble(getDouble(resolvedSource, "max-health-bonus", 0.0D), 0.0D, 10.0D),
						PetSettings.clampDouble(getDouble(resolvedSource, "armor-bonus", 0.0D), 0.0D, 1024.0D),
						PetSettings.clampDouble(getDouble(resolvedSource, "damage-block", 0.0D), 0.0D, 1024.0D),
						Math.max(0.0D, getDouble(resolvedSource, "health-regeneration", 0.0D)),
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
				if (MadokuPetManager.PET_ABILITY_EGG_PROJECTILE.equals(abilityType)) return 1.5D;
				return 0.0D;
			}

			private static long defaultStunDurationTicksForAbility(String abilityType) {
				return MadokuPetManager.PET_ABILITY_WEB_PROJECTILE.equals(abilityType) ? 50L : 0L;
			}

			private static long defaultSlowDurationTicksForAbility(String abilityType) {
				return MadokuPetManager.PET_ABILITY_WEB_PROJECTILE.equals(abilityType) ? 240L : 0L;
			}

			private static double defaultSlowPercentageForAbility(String abilityType) {
				return MadokuPetManager.PET_ABILITY_WEB_PROJECTILE.equals(abilityType) ? 0.40D : 0.0D;
			}

			private static double defaultVulnerabilityForAbility(String abilityType) {
				return MadokuPetManager.PET_ABILITY_EXPLOSIVE_PROJECTILE.equals(abilityType) ? 0.10D : 0.0D;
			}

			private static long defaultVulnerabilityDurationTicksForAbility(String abilityType) {
				return MadokuPetManager.PET_ABILITY_EXPLOSIVE_PROJECTILE.equals(abilityType) ? 100L : 0L;
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
					if ("id".equals(entry.getKey())) continue;
					if (!flattened.has(entry.getKey())) flattened.add(entry.getKey(), entry.getValue().deepCopy());
				}
				double abilityCooldownSeconds = getDouble(abilityGroup, "cooldown", 0.0D);
				flattened.addProperty("cooldown-ticks", Math.max(0L, Math.round(abilityCooldownSeconds * 20.0D)));
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
						descriptions.add("Active: Fires " + MadokuPetManager.formatAbilityAmount(ability.projectileCount)
							+ " homing arrow(s) at a target for " + MadokuPetManager.formatAbilityAmount(ability.attackDamage) + " damage.");
					} else if (MadokuPetManager.PET_ABILITY_WEB_PROJECTILE.equals(configuredAbility) && ability.attackDamage > 0.0F) {
						descriptions.add("Active: Launches " + MadokuPetManager.formatAbilityAmount(ability.projectileCount) + " web projectile that stun enemies for "
							+ MadokuPetManager.formatCooldownSeconds(ability.stunDurationTicks) + "s and deals "
							+ MadokuPetManager.formatAbilityAmount(ability.attackDamage) + " damage.");
					} else if (MadokuPetManager.PET_ABILITY_EXPLOSIVE_PROJECTILE.equals(configuredAbility) && ability.attackDamage > 0.0F && ability.explosionRadius > 0.0F) {
						descriptions.add("Active: Fires " + MadokuPetManager.formatAbilityAmount(ability.projectileCount) + " explosive projectile for " + MadokuPetManager.formatAbilityAmount(ability.attackDamage)
							+ " damage within a " + MadokuPetManager.formatAbilityAmount(ability.explosionRadius) + " radius.");
					} else if (MadokuPetManager.PET_ABILITY_EGG_PROJECTILE.equals(configuredAbility) && ability.attackDamage > 0.0F && ability.explosionRadius > 0.0F) {
						descriptions.add("Active: Fires " + MadokuPetManager.formatAbilityAmount(ability.projectileCount) + " egg projectiles for " + MadokuPetManager.formatAbilityAmount(ability.attackDamage)
							+ " damage within " + MadokuPetManager.formatAbilityAmount(ability.explosionRadius) + " radius.");
					} else if (MadokuPetManager.PET_ABILITY_PLAYER_DAMAGE_BONUS.equals(configuredAbility) && ability.playerDamageBonusAmount > 0.0D) {
						descriptions.add("Passive: Increases damage by " + MadokuPetManager.formatAbilityAmount(ability.playerDamageBonusAmount) + ".");
					} else if (MadokuPetManager.PET_ABILITY_FALL_DAMAGE_REDUCTION.equals(configuredAbility) && ability.fallDamageReductionAmount > 0.0D) {
						descriptions.add("Passive: Reduces fall damage by " + MadokuPetManager.formatPercent(ability.fallDamageReductionAmount) + ".");
					} else if (MadokuPetManager.PET_ABILITY_MAX_HEALTH_BONUS.equals(configuredAbility) && ability.maxHealthBonusAmount > 0.0D) {
						descriptions.add("Passive: Increases max health by " + MadokuPetManager.formatPercent(ability.maxHealthBonusAmount) + ".");
					} else if (MadokuPetManager.PET_ABILITY_ARMOR_BONUS.equals(configuredAbility) && ability.armorBonusAmount > 0.0D) {
						descriptions.add("Passive: Increases armor and armor toughness by "
							+ MadokuPetManager.formatAbilityAmount(ability.armorBonusAmount) + ".");
					} else if (MadokuPetManager.PET_ABILITY_DAMAGE_BLOCK.equals(configuredAbility) && ability.damageBlockAmount > 0.0D) {
						descriptions.add("Active: Blocks " + MadokuPetManager.formatAbilityAmount(ability.damageBlockAmount) + " incoming damage.");
					} else if (MadokuPetManager.PET_ABILITY_HEALTH_REGENERATION.equals(configuredAbility) && ability.healthRegenerationAmount > 0.0D) {
						descriptions.add("Reactive: Heals " + MadokuPetManager.formatPercent(ability.healthRegenerationAmount)
							+ " health every second for "
							+ MadokuPetManager.formatCooldownSeconds(ability.effectDurationTicks) + "s after taking damage.");
					} else if (MadokuPetManager.PET_ABILITY_MOB_SCAN.equals(configuredAbility)) {
						descriptions.add("Automatic: Reveals nearby mobs; they take " + MadokuPetManager.formatPercent(ability.mobScanVulnerabilityAmount) + " more damage.");
					} else if (MadokuPetManager.PET_ABILITY_BEE_SWARM.equals(configuredAbility) && ability.attackDamage > 0.0F) {
						descriptions.add("Automatic: Swarms nearby hostile mobs for " + MadokuPetManager.formatAbilityAmount(ability.attackDamage) + " damage per second.");
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
			List<String> descriptions = new ArrayList<>();
			for (PetAbilityRule ability : abilities) {
				if (ability.cooldownTicks > 0L) {
					descriptions.add(cooldownLabel(ability.abilityType) + " " + MadokuPetManager.formatCooldownSeconds(ability.cooldownTicks) + "s");
				}
			}
			return List.copyOf(descriptions);
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
				double resolvedAttackDamage = attackDamage;
				double resolvedFallDamageReduction = fallDamageReductionAmount;
				double resolvedArmorBonus = armorBonusAmount;
				double resolvedExplosionRadius = explosionRadius;
				if ("minecraft:chicken".equals(petId)) {
					resolvedAttackDamage = attackDamage + ((safeLevel - 1) * 0.5D);
					resolvedFallDamageReduction = fallDamageReductionAmount + ((safeLevel - 1) * 0.05D);
					if (MadokuPetManager.PET_ABILITY_EGG_PROJECTILE.equals(abilityType)) {
						resolvedExplosionRadius = explosionRadius + ((safeLevel - 1) * 0.25D);
					}
				} else if ("minecraft:creeper".equals(petId)) {
					resolvedAttackDamage = attackDamage + ((safeLevel - 1) * 3.0D);
					resolvedExplosionRadius = explosionRadius + (safeLevel - 1);
				} else if ("minecraft:sheep".equals(petId)) {
					resolvedArmorBonus = armorBonusAmount + ((safeLevel - 1) * 0.25D);
				} else if ("minecraft:skeleton".equals(petId)
					&& MadokuPetManager.PET_ABILITY_RANGED_HOMING_ARROW.equals(abilityType)) {
					resolvedAttackDamage = attackDamage + ((safeLevel - 1) * 0.5D);
				} else if ("minecraft:bee".equals(petId)) {
					resolvedAttackDamage = attackDamage + ((safeLevel - 1) * 0.2D);
				}
				List<PetAbilityRule> resolvedAbilities = new ArrayList<>();
				for (PetAbilityRule ability : abilities) {
					resolvedAbilities.add(ability.atLevel(safeLevel, petId));
				}
				return new PetRule(
					enabled, itemId, rarity, petScale, followSpeed, idleMoveSpeed, idleDistance, teleportDistance,
					idleWanderRadius, idleMinIntervalTicks, idleMaxIntervalTicks,
					soundVolumeMultiplier, abilityType, abilityTypes, (float) resolvedAttackDamage, attackSpeed,
					effectDurationTicks, playerDamageBonusAmount + (MadokuPetManager.PET_ABILITY_PLAYER_DAMAGE_BONUS.equals(abilityType)
						? (safeLevel - 1) * 0.25D
						: 0.0D),
					resolvedFallDamageReduction,
					maxHealthBonusAmount + (MadokuPetManager.PET_ABILITY_MAX_HEALTH_BONUS.equals(abilityType)
						? (safeLevel - 1) * 0.025D
						: 0.0D),
					resolvedArmorBonus,
					damageBlockAmount + (MadokuPetManager.PET_ABILITY_DAMAGE_BLOCK.equals(abilityType)
						? (safeLevel - 1) * 2.0D
						: 0.0D),
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
		Identifier identifier = Identifier.tryParse(
			JSONAPIManager.normalizeRegistryIdentifierForLookup(configuredSoundEventId)
		);
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

		private static double defaultProjectileCountForAbility(String abilityType) {
			return MadokuPetManager.PET_ABILITY_EGG_PROJECTILE.equals(abilityType) ? 3.0D : 1.0D;
		}

		private static long defaultProjectileIntervalTicksForAbility(String abilityType, JsonObject source) {
			long legacyInterval = getLong(source, "shot-delay-ticks", 0L);
			if (legacyInterval > 0L) {
				return legacyInterval;
			}
			if (MadokuPetManager.PET_ABILITY_EGG_PROJECTILE.equals(abilityType)) return 4L;
			if (MadokuPetManager.PET_ABILITY_RANGED_HOMING_ARROW.equals(abilityType)
				|| MadokuPetManager.PET_ABILITY_WEB_PROJECTILE.equals(abilityType)
				|| MadokuPetManager.PET_ABILITY_EXPLOSIVE_PROJECTILE.equals(abilityType)) return 10L;
			return 0L;
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

