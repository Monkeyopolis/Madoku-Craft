package madoku.craft.mixin;

import madoku.craft.mob.system.MadokuMobCreeper;
import net.minecraft.world.level.ServerExplosion;
import org.spongepowered.asm.mixin.Final;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Redirect;

@Mixin(ServerExplosion.class)
public abstract class ServerExplosionBlockRadiusMixin {
	@Shadow
	@Final
	private float radius;

	@Redirect(
		method = "calculateExplodedPositions",
		at = @At(
			value = "FIELD",
			target = "Lnet/minecraft/world/level/ServerExplosion;radius:F"
		)
	)
	private float madokuCraft$useGriefOnlyRadius(ServerExplosion explosion) {
		if (!(explosion.getDirectSourceEntity() instanceof net.minecraft.world.entity.monster.Creeper creeper)
			|| !MadokuMobCreeper.shouldUseMobExplodeBehavior(creeper)) {
			return radius;
		}
		return MadokuMobCreeper.resolveGriefExplosionRadius((ServerExplosion) (Object) this, radius);
	}
}


