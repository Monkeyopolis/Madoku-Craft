package madoku.craft.java.levels;

/** Provider contract for Madoku Levels configuration. */
public interface LevelsConfigProvider {
	default void initialize() { }
	default void reload() { }
	default void reset() { }
	default LevelsConfigManager.Settings settings() { return LevelsConfigManager.Settings.defaults(); }
	default boolean isEnabled() { return false; }
	default LevelsConfigManager.PlayerSettings player() { return new LevelsConfigManager.PlayerSettings(1, 1.0d, 1.0d); }
	default LevelsConfigManager.StatSettings stat(LevelStat stat) { return new LevelsConfigManager.StatSettings(1, LevelsConfigManager.IncrementType.FLAT, 0.0d); }
}
