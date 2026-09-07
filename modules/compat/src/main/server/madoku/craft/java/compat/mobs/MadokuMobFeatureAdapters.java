package madoku.craft.java.compat.mobs;

import madoku.craft.java.attributes.LuckAPIManager;
import madoku.craft.java.compat.MadokuCompatModuleState;
import madoku.craft.java.core.loot.EquipmentsConfigAPIManager;
import madoku.craft.java.farming.FarmingAPIManager;
import madoku.craft.java.mob.MobFeatureAdapter;
import madoku.craft.java.mob.MobFeatureAPIManager;
import madoku.craft.java.pet.PetComponentsAPIManager;

/** Bridges optional Attributes, Farming, and Pets behavior into the Mobs module. */
public final class MadokuMobFeatureAdapters {
	private MadokuMobFeatureAdapters() {
	}

	public static void initialize() {
		MobFeatureAPIManager.registerAdapter(new MobFeatureAdapter() {
			@Override
			public boolean isEquipmentOverrideEnabled() {
				return EquipmentsConfigAPIManager.isEntityEquipmentOverrideEnabled();
			}

			@Override
			public java.util.Map<net.minecraft.world.entity.EquipmentSlot, net.minecraft.world.item.ItemStack> resolveEquipment(
				String equipmentReference,
				net.minecraft.world.entity.EntityType<?> mobType,
				net.minecraft.util.RandomSource random
			) {
				EquipmentsConfigAPIManager.EquipmentProfile profile =
					EquipmentsConfigAPIManager.resolveProfile(equipmentReference, mobType);
				if (profile == null || !profile.enabled()) {
					return null;
				}
				return EquipmentsConfigAPIManager.rollEquipment(profile, random);
			}

			@Override
			public boolean isManagedPet(net.minecraft.world.entity.Entity entity) {
				return MadokuCompatModuleState.isLoaded(MadokuCompatModuleState.PETS_ID)
					&& PetComponentsAPIManager.isManaged(entity);
			}

			@Override
			public double reduceCreeperGriefChance(net.minecraft.world.entity.LivingEntity target, double chance) {
				return MadokuCompatModuleState.isLoaded(MadokuCompatModuleState.ATTRIBUTES_ID)
					? LuckAPIManager.reduceCreeperGriefChanceForTarget(target, chance) : chance;
			}

			@Override
			public double reduceHostileRangedAccuracy(net.minecraft.world.entity.LivingEntity target, double accuracy) {
				return MadokuCompatModuleState.isLoaded(MadokuCompatModuleState.ATTRIBUTES_ID)
					? LuckAPIManager.reduceHostileRangedAccuracyForTarget(target, accuracy) : accuracy;
			}

			@Override
			public boolean isBeeCropGrowthEnabled() {
				return MadokuCompatModuleState.isLoaded(MadokuCompatModuleState.FARMING_ID)
					&& FarmingAPIManager.isEnabled();
			}

			@Override
			public boolean applyBeeCropGrowth(
				net.minecraft.server.level.ServerLevel world,
				net.minecraft.core.BlockPos cropPos,
				double growthPercent,
				String source
			) {
				return MadokuCompatModuleState.isLoaded(MadokuCompatModuleState.FARMING_ID)
					&& FarmingAPIManager.applyExternalGrowthPercent(world, cropPos, growthPercent, source);
			}
		});
	}
}
