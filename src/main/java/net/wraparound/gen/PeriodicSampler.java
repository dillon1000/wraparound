package net.wraparound.gen;

import net.minecraft.world.level.levelgen.densityfunction.DensityBuffer;
import net.minecraft.world.level.levelgen.densityfunction.DensitySampler;
import net.minecraft.world.level.levelgen.densityfunction.DensityVolume;
import net.minecraft.world.level.levelgen.densityfunction.SamplerContext;

/** Wraps a position-dependent density sampler so its output respects the world's gluing. */
public record PeriodicSampler(DensitySampler input, Periodizer periodizer) implements DensitySampler {
	@Override
	public void sampleVolume(SamplerContext context, DensityBuffer outputBuffer, DensityVolume volume) {
		if (periodizer.untouched(volume.minBlockX(), volume.minBlockZ(), volume.maxBlockX(), volume.maxBlockZ())) {
			input.sampleVolume(context, outputBuffer, volume);
			return;
		}

		int index = 0;
		for (int z = 0; z < volume.sizeZ(); z++) {
			int blockZ = volume.blockZ(z);
			for (int x = 0; x < volume.sizeX(); x++) {
				int blockX = volume.blockX(x);
				for (int y = 0; y < volume.sizeY(); y++) {
					outputBuffer.set(index++, sampleValue(context, blockX, volume.blockY(y), blockZ));
				}
			}
		}
	}

	@Override
	public float sampleValue(SamplerContext context, int blockX, int blockY, int blockZ) {
		if (periodizer.untouched(blockX, blockZ, blockX, blockZ)) {
			return input.sampleValue(context, blockX, blockY, blockZ);
		}

		return (float) periodizer.sample(blockX, blockY, blockZ, (x, y, z) -> input.sampleValue(context, (int) x, (int) y, (int) z));
	}
}
