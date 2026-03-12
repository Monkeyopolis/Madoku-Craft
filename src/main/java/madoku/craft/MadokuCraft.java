package madoku.craft;

import net.fabricmc.api.ModInitializer;
import net.fabricmc.fabric.api.entity.event.v1.EntitySleepEvents;
import net.fabricmc.fabric.api.event.lifecycle.v1.ServerLifecycleEvents;
import net.fabricmc.fabric.api.event.lifecycle.v1.ServerTickEvents;

import madoku.craft.debug.MadokuDebug;
import madoku.craft.health.MadokuHealth;
import madoku.craft.hunger.MadokuHunger;
import madoku.craft.armor.MadokuArmor;
import madoku.craft.config.StaticJsonSystem;
import madoku.craft.clock.MadokuClock;
import madoku.craft.clock.MadokuTicks;
import madoku.craft.network.WorldTimeSync;
import madoku.craft.network.HungerHudSync;
import madoku.craft.scheduler.MadokuScheduler;
import madoku.craft.item.system.MadokuItem;
import madoku.craft.itemstack.system.MadokuItemStack;
import madoku.craft.rarity.MadokuRarity;
import madoku.craft.smelting.system.MadokuSmeltingManager;
import madoku.craft.time.MadokuSleep;
import madoku.craft.time.MadokuTime;
import madoku.craft.oxygen.MadokuOxygen;

public class MadokuCraft implements ModInitializer {
	public static final String MOD_ID = "madoku-craft";

	@Override
	public void onInitialize() {
		StaticJsonSystem.initialize();
		MadokuSmeltingManager.initialize();
		MadokuDebug.initialize();
		MadokuTime.initialize();
		MadokuItem.initialize();
		MadokuRarity.initialize();
		MadokuItemStack.initialize();
		MadokuArmor.initialize();
		MadokuHealth.initialize();
		MadokuHunger.initialize();
		MadokuOxygen.initialize();
			EntitySleepEvents.ALLOW_RESETTING_TIME.register(player -> !MadokuTime.isEnabled());
		WorldTimeSync.initialize();
		HungerHudSync.initialize();
		ServerLifecycleEvents.SERVER_STARTED.register(server -> {
			MadokuDebug.resetSession();
			MadokuClock.reset();
			MadokuSleep.reset();
			MadokuTime.reset();
			MadokuItemStack.reset();
			MadokuHealth.reset();
			MadokuHunger.reset();
			MadokuOxygen.reset();
			MadokuTime.loadPersistedData(server);
			MadokuScheduler.reset();
			MadokuScheduler.loadPersistedData(server);
			MadokuSmeltingManager.onServerStarted();
			MadokuHealth.loadPersistedData(server);
			MadokuHunger.loadPersistedData(server);
			MadokuOxygen.loadPersistedData(server);
			MadokuItemStack.loadPersistedData(server);
			MadokuItem.onServerStarted();
			MadokuTime.update(server);
			WorldTimeSync.reset();
			WorldTimeSync.broadcastNow(server);
		});
		ServerLifecycleEvents.SERVER_STOPPED.register(server -> {
			MadokuTime.savePersistedData(server);
			MadokuScheduler.savePersistedData(server);
			MadokuHealth.savePersistedData(server);
			MadokuHunger.savePersistedData(server);
			MadokuOxygen.savePersistedData(server);
			MadokuItemStack.savePersistedData(server);
			MadokuClock.reset();
			MadokuSleep.reset();
			MadokuTime.reset();
			MadokuScheduler.reset();
			MadokuSmeltingManager.onServerStopped();
			MadokuHealth.reset();
			MadokuHunger.reset();
			MadokuOxygen.reset();
			MadokuItemStack.reset();
			WorldTimeSync.reset();
		});
		ServerTickEvents.END_SERVER_TICK.register(server -> {
			long tickIncrement = MadokuSleep.getTickIncrement(server);
			MadokuSmeltingManager.onServerTickIncrement(tickIncrement);
			MadokuTicks.advance(server, tickIncrement);
			MadokuScheduler.autosavePersistedData(server);
			MadokuTime.autosavePersistedData(server);
			MadokuHealth.autosavePersistedData(server);
			MadokuHunger.autosavePersistedData(server);
			MadokuOxygen.autosavePersistedData(server);
			MadokuItemStack.autosavePersistedData(server);
			MadokuTime.update(server);
			WorldTimeSync.broadcastIfChanged(server);
		});
	}
}
