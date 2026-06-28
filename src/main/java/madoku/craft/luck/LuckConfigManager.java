package madoku.craft.luck;

import com.google.gson.JsonObject;
import madoku.craft.attributes.AttributesConfigManager;
import madoku.craft.config.JsonFormatBuilder;
import madoku.craft.config.JsonStaticSystem;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.IOException;
import java.nio.file.Path;

public final class LuckConfigManager {
	private static final Logger LOGGER = LoggerFactory.getLogger(LuckConfigManager.class);

	private static final String LUCK_CONFIG_DIRECTORY_NAME = "madoku-luck";
	private static final String LUCK_CONFIG_FILE_NAME = "madoku-luck";
	private static final boolean DEFAULT_ENABLED = true;
	private static final double DEFAULT_BASE_LUCK = 0.05d;
	private static final double DEFAULT_DROP_MULTIPLIER = 1.0d;
	private static final double DEFAULT_MOB_DROP_MULTIPLIER = 0.5d;
	private static final double DEFAULT_CREEPER_GRIEF_REDUCTION_MULTIPLIER = 0.5d;
	private static final double DEFAULT_RANGED_ACCURACY_REDUCTION_MULTIPLIER = 0.5d;
	private static final float DEFAULT_PLAYER_CRIT_DAMAGE_MULTIPLIER = 1.5f;

	private LuckConfigManager() {
	}

	static Settings loadSettings(boolean systemEnabled) {
		JsonObject defaults = Settings.defaults().toConfigJson();
		Settings fallback = Settings.defaults();

		try {
			Path configFile = AttributesConfigManager.prepareSystemConfigFile(LUCK_CONFIG_DIRECTORY_NAME, LUCK_CONFIG_FILE_NAME);
			JsonObject normalized = JsonStaticSystem.ensureManagedFile(configFile, defaults);
			Settings loaded = Settings.fromJson(normalized);
			JsonStaticSystem.writeManagedFile(configFile, loaded.toConfigJson(), defaults);
			return loaded.withEnabled(systemEnabled);
		} catch (IOException | RuntimeException exception) {
			LOGGER.error("Failed to load MadokuLuckManager config; using defaults.", exception);
			return fallback.withEnabled(systemEnabled);
		}
	}

	static final class Settings {
		final boolean enabled;
		final double baseLuck;
		final double dropMultiplier;
		final double mobDropMultiplier;
		final double creeperGriefReductionMultiplier;
		final double rangedAccuracyReductionMultiplier;
		final float playerCritDamageMultiplier;

		private Settings(
			boolean enabled,
			double baseLuck,
			double dropMultiplier,
			double mobDropMultiplier,
			double creeperGriefReductionMultiplier,
			double rangedAccuracyReductionMultiplier,
			float playerCritDamageMultiplier
		) {
			this.enabled = enabled;
			this.baseLuck = baseLuck;
			this.dropMultiplier = dropMultiplier;
			this.mobDropMultiplier = mobDropMultiplier;
			this.creeperGriefReductionMultiplier = creeperGriefReductionMultiplier;
			this.rangedAccuracyReductionMultiplier = rangedAccuracyReductionMultiplier;
			this.playerCritDamageMultiplier = playerCritDamageMultiplier;
		}

		static Settings defaults() {
			return new Settings(
				DEFAULT_ENABLED,
				DEFAULT_BASE_LUCK,
				DEFAULT_DROP_MULTIPLIER,
				DEFAULT_MOB_DROP_MULTIPLIER,
				DEFAULT_CREEPER_GRIEF_REDUCTION_MULTIPLIER,
				DEFAULT_RANGED_ACCURACY_REDUCTION_MULTIPLIER,
				DEFAULT_PLAYER_CRIT_DAMAGE_MULTIPLIER
			);
		}

		static Settings fromJson(JsonObject source) {
			Settings defaults = defaults();
			double baseLuck = clampDouble(getDouble(source, "base-luck", defaults.baseLuck), 0.0d, 1024.0d);
			double dropMultiplier = clampDouble(getDouble(source, "drop-multiplier", defaults.dropMultiplier), 0.0d, 1024.0d);
			double mobDropMultiplier = clampDouble(
				getDouble(source, "mob-drop-multiplier", defaults.mobDropMultiplier),
				0.0d,
				1024.0d
			);
			double creeperGriefReductionMultiplier = clampDouble(
				getDouble(source, "creeper-grief-reduction-multiplier", defaults.creeperGriefReductionMultiplier),
				0.0d,
				1.0d
			);
			double rangedAccuracyReductionMultiplier = clampDouble(
				getDouble(source, "ranged-accuracy-reduction-multiplier", defaults.rangedAccuracyReductionMultiplier),
				0.0d,
				1.0d
			);
			float playerCritDamageMultiplier = (float) clampDouble(
				getDouble(source, "player-crit-damage-multiplier", defaults.playerCritDamageMultiplier),
				0.0d,
				1024.0d
			);
			return new Settings(
				getBoolean(source, "enabled", defaults.enabled),
				baseLuck,
				dropMultiplier,
				mobDropMultiplier,
				creeperGriefReductionMultiplier,
				rangedAccuracyReductionMultiplier,
				playerCritDamageMultiplier
			);
		}

		JsonObject toConfigJson() {
			return JsonFormatBuilder.object()
				.put("enabled", enabled)
				.put("base-luck", baseLuck)
				.put("drop-multiplier", dropMultiplier)
				.put("mob-drop-multiplier", mobDropMultiplier)
				.put("creeper-grief-reduction-multiplier", creeperGriefReductionMultiplier)
				.put("ranged-accuracy-reduction-multiplier", rangedAccuracyReductionMultiplier)
				.put("player-crit-damage-multiplier", playerCritDamageMultiplier)
				.build();
		}

		Settings withEnabled(boolean systemEnabled) {
			return new Settings(
				systemEnabled && enabled,
				baseLuck,
				dropMultiplier,
				mobDropMultiplier,
				creeperGriefReductionMultiplier,
				rangedAccuracyReductionMultiplier,
				playerCritDamageMultiplier
			);
		}

		private static boolean getBoolean(JsonObject object, String key, boolean fallback) {
			if (object == null || key == null || key.isBlank()) {
				return fallback;
			}
			var element = object.get(key);
			if (element == null || !element.isJsonPrimitive() || !element.getAsJsonPrimitive().isBoolean()) {
				return fallback;
			}
			try {
				return element.getAsBoolean();
			} catch (RuntimeException exception) {
				return fallback;
			}
		}

		private static double getDouble(JsonObject object, String key, double fallback) {
			if (object == null || key == null || key.isBlank()) {
				return fallback;
			}
			var element = object.get(key);
			if (element == null || !element.isJsonPrimitive() || !element.getAsJsonPrimitive().isNumber()) {
				return fallback;
			}
			try {
				return element.getAsDouble();
			} catch (RuntimeException exception) {
				return fallback;
			}
		}

		private static double clampDouble(double value, double min, double max) {
			return Math.max(min, Math.min(max, value));
		}
	}
}

