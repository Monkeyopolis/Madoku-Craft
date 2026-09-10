package madoku.craft.mixin.compat;

import java.util.List;
import java.util.Set;

import org.objectweb.asm.tree.ClassNode;
import org.spongepowered.asm.mixin.extensibility.IMixinConfigPlugin;
import org.spongepowered.asm.mixin.extensibility.IMixinInfo;

import madoku.craft.java.compat.MadokuCompatModuleState;

/** Enables Compat mixins only when the modules they bridge are installed. */
public final class MadokuCompatMixinPlugin implements IMixinConfigPlugin {
	@Override public void onLoad(String mixinPackage) { }
	@Override public String getRefMapperConfig() { return null; }
	@Override public void acceptTargets(Set<String> myTargets, Set<String> otherTargets) { }
	@Override public List<String> getMixins() { return null; }
	@Override public void preApply(String targetClassName, ClassNode targetClass, String mixinClassName, IMixinInfo mixinInfo) { }
	@Override public void postApply(String targetClassName, ClassNode targetClass, String mixinClassName, IMixinInfo mixinInfo) { }

	@Override
	public boolean shouldApplyMixin(String targetClassName, String mixinClassName) {
		if (MadokuCompatModuleState.isUnifiedBundle()) return true;

		if (mixinClassName.endsWith("LivingEntityArmorDamageMixin")) {
			return MadokuCompatModuleState.hasAll(
				MadokuCompatModuleState.CORE_ID,
				MadokuCompatModuleState.ATTRIBUTES_ID,
				MadokuCompatModuleState.MOBS_ID,
				MadokuCompatModuleState.PETS_ID
			);
		}
		if (mixinClassName.endsWith("LivingEntityLuckLootMixin")) {
			return MadokuCompatModuleState.hasAll(
				MadokuCompatModuleState.CORE_ID,
				MadokuCompatModuleState.ATTRIBUTES_ID,
				MadokuCompatModuleState.MOBS_ID
			);
		}
		if (mixinClassName.endsWith("BlockFarmingDropsMixin")
			|| mixinClassName.endsWith("LootTableFarmingMixin")) {
			return MadokuCompatModuleState.hasAll(
				MadokuCompatModuleState.CORE_ID,
				MadokuCompatModuleState.ATTRIBUTES_ID,
				MadokuCompatModuleState.FARMING_ID
			);
		}
		if (mixinClassName.endsWith("ConfiguredFuelValuesMixin")) {
			return MadokuCompatModuleState.hasAll(
				MadokuCompatModuleState.CORE_ID,
				MadokuCompatModuleState.UTILITY_ID,
				MadokuCompatModuleState.ITEMS_ID
			);
		}
		if (mixinClassName.endsWith("BiomeTemperatureColorMixin")
			|| mixinClassName.endsWith("SpruceBirchLeafTintMixin")) {
			return MadokuCompatModuleState.isLoaded(MadokuCompatModuleState.CORE_ID);
		}
		if (mixinClassName.endsWith("SodiumColorProviderContextMixin")
			|| mixinClassName.endsWith("SodiumSpruceBirchFoliageProviderMixin")) {
			return MadokuCompatModuleState.isLoaded("sodium");
		}

		return true;
	}
}
