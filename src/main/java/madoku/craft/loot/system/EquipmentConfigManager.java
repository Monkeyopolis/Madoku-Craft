package madoku.craft.loot.system;

import com.google.gson.JsonArray;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import madoku.craft.config.DynamicStaticSystem;
import madoku.craft.config.JsonManagerSystem;
import madoku.craft.config.JsonStaticSystem;
import madoku.craft.mob.system.MobConfigManager;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.resources.Identifier;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.EquipmentSlot;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.Items;

import java.io.IOException;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.EnumMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;

public final class EquipmentConfigManager {
	private static final String ROOT_FOLDER = "madoku-craft-loot-tables";
	private static final String SETTINGS_FILE = "madoku-loot-tables";
	private static final String EQUIPMENT_FOLDER = "madoku-equipments";

	private static volatile Snapshot snapshot = Snapshot.disabled();

	private EquipmentConfigManager() {
	}

	public static void reloadConfig() {
		try {
			Path rootDirectory = JsonManagerSystem.getOrCreateGlobalSystemDirectory(ROOT_FOLDER);
			Path settingsFile = resolveJsonFile(rootDirectory, SETTINGS_FILE);
			JsonObject settingsRoot = JsonStaticSystem.ensureManagedFile(settingsFile, LootTableConfigManager.buildSettingsDefaults());
			JsonObject settingsMainRoot = resolveMainRoot(settingsRoot);
			boolean enabled = resolveFileEnabled(settingsRoot);
			boolean customEntityEquipmentEnabled = readBoolean(
				settingsMainRoot,
				LootTableConfigManager.FIELD_CUSTOM_ENTITY_EQUIPMENT,
				readBoolean(settingsRoot, LootTableConfigManager.FIELD_CUSTOM_ENTITY_EQUIPMENT, true)
			);
			double customEntityEquipmentChance = clampChancePercent(
				readDouble(
					settingsMainRoot,
					LootTableConfigManager.FIELD_CUSTOM_ENTITY_EQUIPMENT_CHANCE,
					readDouble(settingsRoot, LootTableConfigManager.FIELD_CUSTOM_ENTITY_EQUIPMENT_CHANCE, 10.0D)
				)
			);

			Path equipmentDirectory = rootDirectory.resolve(EQUIPMENT_FOLDER);
			Map<String, JsonObject> defaultFiles = LootTableEquipmentsConfig.buildDefaultEquipmentTableFiles();
			Map<String, JsonObject> files = DynamicStaticSystem.ensureManagedFolder(
				equipmentDirectory,
				defaultFiles,
				fileKey -> buildDynamicEquipmentDefaults(fileKey, defaultFiles),
				(fileKey, sourceRoot) -> true,
				(key, sourceValue) -> null
			);
			Map<String, EquipmentProfile> profiles = new java.util.LinkedHashMap<>();
			for (Map.Entry<String, JsonObject> entry : files.entrySet()) {
				EquipmentProfile profile = parseProfile(entry.getValue());
				if (profile != null) {
					profiles.put(normalizeFileKey(entry.getKey()), profile);
				}
			}
			snapshot = enabled
				? new Snapshot(customEntityEquipmentEnabled, customEntityEquipmentChance, Map.copyOf(profiles))
				: Snapshot.disabled();
		} catch (IOException | RuntimeException exception) {
			snapshot = Snapshot.disabled();
		}
	}

	public static boolean isCustomEntityEquipmentEnabled() {
		return snapshot.customEntityEquipmentEnabled();
	}

	public static EquipmentProfile resolveProfile(String rawReference, EntityType<?> mobType) {
		Snapshot active = snapshot;
		if (!active.customEntityEquipmentEnabled()) {
			return null;
		}
		String key = normalizeFileKey(rawReference);
		if (key.isBlank()) {
			key = defaultFileKeyForType(mobType);
		}
		if (key.isBlank()) {
			return null;
		}
		return active.profilesByFileKey().get(key);
	}

	public static double customEntityEquipmentChanceWhenMobSystemDisabled() {
		return snapshot.customEntityEquipmentChanceWhenMobSystemDisabled();
	}

	private static String defaultFileKeyForType(EntityType<?> type) {
		if (type == madoku.craft.entity.MadokuEntityTypes.SKELETON) {
			return "minecraft-equipment-skeleton";
		}
		if (type == madoku.craft.entity.MadokuEntityTypes.STRAY) {
			return "minecraft-equipment-stray";
		}
		if (type == madoku.craft.entity.MadokuEntityTypes.BOGGED) {
			return "minecraft-equipment-bogged";
		}
		if (type == madoku.craft.entity.MadokuEntityTypes.PARCHED) {
			return "minecraft-equipment-parched";
		}
		if (type == madoku.craft.entity.MadokuEntityTypes.WITHER_SKELETON) {
			return "minecraft-equipment-wither-skeleton";
		}
		if (type == madoku.craft.entity.MadokuEntityTypes.HUSK) {
			return "minecraft-equipment-husk";
		}
		if (type == madoku.craft.entity.MadokuEntityTypes.DROWNED) {
			return "minecraft-equipment-drowned";
		}
		if (type == madoku.craft.entity.MadokuEntityTypes.ZOMBIE_VILLAGER) {
			return "minecraft-equipment-zombie-villager";
		}
		if (type == madoku.craft.entity.MadokuEntityTypes.ZOMBIE) {
			return "minecraft-equipment-zombie";
		}
		return "";
	}

	private static EquipmentProfile parseProfile(JsonObject root) {
		if (root == null) {
			return null;
		}
		JsonObject profileRoot = resolveMainRoot(root);
		boolean enabled = resolveFileEnabled(root);
		JsonObject armorSet = readObject(profileRoot, LootTableEquipmentsConfig.FIELD_ARMOR_SET);
		ArmorSetWeights setWeights = new ArmorSetWeights(
			Math.max(0.0D, readDouble(armorSet, MobConfigManager.FIELD_PARTIAL_SET, 60.0D)),
			Math.max(0.0D, readDouble(armorSet, MobConfigManager.FIELD_HALF_SET, 30.0D)),
			Math.max(0.0D, readDouble(armorSet, MobConfigManager.FIELD_FULL_SET, 10.0D))
		);

		Map<EquipmentSlot, List<WeightedArmorEntry>> slotEntries = new EnumMap<>(EquipmentSlot.class);
		slotEntries.put(EquipmentSlot.HEAD, parseSlotEntries(readArray(profileRoot, MobConfigManager.FIELD_HELMET)));
		slotEntries.put(EquipmentSlot.CHEST, parseSlotEntries(readArray(profileRoot, MobConfigManager.FIELD_CHESTPLATE)));
		slotEntries.put(EquipmentSlot.LEGS, parseSlotEntries(readArray(profileRoot, MobConfigManager.FIELD_LEGGINGS)));
		slotEntries.put(EquipmentSlot.FEET, parseSlotEntries(readArray(profileRoot, MobConfigManager.FIELD_BOOTS)));
		return new EquipmentProfile(enabled, setWeights, Map.copyOf(slotEntries));
	}

	private static List<WeightedArmorEntry> parseSlotEntries(JsonArray entries) {
		if (entries == null || entries.isEmpty()) {
			return List.of();
		}
		List<WeightedArmorEntry> parsed = new ArrayList<>();
		for (JsonElement entry : entries) {
			if (!(entry instanceof JsonObject entryRoot)) {
				continue;
			}
			String itemId = normalizeItemId(readString(entryRoot, LootTableEquipmentsConfig.FIELD_ITEM, ""));
			if (itemId.isBlank()) {
				continue;
			}
			Identifier identifier = Identifier.tryParse(itemId);
			if (identifier == null || !BuiltInRegistries.ITEM.containsKey(identifier)) {
				continue;
			}
			Item item = BuiltInRegistries.ITEM.getValue(identifier);
			if (item == null || item == Items.AIR) {
				continue;
			}
			double weight = Math.max(0.0D, readDouble(entryRoot, LootTableEquipmentsConfig.FIELD_WEIGHT, 0.0D));
			if (weight <= 0.0D) {
				continue;
			}
			parsed.add(new WeightedArmorEntry(item, weight));
		}
		return parsed.isEmpty() ? List.of() : List.copyOf(parsed);
	}

	private static JsonObject buildDynamicEquipmentDefaults(String fileKey, Map<String, JsonObject> defaultsByKey) {
		String normalized = normalizeFileKey(fileKey);
		JsonObject mapped = defaultsByKey.get(normalized);
		if (mapped != null) {
			return mapped.deepCopy();
		}
		return EquipmentConfigZombie.buildZombieDefaults();
	}

	private static Path resolveJsonFile(Path directory, String fileName) {
		String normalized = fileName == null ? "" : fileName.trim();
		if (normalized.isEmpty()) {
			throw new IllegalArgumentException("Zombie equipment config file name must not be blank.");
		}
		if (!normalized.endsWith(".json")) {
			normalized = normalized + ".json";
		}
		return directory.resolve(normalized);
	}

	private static String normalizeFileKey(String value) {
		if (value == null || value.isBlank()) {
			return "";
		}
		String normalized = value.trim().toLowerCase(Locale.ROOT).replace('\\', '/');
		int slashIndex = normalized.lastIndexOf('/');
		if (slashIndex >= 0 && slashIndex < normalized.length() - 1) {
			normalized = normalized.substring(slashIndex + 1);
		}
		if (normalized.endsWith(".json")) {
			normalized = normalized.substring(0, normalized.length() - ".json".length());
		}
		return normalized;
	}

	private static String normalizeItemId(String value) {
		if (value == null || value.isBlank()) {
			return "";
		}
		return value.trim().toLowerCase(Locale.ROOT);
	}

	private static JsonObject resolveMainRoot(JsonObject root) {
		JsonObject mainRoot = readObject(root, "main");
		return mainRoot.entrySet().isEmpty() ? (root == null ? new JsonObject() : root) : mainRoot;
	}

	private static boolean resolveFileEnabled(JsonObject root) {
		JsonObject generalRoot = readObject(root, "general");
		if (!generalRoot.entrySet().isEmpty()) {
			return readBoolean(generalRoot, MobConfigManager.FIELD_ENABLED, true);
		}
		return readBoolean(root, MobConfigManager.FIELD_ENABLED, true);
	}

	private static JsonObject readObject(JsonObject root, String key) {
		if (root == null || key == null || key.isBlank()) {
			return new JsonObject();
		}
		JsonElement element = root.get(key);
		return element != null && element.isJsonObject() ? element.getAsJsonObject() : new JsonObject();
	}

	private static JsonArray readArray(JsonObject root, String key) {
		if (root == null || key == null || key.isBlank()) {
			return new JsonArray();
		}
		JsonElement element = root.get(key);
		return element != null && element.isJsonArray() ? element.getAsJsonArray() : new JsonArray();
	}

	private static boolean readBoolean(JsonObject root, String key, boolean fallback) {
		if (root == null || key == null || key.isBlank()) {
			return fallback;
		}
		JsonElement element = root.get(key);
		if (element == null || !element.isJsonPrimitive() || !element.getAsJsonPrimitive().isBoolean()) {
			return fallback;
		}
		return element.getAsBoolean();
	}

	private static double readDouble(JsonObject root, String key, double fallback) {
		if (root == null || key == null || key.isBlank()) {
			return fallback;
		}
		JsonElement element = root.get(key);
		if (element == null || !element.isJsonPrimitive() || !element.getAsJsonPrimitive().isNumber()) {
			return fallback;
		}
		try {
			double value = element.getAsDouble();
			return Double.isFinite(value) ? value : fallback;
		} catch (RuntimeException ignored) {
			return fallback;
		}
	}

	private static double clampChancePercent(double value) {
		if (!Double.isFinite(value)) {
			return 0.0D;
		}
		return Math.max(0.0D, Math.min(100.0D, value));
	}

	private static String readString(JsonObject root, String key, String fallback) {
		if (root == null || key == null || key.isBlank()) {
			return fallback;
		}
		JsonElement element = root.get(key);
		if (element == null || !element.isJsonPrimitive() || !element.getAsJsonPrimitive().isString()) {
			return fallback;
		}
		return element.getAsString();
	}

	public record EquipmentProfile(boolean enabled, ArmorSetWeights armorSetWeights, Map<EquipmentSlot, List<WeightedArmorEntry>> slotEntries) {
	}

	public record ArmorSetWeights(double partialSetWeight, double halfSetWeight, double fullSetWeight) {
	}

	public record WeightedArmorEntry(Item item, double weight) {
	}

	private record Snapshot(
		boolean customEntityEquipmentEnabled,
		double customEntityEquipmentChanceWhenMobSystemDisabled,
		Map<String, EquipmentProfile> profilesByFileKey
	) {
		private static Snapshot disabled() {
			return new Snapshot(false, 0.0D, Map.of());
		}
	}
}
