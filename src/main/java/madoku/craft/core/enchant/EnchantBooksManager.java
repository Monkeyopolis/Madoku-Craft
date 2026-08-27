package madoku.craft.core.enchant;

import com.google.gson.JsonArray;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import madoku.craft.core.json.JSONFormatManager;
import madoku.craft.core.json.MadokuJSONManager;
import net.minecraft.core.Holder;
import net.minecraft.core.Registry;
import net.minecraft.core.component.DataComponents;
import net.minecraft.core.registries.Registries;
import net.minecraft.tags.DamageTypeTags;
import net.minecraft.tags.ItemTags;
import net.minecraft.server.MinecraftServer;
import net.minecraft.util.RandomSource;
import net.minecraft.world.damagesource.DamageSource;
import net.minecraft.world.effect.MobEffectInstance;
import net.minecraft.world.effect.MobEffects;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.EquipmentSlot;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.entity.projectile.arrow.AbstractArrow;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.item.enchantment.Enchantment;
import net.minecraft.world.item.enchantment.EnchantmentEffectComponents;
import net.minecraft.world.item.enchantment.EnchantmentHelper;
import net.minecraft.world.item.enchantment.ItemEnchantments;
import net.minecraft.world.item.enchantment.effects.EnchantmentAttributeEffect;
import net.minecraft.world.entity.ai.attributes.AttributeModifier;
import org.apache.commons.lang3.mutable.MutableFloat;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.IOException;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;

/** Runtime group that owns configured enchantments and enchantment-book rules. */
public final class EnchantBooksManager {
	private static final Logger LOGGER = LoggerFactory.getLogger(EnchantBooksManager.class);
	private static final String AQUA_AFFINITY_ID = "minecraft:aqua_affinity";
	private static final String BANE_OF_ARTHROPODS_ID = "minecraft:bane_of_arthropods";
	private static final String BLAST_PROTECTION_ID = "minecraft:blast_protection";
	private static final String FIELD_ENCHANTMENT_ID = "enchantment-id";
	private static final String FIELD_MAXIMUM_LEVEL = "maximum-level";
	private static final String FIELD_COMPATIBLE_ITEMS = "compatible-items";
	private static final String FIELD_CONFLICTING_ENCHANTMENT = "conflicting-enchantment";
	private static final String FIELD_WEIGHT = "weight";
	private static final String FIELD_ENCHANTMENT_VALUE = "enchantment-value";
	private static final String FIELD_AQUA_AFFINITY = "aqua-affinity";
	private static final String FIELD_BANE_OF_ARTHROPODS = "bane-of-arthropods";
	private static final String FIELD_EFFECT = "effect";
	private static final String FIELD_BASE_EFFECT = "base-effect";
	private static final String FIELD_DURATION = "duration";
	private static final String FIELD_BASE_DURATION = "base-duration";
	private static final String FIELD_ENCHANTMENT_DURATION = "enchantment-duration";
	private static final String FIELD_BASE_ADJUSTMENT = "base-adjustment";
	private static final String FIELD_BASE_SUBMERGED_MINING_SPEED = "base-submerged-mining-speed";
	private static final String FIELD_LEVEL_ADJUSTMENT = "level-adjustment";
	private static final String FIELD_ENABLED = "enabled";
	private static final String AQUA_AFFINITY_FILE_KEY = "aqua-affinity";
	private static final String BANE_OF_ARTHROPODS_FILE_KEY = "bane-of-arthropods";
	private static final String BLAST_PROTECTION_FILE_KEY = "blast-protection";
	private static final Map<String, EnchantmentDefinition> EMPTY_DEFINITIONS = Map.of();
	private static volatile Map<String, EnchantmentDefinition> definitions = EMPTY_DEFINITIONS;

	private EnchantBooksManager() {
	}

	public static void initialize() {
		reload();
	}

	public static void reset() {
		definitions = EMPTY_DEFINITIONS;
	}

	public static void onServerStarted(MinecraftServer server) {
		reload();
	}

	/** Creates a new book using only enabled, configured enchantment definitions. */
	static ItemStack createEnchantedBook(Player player, int enchantmentCount) {
		if (player == null || enchantmentCount <= 0) return ItemStack.EMPTY;

		Registry<Enchantment> registry = player.level().registryAccess().lookupOrThrow(Registries.ENCHANTMENT);
		List<Holder<Enchantment>> available = selectEnchantments(player.getRandom(), registry, enchantmentCount);
		if (available.isEmpty()) return ItemStack.EMPTY;

		ItemStack result = new ItemStack(Items.ENCHANTED_BOOK);
		ItemEnchantments.Mutable enchantments = new ItemEnchantments.Mutable(ItemEnchantments.EMPTY);
		for (Holder<Enchantment> enchantment : available) enchantments.set(enchantment, 1);
		EnchantmentHelper.setEnchantments(result, enchantments.toImmutable());
		return result;
	}

	static boolean canUpgradeByLevels(ItemStack input, int levels) {
		if (input == null || input.isEmpty() || levels <= 0) return false;
		ItemEnchantments enchantments = EnchantmentHelper.getEnchantmentsForCrafting(input);
		for (Holder<Enchantment> enchantment : enchantments.keySet()) {
			EnchantmentDefinition definition = definitionForHolder(enchantment);
			int current = enchantments.getLevel(enchantment);
			if (definition != null && definition.enabled && current < definition.maximumLevel
				&& current + levels <= definition.maximumLevel) {
				return true;
			}
		}
		return false;
	}

	/** Upgrades configured enchantments by the allocated number of levels. */
	static ItemStack upgradeEnchantedBook(ItemStack input, int levels) {
		if (input == null || input.isEmpty() || levels <= 0) return ItemStack.EMPTY;

		ItemEnchantments existing = EnchantmentHelper.getEnchantmentsForCrafting(input);
		ItemEnchantments.Mutable upgraded = new ItemEnchantments.Mutable(existing);
		boolean changed = false;
		for (Holder<Enchantment> enchantment : existing.keySet()) {
			EnchantmentDefinition definition = definitionForHolder(enchantment);
			if (definition == null || !definition.enabled) continue;

			int current = existing.getLevel(enchantment);
			int target = Math.min(definition.maximumLevel, current + levels);
			if (target > current) {
				upgraded.set(enchantment, target);
				changed = true;
			}
		}
		if (!changed) return ItemStack.EMPTY;

		ItemStack result = new ItemStack(Items.ENCHANTED_BOOK);
		EnchantmentHelper.setEnchantments(result, upgraded.toImmutable());
		return result;
	}

	/** Replaces vanilla Aqua Affinity's attribute amount with the configured percentage. */
	public static AttributeModifier applyConfiguredAquaAffinityModifier(int level, AttributeModifier vanillaModifier) {
		if (vanillaModifier == null || !EnchantConfigManager.isEnabled()) return vanillaModifier;

		EnchantmentDefinition definition = definitions.get(AQUA_AFFINITY_ID);
		if (definition == null || !definition.enabled) return vanillaModifier;

		double adjustment = resolveAdjustment(definition.baseAdjustment, definition.levelAdjustment, Math.max(1, level));
		return new AttributeModifier(
			vanillaModifier.id(),
			Math.max(0.0D, adjustment / 100.0D),
			vanillaModifier.operation()
		);
	}

	/** Exposes a configured definition maximum to vanilla systems such as anvil combination. */
	public static int getConfiguredMaximumLevel(Enchantment enchantment, int vanillaMaximumLevel) {
		if (enchantment == null || !EnchantConfigManager.isEnabled()) return vanillaMaximumLevel;
		if (!isAquaAffinity(enchantment)) return vanillaMaximumLevel;

		EnchantmentDefinition definition = definitions.get(AQUA_AFFINITY_ID);
		if (definition == null || !definition.enabled) return vanillaMaximumLevel;
		return Math.max(1, definition.maximumLevel);
	}

	private static boolean isAquaAffinity(Enchantment enchantment) {
		for (EnchantmentAttributeEffect effect : enchantment.getEffects(EnchantmentEffectComponents.ATTRIBUTES)) {
			if ("minecraft:enchantment.aqua_affinity".equals(effect.id().toString())) return true;
		}
		return false;
	}

	/** Applies configured Bane of Arthropods slowness after a successful weapon hit. */
	public static void applyOnHit(LivingEntity target, DamageSource source) {
		if (target == null || source == null || !target.isAlive() || !EnchantConfigManager.isEnabled()) return;

		Entity attackerEntity = source.getEntity();
		if (!(attackerEntity instanceof LivingEntity attacker)) return;

		EnchantmentDefinition definition = definitions.get(BANE_OF_ARTHROPODS_ID);
		if (definition == null || !definition.enabled) return;

		Entity directEntity = source.getDirectEntity();
		ItemStack weapon;
		if (directEntity == attacker) {
			weapon = attacker.getMainHandItem();
		} else if (directEntity instanceof AbstractArrow projectile) {
			weapon = projectile.getWeaponItem();
		} else {
			return;
		}
		int level = resolveLevel(weapon, BANE_OF_ARTHROPODS_ID);
		if (level <= 0 || !isCompatible(definition, weapon)) return;

		int amplifier = Math.max(0, (int) Math.round(resolveAdjustment(
			definition.baseAdjustment,
			definition.levelAdjustment,
			level
		)) - 1);
		int durationTicks = Math.max(1, (int) Math.round(resolveAdjustment(
			definition.baseDuration,
			definition.levelDuration,
			level
		) * 20.0D));
		target.addEffect(new MobEffectInstance(MobEffects.SLOWNESS, durationTicks, amplifier), attacker);
		TemporaryEnchantDebug.baneOnHit(target, attacker, weapon, level, amplifier, durationTicks);
	}

	private static double resolveAdjustment(double base, double perLevel, int level) {
		return Math.max(0.0D, base + Math.max(0, level - 1) * perLevel);
	}

	/** Resolves vanilla protection points while replacing configured Blast Protection's contribution. */
	public static float resolveDamageProtection(
		net.minecraft.server.level.ServerLevel serverLevel,
		LivingEntity entity,
		DamageSource source
	) {
		if (serverLevel == null || entity == null || source == null) return 0.0F;

		MutableFloat protection = new MutableFloat(0.0F);
		EnchantmentHelper.runIterationOnEquipment(entity, (holder, level, enchantedItem) -> {
			boolean blastProtection = holder.unwrapKey()
				.map(key -> key.identifier().toString().equals(BLAST_PROTECTION_ID))
				.orElse(false);
			EnchantmentDefinition definition = definitions.get(BLAST_PROTECTION_ID);
			if (blastProtection && source.is(DamageTypeTags.IS_EXPLOSION)
				&& definition != null && definition.enabled) {
				if (isCompatible(definition, enchantedItem.itemStack())) {
					// CombatRules uses 25 protection points per 100% reduction.
					protection.add((float) (resolveAdjustment(
						definition.baseAdjustment,
						definition.levelAdjustment,
						level
					) / 4.0D));
				}
				return;
			}
			holder.value().modifyDamageProtection(
				serverLevel,
				level,
				enchantedItem.itemStack(),
				entity,
				source,
				protection
			);
		});
		return protection.floatValue();
	}

	/** Adds configured Blast Protection resistance to vanilla explosion knockback resistance. */
	public static double resolveExplosionKnockbackResistance(LivingEntity entity, double currentResistance) {
		if (entity == null || !EnchantConfigManager.isEnabled()) return currentResistance;
		EnchantmentDefinition definition = definitions.get(BLAST_PROTECTION_ID);
		if (definition == null || !definition.enabled) return currentResistance;

		double adjustment = 0.0D;
		for (EquipmentSlot slot : new EquipmentSlot[] {
			EquipmentSlot.HEAD,
			EquipmentSlot.CHEST,
			EquipmentSlot.LEGS,
			EquipmentSlot.FEET
		}) {
			ItemStack armor = entity.getItemBySlot(slot);
			if (!isCompatible(definition, armor)) continue;
			int level = resolveLevel(armor, BLAST_PROTECTION_ID);
			if (level > 0) {
				adjustment += resolveAdjustment(definition.baseAdjustment, definition.levelAdjustment, level) / 100.0D;
			}
		}
		return Math.min(1.0D, Math.max(0.0D, currentResistance + adjustment));
	}

	private static List<Holder<Enchantment>> selectEnchantments(
		RandomSource random,
		Registry<Enchantment> registry,
		int requestedCount
	) {
		if (registry == null || requestedCount <= 0) return List.of();
		RandomSource source = random == null ? RandomSource.create() : random;
		List<ConfiguredHolder> candidates = new ArrayList<>();
		for (Map.Entry<String, EnchantmentDefinition> entry : definitions.entrySet()) {
			EnchantmentDefinition definition = entry.getValue();
			if (definition == null || !definition.enabled || definition.weight <= 0) continue;

			Holder<Enchantment> holder = resolveHolder(registry, entry.getKey());
			if (holder != null) candidates.add(new ConfiguredHolder(holder, definition));
		}

		List<Holder<Enchantment>> selected = new ArrayList<>();
		int maximum = Math.min(3, requestedCount);
		while (selected.size() < maximum && !candidates.isEmpty()) {
			int totalWeight = 0;
			for (ConfiguredHolder candidate : candidates) totalWeight += Math.max(1, candidate.definition.weight);
			if (totalWeight <= 0) break;

			int pick = source.nextInt(totalWeight);
			int selectedIndex = 0;
			for (int index = 0; index < candidates.size(); index++) {
				pick -= Math.max(1, candidates.get(index).definition.weight);
				if (pick < 0) {
					selectedIndex = index;
					break;
				}
			}

			ConfiguredHolder candidate = candidates.remove(selectedIndex);
			if (hasConfiguredConflict(selected, candidate)) {
				continue;
			}
			selected.add(candidate.holder);
		}
		return selected;
	}

	private static boolean hasConfiguredConflict(List<Holder<Enchantment>> selected, ConfiguredHolder candidate) {
		if (!candidate.definition.conflictingEnchantment || selected.isEmpty()) return false;
		for (Holder<Enchantment> existing : selected) {
			EnchantmentDefinition existingDefinition = definitionForHolder(existing);
			if (existingDefinition == null || !existingDefinition.conflictingEnchantment) return false;
		}
		return !EnchantmentHelper.isEnchantmentCompatible(selected, candidate.holder);
	}

	private static Holder<Enchantment> resolveHolder(Registry<Enchantment> registry, String enchantmentId) {
		for (Map.Entry<net.minecraft.resources.ResourceKey<Enchantment>, Enchantment> entry : registry.entrySet()) {
			if (entry.getKey().identifier().toString().equals(enchantmentId)) {
				return registry.wrapAsHolder(entry.getValue());
			}
		}
		return null;
	}

	private static EnchantmentDefinition definitionForHolder(Holder<Enchantment> holder) {
		if (holder == null) return null;
		return holder.unwrapKey()
			.map(key -> definitions.get(key.identifier().toString()))
			.orElse(null);
	}

	private static int resolveLevel(ItemStack stack, String enchantmentId) {
		if (stack == null || stack.isEmpty()) return 0;
		ItemEnchantments enchantments = EnchantmentHelper.getEnchantmentsForCrafting(stack);
		for (Holder<Enchantment> holder : enchantments.keySet()) {
			if (holder.unwrapKey().map(key -> key.identifier().toString().equals(enchantmentId)).orElse(false)) {
				return enchantments.getLevel(holder);
			}
		}
		return 0;
	}

	private static boolean isCompatible(EnchantmentDefinition definition, ItemStack stack) {
		if (definition == null || stack == null || stack.isEmpty()) return false;
		var equippable = stack.get(DataComponents.EQUIPPABLE);
		EquipmentSlot equipmentSlot = equippable == null ? null : equippable.slot();
		for (String compatibleItem : definition.compatibleItems) {
			switch (compatibleItem) {
				case "sword" -> {
					if (stack.is(ItemTags.SWORDS)) return true;
				}
				case "spear" -> {
					if (stack.is(ItemTags.SPEARS)) return true;
				}
				case "axe" -> {
					if (stack.is(ItemTags.AXES)) return true;
				}
				case "mace" -> {
					if (stack.is(ItemTags.MACE_ENCHANTABLE)) return true;
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

	private static void reload() {
		try {
			Map<String, JsonObject> staticDefaults = new LinkedHashMap<>();
			staticDefaults.put(AQUA_AFFINITY_FILE_KEY, buildAquaAffinityDefaults());
			staticDefaults.put(BANE_OF_ARTHROPODS_FILE_KEY, buildBaneOfArthropodsDefaults());
			staticDefaults.put(BLAST_PROTECTION_FILE_KEY, buildBlastProtectionDefaults());
			Map<String, JsonObject> files = JSONFormatManager.ensureManagedFolder(
				EnchantConfigManager.enchantmentsDirectory(),
				staticDefaults,
				EnchantBooksManager::buildGenericDefaults,
				(fileKey, root) -> true,
				null
			);

			Map<String, EnchantmentDefinition> loaded = new LinkedHashMap<>();
			for (JsonObject root : files.values()) {
				EnchantmentDefinition definition = parseDefinition(root);
				if (definition != null) loaded.put(definition.enchantmentId, definition);
			}
			definitions = loaded.isEmpty() ? EMPTY_DEFINITIONS : Map.copyOf(loaded);
			EnchantmentDefinition baneOfArthropods = loaded.get(BANE_OF_ARTHROPODS_ID);
			TemporaryEnchantDebug.baneDefinition(
				baneOfArthropods != null,
				baneOfArthropods != null && baneOfArthropods.enabled,
				baneOfArthropods == null ? 0.0D : baneOfArthropods.baseAdjustment,
				baneOfArthropods == null ? 0.0D : baneOfArthropods.levelAdjustment,
				baneOfArthropods == null ? 0.0D : baneOfArthropods.baseDuration,
				baneOfArthropods == null ? 0.0D : baneOfArthropods.levelDuration
			);
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
			.put(FIELD_ENABLED, false)
			.put(FIELD_ENCHANTMENT_ID, "minecraft:blast-protection")
			.put(FIELD_MAXIMUM_LEVEL, 5)
			.array(FIELD_COMPATIBLE_ITEMS, values -> values
				.add("helmet")
				.add("chestplate")
				.add("leggings")
				.add("boots"))
			.put(FIELD_CONFLICTING_ENCHANTMENT, true)
			.put(FIELD_WEIGHT, 1)
			.object(FIELD_ENCHANTMENT_VALUE, value -> value
				.put(FIELD_BASE_ADJUSTMENT, 20)
				.put(FIELD_LEVEL_ADJUSTMENT, 5))
			.build();
	}

	private static JsonObject buildGenericDefaults(String fileKey) {
		return JSONFormatManager.object()
			.put(FIELD_ENCHANTMENT_ID, "")
			.put(FIELD_MAXIMUM_LEVEL, 1)
			.array(FIELD_COMPATIBLE_ITEMS, ignored -> { })
			.put(FIELD_CONFLICTING_ENCHANTMENT, true)
			.put(FIELD_WEIGHT, 1)
			.object(FIELD_ENCHANTMENT_VALUE, value -> value
				.put(FIELD_BASE_ADJUSTMENT, 0)
				.put(FIELD_LEVEL_ADJUSTMENT, 0))
			.object(FIELD_ENCHANTMENT_DURATION, duration -> duration
				.put(FIELD_BASE_ADJUSTMENT, 0)
				.put(FIELD_LEVEL_ADJUSTMENT, 0))
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
		if (AQUA_AFFINITY_ID.equals(enchantmentId)) {
			JsonObject aquaAffinity = object(root, FIELD_AQUA_AFFINITY);
			baseAdjustment = Math.max(0.0D, readDouble(aquaAffinity, FIELD_BASE_SUBMERGED_MINING_SPEED, 0.0D));
			levelAdjustment = Math.max(0.0D, readDouble(aquaAffinity, FIELD_LEVEL_ADJUSTMENT, 0.0D));
			baseDuration = 0.0D;
			levelDuration = 0.0D;
		} else if (BANE_OF_ARTHROPODS_ID.equals(enchantmentId)) {
			JsonObject baneOfArthropods = object(root, FIELD_BANE_OF_ARTHROPODS);
			JsonObject effect = object(baneOfArthropods, FIELD_EFFECT);
			baseAdjustment = Math.max(0.0D, readDouble(effect, FIELD_BASE_EFFECT, 0.0D));
			levelAdjustment = Math.max(0.0D, readDouble(effect, FIELD_LEVEL_ADJUSTMENT, 0.0D));
			JsonObject duration = object(baneOfArthropods, FIELD_DURATION);
			baseDuration = Math.max(0.0D, readDouble(duration, FIELD_BASE_DURATION, 0.0D));
			levelDuration = Math.max(0.0D, readDouble(duration, FIELD_LEVEL_ADJUSTMENT, 0.0D));
		} else {
			JsonObject enchantmentValue = object(root, FIELD_ENCHANTMENT_VALUE);
			baseAdjustment = Math.max(0.0D, readDouble(enchantmentValue, FIELD_BASE_ADJUSTMENT, 0.0D));
			levelAdjustment = Math.max(0.0D, readDouble(enchantmentValue, FIELD_LEVEL_ADJUSTMENT, 0.0D));
			JsonObject enchantmentDuration = object(root, FIELD_ENCHANTMENT_DURATION);
			baseDuration = Math.max(0.0D, readDouble(enchantmentDuration, FIELD_BASE_ADJUSTMENT, 0.0D));
			levelDuration = Math.max(0.0D, readDouble(enchantmentDuration, FIELD_LEVEL_ADJUSTMENT, 0.0D));
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

	private record ConfiguredHolder(Holder<Enchantment> holder, EnchantmentDefinition definition) { }

	private record EnchantmentDefinition(
		String enchantmentId,
		int maximumLevel,
		List<String> compatibleItems,
		boolean conflictingEnchantment,
		int weight,
		double baseAdjustment,
		double levelAdjustment,
		double baseDuration,
		double levelDuration,
		boolean enabled
	) { }
}
