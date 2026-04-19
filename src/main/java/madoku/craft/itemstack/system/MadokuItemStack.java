package madoku.craft.itemstack.system;

import com.google.gson.JsonArray;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.mojang.serialization.DataResult;
import com.mojang.serialization.JsonOps;
import madoku.craft.clock.MadokuTicks;
import madoku.craft.config.StaticJsonSystem;
import madoku.craft.data.MadokuData;
import madoku.craft.debug.MadokuDebug;
import net.fabricmc.fabric.api.entity.event.v1.ServerPlayerEvents;
import net.minecraft.resources.RegistryOps;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.item.ItemEntity;
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
	private static final String DATA_ENTRY_COUNT_KEY = "count";
	private static final long AUTOSAVE_INTERVAL_TICKS = 60L * 20L;

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
		MadokuData.createWorldData(server, DATA_FOLDER_NAME, DATA_FILE_NAME, createDefaultData());
		JsonObject data = MadokuData.loadWorldData(server, DATA_FOLDER_NAME, DATA_FILE_NAME);
		applyPersistedData(server, data);
		lastAutosaveBucket = Math.floorDiv(MadokuTicks.getGameplayTicks(), AUTOSAVE_INTERVAL_TICKS);
	}

	public static void autosavePersistedData(MinecraftServer server) {
		if (server == null) {
			return;
		}
		long bucket = Math.floorDiv(MadokuTicks.getGameplayTicks(), AUTOSAVE_INTERVAL_TICKS);
		if (bucket != lastAutosaveBucket) {
			lastAutosaveBucket = bucket;
			savePersistedData(server);
		}
	}

	public static void savePersistedData(MinecraftServer server) {
		if (server == null) {
			return;
		}
		MadokuData.saveWorldData(server, DATA_FOLDER_NAME, DATA_FILE_NAME, toPersistedData(server));
	}

	public static boolean isEnabled() {
		return configuration.enableFeature;
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
		return MadokuItemStackConfig.MAX_STACK_CAP;
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
		emitDeathDropHandled(player, dropCount, keptStacks.size());
		return true;
	}

	private static JsonObject createDefaultData() {
		JsonObject root = new JsonObject();
		root.add(DATA_KEPT_STACKS_KEY, new JsonObject());
		return root;
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
				ItemStack stack = decodeStoredStack(ops, entryObject, stackElement);
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
		JsonObject root = new JsonObject();
		JsonObject keptRoot = new JsonObject();
		if (server == null) {
			root.add(DATA_KEPT_STACKS_KEY, keptRoot);
			return root;
		}

		RegistryOps<JsonElement> ops = RegistryOps.create(JsonOps.INSTANCE, server.registryAccess());
		for (Map.Entry<UUID, List<KeptStack>> entry : keptStacksByPlayer.entrySet()) {
			UUID playerId = entry.getKey();
			List<KeptStack> keptStacks = entry.getValue();
			if (playerId == null || keptStacks == null || keptStacks.isEmpty()) {
				continue;
			}

			JsonArray encodedStacks = new JsonArray();
			for (KeptStack keptStack : keptStacks) {
				if (keptStack == null || keptStack.stack() == null || keptStack.stack().isEmpty()) {
					continue;
				}
				JsonElement stackElement = encodeStoredStack(ops, keptStack.stack());
				if (stackElement == null) {
					continue;
				}

				JsonObject encodedEntry = new JsonObject();
				encodedEntry.addProperty(DATA_ENTRY_SLOT_KEY, keptStack.slot());
				encodedEntry.addProperty(DATA_ENTRY_COUNT_KEY, keptStack.stack().getCount());
				encodedEntry.add(DATA_ENTRY_STACK_KEY, stackElement);
				encodedStacks.add(encodedEntry);
			}

			if (encodedStacks.size() > 0) {
				keptRoot.add(playerId.toString(), encodedStacks);
			}
		}

		root.add(DATA_KEPT_STACKS_KEY, keptRoot);
		return root;
	}

	private static void loadStaticConfig() {
		JsonObject defaults = MadokuItemStackConfig.buildItemStackDefaults();
		try {
			Path directory = StaticJsonSystem.getOrCreateGlobalSystemDirectory(ITEMSTACK_CONFIG_FOLDER_NAME);
			Path configFile = resolveJsonFile(directory, ITEMSTACK_CONFIG_FILE_NAME);
			JsonObject root = StaticJsonSystem.ensureManagedFile(configFile, defaults);
			boolean changed = configuration.updateItemStack(root);
			if (changed) {
				StaticJsonSystem.writeManagedFile(configFile, root, defaults);
			}
			emitConfigLoaded();
		} catch (IOException | RuntimeException exception) {
			configuration.resetToDefaults();
			LOGGER.error("Failed to load MadokuItemStack config; using defaults.", exception);
		}
	}

	private static void emitConfigLoaded() {
		String metricId = "itemstack.config_loaded";
		if (!MadokuDebug.shouldEmit(MadokuDebug.Domain.ITEM, metricId)) {
			return;
		}

		MadokuDebug.event(metricId, MadokuDebug.Domain.ITEM)
			.side(MadokuDebug.Side.SERVER)
			.subject("itemstack:global")
			.field("enabled", configuration.enableFeature)
			.field("stack_limit", configuration.customStackAmount)
			.field("death_drop_enabled", configuration.deathDropEnabled)
			.field("death_drop_percent", configuration.deathDropStackPercent)
			.log();
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
		int restored = 0;
		int inserted = 0;
		int dropped = 0;

		for (KeptStack entry : keptStacks) {
			ItemStack stack = entry.stack();
			if (stack.isEmpty()) {
				continue;
			}

			int slot = entry.slot();
			if (slot >= 0 && slot < inventory.getContainerSize() && inventory.getItem(slot).isEmpty()) {
				inventory.setItem(slot, stack);
				restored++;
				continue;
			}

			if (inventory.add(stack)) {
				inserted++;
			} else {
				ItemEntity droppedEntity = newPlayer.drop(stack, true, false);
				if (droppedEntity != null) {
					dropped++;
				}
			}
		}

		inventory.setChanged();
		savePersistedData(newPlayer.level().getServer());
		emitRespawnRestore(newPlayer, restored, inserted, dropped);
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

	private static void emitDeathDropHandled(ServerPlayer player, int droppedStacks, int keptStacks) {
		String metricId = "itemstack.death_drop_handled";
		if (!MadokuDebug.shouldEmit(MadokuDebug.Domain.ITEM, metricId)) {
			return;
		}
		MadokuDebug.event(metricId, MadokuDebug.Domain.ITEM)
			.side(MadokuDebug.Side.SERVER)
			.subject("player:" + player.getUUID())
			.world(player.level().dimension().toString())
			.field("dropped_stacks", droppedStacks)
			.field("kept_stacks", keptStacks)
			.field("drop_percent", configuration.deathDropStackPercent)
			.log();
	}

	private static void emitRespawnRestore(ServerPlayer player, int restored, int inserted, int dropped) {
		String metricId = "itemstack.death_restore";
		if (!MadokuDebug.shouldEmit(MadokuDebug.Domain.ITEM, metricId)) {
			return;
		}
		MadokuDebug.event(metricId, MadokuDebug.Domain.ITEM)
			.side(MadokuDebug.Side.SERVER)
			.subject("player:" + player.getUUID())
			.world(player.level().dimension().toString())
			.field("restored_to_slot", restored)
			.field("restored_by_insert", inserted)
			.field("forced_drop", dropped)
			.log();
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

	private static ItemStack decodeStoredStack(RegistryOps<JsonElement> ops, JsonObject entryObject, JsonElement stackElement) {
		DataResult<ItemStack> decoded = ItemStack.CODEC.parse(ops, stackElement);
		ItemStack stack = decoded.result().orElse(ItemStack.EMPTY);
		if (stack.isEmpty()) {
			return ItemStack.EMPTY;
		}

		int storedCount = stack.getCount();
		JsonElement countElement = entryObject.get(DATA_ENTRY_COUNT_KEY);
		if (countElement != null && countElement.isJsonPrimitive() && countElement.getAsJsonPrimitive().isNumber()) {
			storedCount = countElement.getAsInt();
		}

		stack.setCount(Math.max(1, storedCount));
		return stack;
	}

	private static JsonElement encodeStoredStack(RegistryOps<JsonElement> ops, ItemStack stack) {
		if (stack == null || stack.isEmpty()) {
			return null;
		}

		int safeCount = Math.max(1, Math.min(stack.getCount(), 99));
		DataResult<JsonElement> encoded = ItemStack.CODEC.encodeStart(ops, stack.copyWithCount(safeCount));
		return encoded.result().orElse(null);
	}

	private record KeptStack(int slot, ItemStack stack) {
	}
}


