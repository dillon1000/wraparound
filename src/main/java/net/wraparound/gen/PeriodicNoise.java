package net.wraparound.gen;

import net.minecraft.util.Interval;
import net.minecraft.world.level.levelgen.synth.Noise;

/**
 * A noise read at {@code blockCoordinate * scale}, made to respect the world's gluing. Used for the
 * surface noises that are sampled directly rather than through density functions.
 */
public record PeriodicNoise(Noise input, double scale, Periodizer periodizer) implements Noise {
	@Override
	public Interval range() {
		return input.range();
	}

	@Override
	public float get(double x, double y) {
		return input.get(x, y);
	}

	@Override
	public float get(double x, double y, double z) {
		return (float) periodizer.sample(x / scale, y, z / scale, (bx, by, bz) -> input.get(bx * scale, by, bz * scale));
	}
}
