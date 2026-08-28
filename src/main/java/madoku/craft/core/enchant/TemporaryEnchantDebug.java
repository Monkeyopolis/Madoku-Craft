package madoku.craft.core.enchant;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;

/** Temporary diagnostics for tracing configured enchantment effects. */
public final class TemporaryEnchantDebug {
	private static final Logger LOGGER = LoggerFactory.getLogger("MadokuCraft/TemporaryEnchantDebug");
	private static final boolean ENABLED = true;
	private static final Set<String> LOGGED_FEATHER_FALLING_PROTECTION = ConcurrentHashMap.newKeySet();

	private TemporaryEnchantDebug() {
	}

	public static void featherFallingDefinition(
		boolean present,
		boolean enabled,
		double baseFallDamageReduction,
		double levelAdjustment
	) {
		if (!ENABLED) return;
		LOGGER.info(
			"feather-falling definition present={} enabled={} base-fall-damage-reduction={} level-adjustment={}",
			present,
			enabled,
			baseFallDamageReduction,
			levelAdjustment
		);
	}

	public static void featherFallingProtection(
		int level,
		double configuredPercent,
		double protectionPoints
	) {
		if (!ENABLED) return;
		String key = level + ":" + configuredPercent + ":" + protectionPoints;
		if (!LOGGED_FEATHER_FALLING_PROTECTION.add(key)) return;
		LOGGER.info(
			"feather-falling protection level={} configured-percent={} protection-points={} applied={}",
			level,
			configuredPercent,
			protectionPoints,
			true
		);
	}

	public static void featherFallingEvaluation(
		String damageType,
		String item,
		int level,
		boolean fallDamage,
		boolean bypassesInvulnerability,
		boolean definitionPresent,
		boolean definitionEnabled,
		boolean compatible,
		double configuredPercent,
		double protectionPoints,
		float protectionBefore,
		float protectionAfter,
		boolean replaced
	) {
		if (!ENABLED) return;
		String key = damageType + ":" + item + ":" + level + ":" + fallDamage + ":"
			+ bypassesInvulnerability + ":" + definitionPresent + ":" + definitionEnabled + ":"
			+ compatible + ":" + configuredPercent + ":" + protectionBefore + ":" + protectionAfter + ":" + replaced;
		if (!LOGGED_FEATHER_FALLING_PROTECTION.add(key)) return;
		LOGGER.info(
			"feather-falling evaluation damage-type={} item={} level={} fall-damage={} bypasses-invulnerability={} definition-present={} definition-enabled={} compatible={} configured-percent={} protection-points={} protection-before={} protection-after={} vanilla-replaced={}",
			damageType,
			item,
			level,
			fallDamage,
			bypassesInvulnerability,
			definitionPresent,
			definitionEnabled,
			compatible,
			configuredPercent,
			protectionPoints,
			protectionBefore,
			protectionAfter,
			replaced
		);
	}
}
