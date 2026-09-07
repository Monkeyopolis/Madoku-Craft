package madoku.craft.java.compat;

import madoku.craft.java.core.module.MadokuStandaloneModule;
import madoku.craft.java.core.module.MadokuStandaloneRuntime;
import net.fabricmc.api.ModInitializer;

/** Fabric entrypoint for the optional Madoku compatibility jar. */
public final class MadokuCompatInitializer implements ModInitializer, MadokuStandaloneModule {
	@Override public void onInitialize() { MadokuStandaloneRuntime.initialize(this); }
	@Override public void initialize() { MadokuCompatManager.initialize(); }
}
