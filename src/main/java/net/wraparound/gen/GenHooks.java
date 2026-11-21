package net.wraparound.gen;

import net.minecraft.resources.ResourceKey;
import net.minecraft.world.level.levelgen.Noises;
import net.minecraft.world.level.levelgen.densityfunction.DensityFunction;
import net.minecraft.world.level.levelgen.densityfunction.DensitySampler;
import net.minecraft.world.level.levelgen.synth.Noise;
import net.minecraft.world.level.levelgen.synth.NormalNoise;
import org.jspecify.annotations.Nullable;

public final class GenHooks {
	/** The periodizer for the RandomState currently being constructed on this thread. */
	public static final ThreadLocal<@Nullable Periodizer> CONSTRUCTING = new ThreadLocal<>();
	private static final boolean DEBUG = Boolean.getBoolean("wraparound.debugGen");

	private GenHooks() {
	}

	public interface PeriodizerHolder {
		@Nullable Periodizer wraparound$periodizer();
	}

	public static @Nullable Periodizer periodizer(Object holder) {
		return holder instanceof PeriodizerHolder h ? h.wraparound$periodizer() : null;
	}

	/** Wraps a compiled leaf sampler if it reads x or z and the world wraps. */
	public static DensitySampler wrapLeaf(DensityFunction function, DensitySampler sampler, DensityFunction.CompileContext context) {
		Periodizer periodizer = periodizer(context);
		if (DEBUG) {
			net.wraparound.Wraparound.LOGGER.info("leaf {} axes {} context {} periodizer {}", function.getClass().getSimpleName(), function.domainAxes(), context.getClass().getName(), periodizer != null);
		}

		if (periodizer == null || sampler instanceof PeriodicSampler) {
			return sampler;
		}

		if ((function.domainAxes() & (DensityFunction.AXIS_X | DensityFunction.AXIS_Z)) == 0) {
			return sampler;
		}

		return new PeriodicSampler(sampler, periodizer);
	}

	/** Scale at which the material system reads each surface noise. */
	public static double surfaceNoiseScale(ResourceKey<NormalNoise> key) {
		if (key == Noises.BADLANDS_PILLAR) {
			return 0.2;
		} else if (key == Noises.BADLANDS_PILLAR_ROOF) {
			return 0.75;
		} else if (key == Noises.ICEBERG_PILLAR) {
			return 1.28;
		} else if (key == Noises.ICEBERG_PILLAR_ROOF) {
			return 1.17;
		}

		return 1.0;
	}

	public static Noise wrapNoise(Noise noise, double scale, @Nullable Periodizer periodizer) {
		if (periodizer == null || noise instanceof PeriodicNoise) {
			return noise;
		}

		return new PeriodicNoise(noise, scale, periodizer);
	}
}
