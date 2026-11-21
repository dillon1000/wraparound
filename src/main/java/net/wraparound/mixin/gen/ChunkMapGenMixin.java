package net.wraparound.mixin.gen;

import com.llamalad7.mixinextras.injector.wrapoperation.Operation;
import com.llamalad7.mixinextras.injector.wrapoperation.WrapOperation;
import com.llamalad7.mixinextras.sugar.Local;
import net.minecraft.core.HolderGetter;
import net.minecraft.server.level.ChunkMap;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.levelgen.NoiseGeneratorSettings;
import net.minecraft.world.level.levelgen.NoiseRouter;
import net.minecraft.world.level.levelgen.RandomState;
import net.minecraft.world.level.levelgen.synth.NormalNoise;
import net.wraparound.Topology;
import net.wraparound.Wrap;
import net.wraparound.Wraparound;
import net.wraparound.gen.GenHooks;
import net.wraparound.gen.Periodizer;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;

/**
 * The topology is decided when a level's chunk map is built, from the game rules, and the terrain
 * generator for that level is made periodic.
 */
@Mixin(ChunkMap.class)
public class ChunkMapGenMixin {
	@WrapOperation(
		method = "<init>",
		at = @At(value = "INVOKE", target = "Lnet/minecraft/world/level/levelgen/RandomState;create(Lnet/minecraft/core/HolderGetter;JLnet/minecraft/world/level/levelgen/NoiseGeneratorSettings;)Lnet/minecraft/world/level/levelgen/RandomState;")
	)
	private RandomState wraparound$createNoise(
		HolderGetter<NormalNoise> noises, long seed, NoiseGeneratorSettings settings, Operation<RandomState> original, @Local(argsOnly = true) ServerLevel level
	) {
		return wraparound$withPeriodizer(level, () -> original.call(noises, seed, settings));
	}

	@WrapOperation(
		method = "<init>",
		at = @At(value = "INVOKE", target = "Lnet/minecraft/world/level/levelgen/RandomState;create(Lnet/minecraft/core/HolderGetter;JZLnet/minecraft/world/level/block/state/BlockState;ILnet/minecraft/world/level/levelgen/NoiseRouter;)Lnet/minecraft/world/level/levelgen/RandomState;")
	)
	private RandomState wraparound$createFlat(
		HolderGetter<NormalNoise> noises,
		long seed,
		boolean legacy,
		BlockState defaultBlock,
		int seaLevel,
		NoiseRouter router,
		Operation<RandomState> original,
		@Local(argsOnly = true) ServerLevel level
	) {
		return wraparound$withPeriodizer(level, () -> original.call(noises, seed, legacy, defaultBlock, seaLevel, router));
	}

	private static RandomState wraparound$withPeriodizer(ServerLevel level, java.util.function.Supplier<RandomState> create) {
		Topology topology = Wraparound.topologyFor(level.getServer(), level.dimension());
		((Wrap.Holder) level).wraparound$setTopology(topology);
		if (!topology.active()) {
			return create.get();
		}

		Wraparound.LOGGER.info("{} wraps as a {}", level.dimension().identifier(), topology);
		GenHooks.CONSTRUCTING.set(new Periodizer(topology));
		try {
			return create.get();
		} finally {
			GenHooks.CONSTRUCTING.remove();
		}
	}
}
