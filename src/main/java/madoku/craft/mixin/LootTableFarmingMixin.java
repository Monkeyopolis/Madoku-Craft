package madoku.craft.mixin;

import it.unimi.dsi.fastutil.objects.ObjectArrayList;
import madoku.craft.clock.MadokuClock;
import madoku.craft.debug.MadokuDebug;
import madoku.craft.farming.system.MadokuFarming;
import net.minecraft.util.RandomSource;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.storage.loot.LootContext;
import net.minecraft.world.level.storage.loot.LootTable;
import net.minecraft.world.level.storage.loot.parameters.LootContextParams;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

import java.lang.reflect.Method;

@Mixin(LootTable.class)
public abstract class LootTableFarmingMixin {
	@Inject(
		method = "getRandomItems(Lnet/minecraft/world/level/storage/loot/LootContext;)Lit/unimi/dsi/fastutil/objects/ObjectArrayList;",
		at = @At("RETURN"),
		cancellable = true
	)
	private void madokuCraft$overrideCropHarvestDrops(
		LootContext lootContext,
		CallbackInfoReturnable<ObjectArrayList<ItemStack>> cir
	) {
		if (!MadokuFarming.isEnabled() || lootContext == null) {
			return;
		}

		BlockState state = resolveBlockStateParameter(lootContext);
		if (!MadokuFarming.isManagedCrop(state) || !MadokuFarming.isMaxAge(state)) {
			return;
		}

		RandomSource random = lootContext.getRandom();
		int count = MadokuFarming.calculateCropHarvestCount(null, null, state, random);
		if (count <= 0) {
			return;
		}

		Item harvestItem = MadokuFarming.getCropHarvestItem(state);
		if (harvestItem == null) {
			return;
		}

		ObjectArrayList<ItemStack> drops = new ObjectArrayList<>(1);
		drops.add(new ItemStack(harvestItem, count));
		if (MadokuDebug.shouldEmit(MadokuDebug.Domain.FARMING, "farming.crop_drops_loot")) {
			MadokuDebug.event("farming.crop_drops_loot", MadokuDebug.Domain.FARMING)
					.side(MadokuDebug.Side.SERVER)
					.tick(MadokuClock.getGameplayTicks())
					.subject("loot_table")
					.field("count", Integer.toString(count))
				.field("state", state == null ? "unknown" : state.getBlock().toString())
				.log();
		}
		cir.setReturnValue(drops);
	}

	private static BlockState resolveBlockStateParameter(LootContext lootContext) {
		Object parameter = LootContextParams.BLOCK_STATE;
		if (lootContext == null || parameter == null) {
			return null;
		}

		try {
			Method hasParameter = findLootContextMethod(lootContext.getClass(), "hasParameter", parameter.getClass());
			if (hasParameter != null) {
				Object present = hasParameter.invoke(lootContext, parameter);
				if (!(present instanceof Boolean) || !((Boolean) present)) {
					return null;
				}
			}

			Method getOrThrow = findLootContextMethod(lootContext.getClass(), "getOrThrow", parameter.getClass());
			if (getOrThrow != null) {
				Object state = getOrThrow.invoke(lootContext, parameter);
				return state instanceof BlockState blockState ? blockState : null;
			}

			Method get = findLootContextMethod(lootContext.getClass(), "get", parameter.getClass());
			if (get != null) {
				Object state = get.invoke(lootContext, parameter);
				return state instanceof BlockState blockState ? blockState : null;
			}
		} catch (ReflectiveOperationException | RuntimeException exception) {
			return null;
		}

		return null;
	}

	private static Method findLootContextMethod(Class<?> type, String name, Class<?> parameterType) {
		if (type == null || name == null || parameterType == null) {
			return null;
		}

		for (Method method : type.getMethods()) {
			if (!name.equals(method.getName()) || method.getParameterCount() != 1) {
				continue;
			}

			Class<?> declaredParameter = method.getParameterTypes()[0];
			if (declaredParameter.isAssignableFrom(parameterType) || parameterType.isAssignableFrom(declaredParameter)) {
				method.setAccessible(true);
				return method;
			}
		}
		return null;
	}
}
