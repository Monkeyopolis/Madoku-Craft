package madoku.craft.entity;

import madoku.craft.MadokuCraft;
import net.fabricmc.fabric.api.event.lifecycle.v1.ServerEntityEvents;
import net.fabricmc.fabric.api.object.builder.v1.entity.FabricDefaultAttributeRegistry;
import net.minecraft.core.Registry;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.core.registries.Registries;
import net.minecraft.resources.Identifier;
import net.minecraft.resources.ResourceKey;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.DifficultyInstance;
import net.minecraft.world.entity.EntitySpawnReason;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.EquipmentSlot;
import net.minecraft.world.entity.MobCategory;
import net.minecraft.world.entity.monster.Witch;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.SpawnEggItem;
import net.minecraft.world.level.levelgen.structure.StructureStart;

public final class MadokuEntities {
	public static final Identifier HAG_ID = Identifier.fromNamespaceAndPath(MadokuCraft.MOD_ID, "hag");
	public static final Identifier HAG_SPAWN_EGG_ID = Identifier.fromNamespaceAndPath(MadokuCraft.MOD_ID, "hag_spawn_egg");
	public static final EntityType<Hag> HAG = Registry.register(
		BuiltInRegistries.ENTITY_TYPE,
		HAG_ID,
		EntityType.Builder.of(Hag::new, MobCategory.MONSTER)
			.sized(0.6F, 1.95F)
			.eyeHeight(1.62F)
			.clientTrackingRange(8)
			.notInPeaceful()
			.build(ResourceKey.create(Registries.ENTITY_TYPE, HAG_ID))
	);
	public static final Item HAG_SPAWN_EGG = Registry.register(
		BuiltInRegistries.ITEM,
		HAG_SPAWN_EGG_ID,
		new SpawnEggItem(
			new Item.Properties()
				.spawnEgg(HAG)
				.setId(ResourceKey.create(Registries.ITEM, HAG_SPAWN_EGG_ID))
		)
	);

	private MadokuEntities() {
	}

	public static void initialize() {
		FabricDefaultAttributeRegistry.register(HAG, Witch.createAttributes());
		ServerEntityEvents.ENTITY_LOAD.register((entity, world) -> {
			if (!(entity instanceof Witch witch) || witch.getType() != EntityType.WITCH || !(world instanceof ServerLevel serverLevel)) {
				return;
			}
			if (!isSwampHutSpawn(serverLevel, witch)) {
				return;
			}

			replaceWitchWithHag(serverLevel, witch);
		});
	}

	private static boolean isSwampHutSpawn(ServerLevel level, Witch witch) {
		StructureStart structureStart = level.structureManager().getStructureWithPieceAt(
			witch.blockPosition(),
			holder -> holder.unwrapKey().map(ResourceKey::identifier).filter(identifier -> "swamp_hut".equals(identifier.getPath())).isPresent()
		);
		return structureStart != null && structureStart != StructureStart.INVALID_START && structureStart.isValid() && !witch.hasActiveRaid();
	}

	private static void replaceWitchWithHag(ServerLevel level, Witch witch) {
		Hag hag = HAG.create(level, EntitySpawnReason.STRUCTURE);
		if (hag == null) {
			return;
		}

		hag.snapTo(witch.getX(), witch.getY(), witch.getZ(), witch.getYRot(), witch.getXRot());
		hag.setYBodyRot(witch.yBodyRot);
		hag.yHeadRot = witch.yHeadRot;
		hag.yHeadRotO = witch.yHeadRotO;
		hag.setHealth(Math.min(witch.getHealth(), hag.getMaxHealth()));
		for (EquipmentSlot slot : EquipmentSlot.values()) {
			hag.setItemSlot(slot, witch.getItemBySlot(slot).copy());
		}

		DifficultyInstance difficulty = level.getCurrentDifficultyAt(witch.blockPosition());
		hag.finalizeSpawn(level, difficulty, EntitySpawnReason.STRUCTURE, null);
		hag.setHealth(Math.min(witch.getHealth(), hag.getMaxHealth()));
		if (witch.hasCustomName()) {
			hag.setCustomName(witch.getCustomName());
			hag.setCustomNameVisible(witch.isCustomNameVisible());
		}
		hag.setInvulnerable(witch.isInvulnerable());
		hag.setSilent(witch.isSilent());
		hag.setNoAi(witch.isNoAi());
		if (witch.isPersistenceRequired()) {
			hag.setPersistenceRequired();
		}

		if (level.addFreshEntity(hag)) {
			witch.discard();
		}
	}
}
