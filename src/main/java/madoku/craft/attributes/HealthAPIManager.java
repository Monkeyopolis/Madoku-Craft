package madoku.craft.attributes;

import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.effect.MobEffect;
import net.minecraft.world.entity.LivingEntity;

/** Public contract for the health attribute subsystem. */
public final class HealthAPIManager {
	private HealthAPIManager() {
	}

	public static void initialize() { MadokuHealthManager.initialize(); }
	public static boolean isEnabled() { return MadokuHealthManager.isEnabled(); }
	public static void reset() { MadokuHealthManager.reset(); }
	public static void loadPersistedData(MinecraftServer server) { MadokuHealthManager.loadPersistedData(server); }
	public static void autosavePersistedData(MinecraftServer server) { MadokuHealthManager.autosavePersistedData(server); }
	public static void savePersistedData(MinecraftServer server) { MadokuHealthManager.savePersistedData(server); }
	public static void onServerStarted(MinecraftServer server) { MadokuHealthManager.onServerStarted(server); }
	public static void handlePlayerEffectsChanged(ServerPlayer player) { MadokuHealthManager.handlePlayerEffectsChanged(player); }
	public static boolean shouldOverrideVanillaEffect(LivingEntity entity, MobEffect effect) {
		return MadokuHealthManager.shouldOverrideVanillaEffect(entity, effect);
	}
	public static boolean shouldOverrideVanillaEffectAttributes(LivingEntity entity, MobEffect effect) {
		return MadokuHealthManager.shouldOverrideVanillaEffectAttributes(entity, effect);
	}
	public static void restoreJoinHealth(ServerPlayer player) { MadokuHealthManager.restoreJoinHealth(player); }
}
