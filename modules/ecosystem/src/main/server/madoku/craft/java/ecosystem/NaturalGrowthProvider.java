package madoku.craft.java.ecosystem;

/** Provider contract implemented by the module that owns natural growth. */
public interface NaturalGrowthProvider {
	default void initialize() { }
	default void reset() { }
	default NaturalGrowthConfigManager.Settings getSettings() { return NaturalGrowthConfigManager.defaults(); }
	default boolean isEnabled() { return false; }
	default boolean acceptsRandomPosition(EcosystemRandomPositionEvent event) { return false; }
	default void onRandomPosition(EcosystemRandomPositionEvent event) { }
}
