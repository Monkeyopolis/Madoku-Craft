package madoku.craft.mixin;

import madoku.craft.levels.MadokuLevels;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.player.Player;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

@Mixin(Player.class)
public abstract class PlayerLevelsHealthPersistenceMixin {
	@Inject(method = "addAdditionalSaveData", at = @At("TAIL"))
	private void madokuCraft$saveExactLevelHealth(CompoundTag output, CallbackInfo ci) {
		if ((Object) this instanceof ServerPlayer player) {
			MadokuLevels.writeSavedHealthTag(player, output);
		}
	}

	@Inject(method = "readAdditionalSaveData", at = @At("TAIL"))
	private void madokuCraft$queueExactLevelHealth(CompoundTag input, CallbackInfo ci) {
		if ((Object) this instanceof ServerPlayer player) {
			MadokuLevels.queueSavedHealthTag(player, input);
		}
	}
}
