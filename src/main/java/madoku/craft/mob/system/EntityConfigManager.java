package madoku.craft.mob.system;

import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;

/** Configuration group for entity definitions. */
public final class EntityConfigManager {
	private EntityConfigManager() {
	}

	public static void initialize() {
	}

	public static JsonObject resolveVariant(JsonObject fileRoot, String variantKey) {
		if (fileRoot == null) return new JsonObject();
		JsonElement entityElement = fileRoot.get(MobConfigManager.FIELD_ENTITY);
		if (entityElement == null || !entityElement.isJsonObject()) return new JsonObject();
		JsonObject entity = entityElement.getAsJsonObject();
		JsonObject base = firstVariant(entity);
		JsonObject selected = resolveVariantPath(entity, variantKey);
		JsonObject resolved = merge(new JsonObject(), base);
		return merge(resolved, selected);
	}

	public static JsonObject resolveDefaultVariant(JsonObject fileRoot) {
		return resolveVariant(fileRoot, "");
	}

	public static boolean isWorldDifficultyScalingEnabled(JsonObject fileRoot) {
		JsonElement entityElement = fileRoot == null ? null : fileRoot.get(MobConfigManager.FIELD_ENTITY);
		if (entityElement == null || !entityElement.isJsonObject()) return true;
		JsonElement enabled = entityElement.getAsJsonObject().get(MobConfigManager.FIELD_WORLD_DIFFICULTY_SCALING);
		try {
			return enabled == null || enabled.getAsBoolean();
		} catch (RuntimeException exception) {
			return true;
		}
	}

	private static JsonObject resolveVariantPath(JsonObject entity, String variantKey) {
		if (entity == null || entity.entrySet().isEmpty()) return new JsonObject();
		List<String> path = splitPath(variantKey);
		JsonObject current = path.isEmpty() ? firstVariant(entity) : readObject(entity, path.get(0));
		if (current.entrySet().isEmpty()) current = firstVariant(entity);
		JsonObject resolved = merge(new JsonObject(), current);
		for (int index = 1; index < path.size(); index++) {
			JsonObject nested = readObject(current, path.get(index));
			if (nested.entrySet().isEmpty()) break;
			merge(resolved, nested);
			current = nested;
		}
		return resolved;
	}

	private static List<String> splitPath(String value) {
		List<String> segments = new ArrayList<>();
		if (value == null) return segments;
		for (String segment : value.split("[./]")) {
			if (!segment.isBlank()) segments.add(segment.trim().toLowerCase());
		}
		return segments;
	}

	private static JsonObject firstVariant(JsonObject entity) {
		for (Map.Entry<String, JsonElement> entry : entity.entrySet()) {
			if (isVariantKey(entry.getKey()) && entry.getValue().isJsonObject()) return entry.getValue().getAsJsonObject();
		}
		return new JsonObject();
	}

	private static boolean isVariantKey(String key) {
		return !MobConfigManager.FIELD_CUSTOM_MOB_DROPS.equals(key)
			&& !MobConfigManager.FIELD_WORLD_DIFFICULTY_SCALING.equals(key)
			&& !MobConfigManager.FIELD_REGIONAL_DIFFICULTY_SCALING_NEW.equals(key);
	}

	private static JsonObject readObject(JsonObject root, String key) {
		JsonElement value = root.get(key);
		return value != null && value.isJsonObject() ? value.getAsJsonObject() : new JsonObject();
	}

	private static JsonObject merge(JsonObject target, JsonObject source) {
		if (source == null) return target;
		for (Map.Entry<String, JsonElement> entry : source.entrySet()) {
			JsonElement current = target.get(entry.getKey());
			if (current != null && current.isJsonObject() && entry.getValue().isJsonObject()) {
				merge(current.getAsJsonObject(), entry.getValue().getAsJsonObject());
			} else {
				target.add(entry.getKey(), entry.getValue().deepCopy());
			}
		}
		return target;
	}
}
