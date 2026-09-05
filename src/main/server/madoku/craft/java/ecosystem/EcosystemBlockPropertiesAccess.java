package madoku.craft.java.ecosystem;

/** Internal marker added to vanilla block properties during block bootstrap. */
public interface EcosystemBlockPropertiesAccess {
	void madokuCraft$setEcosystemStateProperties(boolean enabled);

	boolean madokuCraft$hasEcosystemStateProperties();
}
