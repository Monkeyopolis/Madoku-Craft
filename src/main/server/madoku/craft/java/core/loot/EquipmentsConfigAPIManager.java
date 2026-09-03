package madoku.craft.java.core.loot;

import com.google.gson.JsonArray;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;

import madoku.craft.java.core.json.JSONFormatAPIManager;
import madoku.craft.java.core.json.JSONAPIManager;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.resources.Identifier;
import net.minecraft.util.RandomSource;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.EquipmentSlot;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;

import java.io.IOException;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.EnumMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;

public final class EquipmentsConfigAPIManager {
	public static final String FIELD_ENABLED = "enabled";
	public static final String FIELD_MOB_ID = "mob-id";
	public static final String FIELD_EQUIPMENTS = "equipments";
	public static final String FIELD_HEAD = "head";
	public static final String FIELD_CHEST = "chest";
	public static final String FIELD_LEGS = "legs";
	public static final String FIELD_FEET = "feet";
	public static final String FIELD_MAIN_HAND = "main-hand";
	public static final String FIELD_OFF_HAND = "off-hand";
	public static final String FIELD_ITEM = "item";
	public static final String FIELD_WEIGHT = "weight";
	private static final String ROOT_FOLDER = LootTableAPIManager.CONFIG_ROOT_FOLDER_NAME;
	private static final String SETTINGS_FILE = "madoku-loot-tables";
	private static final String EQUIPMENT_FOLDER = "madoku-equipments";

	private static volatile Snapshot snapshot = Snapshot.disabled();

	private EquipmentsConfigAPIManager() {
	}

	public static void initialize() { }

	public static void reset() {
		snapshot = Snapshot.disabled();
	}

	public static Map<String, JsonObject> buildDefaultEquipmentTableFiles() {
		Map<String, JsonObject> defaults = new LinkedHashMap<>();
		String[] mobs = {
			"skeleton", "stray", "bogged", "parched", "wither-skeleton",
			"zombie", "husk", "drowned", "zombie-villager"
		};
		for (String mob : mobs) {
			defaults.put("minecraft-equipment-" + mob, buildDefaultProfile("minecraft:" + mob));
		}
		return defaults;
	}

	private static JsonObject buildDefaultProfile(String mobId) {
		return JSONFormatAPIManager.object()
			.put(FIELD_ENABLED, true)
			.put(FIELD_MOB_ID, mobId)
			.object(FIELD_EQUIPMENTS, equipments -> equipments
				.put(FIELD_HEAD, buildDefaultArmorEntries("helmet"))
				.put(FIELD_CHEST, buildDefaultArmorEntries("chestplate"))
				.put(FIELD_LEGS, buildDefaultArmorEntries("leggings"))
				.put(FIELD_FEET, buildDefaultArmorEntries("boots"))
				.put(FIELD_MAIN_HAND, buildEmptySlotEntries())
				.put(FIELD_OFF_HAND, buildEmptySlotEntries()))
			.build();
	}

	private static JsonArray buildDefaultArmorEntries(String piece) {
		return JSONFormatAPIManager.array()
			.add(defaultItem("minecraft:netherite-" + piece, 1.0D))
			.add(defaultItem("minecraft:diamond-" + piece, 5.0D))
			.add(defaultItem("minecraft:golden-" + piece, 10.0D))
			.add(defaultItem("minecraft:iron-" + piece, 17.0D))
			.add(defaultItem("minecraft:copper-" + piece, 28.0D))
			.add(defaultItem("minecraft:leather-" + piece, 39.0D))
			.add(defaultItem("empty", 1900.0D))
			.build();
	}

	private static JsonArray buildEmptySlotEntries() {
		return JSONFormatAPIManager.array().add(defaultItem("empty", 1000.0D)).build();
	}

	private static JsonObject defaultItem(String itemId, double weight) {
		return JSONFormatAPIManager.object().put(FIELD_ITEM, itemId).put(FIELD_WEIGHT, weight).build();
	}

	public static void reloadConfig() {
		try {
			Path rootDirectory = JSONAPIManager.getOrCreateGlobalSystemDirectory(ROOT_FOLDER);
			Path settingsFile = resolveJsonFile(rootDirectory, SETTINGS_FILE);
			JsonObject settingsRoot = JSONFormatAPIManager.ensureManagedFile(settingsFile, LootTableConfigManager.buildSettingsDefaults());
			JsonObject settingsMainRoot = resolveMainRoot(settingsRoot);
			boolean enabled = resolveFileEnabled(settingsRoot);
			boolean overrideEntityEquipment = readBoolean(
				settingsMainRoot,
				LootTableConfigManager.FIELD_OVERRIDE_ENTITY_EQUIPMENT,
				readBoolean(settingsRoot, LootTableConfigManager.FIELD_OVERRIDE_ENTITY_EQUIPMENT, true)
			);

			Path equipmentDirectory = rootDirectory.resolve(EQUIPMENT_FOLDER);
			Map<String, JsonObject> defaultFiles = buildDefaultEquipmentTableFiles();
			Map<String, JsonObject> files = JSONFormatAPIManager.ensureManagedFolder(
				equipmentDirectory,
				defaultFiles,
				fileKey -> buildDynamicEquipmentDefaults(fileKey, defaultFiles),
				(fileKey, sourceRoot) -> true,
				(key, sourceValue) -> null
			);
			Map<String, EquipmentProfile> profiles = new LinkedHashMap<>();
			for (Map.Entry<String, JsonObject> entry : files.entrySet()) {
				EquipmentProfile profile = parseProfile(entry.getValue());
				if (profile != null) {
					profiles.put(normalizeFileKey(entry.getKey()), profile);
				}
			}
			snapshot = enabled && overrideEntityEquipment
				? new Snapshot(true, Map.copyOf(profiles))
				: Snapshot.disabled();
		} catch (IOException | RuntimeException exception) {
			snapshot = Snapshot.disabled();
		}
	}

	public static boolean isEntityEquipmentOverrideEnabled() {
		return snapshot.overrideEntityEquipment();
	}

	public static EquipmentProfile resolveProfile(String rawReference, EntityType<?> mobType) {
		Snapshot active = snapshot;
		if (!active.overrideEntityEquipment()) {
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

	public static Map<EquipmentSlot, ItemStack> rollEquipment(EquipmentProfile profile, RandomSource random) {
		if (profile == null || !profile.enabled() || random == null) {
			return Map.of();
		}
		Map<EquipmentSlot, ItemStack> selected = new EnumMap<>(EquipmentSlot.class);
		for (EquipmentSlot slot : EquipmentSlot.values()) {
			WeightedArmorEntry entry = selectWeightedEntry(profile.slotEntries().get(slot), random);
			if (entry != null && entry.item() != null && entry.item() != Items.AIR) {
				selected.put(slot, new ItemStack(entry.item()));
			}
		}
		return selected.isEmpty() ? Map.of() : Map.copyOf(selected);
	}

	private static WeightedArmorEntry selectWeightedEntry(List<WeightedArmorEntry> entries, RandomSource random) {
		if (entries == null || entries.isEmpty() || random == null) {
			return null;
		}
		double totalWeight = 0.0D;
		for (WeightedArmorEntry entry : entries) {
			if (entry != null && entry.item() != null && entry.weight() > 0.0D) {
				totalWeight += entry.weight();
			}
		}
		if (totalWeight <= 0.0D) {
			return null;
		}
		double roll = random.nextDouble() * totalWeight;
		for (WeightedArmorEntry entry : entries) {
			if (entry == null || entry.item() == null || entry.weight() <= 0.0D) {
				continue;
			}
			if (roll < entry.weight()) {
				return entry;
			}
			roll -= entry.weight();
		}
		return null;
	}

	private static String defaultFileKeyForType(EntityType<?> type) {
		if (matches(type, vanillaEntityType("skeleton"), madoku.craft.java.entity.MadokuEntityTypes.SKELETON)) return "minecraft-equipment-skeleton";
		if (matches(type, vanillaEntityType("stray"), madoku.craft.java.entity.MadokuEntityTypes.STRAY)) return "minecraft-equipment-stray";
		if (matches(type, vanillaEntityType("bogged"), madoku.craft.java.entity.MadokuEntityTypes.BOGGED)) return "minecraft-equipment-bogged";
		if (type == madoku.craft.java.entity.MadokuEntityTypes.PARCHED) return "minecraft-equipment-parched";
		if (matches(type, vanillaEntityType("wither_skeleton"), madoku.craft.java.entity.MadokuEntityTypes.WITHER_SKELETON)) return "minecraft-equipment-wither-skeleton";
		if (matches(type, vanillaEntityType("husk"), madoku.craft.java.entity.MadokuEntityTypes.HUSK)) return "minecraft-equipment-husk";
		if (matches(type, vanillaEntityType("drowned"), madoku.craft.java.entity.MadokuEntityTypes.DROWNED)) return "minecraft-equipment-drowned";
		if (matches(type, vanillaEntityType("zombie_villager"), madoku.craft.java.entity.MadokuEntityTypes.ZOMBIE_VILLAGER)) return "minecraft-equipment-zombie-villager";
		if (matches(type, vanillaEntityType("zombie"), madoku.craft.java.entity.MadokuEntityTypes.ZOMBIE)) return "minecraft-equipment-zombie";
		return "";
	}

	private static EntityType<?> vanillaEntityType(String path) {
		Identifier identifier = Identifier.tryParse("minecraft:" + path);
		return identifier == null ? null : BuiltInRegistries.ENTITY_TYPE.getValue(identifier);
	}

	private static boolean matches(EntityType<?> actual, EntityType<?> vanilla, EntityType<?> madoku) {
		return actual == vanilla || actual == madoku;
	}

	private static EquipmentProfile parseProfile(JsonObject root) {
		if (root == null) {
			return null;
		}
		JsonObject profileRoot = resolveMainRoot(root);
		boolean enabled = resolveFileEnabled(root);
		String mobId = normalizeMobId(readString(profileRoot, FIELD_MOB_ID, ""));
		JsonObject equipments = readObject(profileRoot, FIELD_EQUIPMENTS);
		Map<EquipmentSlot, List<WeightedArmorEntry>> slotEntries = new EnumMap<>(EquipmentSlot.class);
		slotEntries.put(EquipmentSlot.HEAD, parseSlotEntries(readArray(equipments, FIELD_HEAD)));
		slotEntries.put(EquipmentSlot.CHEST, parseSlotEntries(readArray(equipments, FIELD_CHEST)));
		slotEntries.put(EquipmentSlot.LEGS, parseSlotEntries(readArray(equipments, FIELD_LEGS)));
		slotEntries.put(EquipmentSlot.FEET, parseSlotEntries(readArray(equipments, FIELD_FEET)));
		slotEntries.put(EquipmentSlot.MAINHAND, parseSlotEntries(readArray(equipments, FIELD_MAIN_HAND)));
		slotEntries.put(EquipmentSlot.OFFHAND, parseSlotEntries(readArray(equipments, FIELD_OFF_HAND)));
		return new EquipmentProfile(enabled, mobId, Map.copyOf(slotEntries));
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
			double weight = Math.max(0.0D, readDouble(entryRoot, FIELD_WEIGHT, 0.0D));
			if (weight <= 0.0D) {
				continue;
			}
			String rawItemId = readString(entryRoot, FIELD_ITEM, "").trim();
			if ("empty".equalsIgnoreCase(rawItemId)) {
				parsed.add(new WeightedArmorEntry(Items.AIR, weight));
				continue;
			}
			Identifier identifier = Identifier.tryParse(JSONAPIManager.normalizeRegistryIdentifierForLookup(rawItemId));
			if (identifier == null || !BuiltInRegistries.ITEM.containsKey(identifier)) {
				continue;
			}
			Item item = BuiltInRegistries.ITEM.getValue(identifier);
			if (item != null) {
				parsed.add(new WeightedArmorEntry(item, weight));
			}
		}
		return parsed.isEmpty() ? List.of() : List.copyOf(parsed);
	}

	private static JsonObject buildDynamicEquipmentDefaults(String fileKey, Map<String, JsonObject> defaultsByKey) {
		String normalized = normalizeFileKey(fileKey);
		JsonObject mapped = defaultsByKey.get(normalized);
		return mapped == null
			? buildDefaultEquipmentTableFiles().get("minecraft-equipment-zombie")
			: mapped.deepCopy();
	}

	private static Path resolveJsonFile(Path directory, String fileName) {
		String normalized = fileName == null ? "" : fileName.trim();
		if (normalized.isEmpty()) {
			throw new IllegalArgumentException("Equipment config file name must not be blank.");
		}
		if (!normalized.endsWith(".json")) {
			normalized = normalized + ".json";
		}
		return directory.resolve(normalized);
	}

	private static String normalizeFileKey(String value) {
		if (value == null || value.isBlank()) return "";
		String normalized = value.trim().toLowerCase(Locale.ROOT).replace('\\', '/');
		int slashIndex = normalized.lastIndexOf('/');
		if (slashIndex >= 0 && slashIndex < normalized.length() - 1) normalized = normalized.substring(slashIndex + 1);
		if (normalized.endsWith(".json")) normalized = normalized.substring(0, normalized.length() - 5);
		return normalized.replace('_', '-');
	}

	private static String normalizeMobId(String value) {
		return JSONAPIManager.normalizeRegistryIdentifierForJson(value);
	}

	private static JsonObject resolveMainRoot(JsonObject root) {
		JsonObject mainRoot = readObject(root, "main");
		return mainRoot.entrySet().isEmpty() ? (root == null ? new JsonObject() : root) : mainRoot;
	}

	private static boolean resolveFileEnabled(JsonObject root) {
		JsonObject generalRoot = readObject(root, "general");
		return !generalRoot.entrySet().isEmpty()
			? readBoolean(generalRoot, FIELD_ENABLED, true)
			: readBoolean(root, FIELD_ENABLED, true);
	}

	private static JsonObject readObject(JsonObject root, String key) {
		if (root == null || key == null || key.isBlank()) return new JsonObject();
		JsonElement element = root.get(key);
		return element != null && element.isJsonObject() ? element.getAsJsonObject() : new JsonObject();
	}

	private static JsonArray readArray(JsonObject root, String key) {
		if (root == null || key == null || key.isBlank()) return new JsonArray();
		JsonElement element = root.get(key);
		return element != null && element.isJsonArray() ? element.getAsJsonArray() : new JsonArray();
	}

	private static boolean readBoolean(JsonObject root, String key, boolean fallback) {
		if (root == null || key == null || key.isBlank()) return fallback;
		JsonElement element = root.get(key);
		return element != null && element.isJsonPrimitive() && element.getAsJsonPrimitive().isBoolean()
			? element.getAsBoolean() : fallback;
	}

	private static double readDouble(JsonObject root, String key, double fallback) {
		if (root == null || key == null || key.isBlank()) return fallback;
		JsonElement element = root.get(key);
		if (element == null || !element.isJsonPrimitive() || !element.getAsJsonPrimitive().isNumber()) return fallback;
		try {
			double value = element.getAsDouble();
			return Double.isFinite(value) ? value : fallback;
		} catch (RuntimeException ignored) {
			return fallback;
		}
	}

	private static String readString(JsonObject root, String key, String fallback) {
		if (root == null || key == null || key.isBlank()) return fallback;
		JsonElement element = root.get(key);
		return element != null && element.isJsonPrimitive() && element.getAsJsonPrimitive().isString()
			? element.getAsString() : fallback;
	}

	public record EquipmentProfile(
		boolean enabled,
		String mobId,
		Map<EquipmentSlot, List<WeightedArmorEntry>> slotEntries
	) {
	}

	public record WeightedArmorEntry(Item item, double weight) {
	}

	private record Snapshot(boolean overrideEntityEquipment, Map<String, EquipmentProfile> profilesByFileKey) {
		private static Snapshot disabled() {
			return new Snapshot(false, Map.of());
		}
	}
}

