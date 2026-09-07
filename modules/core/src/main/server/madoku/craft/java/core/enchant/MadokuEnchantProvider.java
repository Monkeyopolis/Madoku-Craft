package madoku.craft.java.core.enchant;

import net.minecraft.server.MinecraftServer;
import net.minecraft.world.item.ItemStack;

/** Built-in provider backed by the Madoku enchantment implementation. */
public final class MadokuEnchantProvider implements EnchantProvider {
	@Override public void initialize() { MadokuEnchantManager.initialize(); }
	@Override public void reset() { MadokuEnchantManager.reset(); }
	@Override public void onServerTick(MinecraftServer server) { MadokuEnchantManager.onServerTick(server); }
	@Override public void onServerStarted(MinecraftServer server) { MadokuEnchantManager.onServerStarted(server); }
	@Override public boolean isEnabled() { return MadokuEnchantManager.isEnabled(); }
	@Override public void resetClientSynchronizedState() { MadokuEnchantManager.resetClientSynchronizedState(); }
	@Override public void applyClientSynchronizedSnapshot(String snapshot) { MadokuEnchantManager.applyClientSynchronizedSnapshot(snapshot); }
	@Override public void copyEnchantments(ItemStack source, ItemStack target) { MadokuEnchantManager.copyEnchantments(source, target); }
	@Override public void mergeEnchantments(ItemStack primary, ItemStack duplicate, ItemStack target) { MadokuEnchantManager.mergeEnchantments(primary, duplicate, target); }
}
