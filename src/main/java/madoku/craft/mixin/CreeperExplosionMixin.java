package madoku.craft.mixin;

import madoku.craft.mob.system.MadokuMob;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.monster.Creeper;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.Explosion;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Redirect;

@Mixin(Creeper.class)
public abstract class CreeperExplosionMixin {
	@Redirect(
		method = "explodeCreeper",
		at = @At(
			value = "INVOKE",
			target = "Lnet/minecraft/world/level/Level;explode(Lnet/minecraft/world/entity/Entity;DDDFLnet/minecraft/world/level/Level$ExplosionInteraction;)Lnet/minecraft/world/level/Explosion;"
		)
	)
	private Explosion madokuCraft$applyExplosionOverride(
		Level level,
		Entity source,
		double x,
		double y,
		double z,
		float power,
		Level.ExplosionInteraction interaction
	) {
		if (level instanceof net.minecraft.server.level.ServerLevel serverLevel) {
			MadokuMob.applyCreeperExplosionOverride((Creeper) (Object) this, serverLevel, source, x, y, z, power, interaction);
			return null;
		}
		return level.explode(source, x, y, z, power, interaction);
	}
}
