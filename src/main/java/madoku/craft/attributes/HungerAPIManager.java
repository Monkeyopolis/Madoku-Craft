package madoku.craft.attributes;

import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.effect.MobEffect;
import net.minecraft.world.entity.LivingEntity;

/** Public contract for the hunger attribute subsystem. */
public final class HungerAPIManager {
	private HungerAPIManager() {
	}

	public static void initialize() { MadokuHungerManager.initialize(); }
	public static void reset() { MadokuHungerManager.reset(); }
	public static void loadPersistedData(MinecraftServer server) { MadokuHungerManager.loadPersistedData(server); }
	public static void autosavePersistedData(MinecraftServer server) { MadokuHungerManager.autosavePersistedData(server); }
	public static void savePersistedData(MinecraftServer server) { MadokuHungerManager.savePersistedData(server); }
	public static void onServerStarted(MinecraftServer server) { MadokuHungerManager.onServerStarted(server); }
	public static void handlePlayerTeleport(ServerPlayer player) { MadokuHungerManager.handlePlayerTeleport(player); }
	public static boolean isEnabled() { return MadokuHungerManager.isEnabled(); }
	public static boolean isSaturationEnabled() { return MadokuHungerManager.isSaturationEnabled(); }
	public static boolean isHungerEffectEnabled() { return MadokuHungerManager.isHungerEffectEnabled(); }
	public static int getCurrentHungerPoints(ServerPlayer player) { return MadokuHungerManager.getCurrentHungerPoints(player); }
	public static int getEffectiveHungerPoints(ServerPlayer player) { return MadokuHungerManager.getEffectiveHungerPoints(player); }
	public static int getMaximumHungerPoints(ServerPlayer player) { return MadokuHungerManager.getMaximumHungerPoints(player); }
	public static int getConfiguredMaximumHungerPoints() { return MadokuHungerManager.getConfiguredMaximumHungerPoints(); }
	public static void applyClientSynchronizedSettings(boolean enabled, int maximum) {
		MadokuHungerManager.applyClientSynchronizedSettings(enabled, maximum);
	}
	public static void resetClientSynchronizedSettings() { MadokuHungerManager.resetClientSynchronizedSettings(); }
	public static boolean shouldApplyStarvationDamage(ServerPlayer player) { return MadokuHungerManager.shouldApplyStarvationDamage(player); }
	public static void handleMaximumHungerChanged(ServerPlayer player) { MadokuHungerManager.handleMaximumHungerChanged(player); }
	public static boolean shouldOverrideVanillaEffect(LivingEntity entity, MobEffect effect) {
		return MadokuHungerManager.shouldOverrideVanillaEffect(entity, effect);
	}
	public static boolean canConsumeFood(ServerPlayer player, boolean ignoreHunger) {
		return MadokuHungerManager.canConsumeFood(player, ignoreHunger);
	}
	public static void onFoodConsumed(ServerPlayer player, int nutrition) {
		MadokuHungerManager.onFoodConsumed(player, nutrition);
	}
	public static int drainHunger(ServerPlayer player, int amount) {
		return MadokuHungerManager.drainHunger(player, amount);
	}
}
