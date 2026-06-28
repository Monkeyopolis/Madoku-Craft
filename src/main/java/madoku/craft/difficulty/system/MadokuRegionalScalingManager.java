package madoku.craft.difficulty.system;

import net.minecraft.resources.Identifier;
import net.minecraft.world.entity.EntityType;

import java.util.LinkedHashSet;
import java.util.List;
import java.util.Locale;
import java.util.Set;

public final class MadokuRegionalScalingManager {
	private MadokuRegionalScalingManager() {
	}

	public static List<String> resolveMobScalingFileKeys(EntityType<?> type) {
		if (type == null) {
			return List.of();
		}
		Identifier entityId = EntityType.getKey(type);
		if (entityId == null) {
			return List.of();
		}
		return resolveMobScalingFileKeys(entityId);
	}

	public static List<String> resolveMobScalingFileKeys(Identifier entityId) {
		if (entityId == null) {
			return List.of();
		}
		String namespace = normalizeFileKey(entityId.getNamespace());
		String pathRaw = normalizeFileKey(entityId.getPath());
		if (pathRaw.isBlank()) {
			return List.of();
		}
		String pathHyphen = pathRaw.replace('_', '-');
		Set<String> keys = new LinkedHashSet<>();
		keys.add(pathHyphen);
		keys.add(pathRaw);
		if (!"minecraft".equals(namespace) && !namespace.isBlank()) {
			keys.add(namespace + "-" + pathHyphen);
			keys.add(namespace + "-" + pathRaw);
		}
		return List.copyOf(keys);
	}

	private static String normalizeFileKey(String rawValue) {
		return rawValue == null ? "" : rawValue.trim().toLowerCase(Locale.ROOT);
	}
}

