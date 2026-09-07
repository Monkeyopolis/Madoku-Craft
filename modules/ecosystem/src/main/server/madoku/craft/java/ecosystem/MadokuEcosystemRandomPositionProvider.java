package madoku.craft.java.ecosystem;

/** Built-in provider for the shared random-position source. */
public final class MadokuEcosystemRandomPositionProvider implements EcosystemRandomPositionProvider {
	@Override public void initialize() { EcosystemRandomPositionManager.initialize(); }
	@Override public void reset() { EcosystemRandomPositionManager.reset(); }
	@Override public void registerListener(EcosystemRandomPositionListener listener) { EcosystemRandomPositionManager.registerListener(listener); }
	@Override public void unregisterListener(EcosystemRandomPositionListener listener) { EcosystemRandomPositionManager.unregisterListener(listener); }
	@Override public void dispatch(EcosystemRandomPositionEvent event) { EcosystemRandomPositionManager.dispatch(event); }
}
