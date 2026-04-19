package madoku.craft.levels;

import com.mojang.blaze3d.platform.InputConstants;
import madoku.craft.network.MadokuLevelUpPayload;
import madoku.craft.network.MadokuLevelsPayload;
import net.fabricmc.fabric.api.client.event.lifecycle.v1.ClientTickEvents;
import net.fabricmc.fabric.api.client.networking.v1.ClientPlayConnectionEvents;
import net.fabricmc.fabric.api.client.networking.v1.ClientPlayNetworking;
import net.minecraft.client.Minecraft;
import org.lwjgl.glfw.GLFW;

public final class MadokuLevelsClient {
	private static boolean initialized = false;
	private static boolean wasOpenKeyDown = false;

	private MadokuLevelsClient() {
	}

	public static void initialize() {
		if (initialized) {
			return;
		}

		ClientPlayNetworking.registerGlobalReceiver(MadokuLevelsPayload.TYPE, (payload, context) ->
			MadokuLevelsClientState.applyPayload(payload)
		);
		ClientPlayConnectionEvents.DISCONNECT.register((handler, client) -> MadokuLevelsClientState.clear());
		ClientTickEvents.END_CLIENT_TICK.register(MadokuLevelsClient::handleClientTick);
		initialized = true;
	}

	private static void handleClientTick(Minecraft client) {
		if (client == null || client.player == null) {
			wasOpenKeyDown = false;
			return;
		}

		boolean openKeyDown = InputConstants.isKeyDown(client.getWindow().getWindow(), GLFW.GLFW_KEY_K);
		if (openKeyDown && !wasOpenKeyDown && client.screen == null) {
			client.setScreen(new MadokuLevelsScreen());
		}
		wasOpenKeyDown = openKeyDown;
	}

	public static void requestStatUpgrade(MadokuLevelStat stat) {
		if (stat == null) {
			return;
		}

		ClientPlayNetworking.send(new MadokuLevelUpPayload(stat.id()));
	}
}
