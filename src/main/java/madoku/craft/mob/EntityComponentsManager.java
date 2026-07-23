package madoku.craft.mob;

import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import madoku.craft.api.debug.MadokuDebugManager;
import net.minecraft.world.entity.AgeableMob;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.ai.attributes.AttributeInstance;
import net.minecraft.world.entity.ai.attributes.Attributes;

/** Runtime group for configured entity components and attributes. */
public final class EntityComponentsManager {
	public record MobBabySettings(boolean configured, boolean ageable, long durationTicks) {
		private static final MobBabySettings DISABLED = new MobBabySettings(false, false, 0L);
	}

	private EntityComponentsManager() {
	}

	public static void initialize() {
	}

	public static boolean applyConfiguredComponents(LivingEntity entity, JsonObject variant) {
		if (entity == null || variant == null) return false;
		JsonElement element = variant.get(MobConfigManager.FIELD_MOB_COMPONENTS);
		if (element == null || !element.isJsonObject()) return false;
		JsonObject components = element.getAsJsonObject();
		boolean changed = false;
		changed |= set(entity, Attributes.MAX_HEALTH, components, MobConfigManager.FIELD_HEALTH);
		changed |= set(entity, Attributes.ARMOR, components, MobConfigManager.FIELD_ARMOR);
		changed |= set(entity, Attributes.ATTACK_DAMAGE, components, MobConfigManager.FIELD_DAMAGE);
		changed |= set(entity, Attributes.MOVEMENT_SPEED, components, MobConfigManager.FIELD_MOVEMENT_SPEED);
		changed |= set(entity, Attributes.KNOCKBACK_RESISTANCE, components, MobConfigManager.FIELD_KNOCKBACK_RESISTANCE);
		changed |= set(entity, Attributes.FLYING_SPEED, components, MobConfigManager.FIELD_FLYING_SPEED);
		changed |= set(entity, Attributes.WATER_MOVEMENT_EFFICIENCY, components, MobConfigManager.FIELD_SWIMMING_SPEED);
		emitBaseStatsDebug(entity);
		return changed;
	}

	public static MobBabySettings resolveMobBabySettings(LivingEntity entity) {
		if (entity == null) {
			return MobBabySettings.DISABLED;
		}
		JsonObject variant = MobEntityManager.resolveConfiguredEntityVariantForRuntime(entity);
		JsonElement componentsElement = variant.get(MobConfigManager.FIELD_MOB_COMPONENTS);
		if (componentsElement == null || !componentsElement.isJsonObject()) {
			return MobBabySettings.DISABLED;
		}
		JsonObject components = componentsElement.getAsJsonObject();
		JsonElement mobBabyElement = components.get(MobConfigManager.FIELD_MOB_BABY);
		if (mobBabyElement == null || !mobBabyElement.isJsonObject()) {
			return MobBabySettings.DISABLED;
		}
		JsonObject mobBaby = mobBabyElement.getAsJsonObject();
		JsonElement ageableElement = mobBaby.get(MobConfigManager.FIELD_AGEABLE);
		if (ageableElement == null || ageableElement.isJsonNull()) {
			return MobBabySettings.DISABLED;
		}
		boolean ageable = false;
		double durationSeconds = readDouble(mobBaby, MobConfigManager.FIELD_DURATION, 0.0D);
		if (ageableElement.isJsonObject()) {
			JsonObject ageableGroup = ageableElement.getAsJsonObject();
			ageable = readBoolean(ageableGroup, MobConfigManager.FIELD_ENABLED, false);
			durationSeconds = readDouble(ageableGroup, MobConfigManager.FIELD_DURATION, durationSeconds);
		} else if (ageableElement.isJsonPrimitive() && ageableElement.getAsJsonPrimitive().isBoolean()) {
			ageable = ageableElement.getAsBoolean();
		} else {
			return MobBabySettings.DISABLED;
		}
		return new MobBabySettings(true, ageable, resolveDurationTicks(durationSeconds));
	}

	public static boolean applyMobBabyComponent(LivingEntity entity) {
		if (!(entity instanceof AgeableMob ageableMob)) {
			return false;
		}
		MobBabySettings settings = resolveMobBabySettings(entity);
		if (!settings.configured() || !ageableMob.isBaby()) {
			return false;
		}
		int desiredAge = settings.ageable()
			? settings.durationTicks() <= 0L ? 0 : (int) -settings.durationTicks()
			: AgeableMob.BABY_START_AGE;
		if (settings.ageable() && ageableMob.getAge() != AgeableMob.BABY_START_AGE && settings.durationTicks() > 0L) {
			return false;
		}
		if (ageableMob.getAge() == desiredAge) {
			return false;
		}
		ageableMob.setAge(desiredAge);
		return true;
	}

	public static boolean applyWorldDifficultyScaling(LivingEntity entity) {
		if (entity == null || !MobWorldDifficultyManager.isEnabled()
			|| !EntityConfigManager.isWorldDifficultyScalingEnabled(MobEntityManager.resolveMobFileConfigRootForRuntime(MobEntityManager.resolveRuntimeMobFileKey(entity)))) return false;
		if (entity instanceof MobEntityManager.DifficultyState state && state.madokuCraft$isWorldDifficultyScalingApplied()) {
			return false;
		}
		boolean hardcore = entity.level().getServer() != null && entity.level().getServer().isHardcore();
		double healthBefore = readBaseValue(entity, Attributes.MAX_HEALTH);
		double damageBefore = readBaseValue(entity, Attributes.ATTACK_DAMAGE);
		double movementSpeedBefore = readBaseValue(entity, Attributes.MOVEMENT_SPEED);
		double flyingSpeedBefore = readBaseValue(entity, Attributes.FLYING_SPEED);
		double swimmingSpeedBefore = readBaseValue(entity, Attributes.WATER_MOVEMENT_EFFICIENCY);
		double healthAdjustment = add(entity, Attributes.MAX_HEALTH, MobConfigManager.FIELD_HEALTH, hardcore);
		double damageAdjustment = add(entity, Attributes.ATTACK_DAMAGE, MobConfigManager.FIELD_DAMAGE, hardcore);
		double movementSpeedAdjustment = add(entity, Attributes.MOVEMENT_SPEED, MobConfigManager.FIELD_MOVEMENT_SPEED, hardcore);
		double flyingSpeedAdjustment = add(entity, Attributes.FLYING_SPEED, MobConfigManager.FIELD_FLYING_SPEED, hardcore);
		double swimmingSpeedAdjustment = add(entity, Attributes.WATER_MOVEMENT_EFFICIENCY, MobConfigManager.FIELD_SWIMMING_SPEED, hardcore);
		boolean changed = healthAdjustment != 0.0D
			|| damageAdjustment != 0.0D
			|| movementSpeedAdjustment != 0.0D
			|| flyingSpeedAdjustment != 0.0D
			|| swimmingSpeedAdjustment != 0.0D;
		int baseExperienceDrop = MobEntityManager.resolveMobExperienceDropForRuntime(entity);
		int resolvedExperienceDrop = (int) Math.round(MobWorldDifficultyManager.resolveValue(
			MobConfigManager.FIELD_EXPERIENCE_DROP,
			baseExperienceDrop,
			entity.level().getDifficulty(),
			hardcore
		));
		if (resolvedExperienceDrop != baseExperienceDrop) {
			MobEntityManager.applyExperienceDropForRuntime(entity, resolvedExperienceDrop);
			changed = true;
		}
		if (entity instanceof MobEntityManager.DifficultyState state) {
			state.madokuCraft$setWorldDifficultyScalingApplied(true);
		}
		emitWorldDifficultyDebug(
			entity,
			hardcore,
			healthBefore,
			healthAdjustment,
			damageBefore,
			damageAdjustment,
			movementSpeedBefore,
			movementSpeedAdjustment,
			flyingSpeedBefore,
			flyingSpeedAdjustment,
			swimmingSpeedBefore,
			swimmingSpeedAdjustment,
			baseExperienceDrop,
			resolvedExperienceDrop - baseExperienceDrop
		);
		return changed;
	}

	private static boolean set(LivingEntity entity, net.minecraft.core.Holder<net.minecraft.world.entity.ai.attributes.Attribute> attribute, JsonObject root, String key) {
		JsonElement value = root.get(key);
		if (value == null || !value.isJsonPrimitive() || !value.getAsJsonPrimitive().isNumber()) return false;
		AttributeInstance instance = entity.getAttribute(attribute);
		if (instance == null) return false;
		double resolved = value.getAsDouble();
		if (!Double.isFinite(resolved)) return false;
		instance.setBaseValue(Math.max(0.0D, resolved));
		if (attribute == Attributes.MAX_HEALTH) entity.setHealth(Math.min(entity.getHealth(), entity.getMaxHealth()));
		return true;
	}

	private static double add(LivingEntity entity, net.minecraft.core.Holder<net.minecraft.world.entity.ai.attributes.Attribute> attribute, String key, boolean hardcore) {
		AttributeInstance instance = entity.getAttribute(attribute);
		if (instance == null) return 0.0D;
		double base = instance.getBaseValue();
		double addition = MobWorldDifficultyManager.resolveAddition(key, base, entity.level().getDifficulty(), hardcore);
		if (!Double.isFinite(addition) || addition == 0.0D) return 0.0D;
		instance.setBaseValue(Math.max(0.0D, base + addition));
		if (attribute == Attributes.MAX_HEALTH) entity.setHealth(Math.min(entity.getHealth(), entity.getMaxHealth()));
		return addition;
	}

	private static void emitBaseStatsDebug(LivingEntity entity) {
		String entry = "apply-configured-components";
		if (entity == null || !MadokuDebugManager.shouldEmit("mob", "entity", "components", entry)) {
			return;
		}
		MadokuDebugManager.event("mob.stats.base", "mob", "entity", "components", entry)
			.side(MadokuDebugManager.Side.SERVER)
			.world(entity.level().dimension().identifier().toString())
			.subject(entity.getType().toString())
			.field("entity-uuid", entity.getUUID())
			.field("base-health", readBaseValue(entity, Attributes.MAX_HEALTH))
			.field("base-damage", readBaseValue(entity, Attributes.ATTACK_DAMAGE))
			.field("base-movement-speed", readBaseValue(entity, Attributes.MOVEMENT_SPEED))
			.field("base-flying-speed", readBaseValue(entity, Attributes.FLYING_SPEED))
			.field("base-swimming-speed", readBaseValue(entity, Attributes.WATER_MOVEMENT_EFFICIENCY))
			.field("base-armor", readBaseValue(entity, Attributes.ARMOR))
			.field("base-knockback-resistance", readBaseValue(entity, Attributes.KNOCKBACK_RESISTANCE))
			.field("base-scale", readBaseValue(entity, Attributes.SCALE))
			.field("base-experience-drop", MobEntityManager.resolveMobExperienceDropForRuntime(entity))
			.log();
	}

	private static void emitWorldDifficultyDebug(
		LivingEntity entity,
		boolean hardcore,
		double healthBefore,
		double healthAdjustment,
		double damageBefore,
		double damageAdjustment,
		double movementSpeedBefore,
		double movementSpeedAdjustment,
		double flyingSpeedBefore,
		double flyingSpeedAdjustment,
		double swimmingSpeedBefore,
		double swimmingSpeedAdjustment,
		int experienceBefore,
		double experienceAdjustment
	) {
		String entry = "apply-world-difficulty-scaling";
		if (entity == null || !MadokuDebugManager.shouldEmit("mob", "entity", "components", entry)) {
			return;
		}
		MadokuDebugManager.event("mob.stats.world_difficulty", "mob", "entity", "components", entry)
			.side(MadokuDebugManager.Side.SERVER)
			.world(entity.level().dimension().identifier().toString())
			.subject(entity.getType().toString())
			.field("entity-uuid", entity.getUUID())
			.field("difficulty", entity.level().getDifficulty())
			.field("hardcore", hardcore)
			.field("health-before", healthBefore)
			.field("health-adjustment", healthAdjustment)
			.field("health-after", readBaseValue(entity, Attributes.MAX_HEALTH))
			.field("damage-before", damageBefore)
			.field("damage-adjustment", damageAdjustment)
			.field("damage-after", readBaseValue(entity, Attributes.ATTACK_DAMAGE))
			.field("movement-speed-before", movementSpeedBefore)
			.field("movement-speed-adjustment", movementSpeedAdjustment)
			.field("movement-speed-after", readBaseValue(entity, Attributes.MOVEMENT_SPEED))
			.field("flying-speed-before", flyingSpeedBefore)
			.field("flying-speed-adjustment", flyingSpeedAdjustment)
			.field("flying-speed-after", readBaseValue(entity, Attributes.FLYING_SPEED))
			.field("swimming-speed-before", swimmingSpeedBefore)
			.field("swimming-speed-adjustment", swimmingSpeedAdjustment)
			.field("swimming-speed-after", readBaseValue(entity, Attributes.WATER_MOVEMENT_EFFICIENCY))
			.field("experience-before", experienceBefore)
			.field("experience-adjustment", experienceAdjustment)
			.field("experience-after", MobEntityManager.resolveMobExperienceDropForRuntime(entity))
			.log();
	}

	private static double readBaseValue(LivingEntity entity, net.minecraft.core.Holder<net.minecraft.world.entity.ai.attributes.Attribute> attribute) {
		if (entity == null || attribute == null) {
			return 0.0D;
		}
		AttributeInstance instance = entity.getAttribute(attribute);
		return instance == null ? 0.0D : instance.getBaseValue();
	}

	private static boolean readBoolean(JsonObject root, String key, boolean fallback) {
		JsonElement value = root == null ? null : root.get(key);
		if (value == null || !value.isJsonPrimitive() || !value.getAsJsonPrimitive().isBoolean()) {
			return fallback;
		}
		return value.getAsBoolean();
	}

	private static double readDouble(JsonObject root, String key, double fallback) {
		JsonElement value = root == null ? null : root.get(key);
		if (value == null || !value.isJsonPrimitive() || !value.getAsJsonPrimitive().isNumber()) {
			return fallback;
		}
		try {
			double resolved = value.getAsDouble();
			return Double.isFinite(resolved) ? resolved : fallback;
		} catch (RuntimeException ignored) {
			return fallback;
		}
	}

	private static long resolveDurationTicks(double durationSeconds) {
		if (!Double.isFinite(durationSeconds) || durationSeconds <= 0.0D) {
			return 0L;
		}
		double ticks = durationSeconds * 20.0D;
		return ticks >= Integer.MAX_VALUE ? Integer.MAX_VALUE : Math.max(1L, Math.round(ticks));
	}
}
