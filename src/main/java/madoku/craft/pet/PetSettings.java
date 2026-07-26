package madoku.craft.pet;

import com.google.gson.JsonObject;

final class PetSettings {
	final boolean enabled;
	final boolean entitiesEnabled;
	final long schedulerTickInterval;
	final int petRarityCommonChanceWeight;
	final int petRarityRareChanceWeight;
	final int petRarityEpicChanceWeight;
	final int petRarityMythicChanceWeight;

	PetSettings(
		boolean enabled,
		boolean entitiesEnabled,
		long schedulerTickInterval,
		int petRarityCommonChanceWeight,
		int petRarityRareChanceWeight,
		int petRarityEpicChanceWeight,
		int petRarityMythicChanceWeight
	) {
		this.enabled = enabled;
		this.entitiesEnabled = entitiesEnabled;
		this.schedulerTickInterval = schedulerTickInterval;
		this.petRarityCommonChanceWeight = petRarityCommonChanceWeight;
		this.petRarityRareChanceWeight = petRarityRareChanceWeight;
		this.petRarityEpicChanceWeight = petRarityEpicChanceWeight;
		this.petRarityMythicChanceWeight = petRarityMythicChanceWeight;
	}

	static PetSettings defaults() {
		return new PetSettings(
			true,
			true,
			5L,
			67,
			24,
			8,
			1
		);
	}

	static PetSettings fromJson(JsonObject source) {
		PetSettings defaults = defaults();
		boolean enabled = MadokuPetManager.getBoolean(source, "enabled", defaults.enabled);
		boolean entitiesEnabled = MadokuPetManager.getBoolean(source, "pet-entity", defaults.entitiesEnabled);
		int petRarityCommonChanceWeight = (int) clampLong(
			MadokuPetManager.getLong(source, "pet-rarity-common", defaults.petRarityCommonChanceWeight),
			0,
			100000
		);
		int petRarityRareChanceWeight = (int) clampLong(
			MadokuPetManager.getLong(source, "pet-rarity-rare", defaults.petRarityRareChanceWeight),
			0,
			100000
		);
		int petRarityEpicChanceWeight = (int) clampLong(
			MadokuPetManager.getLong(source, "pet-rarity-epic", defaults.petRarityEpicChanceWeight),
			0,
			100000
		);
		int petRarityMythicChanceWeight = (int) clampLong(
			MadokuPetManager.getLong(source, "pet-rarity-mythic", defaults.petRarityMythicChanceWeight),
			0,
			100000
		);
		long schedulerTickInterval = clampLong(
			MadokuPetManager.getLong(source, "scheduler-tick-interval", defaults.schedulerTickInterval),
			1L,
			20L
		);
		return new PetSettings(
			enabled,
			entitiesEnabled,
			schedulerTickInterval,
			petRarityCommonChanceWeight,
			petRarityRareChanceWeight,
			petRarityEpicChanceWeight,
			petRarityMythicChanceWeight
		);
	}

	JsonObject toConfigJson() {
		return madoku.craft.api.json.JSONFormatManager.object()
			.put("enabled", enabled)
			.put("pet-entity", entitiesEnabled)
			.put("scheduler-tick-interval", schedulerTickInterval)
			.put("pet-rarity-common", petRarityCommonChanceWeight)
			.put("pet-rarity-rare", petRarityRareChanceWeight)
			.put("pet-rarity-epic", petRarityEpicChanceWeight)
			.put("pet-rarity-mythic", petRarityMythicChanceWeight)
			.build();
	}

	static long clampLong(long value, long min, long max) {
		return Math.max(min, Math.min(max, value));
	}

	static double clampDouble(double value, double min, double max) {
		return Math.max(min, Math.min(max, value));
	}
}

