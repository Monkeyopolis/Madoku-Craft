package madoku.craft.items;

import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.google.gson.JsonPrimitive;
import madoku.craft.api.json.JSONFormatManager;

public final class StacksConfigManager {
	public static final int DEFAULT_STACK_LIMIT = 128;
	public static final long MAX_STACK_CAP = 999_000_000L;
	public static final int MAX_STACK_RUNTIME_CAP = Integer.MAX_VALUE;
	public boolean enabled = true;
	public int customStackAmount = DEFAULT_STACK_LIMIT;

	public void resetToDefaults() {
		 enabled = true;
		customStackAmount = DEFAULT_STACK_LIMIT;
	}

	public boolean update(JsonObject root) {
		boolean changed = false;
		JsonObject customStacks = object(root, "custom-item-stacks");
		enabled = readBoolean(customStacks, "enabled", enabled);
		customStackAmount = clampStackAmount(readLong(customStacks, "value", customStackAmount));
		changed |= setBoolean(customStacks, "enabled", enabled);
		changed |= setInteger(customStacks, "value", customStackAmount);
		root.add("custom-item-stacks", customStacks);
		root.remove("item-stacks");
		return changed;
	}

	public static JsonObject buildDefaults() {
		return JSONFormatManager.object()
			.object("custom-item-stacks", group -> {
				group.put("enabled", true);
				group.put("value", DEFAULT_STACK_LIMIT);
			})
			.build();
	}

	private static JsonObject object(JsonObject source, String key) {
		JsonElement element = source == null ? null : source.get(key);
		return element != null && element.isJsonObject() ? element.getAsJsonObject() : new JsonObject();
	}

	private static int clampStackAmount(long rawValue) {
		if (rawValue < 1) {
			return 1;
		}
		long clampedByConfigCap = Math.min(rawValue, MAX_STACK_CAP);
		return (int) Math.min(clampedByConfigCap, (long) MAX_STACK_RUNTIME_CAP);
	}

	private static boolean readBoolean(JsonObject root, String key, boolean fallback) {
		JsonElement element = root.get(key);
		if (element instanceof JsonPrimitive primitive && primitive.isBoolean()) {
			return primitive.getAsBoolean();
		}
		return fallback;
	}

	private static long readLong(JsonObject root, String key, long fallback) {
		JsonElement element = root.get(key);
		if (element instanceof JsonPrimitive primitive && primitive.isNumber()) {
			return primitive.getAsLong();
		}
		return fallback;
	}

	private static boolean setBoolean(JsonObject root, String key, boolean value) {
		JsonElement element = root.get(key);
		if (element instanceof JsonPrimitive primitive && primitive.isBoolean()) {
			if (primitive.getAsBoolean() == value) {
				return false;
			}
		}
		root.addProperty(key, value);
		return true;
	}

	private static boolean setInteger(JsonObject root, String key, int value) {
		JsonElement element = root.get(key);
		if (element instanceof JsonPrimitive primitive && primitive.isNumber()) {
			if (primitive.getAsInt() == value) {
				return false;
			}
		}
		root.addProperty(key, value);
		return true;
	}

}

