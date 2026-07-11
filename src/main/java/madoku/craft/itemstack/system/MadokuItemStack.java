package madoku.craft.itemstack.system;

import com.google.gson.JsonArray;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.mojang.serialization.DataResult;
import com.mojang.serialization.JsonOps;
import madoku.craft.api.time.MadokuTimeManager;
import madoku.craft.api.json.MadokuJSONManager;
import madoku.craft.api.json.JSONFormatManager;
import madoku.craft.api.json.JSONTypeManager;
import net.fabricmc.fabric.api.entity.event.v1.ServerPlayerEvents;
import net.minecraft.resources.RegistryOps;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.player.Inventory;
import net.minecraft.world.item.ItemStack;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.IOException;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;

public final class MadokuItemStack {
	private static final Logger LOGGER = LoggerFactory.getLogger(MadokuItemStack.class);

	private static final String ITEMSTACK_CONFIG_FOLDER_NAME = "madoku-craft-stacks";
	private static final String ITEMSTACK_CONFIG_FILE_NAME = "madoku-stacks";
	private static final String DATA_FOLDER_NAME = "madoku-craft-stacks";
	private static final String DATA_FILE_NAME = "madoku-stacks";
	private static final String DATA_KEPT_STACKS_KEY = "kept_stacks";
	private static final String DATA_ENTRY_SLOT_KEY = "slot";
	private static final String DATA_ENTRY_STACK_KEY = "stack";

	private static final MadokuItemStackConfig configuration = new MadokuItemStackConfig();
	private static final Map<UUID, List<KeptStack>> keptStacksByPlayer = new HashMap<>();
	private static long lastAutosaveBucket = Long.MIN_VALUE;

	private MadokuItemStack() {
	}

	public static void initialize() {
		loadStaticConfig();
		ServerPlayerEvents.AFTER_RESPAWN.register(MadokuItemStack::onAfterRespawn);
	}

	public static void reset() {
		keptStacksByPlayer.clear();
		lastAutosaveBucket = Long.MIN_VALUE;
	}

	public static void loadPersistedData(MinecraftServer server) {
		if (server == null) {
			return;
		}
		JsonObject data = MadokuJSONManager.loadWorldData(server, DATA_FOLDER_NAME, DATA_FILE_NAME, createDefaultData());
		applyPersistedData(server, data);
		long autoSaveIntervalTicks = MadokuJSONManager.getAutoSaveIntervalTicks(server, DATA_FOLDER_NAME, DATA_FILE_NAME);
		lastAutosaveBucket = Math.floorDiv(MadokuTimeManager.getGameplayTicks(), autoSaveIntervalTicks);
	}

	public static void autosavePersistedData(MinecraftServer server) {
		if (server == null) {
			return;
		}
		long autoSaveIntervalTicks = MadokuJSONManager.getAutoSaveIntervalTicks(server, DATA_FOLDER_NAME, DATA_FILE_NAME);
		long bucket = Math.floorDiv(MadokuTimeManager.getGameplayTicks(), autoSaveIntervalTicks);
		if (bucket != lastAutosaveBucket) {
			lastAutosaveBucket = bucket;
			savePersistedData(server);
		}
	}

	public static void savePersistedData(MinecraftServer server) {
		if (server == null) {
			return;
		}
		MadokuJSONManager.saveWorldData(server, DATA_FOLDER_NAME, DATA_FILE_NAME, toPersistedData(server));
	}

	public static boolean isEnabled() {
		return configuration.enabled;
	}

	public static boolean usesManagedDeathDrop() {
		return isEnabled() && configuration.deathDropEnabled;
	}

	public static int getDeathDropStackPercent() {
		return configuration.deathDropStackPercent;
	}

	public static int getStackLimit() {
		return configuration.customStackAmount;
	}

	public static int getMaxStackCap() {
		return MadokuItemStackConfig.MAX_STACK_RUNTIME_CAP;
	}

	public static int adjustStackLimit(int originalLimit) {
		if (!isEnabled() || originalLimit <= 1) {
			return originalLimit;
		}
		return Math.max(originalLimit, getStackLimit());
	}

	public static boolean shouldExtendCodecRange(int minimum, int maximum) {
		return isEnabled()
			&& minimum <= 1
			&& maximum == 99;
	}

	public static int getCodecUpperBound(int maximum) {
		if (!isEnabled()) {
			return maximum;
		}
		return Math.max(maximum, getMaxStackCap());
	}

	public static DataResult<Integer> validateCodecCount(int minimum, int maximum, int value) {
		int upper = shouldExtendCodecRange(minimum, maximum)
			? getCodecUpperBound(maximum)
			: maximum;

		if (value < minimum) {
			return DataResult.error(() -> "Value must be within range [" + minimum + ";" + upper + "]: " + value);
		}

		if (value > upper) {
			return DataResult.success(upper);
		}

		return DataResult.success(value);
	}

	public static String formatCompactStackCount(int count) {
		if (count < 1000) {
			return Integer.toString(count);
		}
		return formatCompactValue(count);
	}

	private static String formatCompactValue(long value) {
		if (value >= 1_000_000L) {
			return formatCompactUnit(value, 1_000_000L, "M");
		}
		return formatCompactUnit(value, 1_000L, "K");
	}

	private static String formatCompactUnit(long value, long unit, String suffix) {
		long whole = value / unit;
		if (whole >= 10L) {
			return whole + suffix;
		}
		long tenth = ((value % unit) * 10L) / unit;
		if (tenth <= 0L) {
			return whole + suffix;
		}
		return whole + "." + tenth + suffix;
	}

	public static boolean handleInventoryDrop(Inventory inventory) {
		if (inventory == null || !isEnabled() || !configuration.deathDropEnabled) {
			return false;
		}

		ServerPlayer player = resolveServerPlayer(inventory);
		if (player == null) {
			return false;
		}

		List<Integer> occupiedSlots = collectOccupiedSlots(inventory);
		if (occupiedSlots.isEmpty()) {
			return true;
		}

		shuffleSlots(occupiedSlots, player);
		int dropCount = calculateDropCount(occupiedSlots.size(), configuration.deathDropStackPercent);
		List<KeptStack> keptStacks = new ArrayList<>(Math.max(0, occupiedSlots.size() - dropCount));

		for (int index = 0; index < occupiedSlots.size(); index++) {
			int slot = occupiedSlots.get(index);
			ItemStack stack = inventory.getItem(slot);
			if (stack.isEmpty()) {
				continue;
			}

			if (index < dropCount) {
				player.drop(stack, true, false);
			} else {
				keptStacks.add(new KeptStack(slot, stack.copy()));
			}
			inventory.setItem(slot, ItemStack.EMPTY);
		}

		if (keptStacks.isEmpty()) {
			keptStacksByPlayer.remove(player.getUUID());
		} else {
			keptStacksByPlayer.put(player.getUUID(), keptStacks);
		}

		inventory.setChanged();
		savePersistedData(player.level().getServer());
		return true;
	}

	private static JsonObject createDefaultData() {
		return JSONFormatManager.object()
			.object(DATA_KEPT_STACKS_KEY, kept -> {
			})
			.build();
	}

	private static void applyPersistedData(MinecraftServer server, JsonObject data) {
		keptStacksByPlayer.clear();
		if (server == null || data == null) {
			return;
		}

		JsonElement keptElement = data.get(DATA_KEPT_STACKS_KEY);
		if (keptElement == null || !keptElement.isJsonObject()) {
			return;
		}

		RegistryOps<JsonElement> ops = RegistryOps.create(JsonOps.INSTANCE, server.registryAccess());
		JsonObject keptRoot = keptElement.getAsJsonObject();

		for (Map.Entry<String, JsonElement> playerEntry : keptRoot.entrySet()) {
			UUID playerId;
			try {
				playerId = UUID.fromString(playerEntry.getKey());
			} catch (IllegalArgumentException ignored) {
				continue;
			}

			JsonElement listElement = playerEntry.getValue();
			if (listElement == null || !listElement.isJsonArray()) {
				continue;
			}

			List<KeptStack> keptStacks = new ArrayList<>();
			for (JsonElement rawEntry : listElement.getAsJsonArray()) {
				if (rawEntry == null || !rawEntry.isJsonObject()) {
					continue;
				}
				JsonObject entryObject = rawEntry.getAsJsonObject();
				JsonElement slotElement = entryObject.get(DATA_ENTRY_SLOT_KEY);
				JsonElement stackElement = entryObject.get(DATA_ENTRY_STACK_KEY);
				if (slotElement == null || !slotElement.isJsonPrimitive() || !slotElement.getAsJsonPrimitive().isNumber()) {
					continue;
				}
				if (stackElement == null) {
					continue;
				}

				int slot = slotElement.getAsInt();
				DataResult<ItemStack> decoded = ItemStack.CODEC.parse(ops, stackElement);
				ItemStack stack = decoded.result().orElse(ItemStack.EMPTY);
				if (stack.isEmpty()) {
					continue;
				}
				keptStacks.add(new KeptStack(slot, stack));
			}

			if (!keptStacks.isEmpty()) {
				keptStacksByPlayer.put(playerId, keptStacks);
			}
		}
	}

	private static JsonObject toPersistedData(MinecraftServer server) {
		JSONFormatManager.ObjectBuilder keptRoot = JSONFormatManager.object();
		if (server != null) {
			RegistryOps<JsonElement> ops = RegistryOps.create(JsonOps.INSTANCE, server.registryAccess());
			for (Map.Entry<UUID, List<KeptStack>> entry : keptStacksByPlayer.entrySet()) {
				UUID playerId = entry.getKey();
				List<KeptStack> keptStacks = entry.getValue();
				if (playerId == null || keptStacks == null || keptStacks.isEmpty()) {
					continue;
				}

				JSONFormatManager.ArrayBuilder encodedStacks = JSONFormatManager.array();
				for (KeptStack keptStack : keptStacks) {
					if (keptStack == null || keptStack.stack() == null || keptStack.stack().isEmpty()) {
						continue;
					}
					DataResult<JsonElement> encoded = ItemStack.CODEC.encodeStart(ops, keptStack.stack());
					JsonElement stackElement = encoded.result().orElse(null);
					if (stackElement == null) {
						continue;
					}

					encodedStacks.object(encodedEntry -> encodedEntry
						.put(DATA_ENTRY_SLOT_KEY, keptStack.slot())
						.put(DATA_ENTRY_STACK_KEY, stackElement));
				}

				JsonArray encodedArray = encodedStacks.build();
				if (!encodedArray.isEmpty()) {
					keptRoot.put(playerId.toString(), encodedArray);
				}
			}
		}

		return JSONFormatManager.object()
			.put(DATA_KEPT_STACKS_KEY, keptRoot.build())
			.build();
	}

	private static void loadStaticConfig() {
		try {
			Path directory = MadokuJSONManager.getOrCreateGlobalSystemDirectory(ITEMSTACK_CONFIG_FOLDER_NAME);
			Path configFile = resolveJsonFile(directory, ITEMSTACK_CONFIG_FILE_NAME);
			JSONFormatManager.ManagedDocument document = JSONFormatManager.readManagedDocument(configFile);
			JsonObject root = document.data();
			JsonObject general = document.settings();
			configuration.enabled = readBoolean(general, "enabled", true);
			boolean changed = configuration.updateItemStack(root);
			general.addProperty("enabled", configuration.enabled);
			if (changed || !root.equals(document.data()) || !general.equals(document.settings())) {
				JSONFormatManager.writeManagedDocument(configFile, root, general, JSONTypeManager.STATIC_CONFIG);
			}
		} catch (IOException | RuntimeException exception) {
			configuration.resetToDefaults();
			LOGGER.error("Failed to load MadokuItemStack config; using defaults.", exception);
		}
	}

	private static void onAfterRespawn(ServerPlayer oldPlayer, ServerPlayer newPlayer, boolean alive) {
		if (newPlayer == null || alive || !isEnabled() || !configuration.deathDropEnabled) {
			return;
		}

		List<KeptStack> keptStacks = keptStacksByPlayer.remove(newPlayer.getUUID());
		if (keptStacks == null || keptStacks.isEmpty()) {
			return;
		}

		Inventory inventory = newPlayer.getInventory();
		for (KeptStack entry : keptStacks) {
			ItemStack stack = entry.stack();
			if (stack.isEmpty()) {
				continue;
			}

			int slot = entry.slot();
			if (slot >= 0 && slot < inventory.getContainerSize() && inventory.getItem(slot).isEmpty()) {
				inventory.setItem(slot, stack);
				continue;
			}

			if (!inventory.add(stack)) {
				newPlayer.drop(stack, true, false);
			}
		}

		inventory.setChanged();
		savePersistedData(newPlayer.level().getServer());
	}

	private static ServerPlayer resolveServerPlayer(Inventory inventory) {
		if (inventory.player == null || inventory.player.level().isClientSide()) {
			return null;
		}
		if (!(inventory.player instanceof ServerPlayer serverPlayer)) {
			return null;
		}
		return serverPlayer;
	}

	private static List<Integer> collectOccupiedSlots(Inventory inventory) {
		int size = inventory.getContainerSize();
		List<Integer> occupied = new ArrayList<>(size);
		for (int slot = 0; slot < size; slot++) {
			if (!inventory.getItem(slot).isEmpty()) {
				occupied.add(slot);
			}
		}
		return occupied;
	}

	private static int calculateDropCount(int totalStacks, int percent) {
		int clampedPercent = Math.max(0, Math.min(100, percent));
		int dropCount = Math.round(totalStacks * (clampedPercent / 100.0f));
		if (dropCount < 0) {
			return 0;
		}
		return Math.min(dropCount, totalStacks);
	}

	private static void shuffleSlots(List<Integer> slots, ServerPlayer player) {
		if (slots == null || slots.size() <= 1 || player == null) {
			return;
		}
		for (int i = slots.size() - 1; i > 0; i--) {
			int j = player.getRandom().nextInt(i + 1);
			if (i == j) {
				continue;
			}
			int temp = slots.get(i);
			slots.set(i, slots.get(j));
			slots.set(j, temp);
		}
	}



	private static Path resolveJsonFile(Path directory, String fileName) {
		String normalized = fileName == null ? "" : fileName.trim();
		if (normalized.isEmpty()) {
			throw new IllegalArgumentException("Config file name must not be blank.");
		}
		if (!normalized.endsWith(".json")) {
			normalized = normalized + ".json";
		}
		return directory.resolve(normalized);
	}

	private static boolean readBoolean(JsonObject root, String key, boolean fallback) {
		if (root == null || key == null || key.isBlank()) {
			return fallback;
		}
		JsonElement element = root.get(key);
		if (element == null || !element.isJsonPrimitive() || !element.getAsJsonPrimitive().isBoolean()) {
			return fallback;
		}
		try {
			return element.getAsBoolean();
		} catch (RuntimeException exception) {
			return fallback;
		}
	}

	private record KeptStack(int slot, ItemStack stack) {
	}
}


