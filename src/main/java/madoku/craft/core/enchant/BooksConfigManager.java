package madoku.craft.core.enchant;

import com.google.gson.JsonArray;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import madoku.craft.core.json.JSONFormatManager;
import madoku.craft.core.json.MadokuJSONManager;
import net.minecraft.core.Holder;
import net.minecraft.core.Registry;
import net.minecraft.core.component.DataComponents;
import net.minecraft.server.MinecraftServer;
import net.minecraft.tags.ItemTags;
import net.minecraft.network.chat.contents.TranslatableContents;
import net.minecraft.world.entity.EquipmentSlot;
import net.minecraft.world.item.ItemStack;
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
public final class BooksConfigManager {
	private static final Logger LOGGER = LoggerFactory.getLogger(BooksConfigManager.class);
	static final String AQUA_AFFINITY_ID = "minecraft:aqua_affinity";
	static final String BANE_OF_ARTHROPODS_ID = "minecraft:bane_of_arthropods";
	static final String BLAST_PROTECTION_ID = "minecraft:blast_protection";
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
	static final String INFINITY_ID = "minecraft:infinity";
	static final String KNOCKBACK_ID = "minecraft:knockback";
	private static final String FIELD_ENCHANTMENT_ID = "enchantment-id";
	private static final String FIELD_MAXIMUM_LEVEL = "maximum-level";
	private static final String FIELD_COMPATIBLE_ITEMS = "compatible-items";
	private static final String FIELD_CONFLICTING_ENCHANTMENT = "conflicting-enchantment";
	private static final String FIELD_WEIGHT = "weight";
	private static final String FIELD_AQUA_AFFINITY = "aqua-affinity";
	private static final String FIELD_BANE_OF_ARTHROPODS = "bane-of-arthropods";
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
	private static final String FIELD_BASE_ADDED_DAMAGE = "base-added-damage";
	private static final String FIELD_INFINITY = "infinity";
	private static final String FIELD_BASE_CHANCE = "base-chance";
	private static final String FIELD_KNOCKBACK = "knockback";
	private static final String FIELD_BASE_KNOCKBACK = "base-knockback";
	private static final String FIELD_BASE_ADJUSTMENT = "base-adjustment";
	private static final String FIELD_BASE_SUBMERGED_MINING_SPEED = "base-submerged-mining-speed";
	private static final String FIELD_LEVEL_ADJUSTMENT = "level-adjustment";
	private static final String FIELD_ENABLED = "enabled";
	private static final String AQUA_AFFINITY_FILE_KEY = "aqua-affinity";
	private static final String BANE_OF_ARTHROPODS_FILE_KEY = "bane-of-arthropods";
	private static final String BLAST_PROTECTION_FILE_KEY = "blast-protection";
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
	private static final String INFINITY_FILE_KEY = "infinity";
	private static final String KNOCKBACK_FILE_KEY = "knockback";
	private static final Map<String, EnchantmentDefinition> EMPTY_DEFINITIONS = Map.of();
	private static volatile Map<String, EnchantmentDefinition> definitions = EMPTY_DEFINITIONS;
	private static volatile Map<String, EnchantmentDefinition> clientSynchronizedDefinitions = EMPTY_DEFINITIONS;
	private static volatile boolean clientSynchronized;
	private static volatile Map<Enchantment, String> enchantmentIds = Map.of();

	private BooksConfigManager() {
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
		snapshot.addProperty("enabled", EnchantConfigManager.isEnabled());
		snapshot.addProperty("enchantment-table-enabled", EnchantConfigManager.isEnchantmentTableEnabled());
		snapshot.addProperty("custom-enchantments-enabled", EnchantConfigManager.areCustomEnchantmentsEnabled());
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

			EnchantConfigManager.applyClientSynchronizedSettings(
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
		EnchantConfigManager.resetClientSynchronizedState();
	}

	private static Map<String, EnchantmentDefinition> activeDefinitions() {
		return clientSynchronized ? clientSynchronizedDefinitions : definitions;
	}

	public static int getConfiguredMaximumLevel(Enchantment enchantment, int vanillaMaximumLevel) {
		if (enchantment == null || !EnchantConfigManager.areCustomEnchantmentsEnabled()) return vanillaMaximumLevel;
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
			|| !EnchantConfigManager.areCustomEnchantmentsEnabled()) return vanillaCanEnchant;

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
		if (!EnchantConfigManager.areCustomEnchantmentsEnabled() || first == null || second == null) {
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
		if (enchantment == null || !EnchantConfigManager.areCustomEnchantmentsEnabled()) return false;
		if (!BANE_OF_ARTHROPODS_ID.equals(resolveEnchantmentId(enchantment))) return false;
		EnchantmentDefinition definition = activeDefinitions().get(BANE_OF_ARTHROPODS_ID);
		return definition != null && definition.enabled;
	}

	public static boolean shouldOverrideFireAspect(Enchantment enchantment) {
		if (enchantment == null || !EnchantConfigManager.areCustomEnchantmentsEnabled()) return false;
		if (!isFireAspect(enchantment)) return false;
		EnchantmentDefinition definition = activeDefinitions().get(FIRE_ASPECT_ID);
		return definition != null && definition.enabled;
	}

	static boolean shouldOverrideImpaling(Enchantment enchantment) {
		if (enchantment == null || !EnchantConfigManager.areCustomEnchantmentsEnabled()) return false;
		if (!IMPALING_ID.equals(resolveEnchantmentId(enchantment))) return false;
		EnchantmentDefinition definition = activeDefinitions().get(IMPALING_ID);
		return definition != null && definition.enabled;
	}

	static boolean shouldOverrideInfinity(Enchantment enchantment) {
		if (enchantment == null || !EnchantConfigManager.areCustomEnchantmentsEnabled()) return false;
		if (!INFINITY_ID.equals(resolveEnchantmentId(enchantment))) return false;
		EnchantmentDefinition definition = activeDefinitions().get(INFINITY_ID);
		return definition != null && definition.enabled;
	}

	static boolean shouldOverrideKnockback(Enchantment enchantment) {
		if (enchantment == null || !EnchantConfigManager.areCustomEnchantmentsEnabled()) return false;
		if (!KNOCKBACK_ID.equals(resolveEnchantmentId(enchantment))) return false;
		EnchantmentDefinition definition = activeDefinitions().get(KNOCKBACK_ID);
		return definition != null && definition.enabled;
	}

	public static boolean isFireAspect(Enchantment enchantment) {
		return enchantment != null && FIRE_ASPECT_ID.equals(resolveEnchantmentId(enchantment));
	}

	/** Returns configured Breach penetration, or a negative value when vanilla behavior should remain active. */
	public static double getConfiguredBreachArmorPenetration(Enchantment enchantment, ItemStack stack, int level) {
		if (enchantment == null || stack == null || stack.isEmpty()
			|| !EnchantConfigManager.areCustomEnchantmentsEnabled()) return -1.0D;

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
		String enchantmentId = MadokuJSONManager.normalizeRegistryIdentifierForLookup(
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
			readBoolean(root, FIELD_ENABLED, true)
		);
	}

	private static void reload() {
		try {
			Map<String, JsonObject> staticDefaults = new LinkedHashMap<>();
			staticDefaults.put(AQUA_AFFINITY_FILE_KEY, buildAquaAffinityDefaults());
			staticDefaults.put(BANE_OF_ARTHROPODS_FILE_KEY, buildBaneOfArthropodsDefaults());
			staticDefaults.put(BLAST_PROTECTION_FILE_KEY, buildBlastProtectionDefaults());
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
			staticDefaults.put(INFINITY_FILE_KEY, buildInfinityDefaults());
			staticDefaults.put(KNOCKBACK_FILE_KEY, buildKnockbackDefaults());
			Map<String, JsonObject> files = JSONFormatManager.ensureManagedFolder(
				EnchantConfigManager.enchantmentsDirectory(),
				staticDefaults,
				BooksConfigManager::buildGenericDefaults,
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
		return JSONFormatManager.object()
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
		return JSONFormatManager.object()
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

	private static JsonObject buildBlastProtectionDefaults() {
		return JSONFormatManager.object()
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

	private static JsonObject buildBreachDefaults() {
		return JSONFormatManager.object()
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
		return JSONFormatManager.object()
			.put(FIELD_ENCHANTMENT_ID, "minecraft:channeling")
			.put(FIELD_MAXIMUM_LEVEL, 1)
			.array(FIELD_COMPATIBLE_ITEMS, values -> values.add("trident"))
			.put(FIELD_CONFLICTING_ENCHANTMENT, true)
			.put(FIELD_WEIGHT, 1)
			.build();
	}

	private static JsonObject buildCurseOfBindingDefaults() {
		return JSONFormatManager.object()
			.put(FIELD_ENCHANTMENT_ID, "minecraft:curse-of-binding")
			.put(FIELD_MAXIMUM_LEVEL, 1)
			.array(FIELD_COMPATIBLE_ITEMS, values -> values.add("universal"))
			.put(FIELD_CONFLICTING_ENCHANTMENT, false)
			.put(FIELD_WEIGHT, 1)
			.build();
	}

	private static JsonObject buildCurseOfVanishingDefaults() {
		return JSONFormatManager.object()
			.put(FIELD_ENCHANTMENT_ID, "minecraft:curse-of-vanishing")
			.put(FIELD_MAXIMUM_LEVEL, 1)
			.array(FIELD_COMPATIBLE_ITEMS, values -> values.add("universal"))
			.put(FIELD_CONFLICTING_ENCHANTMENT, false)
			.put(FIELD_WEIGHT, 1)
			.build();
	}

	private static JsonObject buildDensityDefaults() {
		return JSONFormatManager.object()
			.put(FIELD_ENCHANTMENT_ID, "minecraft:density")
			.put(FIELD_MAXIMUM_LEVEL, 5)
			.array(FIELD_COMPATIBLE_ITEMS, values -> values.add("mace"))
			.put(FIELD_CONFLICTING_ENCHANTMENT, false)
			.put(FIELD_WEIGHT, 1)
			.build();
	}

	private static JsonObject buildDepthStriderDefaults() {
		return JSONFormatManager.object()
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
		return JSONFormatManager.object()
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
		return JSONFormatManager.object()
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
		return JSONFormatManager.object()
			.put(FIELD_ENCHANTMENT_ID, "minecraft:frost-walker")
			.put(FIELD_MAXIMUM_LEVEL, 3)
			.array(FIELD_COMPATIBLE_ITEMS, values -> values.add("boots"))
			.put(FIELD_CONFLICTING_ENCHANTMENT, true)
			.put(FIELD_WEIGHT, 1)
			.build();
	}

	private static JsonObject buildFortuneDefaults() {
		return JSONFormatManager.object()
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
		return JSONFormatManager.object()
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
		return JSONFormatManager.object()
			.put(FIELD_ENCHANTMENT_ID, LOYALTY_ID)
			.put(FIELD_MAXIMUM_LEVEL, 3)
			.array(FIELD_COMPATIBLE_ITEMS, values -> values.add("trident"))
			.put(FIELD_CONFLICTING_ENCHANTMENT, true)
			.put(FIELD_WEIGHT, 1)
			.build();
	}

	private static JsonObject buildFireAspectDefaults() {
		return JSONFormatManager.object()
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
		return JSONFormatManager.object()
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
		return JSONFormatManager.object()
			.put(FIELD_ENCHANTMENT_ID, "minecraft:flame")
			.put(FIELD_MAXIMUM_LEVEL, 5)
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
		return JSONFormatManager.object()
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

	private static JsonObject buildInfinityDefaults() {
		return JSONFormatManager.object()
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
		return JSONFormatManager.object()
			.put(FIELD_ENCHANTMENT_ID, KNOCKBACK_ID)
			.put(FIELD_MAXIMUM_LEVEL, 3)
			.array(FIELD_COMPATIBLE_ITEMS, values -> values
				.add("sword")
				.add("spear")
				.add("axe"))
			.put(FIELD_CONFLICTING_ENCHANTMENT, false)
			.put(FIELD_WEIGHT, 1)
			.object(FIELD_KNOCKBACK, knockback -> knockback
				.put(FIELD_BASE_KNOCKBACK, 3)
				.put(FIELD_LEVEL_ADJUSTMENT, 2))
			.build();
	}

	private static JsonObject buildGenericDefaults(String fileKey) {
		return JSONFormatManager.object()
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
		String enchantmentId = MadokuJSONManager.normalizeRegistryIdentifierForLookup(rawId);
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
			root.addProperty(FIELD_ENABLED, enabled);
			return root;
		}
	}
}
