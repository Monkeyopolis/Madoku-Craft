package madoku.craft.java.core.enchant;

import com.google.gson.JsonArray;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import madoku.craft.java.core.json.JSONFormatAPIManager;
import madoku.craft.java.core.json.JSONAPIManager;
import net.minecraft.core.Holder;
import net.minecraft.core.Registry;
import net.minecraft.core.component.DataComponents;
import net.minecraft.server.MinecraftServer;
import net.minecraft.tags.ItemTags;
import net.minecraft.network.chat.contents.TranslatableContents;
import net.minecraft.world.entity.EquipmentSlot;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.item.enchantment.Enchantment;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.IOException;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;

/** Owns configured enchantment definitions used by the Madoku enchantment-book system. */
public final class BooksConfigAPIManager {
	private static final Logger LOGGER = LoggerFactory.getLogger(BooksConfigAPIManager.class);
	static final String AQUA_AFFINITY_ID = "minecraft:aqua_affinity";
	static final String BANE_OF_ARTHROPODS_ID = "minecraft:bane_of_arthropods";
	static final String PROTECTION_ID = "minecraft:protection";
	static final String BLAST_PROTECTION_ID = "minecraft:blast_protection";
	static final String PROJECTILE_PROTECTION_ID = "minecraft:projectile_protection";
	static final String RESPIRATION_ID = "minecraft:respiration";
	static final String RIPTIDE_ID = "minecraft:riptide";
	static final String BREACH_ID = "minecraft:breach";
	static final String DEPTH_STRIDER_ID = "minecraft:depth_strider";
	static final String EFFICIENCY_ID = "minecraft:efficiency";
	static final String FEATHER_FALLING_ID = "minecraft:feather_falling";
	static final String FROST_WALKER_ID = "minecraft:frost_walker";
	static final String FORTUNE_ID = "minecraft:fortune";
	static final String LOOTING_ID = "minecraft:looting";
	static final String LOYALTY_ID = "minecraft:loyalty";
	static final String FIRE_ASPECT_ID = "minecraft:fire_aspect";
	static final String FIRE_PROTECTION_ID = "minecraft:fire_protection";
	static final String FLAME_ID = "minecraft:flame";
	static final String IMPALING_ID = "minecraft:impaling";
	static final String SOUL_SPEED_ID = "minecraft:soul_speed";
	static final String SWEEPING_EDGE_ID = "minecraft:sweeping_edge";
	static final String SWIFT_SNEAK_ID = "minecraft:swift_sneak";
	static final String WIND_BURST_ID = "minecraft:wind_burst";
	static final String THORNS_ID = "minecraft:thorns";
	static final String UNBREAKING_ID = "minecraft:unbreaking";
	static final String SHARPNESS_ID = "minecraft:sharpness";
	static final String SILK_TOUCH_ID = "minecraft:silk_touch";
	static final String SMITE_ID = "minecraft:smite";
	static final String INFINITY_ID = "minecraft:infinity";
	static final String KNOCKBACK_ID = "minecraft:knockback";
	static final String PUNCH_ID = "minecraft:punch";
	static final String POWER_ID = "minecraft:power";
	static final String QUICK_CHARGE_ID = "minecraft:quick_charge";
	static final String LUCK_OF_THE_SEA_ID = "minecraft:luck_of_the_sea";
	static final String LUNGE_ID = "minecraft:lunge";
	static final String LURE_ID = "minecraft:lure";
	static final String MENDING_ID = "minecraft:mending";
	static final String PIERCING_ID = "minecraft:piercing";
	static final String MULTISHOT_ID = "minecraft:multishot";
	private static final String FIELD_ENCHANTMENT_ID = "enchantment-id";
	private static final String FIELD_MAXIMUM_LEVEL = "maximum-level";
	private static final String FIELD_COMPATIBLE_ITEMS = "compatible-items";
	private static final String FIELD_CONFLICTING_ENCHANTMENT = "conflicting-enchantment";
	private static final String FIELD_WEIGHT = "weight";
	private static final String FIELD_AQUA_AFFINITY = "aqua-affinity";
	private static final String FIELD_BANE_OF_ARTHROPODS = "bane-of-arthropods";
	private static final String FIELD_PROTECTION = "protection";
	private static final String FIELD_BASE_DAMAGE_REDUCTION = "base-damage-reduction";
	private static final String FIELD_EFFECT = "effect";
	private static final String FIELD_BASE_EFFECT = "base-effect";
	private static final String FIELD_DURATION = "duration";
	private static final String FIELD_BASE_DURATION = "base-duration";
	private static final String FIELD_LEVEL_DURATION = "level-duration";
	private static final String FIELD_BASE_KNOCKBACK_RESISTANCE = "base-knockback-resistance";
	private static final String FIELD_LEVEL_KNOCKBACK_RESISTANCE = "level-knockback-resistance";
	private static final String FIELD_LEVEL_ARMOR_PENETRATION = "level-armor-penetration";
	private static final String FIELD_BLAST_PROTECTION = "blast-protection";
	private static final String FIELD_EXPLOSIVE_PROTECTION = "explosive-protection";
	private static final String FIELD_EXPLOSION_KNOCKBACK_RESISTANCE = "explosion-knockback-resistance";
	private static final String FIELD_PROJECTILE_PROTECTION = "projectile-protection";
	private static final String FIELD_BASE_VALUE = "base-value";
	private static final String FIELD_BREACH = "breach";
	private static final String FIELD_BASE_ARMOR_PENETRATION = "base-armor-penetration";
	private static final String FIELD_DEPTH_STRIDER = "depth-strider";
	private static final String FIELD_BASE_WATER_MOVEMENT_EFFICIENCY = "base-water-movement-efficiency";
	private static final String FIELD_EFFICIENCY = "efficiency";
	private static final String FIELD_BASE_MINING_EFFICIENCY = "base-mining-efficiency";
	private static final String FIELD_FEATHER_FALLING = "efficiency";
	private static final String FIELD_BASE_FALL_DAMAGE_REDUCTION = "base-fall-damage-reduction";
	private static final String FIELD_FORTUNE = "fortune";
	private static final String FIELD_LOOTING = "looting";
	private static final String FIELD_BASE_MULTIPLIER_CHANCE = "base-multiplier-chance";
	private static final String FIELD_FIRE_ASPECT = "fire-aspect";
	private static final String FIELD_BASE_FIRE_DURATION = "base-fire-duration";
	private static final String FIELD_FIRE_PROTECTION = "fire-protection";
	private static final String FIELD_BURN_PROTECTION = "burn-protection";
	private static final String FIELD_BURN_DURATION_REDUCTION = "burn-duration-reduction";
	private static final String FIELD_FLAME = "flame";
	private static final String FIELD_IMPALING = "impaling";
	private static final String FIELD_SOUL_SPEED = "soul-speed";
	private static final String FIELD_BASE_SPEED_INCREASE = "base-speed-increase";
	private static final String FIELD_SWEEPING_EDGE = "sweeping-edge";
	private static final String FIELD_BASE_SWEEPING_DAMAGE = "base-sweeping-damage";
	private static final String FIELD_SHARPNESS = "sharpness";
	private static final String FIELD_SMITE = "smite";
	private static final String FIELD_VULNERABILITY = "vulnerability";
	private static final String FIELD_BASE_VULNERABILITY = "base-vulnerability";
	private static final String FIELD_GLOW_EFFECT = "glow-effect";
	private static final String FIELD_THORNS = "thorns";
	private static final String FIELD_UNBREAKING = "unbreaking";
	private static final String FIELD_DAMAGE_REFLECTION = "damage-reflection";
	private static final String FIELD_DAMAGE_CHANCE = "damage-chance";
	private static final String FIELD_BASE_DAMAGE_PERCENTAGE = "base-damage-percentage";
	private static final String FIELD_DURABILITY_BASE_INCREASE = "durability-base-increase";
	private static final String FIELD_BASE_THORNS_CHANCE = "base-thorns-chance";
	private static final String FIELD_LEVEL_THORNS_CHANCE = "level-thorns-chance";
	private static final String FIELD_BASE_ADDED_DAMAGE = "base-added-damage";
	private static final String FIELD_INFINITY = "infinity";
	private static final String FIELD_BASE_CHANCE = "base-chance";
	private static final String FIELD_KNOCKBACK = "knockback";
	private static final String FIELD_BASE_KNOCKBACK = "base-knockback";
	private static final String FIELD_PUNCH = "punch";
	private static final String FIELD_BASE_ADDED_KNOCKBACK = "base-added-knockback";
	private static final String FIELD_LUCK_OF_THE_SEA = "luck-of-the-sea";
	private static final String FIELD_TREASURE = "treasure";
	private static final String FIELD_JUNK = "junk";
	private static final String FIELD_FISH = "fish";
	private static final String FIELD_BASE_CHANCE_ADJUSTMENT = "base-chance-adjustment";
	private static final String FIELD_BASE_ADJUSTMENT = "base-adjustment";
	private static final String FIELD_BASE_SUBMERGED_MINING_SPEED = "base-submerged-mining-speed";
	private static final String FIELD_LEVEL_ADJUSTMENT = "level-adjustment";
	private static final String FIELD_ENABLED = "enabled";
	private static final String AQUA_AFFINITY_FILE_KEY = "aqua-affinity";
	private static final String BANE_OF_ARTHROPODS_FILE_KEY = "bane-of-arthropods";
	private static final String PROTECTION_FILE_KEY = "protection";
	private static final String BLAST_PROTECTION_FILE_KEY = "blast-protection";
	private static final String PROJECTILE_PROTECTION_FILE_KEY = "projectile-protection";
	private static final String RESPIRATION_FILE_KEY = "respiration";
	private static final String RIPTIDE_FILE_KEY = "riptide";
	private static final String BREACH_FILE_KEY = "breach";
	private static final String CHANNELING_FILE_KEY = "channeling";
	private static final String CURSE_OF_BINDING_FILE_KEY = "curse-of-binding";
	private static final String CURSE_OF_VANISHING_FILE_KEY = "curse-of-vanishing";
	private static final String DENSITY_FILE_KEY = "density";
	private static final String DEPTH_STRIDER_FILE_KEY = "depth-strider";
	private static final String EFFICIENCY_FILE_KEY = "efficiency";
	private static final String FEATHER_FALLING_FILE_KEY = "feather-falling";
	private static final String FROST_WALKER_FILE_KEY = "frost-walker";
	private static final String FORTUNE_FILE_KEY = "fortune";
	private static final String LOOTING_FILE_KEY = "looting";
	private static final String LOYALTY_FILE_KEY = "loyalty";
	private static final String FIRE_ASPECT_FILE_KEY = "fire-aspect";
	private static final String FIRE_PROTECTION_FILE_KEY = "fire-protection";
	private static final String FLAME_FILE_KEY = "flame";
	private static final String IMPALING_FILE_KEY = "impaling";
	private static final String SOUL_SPEED_FILE_KEY = "soul-speed";
	private static final String SWEEPING_EDGE_FILE_KEY = "sweeping-edge";
	private static final String SWIFT_SNEAK_FILE_KEY = "swift-sneak";
	private static final String WIND_BURST_FILE_KEY = "wind-burst";
	private static final String THORNS_FILE_KEY = "thorns";
	private static final String UNBREAKING_FILE_KEY = "unbreaking";
	private static final String SHARPNESS_FILE_KEY = "sharpness";
	private static final String SILK_TOUCH_FILE_KEY = "silk-touch";
	private static final String SMITE_FILE_KEY = "smite";
	private static final String INFINITY_FILE_KEY = "infinity";
	private static final String KNOCKBACK_FILE_KEY = "knockback";
	private static final String PUNCH_FILE_KEY = "punch";
	private static final String POWER_FILE_KEY = "power";
	private static final String QUICK_CHARGE_FILE_KEY = "quick-charge";
	private static final String LUCK_OF_THE_SEA_FILE_KEY = "luck-of-the-sea";
	private static final String LUNGE_FILE_KEY = "lunge";
	private static final String LURE_FILE_KEY = "lure";
	private static final String MENDING_FILE_KEY = "mending";
	private static final String FIELD_MENDING = "mending";
	private static final String PIERCING_FILE_KEY = "piercing";
	private static final String MULTISHOT_FILE_KEY = "multishot";
	private static final Map<String, EnchantmentDefinition> EMPTY_DEFINITIONS = Map.of();
	private static volatile Map<String, EnchantmentDefinition> definitions = EMPTY_DEFINITIONS;
	private static volatile Map<String, EnchantmentDefinition> clientSynchronizedDefinitions = EMPTY_DEFINITIONS;
	private static volatile boolean clientSynchronized;
	private static volatile Map<Enchantment, String> enchantmentIds = Map.of();

	private BooksConfigAPIManager() {
	}

	static void initialize() {
		reload();
	}

	static void reset() {
		definitions = EMPTY_DEFINITIONS;
		clientSynchronizedDefinitions = EMPTY_DEFINITIONS;
		clientSynchronized = false;
		enchantmentIds = Map.of();
	}

	static void onServerStarted(MinecraftServer server) {
		reload();
		if (server != null) rememberEnchantmentIds(server.registryAccess().lookupOrThrow(net.minecraft.core.registries.Registries.ENCHANTMENT));
	}

	static Map<String, EnchantmentDefinition> definitions() {
		return activeDefinitions();
	}

	static EnchantmentDefinition definition(String enchantmentId) {
		return enchantmentId == null ? null : activeDefinitions().get(enchantmentId);
	}

	static EnchantmentDefinition definitionForHolder(Holder<Enchantment> holder) {
		if (holder == null) return null;
		return holder.unwrapKey()
			.map(key -> activeDefinitions().get(key.identifier().toString()))
			.orElse(null);
	}

	static String createClientSyncSnapshot() {
		JsonObject snapshot = new JsonObject();
		snapshot.addProperty("enabled", EnchantConfigAPIManager.isEnabled());
		snapshot.addProperty("enchantment-table-enabled", EnchantConfigAPIManager.isEnchantmentTableEnabled());
		snapshot.addProperty("custom-enchantments-enabled", EnchantConfigAPIManager.areCustomEnchantmentsEnabled());
		JsonArray syncedDefinitions = new JsonArray();
		for (EnchantmentDefinition definition : definitions.values()) {
			syncedDefinitions.add(definition.toClientSyncJson());
		}
		snapshot.add("definitions", syncedDefinitions);
		return snapshot.toString();
	}

	static void applyClientSynchronizedSnapshot(String snapshot) {
		try {
			JsonElement parsed = JsonParser.parseString(snapshot == null ? "" : snapshot);
			if (!parsed.isJsonObject()) return;
			JsonObject root = parsed.getAsJsonObject();
			JsonElement definitionsElement = root.get("definitions");
			if (definitionsElement == null || !definitionsElement.isJsonArray()) return;

			Map<String, EnchantmentDefinition> synchronizedDefinitions = new LinkedHashMap<>();
			for (JsonElement element : definitionsElement.getAsJsonArray()) {
				if (element == null || !element.isJsonObject()) continue;
				EnchantmentDefinition definition = parseClientSyncDefinition(element.getAsJsonObject());
				if (definition != null) synchronizedDefinitions.put(definition.enchantmentId, definition);
			}

			EnchantConfigAPIManager.applyClientSynchronizedSettings(
				readBoolean(root, "enabled", false),
				readBoolean(root, "enchantment-table-enabled", false),
				readBoolean(root, "custom-enchantments-enabled", false)
			);
			clientSynchronizedDefinitions = synchronizedDefinitions.isEmpty()
				? EMPTY_DEFINITIONS : Map.copyOf(synchronizedDefinitions);
			clientSynchronized = true;
		} catch (RuntimeException exception) {
			LOGGER.warn("Failed to apply synchronized Madoku enchantment configuration.", exception);
		}
	}

	static void resetClientSynchronizedState() {
		clientSynchronizedDefinitions = EMPTY_DEFINITIONS;
		clientSynchronized = false;
		EnchantConfigAPIManager.resetClientSynchronizedState();
	}

	private static Map<String, EnchantmentDefinition> activeDefinitions() {
		return clientSynchronized ? clientSynchronizedDefinitions : definitions;
	}

	public static int getConfiguredMaximumLevel(Enchantment enchantment, int vanillaMaximumLevel) {
		if (enchantment == null || !EnchantConfigAPIManager.areCustomEnchantmentsEnabled()) return vanillaMaximumLevel;
		String enchantmentId = resolveEnchantmentId(enchantment);
		if (enchantmentId == null) return vanillaMaximumLevel;
		EnchantmentDefinition definition = activeDefinitions().get(enchantmentId);
		if (definition == null || !definition.enabled) return vanillaMaximumLevel;
		return Math.max(1, definition.maximumLevel);
	}

	public static boolean resolveConfiguredCanEnchant(
		Enchantment enchantment,
		ItemStack stack,
		boolean vanillaCanEnchant
	) {
		if (enchantment == null || stack == null || stack.isEmpty()
			|| !EnchantConfigAPIManager.areCustomEnchantmentsEnabled()) return vanillaCanEnchant;

		String enchantmentId = resolveEnchantmentId(enchantment);
		if (enchantmentId == null) return vanillaCanEnchant;
		EnchantmentDefinition definition = activeDefinitions().get(enchantmentId);
		if (definition == null || !definition.enabled) return vanillaCanEnchant;
		return isCompatible(definition, stack);
	}

	public static boolean resolveConfiguredCompatibility(
		Holder<Enchantment> first,
		Holder<Enchantment> second,
		boolean vanillaCompatible
	) {
		if (!EnchantConfigAPIManager.areCustomEnchantmentsEnabled() || first == null || second == null) {
			return vanillaCompatible;
		}

		EnchantmentDefinition firstDefinition = definitionForHolder(first);
		EnchantmentDefinition secondDefinition = definitionForHolder(second);
		if ((firstDefinition != null && firstDefinition.enabled && !firstDefinition.conflictingEnchantment)
			|| (secondDefinition != null && secondDefinition.enabled && !secondDefinition.conflictingEnchantment)) {
			return true;
		}
		return vanillaCompatible;
	}

	public static boolean shouldOverrideBaneOfArthropods(Enchantment enchantment) {
		if (enchantment == null || !EnchantConfigAPIManager.areCustomEnchantmentsEnabled()) return false;
		if (!BANE_OF_ARTHROPODS_ID.equals(resolveEnchantmentId(enchantment))) return false;
		EnchantmentDefinition definition = activeDefinitions().get(BANE_OF_ARTHROPODS_ID);
		return definition != null && definition.enabled;
	}

	public static boolean shouldOverrideFireAspect(Enchantment enchantment) {
		if (enchantment == null || !EnchantConfigAPIManager.areCustomEnchantmentsEnabled()) return false;
		if (!isFireAspect(enchantment)) return false;
		EnchantmentDefinition definition = activeDefinitions().get(FIRE_ASPECT_ID);
		return definition != null && definition.enabled;
	}

	static boolean shouldOverrideImpaling(Enchantment enchantment) {
		if (enchantment == null || !EnchantConfigAPIManager.areCustomEnchantmentsEnabled()) return false;
		if (!IMPALING_ID.equals(resolveEnchantmentId(enchantment))) return false;
		EnchantmentDefinition definition = activeDefinitions().get(IMPALING_ID);
		return definition != null && definition.enabled;
	}

	static boolean shouldOverrideSoulSpeed(Enchantment enchantment) {
		if (enchantment == null || !EnchantConfigAPIManager.areCustomEnchantmentsEnabled()) return false;
		if (!SOUL_SPEED_ID.equals(resolveEnchantmentId(enchantment))) return false;
		EnchantmentDefinition definition = activeDefinitions().get(SOUL_SPEED_ID);
		return definition != null && definition.enabled;
	}

	static boolean shouldOverrideSweepingEdge(Enchantment enchantment) {
		if (enchantment == null || !EnchantConfigAPIManager.areCustomEnchantmentsEnabled()) return false;
		if (!SWEEPING_EDGE_ID.equals(resolveEnchantmentId(enchantment))) return false;
		EnchantmentDefinition definition = activeDefinitions().get(SWEEPING_EDGE_ID);
		return definition != null && definition.enabled;
	}

	static boolean shouldOverrideThorns(Enchantment enchantment) {
		if (enchantment == null || !EnchantConfigAPIManager.areCustomEnchantmentsEnabled()) return false;
		if (!THORNS_ID.equals(resolveEnchantmentId(enchantment))) return false;
		EnchantmentDefinition definition = activeDefinitions().get(THORNS_ID);
		return definition != null && definition.enabled;
	}

	public static boolean shouldOverrideUnbreaking(Enchantment enchantment) {
		if (enchantment == null || !EnchantConfigAPIManager.areCustomEnchantmentsEnabled()) return false;
		if (!UNBREAKING_ID.equals(resolveEnchantmentId(enchantment))) return false;
		EnchantmentDefinition definition = activeDefinitions().get(UNBREAKING_ID);
		return definition != null && definition.enabled;
	}

	static boolean shouldOverrideSharpness(Enchantment enchantment) {
		if (enchantment == null || !EnchantConfigAPIManager.areCustomEnchantmentsEnabled()) return false;
		if (!SHARPNESS_ID.equals(resolveEnchantmentId(enchantment))) return false;
		EnchantmentDefinition definition = activeDefinitions().get(SHARPNESS_ID);
		return definition != null && definition.enabled;
	}

	static boolean shouldOverrideSmite(Enchantment enchantment) {
		if (enchantment == null || !EnchantConfigAPIManager.areCustomEnchantmentsEnabled()) return false;
		if (!SMITE_ID.equals(resolveEnchantmentId(enchantment))) return false;
		EnchantmentDefinition definition = activeDefinitions().get(SMITE_ID);
		return definition != null && definition.enabled;
	}

	static boolean shouldOverrideInfinity(Enchantment enchantment) {
		if (enchantment == null || !EnchantConfigAPIManager.areCustomEnchantmentsEnabled()) return false;
		if (!INFINITY_ID.equals(resolveEnchantmentId(enchantment))) return false;
		EnchantmentDefinition definition = activeDefinitions().get(INFINITY_ID);
		return definition != null && definition.enabled;
	}

	static boolean shouldOverrideMending(Enchantment enchantment) {
		if (enchantment == null || !EnchantConfigAPIManager.areCustomEnchantmentsEnabled()) return false;
		if (!MENDING_ID.equals(resolveEnchantmentId(enchantment))) return false;
		EnchantmentDefinition definition = activeDefinitions().get(MENDING_ID);
		return definition != null && definition.enabled;
	}

	static boolean shouldOverrideKnockback(Enchantment enchantment) {
		if (enchantment == null || !EnchantConfigAPIManager.areCustomEnchantmentsEnabled()) return false;
		if (!KNOCKBACK_ID.equals(resolveEnchantmentId(enchantment))) return false;
		EnchantmentDefinition definition = activeDefinitions().get(KNOCKBACK_ID);
		return definition != null && definition.enabled;
	}

	static boolean shouldOverridePunch(Enchantment enchantment) {
		if (enchantment == null || !EnchantConfigAPIManager.areCustomEnchantmentsEnabled()) return false;
		if (!PUNCH_ID.equals(resolveEnchantmentId(enchantment))) return false;
		EnchantmentDefinition definition = activeDefinitions().get(PUNCH_ID);
		return definition != null && definition.enabled;
	}

	static boolean shouldOverrideLuckOfTheSea(Enchantment enchantment) {
		if (enchantment == null || !EnchantConfigAPIManager.areCustomEnchantmentsEnabled()) return false;
		if (!LUCK_OF_THE_SEA_ID.equals(resolveEnchantmentId(enchantment))) return false;
		EnchantmentDefinition definition = activeDefinitions().get(LUCK_OF_THE_SEA_ID);
		return definition != null && definition.enabled;
	}

	public static boolean isFireAspect(Enchantment enchantment) {
		return enchantment != null && FIRE_ASPECT_ID.equals(resolveEnchantmentId(enchantment));
	}

	static boolean isSharpness(Enchantment enchantment) {
		return enchantment != null && SHARPNESS_ID.equals(resolveEnchantmentId(enchantment));
	}

	static boolean isSmite(Enchantment enchantment) {
		return enchantment != null && SMITE_ID.equals(resolveEnchantmentId(enchantment));
	}

	/** Returns configured Breach penetration, or a negative value when vanilla behavior should remain active. */
	public static double getConfiguredBreachArmorPenetration(Enchantment enchantment, ItemStack stack, int level) {
		if (enchantment == null || stack == null || stack.isEmpty()
			|| !EnchantConfigAPIManager.areCustomEnchantmentsEnabled()) return -1.0D;

		if (!BREACH_ID.equals(resolveEnchantmentId(enchantment))) return -1.0D;
		EnchantmentDefinition definition = activeDefinitions().get(BREACH_ID);
		if (definition == null || !definition.enabled || !isCompatible(definition, stack)) return -1.0D;
		return resolveAdjustment(definition.baseArmorPenetration, definition.levelArmorPenetration, Math.max(1, level));
	}

	static boolean isCompatible(EnchantmentDefinition definition, ItemStack stack) {
		if (definition == null || stack == null || stack.isEmpty()) return false;
		var equippable = stack.get(DataComponents.EQUIPPABLE);
		EquipmentSlot equipmentSlot = equippable == null ? null : equippable.slot();
		for (String compatibleItem : definition.compatibleItems) {
			switch (compatibleItem) {
				case "universal" -> {
					return true;
				}
				case "sword" -> {
					if (stack.is(ItemTags.SWORDS)) return true;
				}
				case "spear" -> {
					if (stack.is(ItemTags.SPEARS)) return true;
				}
				case "axe" -> {
					if (stack.is(ItemTags.AXES)) return true;
				}
				case "pickaxe" -> {
					if (stack.is(ItemTags.PICKAXES)) return true;
				}
				case "shovel" -> {
					if (stack.is(ItemTags.SHOVELS)) return true;
				}
				case "hoe" -> {
					if (stack.is(ItemTags.HOES)) return true;
				}
				case "mace" -> {
					if (stack.is(ItemTags.MACE_ENCHANTABLE)) return true;
				}
				case "bow" -> {
					if (stack.is(ItemTags.BOW_ENCHANTABLE)) return true;
				}
				case "crossbow" -> {
					if (stack.is(ItemTags.CROSSBOW_ENCHANTABLE)) return true;
				}
				case "trident" -> {
					if (stack.is(ItemTags.TRIDENT_ENCHANTABLE)) return true;
				}
				case "fishing-rod" -> {
					if (stack.is(ItemTags.FISHING_ENCHANTABLE)) return true;
				}
				case "flint-and-steel" -> {
					if (stack.is(Items.FLINT_AND_STEEL)) return true;
				}
				case "shears" -> {
					if (stack.is(Items.SHEARS)) return true;
				}
				case "shield" -> {
					if (stack.is(Items.SHIELD)) return true;
				}
				case "elytra" -> {
					if (stack.is(Items.ELYTRA)) return true;
				}
				case "brush" -> {
					if (stack.is(Items.BRUSH)) return true;
				}
				case "carrot-on-a-stick" -> {
					if (stack.is(Items.CARROT_ON_A_STICK)) return true;
				}
				case "warped-fungus-on-a-stick" -> {
					if (stack.is(Items.WARPED_FUNGUS_ON_A_STICK)) return true;
				}
				case "helmet" -> {
					if (equipmentSlot == EquipmentSlot.HEAD) return true;
				}
				case "chestplate" -> {
					if (equipmentSlot == EquipmentSlot.CHEST) return true;
				}
				case "leggings" -> {
					if (equipmentSlot == EquipmentSlot.LEGS) return true;
				}
				case "boots" -> {
					if (equipmentSlot == EquipmentSlot.FEET) return true;
				}
				default -> {
				}
			}
		}
		return false;
	}

	static double resolveAdjustment(double base, double perLevel, int level) {
		return Math.max(0.0D, base + Math.max(0, level - 1) * perLevel);
	}

	private static void rememberEnchantmentIds(Registry<Enchantment> registry) {
		if (registry == null) return;
		Map<Enchantment, String> ids = new LinkedHashMap<>(enchantmentIds);
		for (Map.Entry<net.minecraft.resources.ResourceKey<Enchantment>, Enchantment> entry : registry.entrySet()) {
			ids.put(entry.getValue(), entry.getKey().identifier().toString());
		}
		enchantmentIds = ids.isEmpty() ? Map.of() : Map.copyOf(ids);
	}

	private static String resolveEnchantmentId(Enchantment enchantment) {
		if (enchantment == null) return null;
		String registryId = enchantmentIds.get(enchantment);
		if (registryId != null) return registryId;
		if (enchantment.description().getContents() instanceof TranslatableContents contents) {
			String key = contents.getKey();
			String prefix = "enchantment.";
			if (key.startsWith(prefix)) {
				String path = key.substring(prefix.length());
				int separator = path.indexOf('.');
				if (separator > 0 && separator < path.length() - 1) {
					return path.substring(0, separator) + ":" + path.substring(separator + 1);
				}
			}
		}
		return null;
	}

	private static EnchantmentDefinition parseClientSyncDefinition(JsonObject root) {
		String enchantmentId = JSONAPIManager.normalizeRegistryIdentifierForLookup(
			readString(root, FIELD_ENCHANTMENT_ID, "")
		);
		if (enchantmentId.isBlank()) return null;

		List<String> compatibleItems = new ArrayList<>();
		JsonElement compatible = root.get(FIELD_COMPATIBLE_ITEMS);
		if (compatible instanceof JsonArray values) {
			for (JsonElement value : values) {
				if (value != null && value.isJsonPrimitive()) {
					String normalized = value.getAsString().trim().toLowerCase(Locale.ROOT).replace('_', '-');
					if (!normalized.isBlank()) compatibleItems.add(normalized);
				}
			}
		}
		JsonObject luckOfTheSea = object(root, FIELD_LUCK_OF_THE_SEA);
		JsonObject treasure = object(luckOfTheSea, FIELD_TREASURE);
		JsonObject junk = object(luckOfTheSea, FIELD_JUNK);
		JsonObject fish = object(luckOfTheSea, FIELD_FISH);

		return new EnchantmentDefinition(
			enchantmentId,
			Math.max(1, readInt(root, FIELD_MAXIMUM_LEVEL, 1)),
			List.copyOf(compatibleItems),
			readBoolean(root, FIELD_CONFLICTING_ENCHANTMENT, true),
			Math.max(0, readInt(root, FIELD_WEIGHT, 1)),
			Math.max(0.0D, readDouble(root, FIELD_BASE_ADJUSTMENT, 0.0D)),
			Math.max(0.0D, readDouble(root, FIELD_LEVEL_ADJUSTMENT, 0.0D)),
			Math.max(0.0D, readDouble(root, FIELD_BASE_DURATION, 0.0D)),
			Math.max(0.0D, readDouble(root, FIELD_LEVEL_DURATION, 0.0D)),
			Math.max(0.0D, readDouble(root, FIELD_BASE_KNOCKBACK_RESISTANCE, 0.0D)),
			Math.max(0.0D, readDouble(root, FIELD_LEVEL_KNOCKBACK_RESISTANCE, 0.0D)),
			Math.max(0.0D, readDouble(root, FIELD_BASE_ARMOR_PENETRATION, 0.0D)),
			Math.max(0.0D, readDouble(root, FIELD_LEVEL_ARMOR_PENETRATION, 0.0D)),
			Math.max(0.0D, readDouble(treasure, FIELD_BASE_CHANCE_ADJUSTMENT, 0.0D)),
			Math.max(0.0D, readDouble(treasure, FIELD_LEVEL_ADJUSTMENT, 0.0D)),
			Math.max(0.0D, readDouble(junk, FIELD_BASE_CHANCE_ADJUSTMENT, 0.0D)),
			Math.max(0.0D, readDouble(junk, FIELD_LEVEL_ADJUSTMENT, 0.0D)),
			Math.max(0.0D, readDouble(fish, FIELD_BASE_CHANCE_ADJUSTMENT, 0.0D)),
			Math.max(0.0D, readDouble(fish, FIELD_LEVEL_ADJUSTMENT, 0.0D)),
			Math.max(0.0D, readDouble(root, FIELD_BASE_THORNS_CHANCE, 0.0D)),
			Math.max(0.0D, readDouble(root, FIELD_LEVEL_THORNS_CHANCE, 0.0D)),
			readBoolean(root, FIELD_ENABLED, true)
		);
	}

	private static void reload() {
		try {
			Map<String, JsonObject> staticDefaults = new LinkedHashMap<>();
			staticDefaults.put(AQUA_AFFINITY_FILE_KEY, buildAquaAffinityDefaults());
			staticDefaults.put(BANE_OF_ARTHROPODS_FILE_KEY, buildBaneOfArthropodsDefaults());
			staticDefaults.put(PROTECTION_FILE_KEY, buildProtectionDefaults());
			staticDefaults.put(BLAST_PROTECTION_FILE_KEY, buildBlastProtectionDefaults());
			staticDefaults.put(PROJECTILE_PROTECTION_FILE_KEY, buildProjectileProtectionDefaults());
			staticDefaults.put(RESPIRATION_FILE_KEY, buildRespirationDefaults());
			staticDefaults.put(RIPTIDE_FILE_KEY, buildRiptideDefaults());
			staticDefaults.put(BREACH_FILE_KEY, buildBreachDefaults());
			staticDefaults.put(CHANNELING_FILE_KEY, buildChannelingDefaults());
			staticDefaults.put(CURSE_OF_BINDING_FILE_KEY, buildCurseOfBindingDefaults());
			staticDefaults.put(CURSE_OF_VANISHING_FILE_KEY, buildCurseOfVanishingDefaults());
			staticDefaults.put(DENSITY_FILE_KEY, buildDensityDefaults());
			staticDefaults.put(DEPTH_STRIDER_FILE_KEY, buildDepthStriderDefaults());
			staticDefaults.put(EFFICIENCY_FILE_KEY, buildEfficiencyDefaults());
			staticDefaults.put(FEATHER_FALLING_FILE_KEY, buildFeatherFallingDefaults());
			staticDefaults.put(FROST_WALKER_FILE_KEY, buildFrostWalkerDefaults());
			staticDefaults.put(FORTUNE_FILE_KEY, buildFortuneDefaults());
			staticDefaults.put(LOOTING_FILE_KEY, buildLootingDefaults());
			staticDefaults.put(LOYALTY_FILE_KEY, buildLoyaltyDefaults());
			staticDefaults.put(FIRE_ASPECT_FILE_KEY, buildFireAspectDefaults());
			staticDefaults.put(FIRE_PROTECTION_FILE_KEY, buildFireProtectionDefaults());
			staticDefaults.put(FLAME_FILE_KEY, buildFlameDefaults());
			staticDefaults.put(IMPALING_FILE_KEY, buildImpalingDefaults());
			staticDefaults.put(SOUL_SPEED_FILE_KEY, buildSoulSpeedDefaults());
			staticDefaults.put(SWEEPING_EDGE_FILE_KEY, buildSweepingEdgeDefaults());
			staticDefaults.put(SWIFT_SNEAK_FILE_KEY, buildSwiftSneakDefaults());
			staticDefaults.put(WIND_BURST_FILE_KEY, buildWindBurstDefaults());
			staticDefaults.put(THORNS_FILE_KEY, buildThornsDefaults());
			staticDefaults.put(UNBREAKING_FILE_KEY, buildUnbreakingDefaults());
			staticDefaults.put(SHARPNESS_FILE_KEY, buildSharpnessDefaults());
			staticDefaults.put(SILK_TOUCH_FILE_KEY, buildSilkTouchDefaults());
			staticDefaults.put(SMITE_FILE_KEY, buildSmiteDefaults());
			staticDefaults.put(INFINITY_FILE_KEY, buildInfinityDefaults());
			staticDefaults.put(KNOCKBACK_FILE_KEY, buildKnockbackDefaults());
			staticDefaults.put(PUNCH_FILE_KEY, buildPunchDefaults());
			staticDefaults.put(POWER_FILE_KEY, buildPowerDefaults());
			staticDefaults.put(QUICK_CHARGE_FILE_KEY, buildQuickChargeDefaults());
			staticDefaults.put(LUCK_OF_THE_SEA_FILE_KEY, buildLuckOfTheSeaDefaults());
			staticDefaults.put(LUNGE_FILE_KEY, buildLungeDefaults());
			staticDefaults.put(LURE_FILE_KEY, buildLureDefaults());
			staticDefaults.put(MENDING_FILE_KEY, buildMendingDefaults());
			staticDefaults.put(PIERCING_FILE_KEY, buildPiercingDefaults());
			staticDefaults.put(MULTISHOT_FILE_KEY, buildMultishotDefaults());
			Map<String, JsonObject> files = JSONFormatAPIManager.ensureManagedFolder(
				EnchantConfigAPIManager.enchantmentsDirectory(),
				staticDefaults,
				BooksConfigAPIManager::buildGenericDefaults,
				(fileKey, root) -> true,
				null
			);

			Map<String, EnchantmentDefinition> loaded = new LinkedHashMap<>();
			for (JsonObject root : files.values()) {
				EnchantmentDefinition definition = parseDefinition(root);
				if (definition != null) loaded.put(definition.enchantmentId, definition);
			}
			definitions = loaded.isEmpty() ? EMPTY_DEFINITIONS : Map.copyOf(loaded);
		} catch (IOException | RuntimeException exception) {
			LOGGER.error("Failed to load Madoku enchantment definitions.", exception);
			definitions = EMPTY_DEFINITIONS;
		}
	}

	private static JsonObject buildAquaAffinityDefaults() {
		return JSONFormatAPIManager.object()
			.put(FIELD_ENCHANTMENT_ID, "minecraft:aqua-affinity")
			.put(FIELD_MAXIMUM_LEVEL, 5)
			.array(FIELD_COMPATIBLE_ITEMS, values -> values.add("helmet"))
			.put(FIELD_CONFLICTING_ENCHANTMENT, false)
			.put(FIELD_WEIGHT, 1)
			.object(FIELD_AQUA_AFFINITY, value -> value
				.put(FIELD_BASE_SUBMERGED_MINING_SPEED, 300)
				.put(FIELD_LEVEL_ADJUSTMENT, 50))
			.build();
	}

	private static JsonObject buildBaneOfArthropodsDefaults() {
		return JSONFormatAPIManager.object()
			.put(FIELD_ENCHANTMENT_ID, "minecraft:bane-of-arthropods")
			.put(FIELD_MAXIMUM_LEVEL, 5)
			.array(FIELD_COMPATIBLE_ITEMS, values -> values
				.add("sword")
				.add("spear")
				.add("axe")
				.add("mace")
				.add("trident"))
			.put(FIELD_CONFLICTING_ENCHANTMENT, true)
			.put(FIELD_WEIGHT, 1)
			.object(FIELD_BANE_OF_ARTHROPODS, bane -> bane
				.object(FIELD_EFFECT, effect -> effect
					.put(FIELD_BASE_EFFECT, 1)
					.put(FIELD_LEVEL_ADJUSTMENT, 1))
				.object(FIELD_DURATION, duration -> duration
					.put(FIELD_BASE_DURATION, 3)
					.put(FIELD_LEVEL_ADJUSTMENT, 1)))
			.build();
	}

	private static JsonObject buildProtectionDefaults() {
		return JSONFormatAPIManager.object()
			.put(FIELD_ENABLED, true)
			.put(FIELD_ENCHANTMENT_ID, PROTECTION_ID)
			.put(FIELD_MAXIMUM_LEVEL, 3)
			.array(FIELD_COMPATIBLE_ITEMS, values -> values
				.add("helmet")
				.add("chestplate")
				.add("leggings")
				.add("boots"))
			.put(FIELD_CONFLICTING_ENCHANTMENT, false)
			.put(FIELD_WEIGHT, 1)
			.object(FIELD_PROTECTION, protection -> protection
				.put(FIELD_BASE_DAMAGE_REDUCTION, 3)
				.put(FIELD_LEVEL_ADJUSTMENT, 1))
			.build();
	}

	private static JsonObject buildBlastProtectionDefaults() {
		return JSONFormatAPIManager.object()
			.put(FIELD_ENABLED, true)
			.put(FIELD_ENCHANTMENT_ID, "minecraft:blast-protection")
			.put(FIELD_MAXIMUM_LEVEL, 5)
			.array(FIELD_COMPATIBLE_ITEMS, values -> values
				.add("helmet")
				.add("chestplate")
				.add("leggings")
				.add("boots"))
			.put(FIELD_CONFLICTING_ENCHANTMENT, false)
			.put(FIELD_WEIGHT, 1)
			.object(FIELD_BLAST_PROTECTION, blast -> blast
				.object(FIELD_EXPLOSIVE_PROTECTION, protection -> protection
					.put(FIELD_BASE_VALUE, 6)
					.put(FIELD_LEVEL_ADJUSTMENT, 1))
				.object(FIELD_EXPLOSION_KNOCKBACK_RESISTANCE, resistance -> resistance
					.put(FIELD_BASE_VALUE, 6)
					.put(FIELD_LEVEL_ADJUSTMENT, 1)))
			.build();
	}

	private static JsonObject buildProjectileProtectionDefaults() {
		return JSONFormatAPIManager.object()
			.put(FIELD_ENABLED, true)
			.put(FIELD_ENCHANTMENT_ID, PROJECTILE_PROTECTION_ID)
			.put(FIELD_MAXIMUM_LEVEL, 5)
			.array(FIELD_COMPATIBLE_ITEMS, values -> values
				.add("helmet")
				.add("chestplate")
				.add("leggings")
				.add("boots"))
			.put(FIELD_CONFLICTING_ENCHANTMENT, false)
			.put(FIELD_WEIGHT, 1)
			.object(FIELD_PROJECTILE_PROTECTION, projectileProtection -> projectileProtection
				.object(FIELD_PROJECTILE_PROTECTION, protection -> protection
					.put(FIELD_BASE_VALUE, 6)
					.put(FIELD_LEVEL_ADJUSTMENT, 1)))
			.build();
	}

	private static JsonObject buildBreachDefaults() {
		return JSONFormatAPIManager.object()
			.put(FIELD_ENCHANTMENT_ID, "minecraft:breach")
			.put(FIELD_MAXIMUM_LEVEL, 5)
			.array(FIELD_COMPATIBLE_ITEMS, values -> values
				.add("sword")
				.add("axe")
				.add("trident")
				.add("mace")
				.add("spear"))
			.put(FIELD_CONFLICTING_ENCHANTMENT, false)
			.put(FIELD_WEIGHT, 1)
			.object(FIELD_BREACH, breach -> breach
				.put(FIELD_BASE_ARMOR_PENETRATION, 20)
				.put(FIELD_LEVEL_ADJUSTMENT, 5))
			.build();
	}

	private static JsonObject buildChannelingDefaults() {
		return JSONFormatAPIManager.object()
			.put(FIELD_ENCHANTMENT_ID, "minecraft:channeling")
			.put(FIELD_MAXIMUM_LEVEL, 1)
			.array(FIELD_COMPATIBLE_ITEMS, values -> values.add("trident"))
			.put(FIELD_CONFLICTING_ENCHANTMENT, true)
			.put(FIELD_WEIGHT, 1)
			.build();
	}

	private static JsonObject buildCurseOfBindingDefaults() {
		return JSONFormatAPIManager.object()
			.put(FIELD_ENCHANTMENT_ID, "minecraft:curse-of-binding")
			.put(FIELD_MAXIMUM_LEVEL, 1)
			.array(FIELD_COMPATIBLE_ITEMS, values -> values.add("universal"))
			.put(FIELD_CONFLICTING_ENCHANTMENT, false)
			.put(FIELD_WEIGHT, 1)
			.build();
	}

	private static JsonObject buildCurseOfVanishingDefaults() {
		return JSONFormatAPIManager.object()
			.put(FIELD_ENCHANTMENT_ID, "minecraft:curse-of-vanishing")
			.put(FIELD_MAXIMUM_LEVEL, 1)
			.array(FIELD_COMPATIBLE_ITEMS, values -> values.add("universal"))
			.put(FIELD_CONFLICTING_ENCHANTMENT, false)
			.put(FIELD_WEIGHT, 1)
			.build();
	}

	private static JsonObject buildDensityDefaults() {
		return JSONFormatAPIManager.object()
			.put(FIELD_ENCHANTMENT_ID, "minecraft:density")
			.put(FIELD_MAXIMUM_LEVEL, 5)
			.array(FIELD_COMPATIBLE_ITEMS, values -> values.add("mace"))
			.put(FIELD_CONFLICTING_ENCHANTMENT, false)
			.put(FIELD_WEIGHT, 1)
			.build();
	}

	private static JsonObject buildDepthStriderDefaults() {
		return JSONFormatAPIManager.object()
			.put(FIELD_ENCHANTMENT_ID, "minecraft:depth-strider")
			.put(FIELD_MAXIMUM_LEVEL, 5)
			.array(FIELD_COMPATIBLE_ITEMS, values -> values.add("boots"))
			.put(FIELD_CONFLICTING_ENCHANTMENT, true)
			.put(FIELD_WEIGHT, 1)
			.object(FIELD_DEPTH_STRIDER, depthStrider -> depthStrider
				.put(FIELD_BASE_WATER_MOVEMENT_EFFICIENCY, 50)
				.put(FIELD_LEVEL_ADJUSTMENT, 25))
			.build();
	}

	private static JsonObject buildEfficiencyDefaults() {
		return JSONFormatAPIManager.object()
			.put(FIELD_ENCHANTMENT_ID, "minecraft:efficiency")
			.put(FIELD_MAXIMUM_LEVEL, 5)
			.array(FIELD_COMPATIBLE_ITEMS, values -> values
				.add("pickaxe")
				.add("axe")
				.add("shovel")
				.add("hoe"))
			.put(FIELD_CONFLICTING_ENCHANTMENT, false)
			.put(FIELD_WEIGHT, 1)
			.object(FIELD_EFFICIENCY, efficiency -> efficiency
				.put(FIELD_BASE_MINING_EFFICIENCY, 3)
				.put(FIELD_LEVEL_ADJUSTMENT, 1.5))
			.build();
	}

	private static JsonObject buildFeatherFallingDefaults() {
		return JSONFormatAPIManager.object()
			.put(FIELD_ENCHANTMENT_ID, "minecraft:feather-falling")
			.put(FIELD_MAXIMUM_LEVEL, 5)
			.array(FIELD_COMPATIBLE_ITEMS, values -> values.add("boots"))
			.put(FIELD_CONFLICTING_ENCHANTMENT, false)
			.put(FIELD_WEIGHT, 1)
			.object(FIELD_FEATHER_FALLING, efficiency -> efficiency
				.put(FIELD_BASE_FALL_DAMAGE_REDUCTION, 20)
				.put(FIELD_LEVEL_ADJUSTMENT, 5))
			.build();
	}

	private static JsonObject buildFrostWalkerDefaults() {
		return JSONFormatAPIManager.object()
			.put(FIELD_ENCHANTMENT_ID, "minecraft:frost-walker")
			.put(FIELD_MAXIMUM_LEVEL, 3)
			.array(FIELD_COMPATIBLE_ITEMS, values -> values.add("boots"))
			.put(FIELD_CONFLICTING_ENCHANTMENT, true)
			.put(FIELD_WEIGHT, 1)
			.build();
	}

	private static JsonObject buildFortuneDefaults() {
		return JSONFormatAPIManager.object()
			.put(FIELD_ENCHANTMENT_ID, "minecraft:fortune")
			.put(FIELD_MAXIMUM_LEVEL, 5)
			.array(FIELD_COMPATIBLE_ITEMS, values -> values
				.add("pickaxe")
				.add("shovel")
				.add("axe")
				.add("hoe"))
			.put(FIELD_CONFLICTING_ENCHANTMENT, true)
			.put(FIELD_WEIGHT, 1)
			.object(FIELD_FORTUNE, fortune -> fortune
				.put(FIELD_BASE_MULTIPLIER_CHANCE, 30)
				.put(FIELD_LEVEL_ADJUSTMENT, 5))
			.build();
	}

	private static JsonObject buildLootingDefaults() {
		return JSONFormatAPIManager.object()
			.put(FIELD_ENCHANTMENT_ID, LOOTING_ID)
			.put(FIELD_MAXIMUM_LEVEL, 5)
			.array(FIELD_COMPATIBLE_ITEMS, values -> values
				.add("sword")
				.add("spear")
				.add("axe")
				.add("trident")
				.add("mace"))
			.put(FIELD_CONFLICTING_ENCHANTMENT, false)
			.put(FIELD_WEIGHT, 1)
			.object(FIELD_LOOTING, looting -> looting
				.put(FIELD_BASE_MULTIPLIER_CHANCE, 30)
				.put(FIELD_LEVEL_ADJUSTMENT, 5))
			.build();
	}

	private static JsonObject buildLoyaltyDefaults() {
		return JSONFormatAPIManager.object()
			.put(FIELD_ENCHANTMENT_ID, LOYALTY_ID)
			.put(FIELD_MAXIMUM_LEVEL, 3)
			.array(FIELD_COMPATIBLE_ITEMS, values -> values.add("trident"))
			.put(FIELD_CONFLICTING_ENCHANTMENT, true)
			.put(FIELD_WEIGHT, 1)
			.build();
	}

	private static JsonObject buildFireAspectDefaults() {
		return JSONFormatAPIManager.object()
			.put(FIELD_ENCHANTMENT_ID, "minecraft:fire-aspect")
			.put(FIELD_MAXIMUM_LEVEL, 5)
			.array(FIELD_COMPATIBLE_ITEMS, values -> values
				.add("sword")
				.add("spear")
				.add("axe")
				.add("mace"))
			.put(FIELD_CONFLICTING_ENCHANTMENT, false)
			.put(FIELD_WEIGHT, 1)
			.object(FIELD_FIRE_ASPECT, fireAspect -> fireAspect
				.put(FIELD_BASE_FIRE_DURATION, 3)
				.put(FIELD_LEVEL_ADJUSTMENT, 1))
			.build();
	}

	private static JsonObject buildFireProtectionDefaults() {
		return JSONFormatAPIManager.object()
			.put(FIELD_ENCHANTMENT_ID, "minecraft:fire-protection")
			.put(FIELD_MAXIMUM_LEVEL, 5)
			.array(FIELD_COMPATIBLE_ITEMS, values -> values
				.add("helmet")
				.add("chestplate")
				.add("leggings")
				.add("boots"))
			.put(FIELD_CONFLICTING_ENCHANTMENT, false)
			.put(FIELD_WEIGHT, 1)
			.object(FIELD_FIRE_PROTECTION, fireProtection -> fireProtection
				.object(FIELD_BURN_PROTECTION, burnProtection -> burnProtection
					.put(FIELD_BASE_VALUE, 6)
					.put(FIELD_LEVEL_ADJUSTMENT, 1))
				.object(FIELD_BURN_DURATION_REDUCTION, burnDuration -> burnDuration
					.put(FIELD_BASE_VALUE, 6)
					.put(FIELD_LEVEL_ADJUSTMENT, 1)))
			.build();
	}

	private static JsonObject buildFlameDefaults() {
		return JSONFormatAPIManager.object()
			.put(FIELD_ENCHANTMENT_ID, "minecraft:flame")
			.put(FIELD_MAXIMUM_LEVEL, 3)
			.array(FIELD_COMPATIBLE_ITEMS, values -> values
				.add("bow")
				.add("crossbow"))
			.put(FIELD_CONFLICTING_ENCHANTMENT, false)
			.put(FIELD_WEIGHT, 1)
			.object(FIELD_FLAME, flame -> flame
				.put(FIELD_BASE_FIRE_DURATION, 3)
				.put(FIELD_LEVEL_ADJUSTMENT, 1))
			.build();
	}

	private static JsonObject buildImpalingDefaults() {
		return JSONFormatAPIManager.object()
			.put(FIELD_ENCHANTMENT_ID, IMPALING_ID)
			.put(FIELD_MAXIMUM_LEVEL, 5)
			.array(FIELD_COMPATIBLE_ITEMS, values -> values.add("trident"))
			.put(FIELD_CONFLICTING_ENCHANTMENT, false)
			.put(FIELD_WEIGHT, 1)
			.object(FIELD_IMPALING, impaling -> impaling
				.put(FIELD_BASE_ADDED_DAMAGE, 3)
				.put(FIELD_LEVEL_ADJUSTMENT, 1))
			.build();
	}

	private static JsonObject buildSoulSpeedDefaults() {
		return JSONFormatAPIManager.object()
			.put(FIELD_ENCHANTMENT_ID, SOUL_SPEED_ID)
			.put(FIELD_MAXIMUM_LEVEL, 3)
			.array(FIELD_COMPATIBLE_ITEMS, values -> values.add("boots"))
			.put(FIELD_CONFLICTING_ENCHANTMENT, false)
			.put(FIELD_WEIGHT, 1)
			.object(FIELD_SOUL_SPEED, soulSpeed -> soulSpeed
				.put(FIELD_BASE_SPEED_INCREASE, 15)
				.put(FIELD_LEVEL_ADJUSTMENT, 5))
			.build();
	}

	private static JsonObject buildSweepingEdgeDefaults() {
		return JSONFormatAPIManager.object()
			.put(FIELD_ENCHANTMENT_ID, SWEEPING_EDGE_ID)
			.put(FIELD_MAXIMUM_LEVEL, 5)
			.array(FIELD_COMPATIBLE_ITEMS, values -> values.add("sword"))
			.put(FIELD_CONFLICTING_ENCHANTMENT, false)
			.put(FIELD_WEIGHT, 1)
			.object(FIELD_SWEEPING_EDGE, sweepingEdge -> sweepingEdge
				.put(FIELD_BASE_SWEEPING_DAMAGE, 60)
				.put(FIELD_LEVEL_ADJUSTMENT, 5))
			.build();
	}

	private static JsonObject buildSwiftSneakDefaults() {
		return JSONFormatAPIManager.object()
			.put(FIELD_ENCHANTMENT_ID, SWIFT_SNEAK_ID)
			.put(FIELD_MAXIMUM_LEVEL, 3)
			.array(FIELD_COMPATIBLE_ITEMS, values -> values.add("leggings"))
			.put(FIELD_CONFLICTING_ENCHANTMENT, false)
			.put(FIELD_WEIGHT, 1)
			.build();
	}

	private static JsonObject buildWindBurstDefaults() {
		return JSONFormatAPIManager.object()
			.put(FIELD_ENCHANTMENT_ID, WIND_BURST_ID)
			.put(FIELD_MAXIMUM_LEVEL, 3)
			.array(FIELD_COMPATIBLE_ITEMS, values -> values.add("mace"))
			.put(FIELD_CONFLICTING_ENCHANTMENT, false)
			.put(FIELD_WEIGHT, 1)
			.build();
	}

	private static JsonObject buildThornsDefaults() {
		return JSONFormatAPIManager.object()
			.put(FIELD_ENCHANTMENT_ID, THORNS_ID)
			.put(FIELD_MAXIMUM_LEVEL, 3)
			.array(FIELD_COMPATIBLE_ITEMS, values -> values
				.add("helmet")
				.add("chestplate")
				.add("leggings")
				.add("boots"))
			.put(FIELD_CONFLICTING_ENCHANTMENT, false)
			.put(FIELD_WEIGHT, 1)
			.object(FIELD_THORNS, thorns -> thorns
				.object(FIELD_DAMAGE_REFLECTION, reflection -> reflection
					.put(FIELD_BASE_DAMAGE_PERCENTAGE, 15)
					.put(FIELD_LEVEL_ADJUSTMENT, 5))
				.object(FIELD_DAMAGE_CHANCE, chance -> chance
					.put(FIELD_BASE_CHANCE, 15)
					.put(FIELD_LEVEL_ADJUSTMENT, 5)))
			.build();
	}

	private static JsonObject buildUnbreakingDefaults() {
		return JSONFormatAPIManager.object()
			.put(FIELD_ENCHANTMENT_ID, UNBREAKING_ID)
			.put(FIELD_MAXIMUM_LEVEL, 5)
			.array(FIELD_COMPATIBLE_ITEMS, values -> values
				.add("helmet")
				.add("chestplate")
				.add("leggings")
				.add("boots")
				.add("sword")
				.add("axe")
				.add("spear")
				.add("mace")
				.add("trident")
				.add("pickaxe")
				.add("shovel")
				.add("hoe")
				.add("fishing-rod")
				.add("bow")
				.add("crossbow")
				.add("flint-and-steel")
				.add("shears")
				.add("shield")
				.add("elytra")
				.add("brush")
				.add("carrot-on-a-stick")
				.add("warped-fungus-on-a-stick"))
			.put(FIELD_CONFLICTING_ENCHANTMENT, false)
			.put(FIELD_WEIGHT, 1)
			.object(FIELD_UNBREAKING, unbreaking -> unbreaking
				.put(FIELD_DURABILITY_BASE_INCREASE, 20)
				.put(FIELD_LEVEL_ADJUSTMENT, 20))
			.build();
	}

	private static JsonObject buildSharpnessDefaults() {
		return JSONFormatAPIManager.object()
			.put(FIELD_ENCHANTMENT_ID, SHARPNESS_ID)
			.put(FIELD_MAXIMUM_LEVEL, 3)
			.array(FIELD_COMPATIBLE_ITEMS, values -> values
				.add("sword")
				.add("axe")
				.add("trident")
				.add("spear"))
			.put(FIELD_CONFLICTING_ENCHANTMENT, false)
			.put(FIELD_WEIGHT, 1)
			.object(FIELD_SHARPNESS, sharpness -> sharpness
				.put(FIELD_BASE_ADDED_DAMAGE, 1)
				.put(FIELD_LEVEL_ADJUSTMENT, 1))
			.build();
	}

	private static JsonObject buildSilkTouchDefaults() {
		return JSONFormatAPIManager.object()
			.put(FIELD_ENCHANTMENT_ID, SILK_TOUCH_ID)
			.put(FIELD_MAXIMUM_LEVEL, 1)
			.array(FIELD_COMPATIBLE_ITEMS, values -> values
				.add("pickaxe")
				.add("shovel")
				.add("axe")
				.add("hoe"))
			.put(FIELD_CONFLICTING_ENCHANTMENT, true)
			.put(FIELD_WEIGHT, 1)
			.build();
	}

	private static JsonObject buildSmiteDefaults() {
		return JSONFormatAPIManager.object()
			.put(FIELD_ENCHANTMENT_ID, SMITE_ID)
			.put(FIELD_MAXIMUM_LEVEL, 3)
			.array(FIELD_COMPATIBLE_ITEMS, values -> values
				.add("sword")
				.add("axe")
				.add("trident")
				.add("spear")
				.add("mace"))
			.put(FIELD_CONFLICTING_ENCHANTMENT, false)
			.put(FIELD_WEIGHT, 1)
			.object(FIELD_SMITE, smite -> smite
				.object(FIELD_VULNERABILITY, vulnerability -> vulnerability
					.put(FIELD_BASE_VULNERABILITY, 10)
					.put(FIELD_LEVEL_ADJUSTMENT, 5))
				.object(FIELD_GLOW_EFFECT, glow -> glow
					.put(FIELD_BASE_DURATION, 3)
					.put(FIELD_LEVEL_ADJUSTMENT, 1)))
			.build();
	}

	private static JsonObject buildPowerDefaults() {
		return JSONFormatAPIManager.object()
			.put(FIELD_ENCHANTMENT_ID, POWER_ID)
			.put(FIELD_MAXIMUM_LEVEL, 5)
			.array(FIELD_COMPATIBLE_ITEMS, values -> values
				.add("bow")
				.add("crossbow"))
			.put(FIELD_CONFLICTING_ENCHANTMENT, false)
			.put(FIELD_WEIGHT, 1)
			.build();
	}

	private static JsonObject buildQuickChargeDefaults() {
		return JSONFormatAPIManager.object()
			.put(FIELD_ENCHANTMENT_ID, QUICK_CHARGE_ID)
			.put(FIELD_MAXIMUM_LEVEL, 3)
			.array(FIELD_COMPATIBLE_ITEMS, values -> values.add("crossbow"))
			.put(FIELD_CONFLICTING_ENCHANTMENT, false)
			.put(FIELD_WEIGHT, 1)
			.build();
	}

	private static JsonObject buildRespirationDefaults() {
		return JSONFormatAPIManager.object()
			.put(FIELD_ENCHANTMENT_ID, RESPIRATION_ID)
			.put(FIELD_MAXIMUM_LEVEL, 3)
			.array(FIELD_COMPATIBLE_ITEMS, values -> values.add("helmet"))
			.put(FIELD_CONFLICTING_ENCHANTMENT, false)
			.put(FIELD_WEIGHT, 1)
			.build();
	}

	private static JsonObject buildRiptideDefaults() {
		return JSONFormatAPIManager.object()
			.put(FIELD_ENCHANTMENT_ID, RIPTIDE_ID)
			.put(FIELD_MAXIMUM_LEVEL, 3)
			.array(FIELD_COMPATIBLE_ITEMS, values -> values.add("trident"))
			.put(FIELD_CONFLICTING_ENCHANTMENT, true)
			.put(FIELD_WEIGHT, 1)
			.build();
	}

	private static JsonObject buildInfinityDefaults() {
		return JSONFormatAPIManager.object()
			.put(FIELD_ENCHANTMENT_ID, INFINITY_ID)
			.put(FIELD_MAXIMUM_LEVEL, 5)
			.array(FIELD_COMPATIBLE_ITEMS, values -> values
				.add("bow")
				.add("crossbow"))
			.put(FIELD_CONFLICTING_ENCHANTMENT, false)
			.put(FIELD_WEIGHT, 1)
			.object(FIELD_INFINITY, infinity -> infinity
				.put(FIELD_BASE_CHANCE, 30)
				.put(FIELD_LEVEL_ADJUSTMENT, 5))
			.build();
	}

	private static JsonObject buildKnockbackDefaults() {
		return JSONFormatAPIManager.object()
			.put(FIELD_ENCHANTMENT_ID, KNOCKBACK_ID)
			.put(FIELD_MAXIMUM_LEVEL, 3)
			.array(FIELD_COMPATIBLE_ITEMS, values -> values
				.add("sword")
				.add("spear")
				.add("axe")
				.add("trident")
				.add("mace"))
			.put(FIELD_CONFLICTING_ENCHANTMENT, false)
			.put(FIELD_WEIGHT, 1)
			.object(FIELD_KNOCKBACK, knockback -> knockback
				.put(FIELD_BASE_KNOCKBACK, 3)
				.put(FIELD_LEVEL_ADJUSTMENT, 2))
			.build();
	}

	private static JsonObject buildPunchDefaults() {
		return JSONFormatAPIManager.object()
			.put(FIELD_ENCHANTMENT_ID, PUNCH_ID)
			.put(FIELD_MAXIMUM_LEVEL, 3)
			.array(FIELD_COMPATIBLE_ITEMS, values -> values
				.add("bow")
				.add("crossbow"))
			.put(FIELD_CONFLICTING_ENCHANTMENT, false)
			.put(FIELD_WEIGHT, 1)
			.object(FIELD_PUNCH, punch -> punch
				.put(FIELD_BASE_ADDED_KNOCKBACK, 1)
				.put(FIELD_LEVEL_ADJUSTMENT, 1))
			.build();
	}

	private static JsonObject buildLuckOfTheSeaDefaults() {
		return JSONFormatAPIManager.object()
			.put(FIELD_ENCHANTMENT_ID, LUCK_OF_THE_SEA_ID)
			.put(FIELD_MAXIMUM_LEVEL, 3)
			.array(FIELD_COMPATIBLE_ITEMS, values -> values.add("fishing-rod"))
			.put(FIELD_CONFLICTING_ENCHANTMENT, false)
			.put(FIELD_WEIGHT, 1)
			.object(FIELD_LUCK_OF_THE_SEA, luckOfTheSea -> luckOfTheSea
				.object(FIELD_TREASURE, treasure -> treasure
					.put(FIELD_BASE_CHANCE_ADJUSTMENT, 6)
					.put(FIELD_LEVEL_ADJUSTMENT, 2))
				.object(FIELD_JUNK, junk -> junk
					.put(FIELD_BASE_CHANCE_ADJUSTMENT, 3)
					.put(FIELD_LEVEL_ADJUSTMENT, 1))
				.object(FIELD_FISH, fish -> fish
					.put(FIELD_BASE_CHANCE_ADJUSTMENT, 3)
					.put(FIELD_LEVEL_ADJUSTMENT, 1)))
			.build();
	}

	private static JsonObject buildLungeDefaults() {
		return JSONFormatAPIManager.object()
			.put(FIELD_ENCHANTMENT_ID, LUNGE_ID)
			.put(FIELD_MAXIMUM_LEVEL, 3)
			.array(FIELD_COMPATIBLE_ITEMS, values -> values.add("spear"))
			.put(FIELD_CONFLICTING_ENCHANTMENT, false)
			.put(FIELD_WEIGHT, 1)
			.build();
	}

	private static JsonObject buildLureDefaults() {
		return JSONFormatAPIManager.object()
			.put(FIELD_ENCHANTMENT_ID, LURE_ID)
			.put(FIELD_MAXIMUM_LEVEL, 3)
			.array(FIELD_COMPATIBLE_ITEMS, values -> values.add("fishing-rod"))
			.put(FIELD_CONFLICTING_ENCHANTMENT, false)
			.put(FIELD_WEIGHT, 1)
			.build();
	}

	private static JsonObject buildMendingDefaults() {
		return JSONFormatAPIManager.object()
			.put(FIELD_ENCHANTMENT_ID, MENDING_ID)
			.put(FIELD_MAXIMUM_LEVEL, 5)
			.array(FIELD_COMPATIBLE_ITEMS, values -> values
				.add("helmet")
				.add("chestplate")
				.add("leggings")
				.add("boots")
				.add("sword")
				.add("axe")
				.add("spear")
				.add("mace")
				.add("trident")
				.add("pickaxe")
				.add("shovel")
				.add("hoe")
				.add("fishing-rod")
				.add("bow")
				.add("crossbow")
				.add("flint-and-steel")
				.add("shears")
				.add("shield")
				.add("elytra")
				.add("brush")
				.add("carrot-on-a-stick")
				.add("warped-fungus-on-a-stick"))
			.put(FIELD_CONFLICTING_ENCHANTMENT, false)
			.put(FIELD_WEIGHT, 1)
			.object(FIELD_MENDING, mending -> mending
				.put(FIELD_BASE_CHANCE, 30)
				.put(FIELD_LEVEL_ADJUSTMENT, 5))
			.build();
	}

	private static JsonObject buildPiercingDefaults() {
		return JSONFormatAPIManager.object()
			.put(FIELD_ENCHANTMENT_ID, PIERCING_ID)
			.put(FIELD_MAXIMUM_LEVEL, 5)
			.array(FIELD_COMPATIBLE_ITEMS, values -> values
				.add("bow")
				.add("crossbow"))
			.put(FIELD_CONFLICTING_ENCHANTMENT, false)
			.put(FIELD_WEIGHT, 1)
			.build();
	}

	private static JsonObject buildMultishotDefaults() {
		return JSONFormatAPIManager.object()
			.put(FIELD_ENCHANTMENT_ID, MULTISHOT_ID)
			.put(FIELD_MAXIMUM_LEVEL, 1)
			.array(FIELD_COMPATIBLE_ITEMS, values -> values
				.add("bow")
				.add("crossbow"))
			.put(FIELD_CONFLICTING_ENCHANTMENT, false)
			.put(FIELD_WEIGHT, 1)
			.build();
	}

	private static JsonObject buildGenericDefaults(String fileKey) {
		return JSONFormatAPIManager.object()
			.put(FIELD_ENCHANTMENT_ID, "")
			.put(FIELD_MAXIMUM_LEVEL, 1)
			.array(FIELD_COMPATIBLE_ITEMS, ignored -> { })
			.put(FIELD_CONFLICTING_ENCHANTMENT, true)
			.put(FIELD_WEIGHT, 1)
			.build();
	}

	private static EnchantmentDefinition parseDefinition(JsonObject root) {
		if (root == null || !readBoolean(root, FIELD_ENABLED, true)) return null;
		String rawId = readString(root, FIELD_ENCHANTMENT_ID, "");
		String enchantmentId = JSONAPIManager.normalizeRegistryIdentifierForLookup(rawId);
		if (enchantmentId.isBlank()) return null;

		int maximumLevel = Math.max(1, readInt(root, FIELD_MAXIMUM_LEVEL, 1));
		boolean conflictingEnchantment = readBoolean(root, FIELD_CONFLICTING_ENCHANTMENT, true);
		int weight = Math.max(0, readInt(root, FIELD_WEIGHT, 1));
		double baseAdjustment;
		double levelAdjustment;
		double baseDuration;
		double levelDuration;
		double baseKnockbackResistance;
		double levelKnockbackResistance;
		double baseArmorPenetration;
		double levelArmorPenetration;
		double baseTreasureChanceAdjustment = 0.0D;
		double levelTreasureChanceAdjustment = 0.0D;
		double baseJunkChanceAdjustment = 0.0D;
		double levelJunkChanceAdjustment = 0.0D;
		double baseFishChanceAdjustment = 0.0D;
		double levelFishChanceAdjustment = 0.0D;
		double baseThornsChanceAdjustment = 0.0D;
		double levelThornsChanceAdjustment = 0.0D;
		if (AQUA_AFFINITY_ID.equals(enchantmentId)) {
			JsonObject aquaAffinity = object(root, FIELD_AQUA_AFFINITY);
			baseAdjustment = Math.max(0.0D, readDouble(aquaAffinity, FIELD_BASE_SUBMERGED_MINING_SPEED, 0.0D));
			levelAdjustment = Math.max(0.0D, readDouble(aquaAffinity, FIELD_LEVEL_ADJUSTMENT, 0.0D));
			baseDuration = 0.0D;
			levelDuration = 0.0D;
			baseKnockbackResistance = 0.0D;
			levelKnockbackResistance = 0.0D;
			baseArmorPenetration = 0.0D;
			levelArmorPenetration = 0.0D;
		} else if (BANE_OF_ARTHROPODS_ID.equals(enchantmentId)) {
			JsonObject baneOfArthropods = object(root, FIELD_BANE_OF_ARTHROPODS);
			JsonObject effect = object(baneOfArthropods, FIELD_EFFECT);
			baseAdjustment = Math.max(0.0D, readDouble(effect, FIELD_BASE_EFFECT, 0.0D));
			levelAdjustment = Math.max(0.0D, readDouble(effect, FIELD_LEVEL_ADJUSTMENT, 0.0D));
			JsonObject duration = object(baneOfArthropods, FIELD_DURATION);
			baseDuration = Math.max(0.0D, readDouble(duration, FIELD_BASE_DURATION, 0.0D));
			levelDuration = Math.max(0.0D, readDouble(duration, FIELD_LEVEL_ADJUSTMENT, 0.0D));
			baseKnockbackResistance = 0.0D;
			levelKnockbackResistance = 0.0D;
			baseArmorPenetration = 0.0D;
			levelArmorPenetration = 0.0D;
		} else if (PROTECTION_ID.equals(enchantmentId)) {
			JsonObject protection = object(root, FIELD_PROTECTION);
			baseAdjustment = Math.max(0.0D, readDouble(protection, FIELD_BASE_DAMAGE_REDUCTION, 0.0D));
			levelAdjustment = Math.max(0.0D, readDouble(protection, FIELD_LEVEL_ADJUSTMENT, 0.0D));
			baseDuration = 0.0D;
			levelDuration = 0.0D;
			baseKnockbackResistance = 0.0D;
			levelKnockbackResistance = 0.0D;
			baseArmorPenetration = 0.0D;
			levelArmorPenetration = 0.0D;
		} else if (BLAST_PROTECTION_ID.equals(enchantmentId)) {
			JsonObject blastProtection = object(root, FIELD_BLAST_PROTECTION);
			JsonObject explosiveProtection = object(blastProtection, FIELD_EXPLOSIVE_PROTECTION);
			baseAdjustment = Math.max(0.0D, readDouble(explosiveProtection, FIELD_BASE_VALUE, 0.0D));
			levelAdjustment = Math.max(0.0D, readDouble(explosiveProtection, FIELD_LEVEL_ADJUSTMENT, 0.0D));
			JsonObject knockbackResistance = object(blastProtection, FIELD_EXPLOSION_KNOCKBACK_RESISTANCE);
			baseKnockbackResistance = Math.max(0.0D, readDouble(knockbackResistance, FIELD_BASE_VALUE, 0.0D));
			levelKnockbackResistance = Math.max(0.0D, readDouble(knockbackResistance, FIELD_LEVEL_ADJUSTMENT, 0.0D));
			baseDuration = 0.0D;
			levelDuration = 0.0D;
			baseArmorPenetration = 0.0D;
			levelArmorPenetration = 0.0D;
		} else if (PROJECTILE_PROTECTION_ID.equals(enchantmentId)) {
			JsonObject projectileProtection = object(root, FIELD_PROJECTILE_PROTECTION);
			JsonObject protection = object(projectileProtection, FIELD_PROJECTILE_PROTECTION);
			baseAdjustment = Math.max(0.0D, readDouble(protection, FIELD_BASE_VALUE, 0.0D));
			levelAdjustment = Math.max(0.0D, readDouble(protection, FIELD_LEVEL_ADJUSTMENT, 0.0D));
			baseDuration = 0.0D;
			levelDuration = 0.0D;
			baseKnockbackResistance = 0.0D;
			levelKnockbackResistance = 0.0D;
			baseArmorPenetration = 0.0D;
			levelArmorPenetration = 0.0D;
		} else if (BREACH_ID.equals(enchantmentId)) {
			JsonObject breach = object(root, FIELD_BREACH);
			baseArmorPenetration = Math.max(0.0D, readDouble(breach, FIELD_BASE_ARMOR_PENETRATION, 0.0D));
			levelArmorPenetration = Math.max(0.0D, readDouble(breach, FIELD_LEVEL_ADJUSTMENT, 0.0D));
			baseAdjustment = 0.0D;
			levelAdjustment = 0.0D;
			baseDuration = 0.0D;
			levelDuration = 0.0D;
			baseKnockbackResistance = 0.0D;
			levelKnockbackResistance = 0.0D;
		} else if (DEPTH_STRIDER_ID.equals(enchantmentId)) {
			JsonObject depthStrider = object(root, FIELD_DEPTH_STRIDER);
			baseAdjustment = Math.max(0.0D, readDouble(depthStrider, FIELD_BASE_WATER_MOVEMENT_EFFICIENCY, 0.0D));
			levelAdjustment = Math.max(0.0D, readDouble(depthStrider, FIELD_LEVEL_ADJUSTMENT, 0.0D));
			baseDuration = 0.0D;
			levelDuration = 0.0D;
			baseKnockbackResistance = 0.0D;
			levelKnockbackResistance = 0.0D;
			baseArmorPenetration = 0.0D;
			levelArmorPenetration = 0.0D;
		} else if (EFFICIENCY_ID.equals(enchantmentId)) {
			JsonObject efficiency = object(root, FIELD_EFFICIENCY);
			baseAdjustment = Math.max(0.0D, readDouble(efficiency, FIELD_BASE_MINING_EFFICIENCY, 0.0D));
			levelAdjustment = Math.max(0.0D, readDouble(efficiency, FIELD_LEVEL_ADJUSTMENT, 0.0D));
			baseDuration = 0.0D;
			levelDuration = 0.0D;
			baseKnockbackResistance = 0.0D;
			levelKnockbackResistance = 0.0D;
				baseArmorPenetration = 0.0D;
				levelArmorPenetration = 0.0D;
		} else if (FEATHER_FALLING_ID.equals(enchantmentId)) {
			JsonObject featherFalling = object(root, FIELD_FEATHER_FALLING);
			baseAdjustment = Math.max(0.0D, readDouble(featherFalling, FIELD_BASE_FALL_DAMAGE_REDUCTION, 0.0D));
			levelAdjustment = Math.max(0.0D, readDouble(featherFalling, FIELD_LEVEL_ADJUSTMENT, 0.0D));
			baseDuration = 0.0D;
			levelDuration = 0.0D;
			baseKnockbackResistance = 0.0D;
			levelKnockbackResistance = 0.0D;
			baseArmorPenetration = 0.0D;
			levelArmorPenetration = 0.0D;
		} else if (FORTUNE_ID.equals(enchantmentId)) {
			JsonObject fortune = object(root, FIELD_FORTUNE);
			baseAdjustment = Math.max(0.0D, readDouble(fortune, FIELD_BASE_MULTIPLIER_CHANCE, 0.0D));
			levelAdjustment = Math.max(0.0D, readDouble(fortune, FIELD_LEVEL_ADJUSTMENT, 0.0D));
			baseDuration = 0.0D;
			levelDuration = 0.0D;
			baseKnockbackResistance = 0.0D;
			levelKnockbackResistance = 0.0D;
			baseArmorPenetration = 0.0D;
			levelArmorPenetration = 0.0D;
		} else if (LOOTING_ID.equals(enchantmentId)) {
			JsonObject looting = object(root, FIELD_LOOTING);
			baseAdjustment = Math.max(0.0D, readDouble(looting, FIELD_BASE_MULTIPLIER_CHANCE, 0.0D));
			levelAdjustment = Math.max(0.0D, readDouble(looting, FIELD_LEVEL_ADJUSTMENT, 0.0D));
			baseDuration = 0.0D;
			levelDuration = 0.0D;
			baseKnockbackResistance = 0.0D;
			levelKnockbackResistance = 0.0D;
			baseArmorPenetration = 0.0D;
			levelArmorPenetration = 0.0D;
		} else if (FIRE_ASPECT_ID.equals(enchantmentId)) {
			JsonObject fireAspect = object(root, FIELD_FIRE_ASPECT);
			baseDuration = Math.max(0.0D, readDouble(fireAspect, FIELD_BASE_FIRE_DURATION, 0.0D));
			levelDuration = Math.max(0.0D, readDouble(fireAspect, FIELD_LEVEL_ADJUSTMENT, 0.0D));
			baseAdjustment = 0.0D;
			levelAdjustment = 0.0D;
			baseKnockbackResistance = 0.0D;
			levelKnockbackResistance = 0.0D;
			baseArmorPenetration = 0.0D;
			levelArmorPenetration = 0.0D;
		} else if (FIRE_PROTECTION_ID.equals(enchantmentId)) {
			JsonObject fireProtection = object(root, FIELD_FIRE_PROTECTION);
			JsonObject burnProtection = object(fireProtection, FIELD_BURN_PROTECTION);
			baseAdjustment = Math.max(0.0D, readDouble(burnProtection, FIELD_BASE_VALUE, 0.0D));
			levelAdjustment = Math.max(0.0D, readDouble(burnProtection, FIELD_LEVEL_ADJUSTMENT, 0.0D));
			JsonObject burnDurationReduction = object(fireProtection, FIELD_BURN_DURATION_REDUCTION);
			baseDuration = Math.max(0.0D, readDouble(burnDurationReduction, FIELD_BASE_VALUE, 0.0D));
			levelDuration = Math.max(0.0D, readDouble(burnDurationReduction, FIELD_LEVEL_ADJUSTMENT, 0.0D));
			baseKnockbackResistance = 0.0D;
			levelKnockbackResistance = 0.0D;
			baseArmorPenetration = 0.0D;
			levelArmorPenetration = 0.0D;
		} else if (FLAME_ID.equals(enchantmentId)) {
			JsonObject flame = object(root, FIELD_FLAME);
			baseDuration = Math.max(0.0D, readDouble(flame, FIELD_BASE_FIRE_DURATION, 0.0D));
			levelDuration = Math.max(0.0D, readDouble(flame, FIELD_LEVEL_ADJUSTMENT, 0.0D));
			baseAdjustment = 0.0D;
			levelAdjustment = 0.0D;
			baseKnockbackResistance = 0.0D;
			levelKnockbackResistance = 0.0D;
			baseArmorPenetration = 0.0D;
			levelArmorPenetration = 0.0D;
		} else if (IMPALING_ID.equals(enchantmentId)) {
			JsonObject impaling = object(root, FIELD_IMPALING);
			baseAdjustment = Math.max(0.0D, readDouble(impaling, FIELD_BASE_ADDED_DAMAGE, 0.0D));
			levelAdjustment = Math.max(0.0D, readDouble(impaling, FIELD_LEVEL_ADJUSTMENT, 0.0D));
			baseDuration = 0.0D;
			levelDuration = 0.0D;
			baseKnockbackResistance = 0.0D;
			levelKnockbackResistance = 0.0D;
			baseArmorPenetration = 0.0D;
			levelArmorPenetration = 0.0D;
		} else if (SHARPNESS_ID.equals(enchantmentId)) {
			JsonObject sharpness = object(root, FIELD_SHARPNESS);
			baseAdjustment = Math.max(0.0D, readDouble(sharpness, FIELD_BASE_ADDED_DAMAGE, 0.0D));
			levelAdjustment = Math.max(0.0D, readDouble(sharpness, FIELD_LEVEL_ADJUSTMENT, 0.0D));
			baseDuration = 0.0D;
			levelDuration = 0.0D;
			baseKnockbackResistance = 0.0D;
			levelKnockbackResistance = 0.0D;
			baseArmorPenetration = 0.0D;
			levelArmorPenetration = 0.0D;
		} else if (SOUL_SPEED_ID.equals(enchantmentId)) {
			JsonObject soulSpeed = object(root, FIELD_SOUL_SPEED);
			baseAdjustment = Math.max(0.0D, readDouble(soulSpeed, FIELD_BASE_SPEED_INCREASE, 0.0D));
			levelAdjustment = Math.max(0.0D, readDouble(soulSpeed, FIELD_LEVEL_ADJUSTMENT, 0.0D));
			baseDuration = 0.0D;
			levelDuration = 0.0D;
			baseKnockbackResistance = 0.0D;
			levelKnockbackResistance = 0.0D;
			baseArmorPenetration = 0.0D;
			levelArmorPenetration = 0.0D;
		} else if (SWEEPING_EDGE_ID.equals(enchantmentId)) {
			JsonObject sweepingEdge = object(root, FIELD_SWEEPING_EDGE);
			baseAdjustment = Math.max(0.0D, readDouble(sweepingEdge, FIELD_BASE_SWEEPING_DAMAGE, 0.0D));
			levelAdjustment = Math.max(0.0D, readDouble(sweepingEdge, FIELD_LEVEL_ADJUSTMENT, 0.0D));
			baseDuration = 0.0D;
			levelDuration = 0.0D;
			baseKnockbackResistance = 0.0D;
			levelKnockbackResistance = 0.0D;
			baseArmorPenetration = 0.0D;
			levelArmorPenetration = 0.0D;
		} else if (THORNS_ID.equals(enchantmentId)) {
			JsonObject thorns = object(root, FIELD_THORNS);
			JsonObject damageReflection = object(thorns, FIELD_DAMAGE_REFLECTION);
			baseAdjustment = Math.max(0.0D, readDouble(damageReflection, FIELD_BASE_DAMAGE_PERCENTAGE, 0.0D));
			levelAdjustment = Math.max(0.0D, readDouble(damageReflection, FIELD_LEVEL_ADJUSTMENT, 0.0D));
			JsonObject damageChance = object(thorns, FIELD_DAMAGE_CHANCE);
			baseThornsChanceAdjustment = Math.max(0.0D, readDouble(damageChance, FIELD_BASE_CHANCE, 0.0D));
			levelThornsChanceAdjustment = Math.max(0.0D, readDouble(damageChance, FIELD_LEVEL_ADJUSTMENT, 0.0D));
			baseDuration = 0.0D;
			levelDuration = 0.0D;
			baseKnockbackResistance = 0.0D;
			levelKnockbackResistance = 0.0D;
			baseArmorPenetration = 0.0D;
			levelArmorPenetration = 0.0D;
		} else if (UNBREAKING_ID.equals(enchantmentId)) {
			JsonObject unbreaking = object(root, FIELD_UNBREAKING);
			baseAdjustment = Math.max(0.0D, readDouble(unbreaking, FIELD_DURABILITY_BASE_INCREASE, 0.0D));
			levelAdjustment = Math.max(0.0D, readDouble(unbreaking, FIELD_LEVEL_ADJUSTMENT, 0.0D));
			baseDuration = 0.0D;
			levelDuration = 0.0D;
			baseKnockbackResistance = 0.0D;
			levelKnockbackResistance = 0.0D;
			baseArmorPenetration = 0.0D;
			levelArmorPenetration = 0.0D;
		} else if (SMITE_ID.equals(enchantmentId)) {
			JsonObject smite = object(root, FIELD_SMITE);
			JsonObject vulnerability = object(smite, FIELD_VULNERABILITY);
			baseAdjustment = Math.max(0.0D, readDouble(vulnerability, FIELD_BASE_VULNERABILITY, 0.0D));
			levelAdjustment = Math.max(0.0D, readDouble(vulnerability, FIELD_LEVEL_ADJUSTMENT, 0.0D));
			JsonObject glowEffect = object(smite, FIELD_GLOW_EFFECT);
			baseDuration = Math.max(0.0D, readDouble(glowEffect, FIELD_BASE_DURATION, 0.0D));
			levelDuration = Math.max(0.0D, readDouble(glowEffect, FIELD_LEVEL_ADJUSTMENT, 0.0D));
			baseKnockbackResistance = 0.0D;
			levelKnockbackResistance = 0.0D;
			baseArmorPenetration = 0.0D;
			levelArmorPenetration = 0.0D;
		} else if (INFINITY_ID.equals(enchantmentId)) {
			JsonObject infinity = object(root, FIELD_INFINITY);
			baseAdjustment = Math.max(0.0D, readDouble(infinity, FIELD_BASE_CHANCE, 0.0D));
			levelAdjustment = Math.max(0.0D, readDouble(infinity, FIELD_LEVEL_ADJUSTMENT, 0.0D));
			baseDuration = 0.0D;
			levelDuration = 0.0D;
			baseKnockbackResistance = 0.0D;
			levelKnockbackResistance = 0.0D;
			baseArmorPenetration = 0.0D;
			levelArmorPenetration = 0.0D;
		} else if (MENDING_ID.equals(enchantmentId)) {
			JsonObject mending = object(root, FIELD_MENDING);
			baseAdjustment = Math.max(0.0D, readDouble(mending, FIELD_BASE_CHANCE, 0.0D));
			levelAdjustment = Math.max(0.0D, readDouble(mending, FIELD_LEVEL_ADJUSTMENT, 0.0D));
			baseDuration = 0.0D;
			levelDuration = 0.0D;
			baseKnockbackResistance = 0.0D;
			levelKnockbackResistance = 0.0D;
			baseArmorPenetration = 0.0D;
			levelArmorPenetration = 0.0D;
		} else if (KNOCKBACK_ID.equals(enchantmentId)) {
			JsonObject knockback = object(root, FIELD_KNOCKBACK);
			baseAdjustment = Math.max(0.0D, readDouble(knockback, FIELD_BASE_KNOCKBACK, 0.0D));
			levelAdjustment = Math.max(0.0D, readDouble(knockback, FIELD_LEVEL_ADJUSTMENT, 0.0D));
			baseDuration = 0.0D;
			levelDuration = 0.0D;
			baseKnockbackResistance = 0.0D;
			levelKnockbackResistance = 0.0D;
			baseArmorPenetration = 0.0D;
			levelArmorPenetration = 0.0D;
		} else if (PUNCH_ID.equals(enchantmentId)) {
			JsonObject punch = object(root, FIELD_PUNCH);
			baseAdjustment = Math.max(0.0D, readDouble(punch, FIELD_BASE_ADDED_KNOCKBACK, 0.0D));
			levelAdjustment = Math.max(0.0D, readDouble(punch, FIELD_LEVEL_ADJUSTMENT, 0.0D));
			baseDuration = 0.0D;
			levelDuration = 0.0D;
			baseKnockbackResistance = 0.0D;
			levelKnockbackResistance = 0.0D;
			baseArmorPenetration = 0.0D;
			levelArmorPenetration = 0.0D;
		} else if (LUCK_OF_THE_SEA_ID.equals(enchantmentId)) {
			JsonObject luckOfTheSea = object(root, FIELD_LUCK_OF_THE_SEA);
			JsonObject treasure = object(luckOfTheSea, FIELD_TREASURE);
			baseTreasureChanceAdjustment = Math.max(0.0D, readDouble(treasure, FIELD_BASE_CHANCE_ADJUSTMENT, 0.0D));
			levelTreasureChanceAdjustment = Math.max(0.0D, readDouble(treasure, FIELD_LEVEL_ADJUSTMENT, 0.0D));
			JsonObject junk = object(luckOfTheSea, FIELD_JUNK);
			baseJunkChanceAdjustment = Math.max(0.0D, readDouble(junk, FIELD_BASE_CHANCE_ADJUSTMENT, 0.0D));
			levelJunkChanceAdjustment = Math.max(0.0D, readDouble(junk, FIELD_LEVEL_ADJUSTMENT, 0.0D));
			JsonObject fish = object(luckOfTheSea, FIELD_FISH);
			baseFishChanceAdjustment = Math.max(0.0D, readDouble(fish, FIELD_BASE_CHANCE_ADJUSTMENT, 0.0D));
			levelFishChanceAdjustment = Math.max(0.0D, readDouble(fish, FIELD_LEVEL_ADJUSTMENT, 0.0D));
			baseAdjustment = 0.0D;
			levelAdjustment = 0.0D;
			baseDuration = 0.0D;
			levelDuration = 0.0D;
			baseKnockbackResistance = 0.0D;
			levelKnockbackResistance = 0.0D;
			baseArmorPenetration = 0.0D;
			levelArmorPenetration = 0.0D;
		} else {
			baseAdjustment = 0.0D;
			levelAdjustment = 0.0D;
			baseDuration = 0.0D;
			levelDuration = 0.0D;
			baseKnockbackResistance = 0.0D;
			levelKnockbackResistance = 0.0D;
			baseArmorPenetration = 0.0D;
			levelArmorPenetration = 0.0D;
		}
		List<String> compatibleItems = new ArrayList<>();
		JsonElement compatible = root.get(FIELD_COMPATIBLE_ITEMS);
		if (compatible instanceof JsonArray values) {
			for (JsonElement value : values) {
				if (value != null && value.isJsonPrimitive()) {
					String normalized = value.getAsString().trim().toLowerCase(Locale.ROOT).replace('_', '-');
					if (!normalized.isBlank()) compatibleItems.add(normalized);
				}
			}
		}
		return new EnchantmentDefinition(
			enchantmentId,
			maximumLevel,
			List.copyOf(compatibleItems),
			conflictingEnchantment,
			weight,
			baseAdjustment,
			levelAdjustment,
			baseDuration,
			levelDuration,
			baseKnockbackResistance,
			levelKnockbackResistance,
			baseArmorPenetration,
			levelArmorPenetration,
			baseTreasureChanceAdjustment,
			levelTreasureChanceAdjustment,
			baseJunkChanceAdjustment,
			levelJunkChanceAdjustment,
			baseFishChanceAdjustment,
			levelFishChanceAdjustment,
			baseThornsChanceAdjustment,
			levelThornsChanceAdjustment,
			true
		);
	}

	private static JsonObject object(JsonObject source, String key) {
		if (source == null || !source.has(key) || !source.get(key).isJsonObject()) return new JsonObject();
		return source.getAsJsonObject(key);
	}

	private static boolean readBoolean(JsonObject root, String key, boolean fallback) {
		try {
			return root != null && root.has(key) ? root.get(key).getAsBoolean() : fallback;
		} catch (RuntimeException exception) {
			return fallback;
		}
	}

	private static String readString(JsonObject root, String key, String fallback) {
		try {
			return root != null && root.has(key) ? root.get(key).getAsString() : fallback;
		} catch (RuntimeException exception) {
			return fallback;
		}
	}

	private static int readInt(JsonObject root, String key, int fallback) {
		try {
			return root != null && root.has(key) ? root.get(key).getAsInt() : fallback;
		} catch (RuntimeException exception) {
			return fallback;
		}
	}

	private static double readDouble(JsonObject root, String key, double fallback) {
		try {
			return root != null && root.has(key) ? root.get(key).getAsDouble() : fallback;
		} catch (RuntimeException exception) {
			return fallback;
		}
	}

	static final class EnchantmentDefinition {
		final String enchantmentId;
		final int maximumLevel;
		final List<String> compatibleItems;
		final boolean conflictingEnchantment;
		final int weight;
		final double baseAdjustment;
		final double levelAdjustment;
		final double baseDuration;
		final double levelDuration;
		final double baseKnockbackResistance;
		final double levelKnockbackResistance;
		final double baseArmorPenetration;
		final double levelArmorPenetration;
		final double baseTreasureChanceAdjustment;
		final double levelTreasureChanceAdjustment;
		final double baseJunkChanceAdjustment;
		final double levelJunkChanceAdjustment;
		final double baseFishChanceAdjustment;
		final double levelFishChanceAdjustment;
		final double baseThornsChanceAdjustment;
		final double levelThornsChanceAdjustment;
		final boolean enabled;

		private EnchantmentDefinition(
			String enchantmentId,
			int maximumLevel,
			List<String> compatibleItems,
			boolean conflictingEnchantment,
			int weight,
			double baseAdjustment,
			double levelAdjustment,
			double baseDuration,
			double levelDuration,
			double baseKnockbackResistance,
			double levelKnockbackResistance,
			double baseArmorPenetration,
			double levelArmorPenetration,
			double baseTreasureChanceAdjustment,
			double levelTreasureChanceAdjustment,
			double baseJunkChanceAdjustment,
			double levelJunkChanceAdjustment,
			double baseFishChanceAdjustment,
			double levelFishChanceAdjustment,
			double baseThornsChanceAdjustment,
			double levelThornsChanceAdjustment,
			boolean enabled
		) {
			this.enchantmentId = enchantmentId;
			this.maximumLevel = maximumLevel;
			this.compatibleItems = compatibleItems;
			this.conflictingEnchantment = conflictingEnchantment;
			this.weight = weight;
			this.baseAdjustment = baseAdjustment;
			this.levelAdjustment = levelAdjustment;
			this.baseDuration = baseDuration;
			this.levelDuration = levelDuration;
			this.baseKnockbackResistance = baseKnockbackResistance;
			this.levelKnockbackResistance = levelKnockbackResistance;
			this.baseArmorPenetration = baseArmorPenetration;
			this.levelArmorPenetration = levelArmorPenetration;
			this.baseTreasureChanceAdjustment = baseTreasureChanceAdjustment;
			this.levelTreasureChanceAdjustment = levelTreasureChanceAdjustment;
			this.baseJunkChanceAdjustment = baseJunkChanceAdjustment;
			this.levelJunkChanceAdjustment = levelJunkChanceAdjustment;
			this.baseFishChanceAdjustment = baseFishChanceAdjustment;
			this.levelFishChanceAdjustment = levelFishChanceAdjustment;
			this.baseThornsChanceAdjustment = baseThornsChanceAdjustment;
			this.levelThornsChanceAdjustment = levelThornsChanceAdjustment;
			this.enabled = enabled;
		}

		private JsonObject toClientSyncJson() {
			JsonObject root = new JsonObject();
			root.addProperty(FIELD_ENCHANTMENT_ID, enchantmentId);
			root.addProperty(FIELD_MAXIMUM_LEVEL, maximumLevel);
			JsonArray values = new JsonArray();
			for (String compatibleItem : compatibleItems) values.add(compatibleItem);
			root.add(FIELD_COMPATIBLE_ITEMS, values);
			root.addProperty(FIELD_CONFLICTING_ENCHANTMENT, conflictingEnchantment);
			root.addProperty(FIELD_WEIGHT, weight);
			root.addProperty(FIELD_BASE_ADJUSTMENT, baseAdjustment);
			root.addProperty(FIELD_LEVEL_ADJUSTMENT, levelAdjustment);
			root.addProperty(FIELD_BASE_DURATION, baseDuration);
			root.addProperty(FIELD_LEVEL_DURATION, levelDuration);
			root.addProperty(FIELD_BASE_KNOCKBACK_RESISTANCE, baseKnockbackResistance);
			root.addProperty(FIELD_LEVEL_KNOCKBACK_RESISTANCE, levelKnockbackResistance);
			root.addProperty(FIELD_BASE_ARMOR_PENETRATION, baseArmorPenetration);
			root.addProperty(FIELD_LEVEL_ARMOR_PENETRATION, levelArmorPenetration);
			root.addProperty(FIELD_BASE_THORNS_CHANCE, baseThornsChanceAdjustment);
			root.addProperty(FIELD_LEVEL_THORNS_CHANCE, levelThornsChanceAdjustment);
			JsonObject luckOfTheSea = new JsonObject();
			JsonObject treasure = new JsonObject();
			treasure.addProperty(FIELD_BASE_CHANCE_ADJUSTMENT, baseTreasureChanceAdjustment);
			treasure.addProperty(FIELD_LEVEL_ADJUSTMENT, levelTreasureChanceAdjustment);
			luckOfTheSea.add(FIELD_TREASURE, treasure);
			JsonObject junk = new JsonObject();
			junk.addProperty(FIELD_BASE_CHANCE_ADJUSTMENT, baseJunkChanceAdjustment);
			junk.addProperty(FIELD_LEVEL_ADJUSTMENT, levelJunkChanceAdjustment);
			luckOfTheSea.add(FIELD_JUNK, junk);
			JsonObject fish = new JsonObject();
			fish.addProperty(FIELD_BASE_CHANCE_ADJUSTMENT, baseFishChanceAdjustment);
			fish.addProperty(FIELD_LEVEL_ADJUSTMENT, levelFishChanceAdjustment);
			luckOfTheSea.add(FIELD_FISH, fish);
			root.add(FIELD_LUCK_OF_THE_SEA, luckOfTheSea);
			root.addProperty(FIELD_ENABLED, enabled);
			return root;
		}
	}
}

