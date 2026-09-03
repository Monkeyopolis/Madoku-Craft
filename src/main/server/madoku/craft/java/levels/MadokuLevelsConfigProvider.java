package madoku.craft.java.levels;

/** Built-in provider for Madoku Levels configuration. */
public final class MadokuLevelsConfigProvider implements LevelsConfigProvider {
	@Override public void initialize() { LevelsConfigManager.initialize(); }
	@Override public void reload() { LevelsConfigManager.reload(); }
	@Override public void reset() { LevelsConfigManager.reset(); }
	@Override public LevelsConfigManager.Settings settings() { return LevelsConfigManager.settings(); }
	@Override public boolean isEnabled() { return LevelsConfigManager.isEnabled(); }
	@Override public LevelsConfigManager.PlayerSettings player() { return LevelsConfigManager.player(); }
	@Override public LevelsConfigManager.StatSettings stat(LevelStat stat) { return LevelsConfigManager.stat(stat); }
}
