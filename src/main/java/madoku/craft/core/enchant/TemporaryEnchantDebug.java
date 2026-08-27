package madoku.craft.core.enchant;

import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.enchantment.EnchantmentHelper;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

/** Temporary diagnostics for tracing configured enchantment effects. */
public final class TemporaryEnchantDebug {
	private static final Logger LOGGER = LoggerFactory.getLogger("MadokuCraft/TemporaryEnchantDebug");
	private static final boolean ENABLED = true;
	private static final long LOG_INTERVAL_TICKS = 20L;
	private static final Map<UUID, Long> LAST_BANE_LOG_TICKS = new ConcurrentHashMap<>();

	private TemporaryEnchantDebug() {
	}

	public static void baneDefinition(
		boolean present,
		boolean enabled,
		double baseEffect,
		double levelEffectAdjustment,
		double baseDuration,
		double levelDurationAdjustment
	) {
		if (!ENABLED) return;
		LOGGER.info(
			"bane-of-arthropods definition present={} enabled={} base-effect={} level-effect-adjustment={} base-duration={} level-duration-adjustment={}",
			present,
			enabled,
			baseEffect,
			levelEffectAdjustment,
			baseDuration,
			levelDurationAdjustment
		);
	}

	public static void baneOnHit(
		LivingEntity target,
		LivingEntity attacker,
		ItemStack weapon,
		int level,
		int effectAmplifier,
		int durationTicks
	) {
		if (!ENABLED || target == null) return;

		long currentTick = target.tickCount;
		Long lastTick = LAST_BANE_LOG_TICKS.get(target.getUUID());
		if (lastTick != null && currentTick - lastTick < LOG_INTERVAL_TICKS) return;
		LAST_BANE_LOG_TICKS.put(target.getUUID(), currentTick);

		LOGGER.info(
			"bane-of-arthropods target={} target-type={} attacker={} weapon={} enchantments={} level={} effect-amplifier={} duration-ticks={} applied={}",
			target.getName().getString(),
			target.getType(),
			attacker == null ? "unknown" : attacker.getName().getString(),
			itemId(weapon),
			enchantments(weapon),
			level,
			effectAmplifier,
			durationTicks,
			true
		);
	}

	private static String itemId(ItemStack stack) {
		if (stack == null || stack.isEmpty()) return "empty";
		return BuiltInRegistries.ITEM.getKey(stack.getItem()).toString();
	}

	private static String enchantments(ItemStack stack) {
		if (stack == null || stack.isEmpty()) return "empty";
		return EnchantmentHelper.getEnchantmentsForCrafting(stack).toString();
	}
}
