package madoku.craft.mixin.client.sodium;

import net.minecraft.world.level.block.Blocks;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Pseudo;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

import java.lang.reflect.Field;
import java.util.Map;

@Pseudo
@Mixin(targets = "net.caffeinemc.mods.sodium.client.model.color.ColorProviderRegistry", remap = false)
public abstract class SodiumSpruceBirchFoliageProviderMixin {
	@Inject(method = "<init>", at = @At("RETURN"))
	private void madokuCraft$useBlendedFoliageProvider(CallbackInfo ci) {
		Object foliageProvider = foliageProvider();
		if (foliageProvider == null) return;
		try {
			Field blocksField = findField("blocks");
			blocksField.setAccessible(true);
			@SuppressWarnings("unchecked")
			Map<Object, Object> providers = (Map<Object, Object>) blocksField.get(this);
			providers.put(Blocks.SPRUCE_LEAVES, foliageProvider);
			providers.put(Blocks.BIRCH_LEAVES, foliageProvider);
		} catch (ReflectiveOperationException exception) {
			// Sodium is optional; leave its normal provider map intact if its internals change.
		}
	}

	private Field findField(String name) throws NoSuchFieldException {
		Class<?> type = getClass();
		while (type != null) {
			try {
				return type.getDeclaredField(name);
			} catch (NoSuchFieldException ignored) {
				type = type.getSuperclass();
			}
		}
		throw new NoSuchFieldException(name);
	}

	private static Object foliageProvider() {
		try {
			Class<?> providerClass = Class.forName(
				"net.caffeinemc.mods.sodium.client.model.color.DefaultColorProviders$FoliageColorProvider");
			Field providerField = providerClass.getDeclaredField("BLOCKS");
			providerField.setAccessible(true);
			return providerField.get(null);
		} catch (ReflectiveOperationException exception) {
			return null;
		}
	}
}
