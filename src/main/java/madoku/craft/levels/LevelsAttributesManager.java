package madoku.craft.levels;

import madoku.craft.MadokuCraft;
import madoku.craft.attributes.HungerAPIManager;
import madoku.craft.pet.PetAbilitiesManager;
import net.minecraft.resources.Identifier;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.ai.attributes.AttributeInstance;
import net.minecraft.world.entity.ai.attributes.AttributeModifier;
import net.minecraft.world.entity.ai.attributes.Attributes;

/** Applies configured level-stat increments to a player's runtime attributes. */
public final class LevelsAttributesManager {
	private static final Identifier HEALTH_MODIFIER = id("levels_health");
	private static final Identifier STRENGTH_MODIFIER = id("levels_strength");
	private static final Identifier ARMOR_MODIFIER = id("levels_armor");
	private static final Identifier LUCK_MODIFIER = id("levels_luck");
	private static final Identifier MOVEMENT_SPEED_MODIFIER = id("levels_movement_speed");

	private LevelsAttributesManager() { }

	public static void initialize() { }

	public static void reset() { }

	public static void applyPlayerAttributes(ServerPlayer player) {
		if (player == null) return;
		LevelsPlayerManager.PlayerState state = LevelsPlayerManager.state(player);
		apply(player.getAttribute(Attributes.MAX_HEALTH), HEALTH_MODIFIER, valueAtLevel(player, LevelStat.HEALTH, state.statLevel(LevelStat.HEALTH)));
		PetAbilitiesManager.applyPlayerMaxHealthAbilityBonus(player);
		apply(player.getAttribute(Attributes.ATTACK_DAMAGE), STRENGTH_MODIFIER, valueAtLevel(player, LevelStat.STRENGTH, state.statLevel(LevelStat.STRENGTH)));
		PetAbilitiesManager.applyPlayerDamageAbilityBonus(player);
		apply(player.getAttribute(Attributes.ARMOR), ARMOR_MODIFIER, valueAtLevel(player, LevelStat.ARMOR, state.statLevel(LevelStat.ARMOR)));
		PetAbilitiesManager.applyPlayerArmorAbilityBonus(player);
		apply(player.getAttribute(Attributes.LUCK), LUCK_MODIFIER, valueAtLevel(player, LevelStat.LUCK, state.statLevel(LevelStat.LUCK)));
		apply(player.getAttribute(Attributes.MOVEMENT_SPEED), MOVEMENT_SPEED_MODIFIER, valueAtLevel(player, LevelStat.MOVEMENT_SPEED, state.statLevel(LevelStat.MOVEMENT_SPEED)));
		if (player.getHealth() > player.getMaxHealth()) player.setHealth(player.getMaxHealth());
	}

	public static int hungerBonusPoints(ServerPlayer player, int level) {
		if (player == null || !HungerAPIManager.isEnabled() || !MadokuLevelsManager.isEnabled()) return 0;
		return Math.max(0, (int) Math.round(valueAtLevel(player, LevelStat.HUNGER, level)));
	}

	public static double valueAtLevel(ServerPlayer player, LevelStat stat, int level) {
		if (stat == null || !MadokuLevelsManager.isEnabled()) return 0.0d;
		LevelsConfigManager.StatSettings settings = LevelsConfigManager.stat(stat);
		double increment = settings.value();
		if (settings.type() == LevelsConfigManager.IncrementType.PERCENTAGE) {
			increment *= baseValue(player, stat);
		}
		return Math.max(0.0d, increment * Math.max(0, stat.clampLevel(level)));
	}

	private static double baseValue(ServerPlayer player, LevelStat stat) {
		if (player == null) {
			return switch (stat) {
				case HEALTH -> 20.0d;
				case HUNGER -> 20.0d;
				case MOVEMENT_SPEED -> 0.1d;
				case STRENGTH -> 1.0d;
				case ARMOR, LUCK -> 0.0d;
			};
		}
		return switch (stat) {
			case HEALTH -> baseAttribute(player, Attributes.MAX_HEALTH, 20.0d);
			case STRENGTH -> baseAttribute(player, Attributes.ATTACK_DAMAGE, 1.0d);
			case ARMOR -> baseAttribute(player, Attributes.ARMOR, 0.0d);
			case LUCK -> baseAttribute(player, Attributes.LUCK, 0.0d);
			case MOVEMENT_SPEED -> baseAttribute(player, Attributes.MOVEMENT_SPEED, 0.1d);
			case HUNGER -> 20.0d;
		};
	}

	private static double baseAttribute(ServerPlayer player, net.minecraft.core.Holder<net.minecraft.world.entity.ai.attributes.Attribute> attribute, double fallback) {
		AttributeInstance instance = player.getAttribute(attribute);
		return instance == null ? fallback : instance.getBaseValue();
	}

	private static void apply(AttributeInstance attribute, Identifier id, double amount) {
		if (attribute == null) return;
		attribute.removeModifier(id);
		if (amount > 0.0d) attribute.addOrUpdateTransientModifier(new AttributeModifier(id, amount, AttributeModifier.Operation.ADD_VALUE));
	}

	private static Identifier id(String path) {
		return Identifier.fromNamespaceAndPath(MadokuCraft.MOD_ID, path);
	}
}
