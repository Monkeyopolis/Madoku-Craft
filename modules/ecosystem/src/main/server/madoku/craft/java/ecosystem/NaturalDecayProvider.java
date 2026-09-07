package madoku.craft.java.ecosystem;

/** Provider contract implemented by the module that owns natural decay. */
public interface NaturalDecayProvider {
	default void initialize() { }
	default void reset() { }
	default NaturalDecayConfigManager.Settings getSettings() { return NaturalDecayConfigManager.defaults(); }
	default boolean isEnabled() { return false; }
	default boolean acceptsRandomPosition(EcosystemRandomPositionEvent event) { return false; }
	default void onRandomPosition(EcosystemRandomPositionEvent event) { }
}
