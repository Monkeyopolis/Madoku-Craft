package madoku.craft.java.hud;

import net.minecraft.client.Minecraft;

/** Orchestrates the Madoku HUD subsystems and owns shared HUD helpers. */
public final class MadokuHudManager {
	private static volatile boolean initialized;

	private MadokuHudManager() {
	}

	public static void initialize() {
		if (initialized) return;
		HudConfigManager.initialize();
		HudPayloadManager.initialize();
		HudAPIManager.initialize();
		initialized = true;
	}

	public static void reset() {
		HudAPIManager.reset();
		HudPayloadManager.reset();
		HudConfigManager.reset();
		initialized = false;
	}

	static boolean hasRenderablePlayer(Minecraft client) {
		return client != null && client.level != null && client.player != null
			&& !client.gui.hud.isHidden() && !client.player.isSpectator();
	}

	public static int clamp(int value, int min, int max) {
		return Math.max(min, Math.min(max, value));
	}
}
