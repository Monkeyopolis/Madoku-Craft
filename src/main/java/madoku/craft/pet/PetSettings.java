package madoku.craft.pet;

import com.google.gson.JsonObject;

final class PetSettings {
	final boolean enabled;
	final boolean entitiesEnabled;
	final long schedulerTickInterval;

	PetSettings(
		boolean enabled,
		boolean entitiesEnabled,
		long schedulerTickInterval
	) {
		this.enabled = enabled;
		this.entitiesEnabled = entitiesEnabled;
		this.schedulerTickInterval = schedulerTickInterval;
	}

	static PetSettings defaults() {
		return new PetSettings(
			true,
			true,
			5L
		);
	}

	static PetSettings fromJson(JsonObject source) {
		PetSettings defaults = defaults();
		boolean enabled = PlayerEntitiesSystem.getBoolean(source, "enabled", defaults.enabled);
		boolean entitiesEnabled = PlayerEntitiesSystem.getBoolean(source, "entities-enabled", defaults.entitiesEnabled);
		long schedulerTickInterval = clampLong(
			PlayerEntitiesSystem.getLong(source, "scheduler-tick-interval", defaults.schedulerTickInterval),
			1L,
			20L
		);
		return new PetSettings(
			enabled,
			entitiesEnabled,
			schedulerTickInterval
		);
	}

	JsonObject toConfigJson() {
		JsonObject root = new JsonObject();
		root.addProperty("enabled", enabled);
		root.addProperty("entities-enabled", entitiesEnabled);
		root.addProperty("scheduler-tick-interval", schedulerTickInterval);
		return root;
	}

	static long clampLong(long value, long min, long max) {
		return Math.max(min, Math.min(max, value));
	}

	static double clampDouble(double value, double min, double max) {
		return Math.max(min, Math.min(max, value));
	}
}
