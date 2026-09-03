package madoku.craft.attributes;

import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.effect.MobEffect;
import net.minecraft.world.entity.LivingEntity;

/** Public contract for the hunger attribute subsystem. */
public final class HungerAPIManager {
	private static final HungerProvider UNAVAILABLE_PROVIDER = new HungerProvider() { };
	private static volatile HungerProvider provider = UNAVAILABLE_PROVIDER;

	private HungerAPIManager() {
	}

	public static void registerProvider(HungerProvider candidate) {
		if (candidate == null) throw new IllegalArgumentException("Hunger provider must not be null.");
		provider = candidate;
	}
	public static void unregisterProvider() { provider = UNAVAILABLE_PROVIDER; }
	public static void initialize() { provider.initialize(); }
	public static void reset() { provider.reset(); }
	public static void loadPersistedData(MinecraftServer server) { provider.loadPersistedData(server); }
	public static void autosavePersistedData(MinecraftServer server) { provider.autosavePersistedData(server); }
	public static void savePersistedData(MinecraftServer server) { provider.savePersistedData(server); }
	public static void onServerStarted(MinecraftServer server) { provider.onServerStarted(server); }
	public static void handlePlayerTeleport(ServerPlayer player) { provider.handlePlayerTeleport(player); }
	public static boolean isEnabled() { return provider.isEnabled(); }
	public static boolean isSaturationEnabled() { return provider.isSaturationEnabled(); }
	public static boolean isHungerEffectEnabled() { return provider.isHungerEffectEnabled(); }
	public static int getCurrentHungerPoints(ServerPlayer player) { return provider.getCurrentHungerPoints(player); }
	public static int getEffectiveHungerPoints(ServerPlayer player) { return provider.getEffectiveHungerPoints(player); }
	public static int getMaximumHungerPoints(ServerPlayer player) { return provider.getMaximumHungerPoints(player); }
	public static int getConfiguredMaximumHungerPoints() { return provider.getConfiguredMaximumHungerPoints(); }
	public static void applyClientSynchronizedSettings(boolean enabled, int maximum) {
		provider.applyClientSynchronizedSettings(enabled, maximum);
	}
	public static void resetClientSynchronizedSettings() { provider.resetClientSynchronizedSettings(); }
	public static boolean shouldApplyStarvationDamage(ServerPlayer player) { return provider.shouldApplyStarvationDamage(player); }
	public static void handleMaximumHungerChanged(ServerPlayer player) { provider.handleMaximumHungerChanged(player); }
	public static boolean shouldOverrideVanillaEffect(LivingEntity entity, MobEffect effect) {
		return provider.shouldOverrideVanillaEffect(entity, effect);
	}
	public static boolean canConsumeFood(ServerPlayer player, boolean ignoreHunger) {
		return provider.canConsumeFood(player, ignoreHunger);
	}
	public static void onFoodConsumed(ServerPlayer player, int nutrition) {
		provider.onFoodConsumed(player, nutrition);
	}
	public static int drainHunger(ServerPlayer player, int amount) {
		return provider.drainHunger(player, amount);
	}
}
