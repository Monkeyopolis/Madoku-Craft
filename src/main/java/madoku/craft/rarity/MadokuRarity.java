package madoku.craft.rarity;

import com.google.gson.JsonObject;
import madoku.craft.config.StaticJsonSystem;
import madoku.craft.debug.MadokuDebug;
import madoku.craft.item.system.MadokuItem;
import net.minecraft.ChatFormatting;
import net.minecraft.core.Holder;
import net.minecraft.core.component.DataComponentType;
import net.minecraft.core.component.DataComponents;
import net.minecraft.network.chat.Component;
import net.minecraft.network.chat.MutableComponent;
import net.minecraft.network.chat.TextColor;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.util.RandomSource;
import net.minecraft.world.entity.EquipmentSlotGroup;
import net.minecraft.world.entity.ai.attributes.Attribute;
import net.minecraft.world.entity.ai.attributes.AttributeModifier;
import net.minecraft.world.entity.ai.attributes.Attributes;
import net.minecraft.world.item.ArmorItem;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.component.ItemAttributeModifiers;
import net.minecraft.world.item.component.ItemLore;
import net.minecraft.world.item.component.Tool;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.IOException;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.Optional;

public final class MadokuRarity {
	private static final Logger LOGGER = LoggerFactory.getLogger(MadokuRarity.class);

	private static final String RARITY_CONFIG_FOLDER_NAME = "madoku-craft-rarity";
	private static final String RARITY_CONFIG_FILE_NAME = "madoku-rarity";
	private static final String DURABILITY_PREFIX = "Durability:";

	private static final double ATTACK_DAMAGE_SCALING_FACTOR = 0.50D;
	private static final double ATTACK_SPEED_SCALING_FACTOR = 0.25D;
	private static final double MINING_SPEED_SCALING_FACTOR = 0.25D;
	private static final double ARMOR_SCALING_FACTOR = 0.50D;
	private static final double ARMOR_TOUGHNESS_SCALING_FACTOR = 0.50D;

	private static final MadokuRarityConfig config = new MadokuRarityConfig();

	private MadokuRarity() {
	}

	public static void initialize() {
		JsonObject defaults = MadokuRarityConfig.buildDefaults();

		try {
			Path directory = StaticJsonSystem.getOrCreateGlobalSystemDirectory(RARITY_CONFIG_FOLDER_NAME);
			Path configFile = resolveJsonFile(directory, RARITY_CONFIG_FILE_NAME);
			JsonObject normalized = StaticJsonSystem.ensureManagedFile(configFile, defaults);
			boolean changed = config.update(normalized);
			if (changed) {
				StaticJsonSystem.writeManagedFile(configFile, normalized, defaults);
			}
			emitConfigLoaded();
		} catch (IOException | RuntimeException exception) {
			config.enabled = false;
			LOGGER.error("Failed to load MadokuRarity config; disabling rarity.", exception);
		}
	}

	public static boolean isEnabled() {
		return config.enabled && MadokuItem.isEnabled();
	}

	public static List<ItemStack> applyCraftedRarity(ServerPlayer player, ItemStack stack) {
		if (!isEnabled() || player == null || stack == null || stack.isEmpty()) {
			return List.of();
		}
		if (!MadokuItem.isRarityCategoryItem(stack)) {
			return List.of();
		}

		// Avoid double-applying when multiple crafting hooks fire for the same output stack.
		if (detectAppliedRarity(stack) != null) {
			return List.of();
		}

		int craftedAmount = Math.max(1, stack.getCount());
		if (craftedAmount == 1) {
			rollAndApplySingle(player, stack);
			return List.of();
		}

		ItemStack base = stack.copy();
		base.setCount(1);

		stack.setCount(1);
		rollAndApplySingle(player, stack);

		List<ItemStack> extras = new ArrayList<>(craftedAmount - 1);
		for (int index = 1; index < craftedAmount; index++) {
			ItemStack extra = base.copy();
			rollAndApplySingle(player, extra);
			extras.add(extra);
		}
		return extras;
	}

	public static void applyGeneratedRarity(ItemStack stack, RandomSource randomSource) {
		if (!isEnabled() || stack == null || stack.isEmpty()) {
			return;
		}
		if (!MadokuItem.isRarityCategoryItem(stack)) {
			return;
		}
		if (detectAppliedRarity(stack) != null) {
			return;
		}

		RandomSource random = randomSource != null ? randomSource : RandomSource.create();
		rollAndApplySingle(random, stack);
	}

	public static void deliverCraftExtras(ServerPlayer player, List<ItemStack> extras) {
		if (player == null || extras == null || extras.isEmpty()) {
			return;
		}

		for (ItemStack extra : extras) {
			if (extra == null || extra.isEmpty()) {
				continue;
			}
			if (!player.getInventory().add(extra)) {
				player.drop(extra, false);
			}
		}
	}

	public static ItemStack createSmithingUpgradeResult(ItemStack baseStack, ItemStack vanillaResult) {
		if (!isEnabled() || baseStack == null || vanillaResult == null || vanillaResult.isEmpty()) {
			return vanillaResult;
		}
		if (baseStack.isEmpty()) {
			return vanillaResult;
		}
		if (!MadokuItem.isRarityCategoryItem(baseStack) || !MadokuItem.isRarityCategoryItem(vanillaResult)) {
			return vanillaResult;
		}

		MadokuRarityTier sourceRarity = detectAppliedRarity(baseStack);
		if (sourceRarity == null) {
			return vanillaResult;
		}

		ItemStack rebuiltResult = vanillaResult.copy();
		applyRarityToStack(rebuiltResult, sourceRarity);
		return rebuiltResult;
	}

	public static void updateDurabilityLore(ItemStack stack) {
		if (stack == null || stack.isEmpty() || !stack.isDamageableItem()) {
			return;
		}
		if (!MadokuItem.isRarityCategoryItem(stack)) {
			return;
		}

		int maxDurability = stack.getMaxDamage();
		if (maxDurability <= 0) {
			return;
		}

		int currentDurability = Math.max(0, maxDurability - stack.getDamageValue());
		Component durabilityLine = Component.literal("Durability: " + currentDurability + "/" + maxDurability)
			.withStyle(ChatFormatting.GRAY);

		ItemLore currentLore = stack.get(DataComponents.LORE);
		List<Component> updatedLines = new ArrayList<>();
		if (currentLore != null) {
			for (Component line : currentLore.lines()) {
				if (!line.getString().startsWith(DURABILITY_PREFIX)) {
					updatedLines.add(line);
				}
			}
		}

		updatedLines.add(durabilityLine);
		stack.set(DataComponents.LORE, new ItemLore(updatedLines));
	}

	public static int resolveConfiguredMaxDamage(ItemStack stack, int configuredBaseMaxDamage) {
		if (configuredBaseMaxDamage <= 0) {
			return configuredBaseMaxDamage;
		}

		MadokuRarityTier rarity = detectAppliedRarity(stack);
		if (rarity == null || rarity == MadokuRarityTier.COMMON) {
			return configuredBaseMaxDamage;
		}

		double buffPercent = getRarityStatBuffPercent(rarity);
		if (buffPercent <= 0.0D) {
			return configuredBaseMaxDamage;
		}

		return roundToWhole(configuredBaseMaxDamage * multiplierFromBuffPercent(buffPercent));
	}

	private static void rollAndApplySingle(ServerPlayer player, ItemStack stack) {
		rollAndApplySingle(player.getRandom(), stack);
	}

	private static void rollAndApplySingle(RandomSource randomSource, ItemStack stack) {
		MadokuRarityTier rarity = rollRandomRarity(randomSource);
		applyRarityToStack(stack, rarity);
	}

	private static void applyRarityToStack(ItemStack stack, MadokuRarityTier rarity) {
		if (stack == null || stack.isEmpty() || rarity == null) {
			return;
		}

		MadokuItem.applyProfilesToFreshStack(stack);

		if (rarity != MadokuRarityTier.COMMON) {
			double buffPercent = getRarityStatBuffPercent(rarity);
			if (buffPercent > 0.0D) {
				applyStatMultiplier(stack, multiplierFromBuffPercent(buffPercent));
			}
		}

		applyNameColor(stack, rarity);
		updateDurabilityLore(stack);
	}

	private static MadokuRarityTier rollRandomRarity(RandomSource randomSource) {
		double mythicWeight = Math.max(0.0D, getRarityWeight(MadokuRarityTier.MYTHIC));
		double epicWeight = Math.max(0.0D, getRarityWeight(MadokuRarityTier.EPIC));
		double rareWeight = Math.max(0.0D, getRarityWeight(MadokuRarityTier.RARE));
		double commonWeight = Math.max(0.0D, getRarityWeight(MadokuRarityTier.COMMON));

		double totalWeight = mythicWeight + epicWeight + rareWeight + commonWeight;
		if (totalWeight <= 0.0D) {
			return MadokuRarityTier.COMMON;
		}

		double roll = randomSource.nextDouble() * totalWeight;
		if (roll < mythicWeight) {
			return MadokuRarityTier.MYTHIC;
		}
		roll -= mythicWeight;
		if (roll < epicWeight) {
			return MadokuRarityTier.EPIC;
		}
		roll -= epicWeight;
		if (roll < rareWeight) {
			return MadokuRarityTier.RARE;
		}
		return MadokuRarityTier.COMMON;
	}

	private static double getRarityWeight(MadokuRarityTier rarity) {
		return switch (rarity) {
			case COMMON -> config.commonChanceWeight;
			case RARE -> config.rareChanceWeight;
			case EPIC -> config.epicChanceWeight;
			case MYTHIC -> config.mythicChanceWeight;
		};
	}

	private static double getRarityStatBuffPercent(MadokuRarityTier rarity) {
		return switch (rarity) {
			case COMMON -> 0.0D;
			case RARE -> config.rareStatBuffPercent;
			case EPIC -> config.epicStatBuffPercent;
			case MYTHIC -> config.mythicStatBuffPercent;
		};
	}

	private static double multiplierFromBuffPercent(double buffPercent) {
		return 1.0D + (Math.max(0.0D, buffPercent) / 100.0D);
	}

	private static void applyStatMultiplier(ItemStack stack, double multiplier) {
		scaleMaxDurability(stack, multiplier);
		scaleMainHandAttackAttributes(
			stack,
			scaledEffectMultiplier(multiplier, ATTACK_DAMAGE_SCALING_FACTOR),
			scaledEffectMultiplier(multiplier, ATTACK_SPEED_SCALING_FACTOR)
		);
		if (!MadokuItem.isArmorCategoryItem(stack)) {
			scaleArmorAttributes(
				stack,
				scaledEffectMultiplier(multiplier, ARMOR_SCALING_FACTOR),
				scaledEffectMultiplier(multiplier, ARMOR_TOUGHNESS_SCALING_FACTOR)
			);
		}
		scaleMiningSpeed(stack, scaledEffectMultiplier(multiplier, MINING_SPEED_SCALING_FACTOR));
	}

	public static ItemAttributeModifiers getScaledArmorAttributeModifiers(ItemStack stack) {
		if (!isEnabled() || stack == null || stack.isEmpty() || !MadokuItem.isArmorCategoryItem(stack)) {
			return null;
		}

		MadokuRarityTier rarity = detectAppliedRarity(stack);
		if (rarity == null || rarity == MadokuRarityTier.COMMON) {
			return null;
		}

		double buffPercent = getRarityStatBuffPercent(rarity);
		if (buffPercent <= 0.0D) {
			return null;
		}

		ItemAttributeModifiers baseAttributes = resolveBaseArmorAttributeModifiers(stack);
		if (baseAttributes == null) {
			return null;
		}

		double multiplier = multiplierFromBuffPercent(buffPercent);
		return scaleArmorAttributeModifiers(
			baseAttributes,
			scaledEffectMultiplier(multiplier, ARMOR_SCALING_FACTOR),
			scaledEffectMultiplier(multiplier, ARMOR_TOUGHNESS_SCALING_FACTOR)
		);
	}

	private static double scaledEffectMultiplier(double multiplier, double factor) {
		return 1.0D + ((multiplier - 1.0D) * factor);
	}

	private static void scaleMaxDurability(ItemStack stack, double multiplier) {
		Integer maxDamage = getResolvedComponent(stack, DataComponents.MAX_DAMAGE);
		if (maxDamage == null || maxDamage <= 0) {
			return;
		}
		stack.set(DataComponents.MAX_DAMAGE, roundToWhole(maxDamage * multiplier));
	}

	private static void scaleMainHandAttackAttributes(ItemStack stack, double attackDamageMultiplier, double attackSpeedMultiplier) {
		ItemAttributeModifiers current = getResolvedComponent(stack, DataComponents.ATTRIBUTE_MODIFIERS);
		if (current == null) {
			return;
		}

			boolean changed = false;
			ItemAttributeModifiers.Builder builder = ItemAttributeModifiers.builder();
			for (ItemAttributeModifiers.Entry entry : current.modifiers()) {
				AttributeModifier modifier = entry.modifier();
				AttributeModifier updatedModifier = modifier;
				if (isScaledAttackDamage(entry, modifier)) {
					double currentValue = 1.0D + modifier.amount();
					double scaledValue = roundToNearestHalf(currentValue * attackDamageMultiplier);
					updatedModifier = new AttributeModifier(
						modifier.id(),
						scaledValue - 1.0D,
						modifier.operation()
					);
					changed = true;
				} else if (isScaledAttackSpeed(entry, modifier)) {
					double currentValue = 4.0D + modifier.amount();
					double scaledValue = roundToTenth(currentValue * attackSpeedMultiplier);
					updatedModifier = new AttributeModifier(
						modifier.id(),
						scaledValue - 4.0D,
						modifier.operation()
					);
					changed = true;
				}
				builder.add(entry.attribute(), updatedModifier, entry.slot());
			}

		if (changed) {
			stack.set(DataComponents.ATTRIBUTE_MODIFIERS, builder.build());
		}
	}

	private static void scaleArmorAttributes(ItemStack stack, double armorMultiplier, double toughnessMultiplier) {
		ItemAttributeModifiers current = getResolvedComponent(stack, DataComponents.ATTRIBUTE_MODIFIERS);
		if (current == null) {
			return;
		}

		ItemAttributeModifiers updated = scaleArmorAttributeModifiers(current, armorMultiplier, toughnessMultiplier);
		if (updated != null && !updated.equals(current)) {
			stack.set(DataComponents.ATTRIBUTE_MODIFIERS, updated);
		}
	}

	private static ItemAttributeModifiers scaleArmorAttributeModifiers(
		ItemAttributeModifiers current,
		double armorMultiplier,
		double toughnessMultiplier
	) {
		if (current == null) {
			return null;
		}

		boolean changed = false;
		ItemAttributeModifiers.Builder builder = ItemAttributeModifiers.builder();
		for (ItemAttributeModifiers.Entry entry : current.modifiers()) {
			AttributeModifier modifier = entry.modifier();
			AttributeModifier updatedModifier = modifier;
			if (isScaledArmor(entry, modifier)) {
				double scaledValue = roundToNearestQuarter(modifier.amount() * armorMultiplier);
				updatedModifier = new AttributeModifier(
					modifier.id(),
					scaledValue,
					modifier.operation()
				);
				changed = true;
			} else if (isScaledArmorToughness(entry, modifier)) {
				double scaledValue = roundToNearestQuarter(modifier.amount() * toughnessMultiplier);
				updatedModifier = new AttributeModifier(
					modifier.id(),
					scaledValue,
					modifier.operation()
				);
				changed = true;
			}
			builder.add(entry.attribute(), updatedModifier, entry.slot());
		}

		return changed ? builder.build() : current;
	}

	private static boolean isScaledAttackDamage(ItemAttributeModifiers.Entry entry, AttributeModifier modifier) {
		return isMainHandAddValue(entry, modifier)
			&& modifier.id().equals(Item.BASE_ATTACK_DAMAGE_ID)
			&& isAttribute(entry, Attributes.ATTACK_DAMAGE);
	}

	private static boolean isScaledAttackSpeed(ItemAttributeModifiers.Entry entry, AttributeModifier modifier) {
		return isMainHandAddValue(entry, modifier)
			&& modifier.id().equals(Item.BASE_ATTACK_SPEED_ID)
			&& isAttribute(entry, Attributes.ATTACK_SPEED);
	}

	private static boolean isScaledArmor(ItemAttributeModifiers.Entry entry, AttributeModifier modifier) {
		return isAddValueModifier(modifier)
			&& isAttribute(entry, Attributes.ARMOR);
	}

	private static boolean isScaledArmorToughness(ItemAttributeModifiers.Entry entry, AttributeModifier modifier) {
		return isAddValueModifier(modifier)
			&& isAttribute(entry, Attributes.ARMOR_TOUGHNESS);
	}

	private static boolean isMainHandAddValue(ItemAttributeModifiers.Entry entry, AttributeModifier modifier) {
		return entry.slot() == EquipmentSlotGroup.MAINHAND
			&& isAddValueModifier(modifier);
	}

	private static boolean isAddValueModifier(AttributeModifier modifier) {
		return modifier.operation() == AttributeModifier.Operation.ADD_VALUE;
	}

	private static boolean isAttribute(ItemAttributeModifiers.Entry entry, Holder<Attribute> attribute) {
		return entry.attribute().value() == attribute.value();
	}

	private static void scaleMiningSpeed(ItemStack stack, double multiplier) {
		Tool current = getResolvedComponent(stack, DataComponents.TOOL);
		if (current == null) {
			return;
		}

			boolean changed = false;
			List<Tool.Rule> updatedRules = new ArrayList<>(current.rules().size());
			for (Tool.Rule rule : current.rules()) {
				Optional<Float> speed = rule.speed();
				if (speed.isPresent()) {
					updatedRules.add(new Tool.Rule(
						rule.blocks(),
						Optional.of((float) roundToNearestHalf(speed.get() * multiplier)),
						rule.correctForDrops()
					));
					changed = true;
				} else {
					updatedRules.add(rule);
				}
			}

			float updatedDefaultSpeed = (float) roundToNearestHalf(current.defaultMiningSpeed() * multiplier);
			if (updatedDefaultSpeed != current.defaultMiningSpeed()) {
				changed = true;
			}

		if (changed) {
			stack.set(DataComponents.TOOL, new Tool(
				updatedRules,
				updatedDefaultSpeed,
				current.damagePerBlock()
			));
		}
	}

	private static void applyNameColor(ItemStack stack, MadokuRarityTier rarity) {
		MutableComponent coloredName = stack.getItem()
			.getName(stack)
			.copy()
			.withStyle(style -> style.withColor(rarity.color()).withItalic(false));
		stack.set(DataComponents.CUSTOM_NAME, coloredName);
	}

	public static MadokuRarityTier detectAppliedRarity(ItemStack stack) {
		if (stack == null || stack.isEmpty()) {
			return null;
		}

		Component customName = stack.get(DataComponents.CUSTOM_NAME);
		if (customName == null) {
			return null;
		}

		TextColor color = customName.getStyle().getColor();
		if (color == null) {
			return null;
		}

		int rgb = color.getValue();
		for (MadokuRarityTier rarity : MadokuRarityTier.values()) {
			Integer rarityColor = rarity.color().getColor();
			if (rarityColor != null && rarityColor == rgb) {
				return rarity;
			}
		}
		return null;
	}

	private static int roundToWhole(double value) {
		return Math.max(1, (int) Math.round(value));
	}

	private static double roundToNearestHalf(double value) {
		return Math.round(value * 2.0D) / 2.0D;
	}

	private static double roundToTenth(double value) {
		return Math.round(value * 10.0D) / 10.0D;
	}

	private static double roundToNearestQuarter(double value) {
		return Math.round(value * 4.0D) / 4.0D;
	}

	private static <T> T getResolvedComponent(ItemStack stack, DataComponentType<? extends T> componentType) {
		if (stack == null || stack.isEmpty() || componentType == null) {
			return null;
		}

		T stackValue = stack.get(componentType);
		if (stackValue != null) {
			return stackValue;
		}

		Item item = stack.getItem();
		if (item == null) {
			return null;
		}
		if (componentType == DataComponents.ATTRIBUTE_MODIFIERS && item instanceof ArmorItem armorItem) {
			@SuppressWarnings("unchecked")
			T armorAttributes = (T) armorItem.getDefaultAttributeModifiers();
			return armorAttributes;
		}
		return item.components().get(componentType);
	}

	private static ItemAttributeModifiers resolveBaseArmorAttributeModifiers(ItemStack stack) {
		if (stack == null || stack.isEmpty()) {
			return null;
		}

		Item item = stack.getItem();
		if (item instanceof ArmorItem armorItem) {
			return armorItem.getDefaultAttributeModifiers();
		}

		return getResolvedComponent(stack, DataComponents.ATTRIBUTE_MODIFIERS);
	}

	private static Path resolveJsonFile(Path directory, String fileName) {
		String normalized = fileName == null ? "" : fileName.trim().toLowerCase(Locale.ROOT);
		if (normalized.isBlank()) {
			throw new IllegalArgumentException("Config file name must not be blank.");
		}
		String withExtension = normalized.endsWith(".json") ? normalized : normalized + ".json";
		return directory.resolve(withExtension);
	}

	private static void emitConfigLoaded() {
		String metricId = "rarity.config_loaded";
		if (!MadokuDebug.shouldEmit(MadokuDebug.Domain.ITEM, metricId)) {
			return;
		}

		MadokuDebug.event(metricId, MadokuDebug.Domain.ITEM)
			.side(MadokuDebug.Side.SERVER)
			.subject("rarity:global")
			.field("enabled", config.enabled)
			.field("common_weight", config.commonChanceWeight)
			.field("rare_weight", config.rareChanceWeight)
			.field("epic_weight", config.epicChanceWeight)
			.field("mythic_weight", config.mythicChanceWeight)
			.field("rare_buff_percent", config.rareStatBuffPercent)
			.field("epic_buff_percent", config.epicStatBuffPercent)
			.field("mythic_buff_percent", config.mythicStatBuffPercent)
			.log();
	}
}

