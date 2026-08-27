package madoku.craft.core.enchant;

import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.ai.attributes.AttributeModifier;
import net.minecraft.world.item.ItemStack;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;

/** Temporary diagnostics for tracing configured enchantment effects. */
public final class TemporaryEnchantDebug {
	private static final Logger LOGGER = LoggerFactory.getLogger("MadokuCraft/TemporaryEnchantDebug");
	private static final boolean ENABLED = true;
	private static final Set<String> LOGGED_DEPTH_STRIDER_MODIFIERS = ConcurrentHashMap.newKeySet();
	private static final Set<String> LOGGED_BANE_EVENTS = ConcurrentHashMap.newKeySet();

	private TemporaryEnchantDebug() {
	}

	public static void depthStriderDefinition(
		boolean present,
		boolean enabled,
		double baseWaterMovementEfficiency,
		double levelAdjustment
	) {
		if (!ENABLED) return;
		LOGGER.info(
			"depth-strider definition present={} enabled={} base-water-movement-efficiency={} level-adjustment={}",
			present,
			enabled,
			baseWaterMovementEfficiency,
			levelAdjustment
		);
	}

	public static void depthStriderModifier(
		int level,
		AttributeModifier vanillaModifier,
		AttributeModifier configuredModifier
	) {
		if (!ENABLED || vanillaModifier == null || configuredModifier == null) return;
		String key = level + ":" + vanillaModifier.amount() + ":" + configuredModifier.amount();
		if (!LOGGED_DEPTH_STRIDER_MODIFIERS.add(key)) return;

		LOGGER.info(
			"depth-strider modifier level={} vanilla-amount={} configured-amount={} configured-percent={} applied={}",
			level,
			vanillaModifier.amount(),
			configuredModifier.amount(),
			configuredModifier.amount() * 100.0D,
			true
		);
	}

	public static void baneDefinition(
		boolean present,
		boolean enabled,
		double baseEffect,
		double levelAdjustment,
		double baseDuration,
		double levelDuration
	) {
		if (!ENABLED) return;
		LOGGER.info(
			"bane-of-arthropods definition present={} enabled={} base-effect={} level-adjustment={} base-duration-seconds={} level-duration-adjustment-seconds={}",
			present,
			enabled,
			baseEffect,
			levelAdjustment,
			baseDuration,
			levelDuration
		);
	}

	public static void baneVanillaDamageSuppressed(
		int level,
		ItemStack weapon,
		Entity target,
		float incomingDamage
	) {
		if (!ENABLED) return;
		String key = "damage:" + level + ":" + describe(target) + ":" + describe(weapon);
		if (!LOGGED_BANE_EVENTS.add(key)) return;
		LOGGER.info(
			"bane-of-arthropods vanilla damage suppressed level={} weapon={} target={} incoming-damage={} applied={}",
			level,
			describe(weapon),
			describe(target),
			incomingDamage,
			false
		);
	}

	public static void baneVanillaPostAttackSuppressed(
		int level,
		Entity target
	) {
		if (!ENABLED) return;
		String key = "post-attack:" + level + ":" + describe(target);
		if (!LOGGED_BANE_EVENTS.add(key)) return;
		LOGGER.info(
			"bane-of-arthropods vanilla post-attack suppressed level={} target={} applied={}",
			level,
			describe(target),
			false
		);
	}

	public static void baneReplacement(
		LivingEntity target,
		LivingEntity attacker,
		ItemStack weapon,
		int level,
		int amplifier,
		int durationTicks
	) {
		if (!ENABLED) return;
		String key = "replacement:" + level + ":" + describe(target) + ":" + amplifier + ":" + durationTicks;
		if (!LOGGED_BANE_EVENTS.add(key)) return;
		LOGGER.info(
			"bane-of-arthropods configured effect applied attacker={} target={} weapon={} level={} slowness-amplifier={} duration-ticks={} applied={}",
			describe(attacker),
			describe(target),
			describe(weapon),
			level,
			amplifier,
			durationTicks,
			true
		);
	}

	private static String describe(Entity entity) {
		return entity == null ? "unknown" : entity.getName().getString() + "(" + entity.getType() + ")";
	}

	private static String describe(ItemStack stack) {
		return stack == null || stack.isEmpty() ? "empty" : stack.getItem().toString();
	}
}
