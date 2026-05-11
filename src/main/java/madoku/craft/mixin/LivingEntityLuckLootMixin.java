package madoku.craft.mixin;

import madoku.craft.luck.MadokuLuck;
import net.minecraft.resources.ResourceKey;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.damagesource.DamageSource;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.level.storage.loot.LootTable;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.ModifyVariable;

import java.util.function.Consumer;

@Mixin(LivingEntity.class)
public abstract class LivingEntityLuckLootMixin {
	@ModifyVariable(
		method = "dropFromLootTable(Lnet/minecraft/server/level/ServerLevel;Lnet/minecraft/world/damagesource/DamageSource;ZLnet/minecraft/resources/ResourceKey;Ljava/util/function/Consumer;)V",
		at = @At("HEAD"),
		argsOnly = true,
		index = 5
	)
	private Consumer<ItemStack> madokuCraft$wrapMobLuckLootConsumer(
		Consumer<ItemStack> consumer,
		ServerLevel level,
		DamageSource damageSource,
		boolean causedByPlayer,
		ResourceKey<LootTable> lootTableKey
	) {
		LivingEntity livingEntity = (LivingEntity) (Object) this;
		Consumer<ItemStack> normalizedConsumer = stack -> {
			if (stack != null
				&& !stack.isEmpty()
				&& livingEntity.getType() == EntityType.ZOMBIFIED_PIGLIN
				&& stack.is(Items.GOLD_NUGGET)) {
				consumer.accept(new ItemStack(Items.IRON_NUGGET, stack.getCount()));
				return;
			}
			consumer.accept(stack);
		};

		return MadokuLuck.wrapMobDeathLootConsumer(
			level,
			livingEntity,
			damageSource,
			causedByPlayer,
			normalizedConsumer
		);
	}
}
