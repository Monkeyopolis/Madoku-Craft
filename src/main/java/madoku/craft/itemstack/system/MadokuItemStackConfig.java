package madoku.craft.itemstack.system;

import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.google.gson.JsonPrimitive;
import madoku.craft.config.JsonFormatBuilder;

public final class MadokuItemStackConfig {
	public static final int DEFAULT_STACK_LIMIT = 128;
	public static final long MAX_STACK_CAP = 999_000_000L;
	public static final int MAX_STACK_RUNTIME_CAP = Integer.MAX_VALUE;
	public static final int DEFAULT_DEATH_DROP_PERCENT = 50;

	public boolean enabled = true;
	public int customStackAmount = DEFAULT_STACK_LIMIT;
	public boolean deathDropEnabled = true;
	public int deathDropStackPercent = DEFAULT_DEATH_DROP_PERCENT;

	public void resetToDefaults() {
		enabled = true;
		customStackAmount = DEFAULT_STACK_LIMIT;
		deathDropEnabled = true;
		deathDropStackPercent = DEFAULT_DEATH_DROP_PERCENT;
	}

	public boolean updateItemStack(JsonObject root) {
		boolean changed = false;
		customStackAmount = clampStackAmount(readLong(root, "customStackAmount", customStackAmount));
		deathDropEnabled = readBoolean(root, "deathDropEnabled", deathDropEnabled);
		deathDropStackPercent = clampPercent(readInteger(root, "deathDropStackPercent", deathDropStackPercent));
		changed |= setInteger(root, "customStackAmount", customStackAmount);
		changed |= setBoolean(root, "deathDropEnabled", deathDropEnabled);
		changed |= setInteger(root, "deathDropStackPercent", deathDropStackPercent);
		return changed;
	}

	public static JsonObject buildItemStackDefaults() {
		return JsonFormatBuilder.object()
			.put("customStackAmount", DEFAULT_STACK_LIMIT)
			.put("deathDropEnabled", true)
			.put("deathDropStackPercent", DEFAULT_DEATH_DROP_PERCENT)
			.build();
	}

	private static int clampStackAmount(long rawValue) {
		if (rawValue < 1) {
			return 1;
		}
		long clampedByConfigCap = Math.min(rawValue, MAX_STACK_CAP);
		return (int) Math.min(clampedByConfigCap, (long) MAX_STACK_RUNTIME_CAP);
	}

	private static int clampPercent(int rawValue) {
		if (rawValue < 0) {
			return 0;
		}
		return Math.min(rawValue, 100);
	}

	private static boolean readBoolean(JsonObject root, String key, boolean fallback) {
		JsonElement element = root.get(key);
		if (element instanceof JsonPrimitive primitive && primitive.isBoolean()) {
			return primitive.getAsBoolean();
		}
		return fallback;
	}

	private static int readInteger(JsonObject root, String key, int fallback) {
		JsonElement element = root.get(key);
		if (element instanceof JsonPrimitive primitive && primitive.isNumber()) {
			return primitive.getAsInt();
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

