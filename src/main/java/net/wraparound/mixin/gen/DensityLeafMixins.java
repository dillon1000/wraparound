package net.wraparound.mixin.gen;

import com.llamalad7.mixinextras.injector.ModifyReturnValue;
import com.llamalad7.mixinextras.sugar.Local;
import net.minecraft.core.Direction;
import net.minecraft.world.level.levelgen.densityfunction.DensityFunction;
import net.minecraft.world.level.levelgen.densityfunction.DensitySampler;
import net.minecraft.world.level.levelgen.densityfunction.generator.DistanceToPointFunction;
import net.minecraft.world.level.levelgen.densityfunction.generator.GradientFunction;
import net.minecraft.world.level.levelgen.densityfunction.generator.NoiseFunction;
import net.minecraft.world.level.levelgen.synth.BlendedNoise;
import net.wraparound.gen.GenHooks;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;

/** Every density function leaf that reads x or z is wrapped so the field respects the gluing. */
public final class DensityLeafMixins {
	private DensityLeafMixins() {
	}

	private static final String COMPILE =
		"compileSampler(Lnet/minecraft/world/level/levelgen/densityfunction/DensityFunction$CompileContext;)Lnet/minecraft/world/level/levelgen/densityfunction/DensitySampler;";

	@Mixin(NoiseFunction.class)
	public static class Noise {
		@ModifyReturnValue(method = COMPILE, at = @At("RETURN"))
		private DensitySampler wraparound$wrap(DensitySampler sampler, @Local(argsOnly = true) DensityFunction.CompileContext context) {
			return GenHooks.wrapLeaf((DensityFunction) (Object) this, sampler, context);
		}
	}

	@Mixin(targets = "net.minecraft.world.level.levelgen.densityfunction.generator.ShiftNoiseFunction$Shift")
	public static class Shift {
		@ModifyReturnValue(method = COMPILE, at = @At("RETURN"))
		private DensitySampler wraparound$wrap(DensitySampler sampler, @Local(argsOnly = true) DensityFunction.CompileContext context) {
			return GenHooks.wrapLeaf((DensityFunction) (Object) this, sampler, context);
		}
	}

	@Mixin(targets = "net.minecraft.world.level.levelgen.densityfunction.generator.ShiftNoiseFunction$ShiftA")
	public static class ShiftA {
		@ModifyReturnValue(method = COMPILE, at = @At("RETURN"))
		private DensitySampler wraparound$wrap(DensitySampler sampler, @Local(argsOnly = true) DensityFunction.CompileContext context) {
			return GenHooks.wrapLeaf((DensityFunction) (Object) this, sampler, context);
		}
	}

	@Mixin(targets = "net.minecraft.world.level.levelgen.densityfunction.generator.ShiftNoiseFunction$ShiftB")
	public static class ShiftB {
		@ModifyReturnValue(method = COMPILE, at = @At("RETURN"))
		private DensitySampler wraparound$wrap(DensitySampler sampler, @Local(argsOnly = true) DensityFunction.CompileContext context) {
			return GenHooks.wrapLeaf((DensityFunction) (Object) this, sampler, context);
		}
	}

	@Mixin(BlendedNoise.class)
	public static class Blended {
		@ModifyReturnValue(method = COMPILE, at = @At("RETURN"))
		private DensitySampler wraparound$wrap(DensitySampler sampler, @Local(argsOnly = true) DensityFunction.CompileContext context) {
			return GenHooks.wrapLeaf((DensityFunction) (Object) this, sampler, context);
		}
	}

	@Mixin(DistanceToPointFunction.class)
	public static class DistanceToPoint {
		@ModifyReturnValue(method = COMPILE, at = @At("RETURN"))
		private DensitySampler wraparound$wrap(DensitySampler sampler, @Local(argsOnly = true) DensityFunction.CompileContext context) {
			return GenHooks.wrapLeaf((DensityFunction) (Object) this, sampler, context);
		}
	}

	@Mixin(GradientFunction.class)
	public static class Gradient {
		@ModifyReturnValue(method = COMPILE, at = @At("RETURN"))
		private DensitySampler wraparound$wrap(DensitySampler sampler, @Local(argsOnly = true) DensityFunction.CompileContext context) {
			GradientFunction self = (GradientFunction) (Object) this;
			return self.axis() == Direction.Axis.Y ? sampler : GenHooks.wrapLeaf(self, sampler, context);
		}
	}
}
