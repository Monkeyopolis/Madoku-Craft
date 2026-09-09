package madoku.craft.java.compat.loot;

import it.unimi.dsi.fastutil.objects.ObjectArrayList;
import madoku.craft.java.attributes.LuckAPIManager;
import madoku.craft.java.compat.MadokuCompatModuleState;
import madoku.craft.java.core.loot.LootFeatureAPIManager;
import madoku.craft.java.core.loot.LootFeatureAdapter;
import madoku.craft.java.farming.FarmingAPIManager;
import madoku.craft.java.mob.MobEntityManager;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.util.RandomSource;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.storage.loot.LootContext;

/** Connects optional Madoku feature modules to Core loot-table behavior. */
public final class MadokuLootFeatureAdapters {
	private MadokuLootFeatureAdapters() {
	}

	public static void initialize() {
		LootFeatureAPIManager.registerAdapter(new LootFeatureAdapter() {
			@Override
			public boolean isLuckEnabled() {
				return MadokuCompatModuleState.isLoaded(MadokuCompatModuleState.ATTRIBUTES_ID)
					&& LuckAPIManager.isEnabled();
			}

			@Override
			public boolean isActiveDropPlayerPlacedBlock() {
				return MadokuCompatModuleState.isLoaded(MadokuCompatModuleState.ATTRIBUTES_ID)
					&& LuckAPIManager.isActiveDropPlayerPlacedBlock();
			}

			@Override
			public ServerPlayer resolveLootPlayer(LootContext lootContext) {
				return MadokuCompatModuleState.isLoaded(MadokuCompatModuleState.ATTRIBUTES_ID)
					? LuckAPIManager.resolveLootPlayer(lootContext) : null;
			}

			@Override
			public ServerPlayer resolveActiveDropPlayer() {
				return MadokuCompatModuleState.isLoaded(MadokuCompatModuleState.ATTRIBUTES_ID)
					? LuckAPIManager.resolveActiveDropPlayer() : null;
			}

			@Override
			public double resolveLootLuckStat(ServerPlayer player) {
				return MadokuCompatModuleState.isLoaded(MadokuCompatModuleState.ATTRIBUTES_ID)
					? LuckAPIManager.resolveLootLuckStat(player) : 0.0D;
			}

			@Override
			public void applyManagedMobDrops(ServerPlayer player, RandomSource random, ObjectArrayList<ItemStack> stacks) {
				if (MadokuCompatModuleState.isLoaded(MadokuCompatModuleState.ATTRIBUTES_ID)) {
					LuckAPIManager.applyManagedMobDrops(player, random, stacks);
				}
			}

			@Override
			public boolean isFarmingEnabled() {
				return MadokuCompatModuleState.isLoaded(MadokuCompatModuleState.FARMING_ID)
					&& FarmingAPIManager.isEnabled();
			}

			@Override
			public boolean isMobEnabled() {
				return MadokuCompatModuleState.isLoaded(MadokuCompatModuleState.MOBS_ID)
					&& MobEntityManager.isEnabled();
			}

			@Override
			public boolean isBeeCustomMobDropsEnabled(LivingEntity entity) {
				return MadokuCompatModuleState.isLoaded(MadokuCompatModuleState.MOBS_ID)
					&& MobEntityManager.isBeeCustomMobDropsEnabled(entity);
			}

			@Override
			public String resolveBeeMobDropsConfigReference(LivingEntity entity) {
				return MadokuCompatModuleState.isLoaded(MadokuCompatModuleState.MOBS_ID)
					? MobEntityManager.resolveBeeMobDropsConfigReference(entity) : "";
			}

			@Override
			public boolean isZombieCustomMobDropsEnabled(LivingEntity entity) {
				return MadokuCompatModuleState.isLoaded(MadokuCompatModuleState.MOBS_ID)
					&& MobEntityManager.isZombieCustomMobDropsEnabled(entity);
			}

			@Override
			public String resolveZombieMobDropsConfigReference(LivingEntity entity) {
				return MadokuCompatModuleState.isLoaded(MadokuCompatModuleState.MOBS_ID)
					? MobEntityManager.resolveZombieMobDropsConfigReference(entity) : "";
			}

		});
	}
}
