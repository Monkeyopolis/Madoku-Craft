package madoku.craft.java.color;

public final class ClientColorContext {
	private static final ThreadLocal<Boolean> FORCED_COLOR_CONTEXT = new ThreadLocal<>();

	private ClientColorContext() {
	}

	public static Boolean forcedLeaves() {
		return FORCED_COLOR_CONTEXT.get();
	}

	public static void force(boolean leaves) {
		FORCED_COLOR_CONTEXT.set(leaves);
	}

	public static void clear() {
		FORCED_COLOR_CONTEXT.remove();
	}
}
