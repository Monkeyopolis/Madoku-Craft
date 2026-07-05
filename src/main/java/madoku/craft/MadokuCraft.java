package madoku.craft;

import madoku.craft.attributes.MadokuAttributesManager;
import madoku.craft.api.MadokuAPIManager;
import madoku.craft.block.MadokuBlocks;
import madoku.craft.clock.MadokuClock;
import madoku.craft.clock.MadokuTicks;
import madoku.craft.composter.system.MadokuComposter;
import madoku.craft.config.JsonManagerSystem;
import madoku.craft.difficulty.system.MadokuRegionalDifficultyManager;
import madoku.craft.ecosystem.system.MadokuEcosystem;
import madoku.craft.entity.MadokuEntities;
import madoku.craft.farming.system.MadokuFarming;
import madoku.craft.attributes.health.MadokuHealthManager;
import madoku.craft.attributes.hunger.MadokuHungerManager;
import madoku.craft.item.system.MadokuItem;
import madoku.craft.itemstack.system.MadokuItemStack;
import madoku.craft.levels.MadokuLevels;
import madoku.craft.attributes.luck.MadokuPlacedBlocks;
import madoku.craft.mob.system.MadokuMobManager;
import madoku.craft.network.HungerHudSync;
import madoku.craft.network.ItemProfileSync;
import madoku.craft.network.PetAbilityHudSync;
import madoku.craft.network.PetSoundStateSync;
import madoku.craft.network.WorldDifficultySync;
import madoku.craft.network.WorldSeasonSync;
import madoku.craft.network.WorldTimeSync;
import madoku.craft.attributes.oxygen.MadokuOxygenManager;
import madoku.craft.rarity.MadokuRarity;
import madoku.craft.loot.system.MadokuLootTableManager;
import madoku.craft.recipe.system.MadokuRecipe;
import madoku.craft.scheduler.SchedulerManagerSystem;
import madoku.craft.season.MadokuSeason;
import madoku.craft.smelting.system.MadokuSmeltingManager;
import madoku.craft.time.MadokuSleep;
import madoku.craft.time.MadokuTime;
import madoku.craft.pet.PlayerEntitiesSystem;
import madoku.craft.worldgen.MadokuWorldgen;
import net.fabricmc.api.ModInitializer;
import net.fabricmc.fabric.api.entity.event.v1.EntitySleepEvents;
import net.fabricmc.fabric.api.event.lifecycle.v1.ServerLifecycleEvents;
import net.fabricmc.fabric.api.event.lifecycle.v1.ServerTickEvents;

public class MadokuCraft implements ModInitializer {
	public static final String MOD_ID = "madoku-craft";

	@Override
	public void onInitialize() {
		JsonManagerSystem.initialize();
		MadokuAPIManager.initialize();
		MadokuSmeltingManager.initialize();
		MadokuRecipe.initialize();
		MadokuLootTableManager.initialize();
		MadokuRegionalDifficultyManager.initialize();
		MadokuTime.initialize();
		MadokuSeason.initialize();
		MadokuWorldgen.initialize();
		MadokuEntities.initialize();
		MadokuBlocks.initialize();
		MadokuItem.initialize();
		MadokuComposter.initialize();
		MadokuFarming.initialize();
		MadokuEcosystem.initialize();
		MadokuMobManager.initialize();
		MadokuRarity.initialize();
		MadokuItemStack.initialize();
		MadokuAttributesManager.initialize();
		MadokuPlacedBlocks.initialize();
		MadokuLevels.initialize();
		PlayerEntitiesSystem.initialize();
		EntitySleepEvents.ALLOW_RESETTING_TIME.register(player -> !MadokuTime.isEnabled());
		WorldTimeSync.initialize();
		WorldDifficultySync.initialize();
		WorldSeasonSync.initialize();
		ItemProfileSync.initialize();
		HungerHudSync.initialize();
		PetAbilityHudSync.initialize();
		PetSoundStateSync.initialize();

		ServerLifecycleEvents.SERVER_STARTED.register(server -> {
			MadokuTicks.reset();
			MadokuClock.reset();
			MadokuSleep.reset();
			MadokuTime.reset();
			MadokuSeason.reset();
			MadokuEntities.reset();
			MadokuFarming.reset();
			MadokuEcosystem.reset();
			MadokuItem.reset();
			MadokuItemStack.reset();
			MadokuPlacedBlocks.reset();
			MadokuHealthManager.reset();
			MadokuHungerManager.reset();
			MadokuOxygenManager.reset();
			MadokuLevels.reset();
			PlayerEntitiesSystem.reset();
			MadokuAPIManager.reset();
			SchedulerManagerSystem.reset();
			SchedulerManagerSystem.loadPersistedData(server);
			MadokuAPIManager.loadPersistedData(server);
			MadokuTime.loadPersistedData(server);
			MadokuSeason.loadPersistedData(server);
			MadokuEntities.loadPersistedData(server);
			MadokuFarming.loadPersistedData(server);
			MadokuEcosystem.loadPersistedData(server);
			MadokuPlacedBlocks.loadPersistedData(server);
			MadokuAPIManager.onServerStarted(server);
			MadokuSeason.onServerStarted(server);
			MadokuFarming.onServerStarted(server);
			MadokuEcosystem.onServerStarted(server);
			MadokuSmeltingManager.onServerStarted();
			MadokuHealthManager.loadPersistedData(server);
			MadokuHungerManager.loadPersistedData(server);
			MadokuOxygenManager.loadPersistedData(server);
			MadokuLevels.loadPersistedData(server);
			PlayerEntitiesSystem.loadPersistedData(server);
			MadokuItemStack.loadPersistedData(server);
			MadokuItem.onServerStarted(server);
			MadokuHungerManager.onServerStarted(server);
			MadokuHealthManager.onServerStarted(server);
			MadokuOxygenManager.onServerStarted(server);
			MadokuEntities.onServerStarted(server);
			PlayerEntitiesSystem.onServerStarted(server);
			MadokuMobManager.onServerStarted(server);
			MadokuRegionalDifficultyManager.onServerStarted(server);
			MadokuTime.update(server);
			WorldTimeSync.reset();
			WorldDifficultySync.reset();
			WorldSeasonSync.reset();
			WorldTimeSync.broadcastNow(server);
			WorldDifficultySync.broadcastNow(server);
			WorldSeasonSync.broadcastNow(server);
		});

		ServerLifecycleEvents.SERVER_STOPPING.register(MadokuAPIManager::onServerStopping);

		ServerLifecycleEvents.SERVER_STOPPED.register(server -> {
			MadokuTime.savePersistedData(server);
			MadokuSeason.savePersistedData(server);
			MadokuEntities.savePersistedData(server);
			MadokuFarming.savePersistedData(server);
			MadokuEcosystem.savePersistedData(server);
			MadokuAPIManager.savePersistedData(server);
			SchedulerManagerSystem.savePersistedData(server);
			MadokuPlacedBlocks.savePersistedData(server);
			MadokuHealthManager.savePersistedData(server);
			MadokuHungerManager.savePersistedData(server);
			MadokuOxygenManager.savePersistedData(server);
			MadokuLevels.savePersistedData(server);
			PlayerEntitiesSystem.savePersistedData(server);
			MadokuItemStack.savePersistedData(server);
			MadokuClock.reset();
			MadokuTicks.reset();
			MadokuSleep.reset();
			MadokuTime.reset();
			MadokuSeason.reset();
			MadokuEntities.reset();
			MadokuFarming.reset();
			MadokuEcosystem.reset();
			MadokuItem.reset();
			MadokuAPIManager.reset();
			SchedulerManagerSystem.reset();
			MadokuPlacedBlocks.reset();
			MadokuSmeltingManager.onServerStopped();
			MadokuRegionalDifficultyManager.onServerStopped();
			MadokuMobManager.onServerStopped();
			MadokuHealthManager.reset();
			MadokuHungerManager.reset();
			MadokuOxygenManager.reset();
			MadokuLevels.reset();
			PlayerEntitiesSystem.reset();
			MadokuItemStack.reset();
			WorldTimeSync.reset();
			WorldDifficultySync.reset();
			WorldSeasonSync.reset();
		});

		ServerTickEvents.END_SERVER_TICK.register(server -> {
			long tickIncrement = MadokuSleep.getTickIncrement(server);
			MadokuTicks.advance(server, tickIncrement);
			MadokuTime.update(server);
			if (MadokuTime.isEnabled()) {
				SchedulerManagerSystem.onClockTick(server);
			} else {
				SchedulerManagerSystem.onServerTick(server);
			}
			MadokuFarming.onServerTickIncrement(server, tickIncrement);
			SchedulerManagerSystem.autosavePersistedData(server);
			MadokuAPIManager.autosavePersistedData(server);
			MadokuTime.autosavePersistedData(server);
			MadokuHealthManager.autosavePersistedData(server);
			MadokuHungerManager.autosavePersistedData(server);
			MadokuSeason.autosavePersistedData(server);
			MadokuEntities.autosavePersistedData(server);
			MadokuFarming.autosavePersistedData(server);
			MadokuEcosystem.autosavePersistedData(server);
			MadokuPlacedBlocks.autosavePersistedData(server);
			MadokuOxygenManager.autosavePersistedData(server);
			MadokuLevels.autosavePersistedData(server);
			PlayerEntitiesSystem.autosavePersistedData(server);
			MadokuItemStack.autosavePersistedData(server);
			MadokuSeason.onServerTick(server);
			MadokuRegionalDifficultyManager.onServerTick(server);
			MadokuMobManager.onServerTick(server);
			MadokuLevels.flushDirtySyncs(server);
			WorldTimeSync.broadcastIfChanged(server);
			WorldDifficultySync.broadcastIfChanged(server);
			WorldSeasonSync.broadcastIfChanged(server);
		});
	}
}






