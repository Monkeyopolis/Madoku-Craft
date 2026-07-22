package madoku.craft.mob.system;

import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.ai.attributes.AttributeInstance;
import net.minecraft.world.entity.ai.attributes.Attributes;

/** Runtime group for configured entity components and attributes. */
public final class EntityComponentsManager {
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
		return changed;
	}

	public static boolean applyWorldDifficultyScaling(LivingEntity entity) {
		if (entity == null || !MobWorldDifficultyManager.isEnabled()
			|| !EntityConfigManager.isWorldDifficultyScalingEnabled(MobEntityManager.resolveMobFileConfigRootForRuntime(MobEntityManager.resolveRuntimeMobFileKey(entity)))) return false;
		boolean hardcore = entity.level().getServer() != null && entity.level().getServer().isHardcore();
		boolean changed = false;
		changed |= add(entity, Attributes.MAX_HEALTH, MobConfigManager.FIELD_HEALTH, hardcore);
		changed |= add(entity, Attributes.ATTACK_DAMAGE, MobConfigManager.FIELD_DAMAGE, hardcore);
		changed |= add(entity, Attributes.MOVEMENT_SPEED, MobConfigManager.FIELD_MOVEMENT_SPEED, hardcore);
		changed |= add(entity, Attributes.FLYING_SPEED, MobConfigManager.FIELD_FLYING_SPEED, hardcore);
		changed |= add(entity, Attributes.WATER_MOVEMENT_EFFICIENCY, MobConfigManager.FIELD_SWIMMING_SPEED, hardcore);
		changed |= add(entity, Attributes.ARMOR, MobConfigManager.FIELD_ARMOR, hardcore);
		changed |= add(entity, Attributes.KNOCKBACK_RESISTANCE, MobConfigManager.FIELD_KNOCKBACK_RESISTANCE, hardcore);
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

	private static boolean add(LivingEntity entity, net.minecraft.core.Holder<net.minecraft.world.entity.ai.attributes.Attribute> attribute, String key, boolean hardcore) {
		AttributeInstance instance = entity.getAttribute(attribute);
		if (instance == null) return false;
		double base = instance.getBaseValue();
		double addition = MobWorldDifficultyManager.resolveAddition(key, base, entity.level().getDifficulty(), hardcore);
		if (!Double.isFinite(addition) || addition == 0.0D) return false;
		instance.setBaseValue(Math.max(0.0D, base + addition));
		if (attribute == Attributes.MAX_HEALTH) entity.setHealth(Math.min(entity.getHealth(), entity.getMaxHealth()));
		return true;
	}
}
