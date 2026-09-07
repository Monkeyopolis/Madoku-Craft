package madoku.craft.java.ecosystem;

/** API provider for the shared Vanilla random-position wake-up source. */
public interface EcosystemRandomPositionProvider {
	default void initialize() { }
	default void reset() { }
	default void registerListener(EcosystemRandomPositionListener listener) { }
	default void unregisterListener(EcosystemRandomPositionListener listener) { }
	default void dispatch(EcosystemRandomPositionEvent event) { }
}
