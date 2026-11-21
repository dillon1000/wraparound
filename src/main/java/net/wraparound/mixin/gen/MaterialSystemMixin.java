package net.wraparound.mixin.gen;

import com.llamalad7.mixinextras.injector.wrapoperation.Operation;
import com.llamalad7.mixinextras.injector.wrapoperation.WrapOperation;
import net.minecraft.resources.ResourceKey;
import net.minecraft.world.level.levelgen.RandomState;
import net.minecraft.world.level.levelgen.material.MaterialSystem;
import net.minecraft.world.level.levelgen.synth.Noise;
import net.minecraft.world.level.levelgen.synth.NormalNoise;
import net.wraparound.gen.GenHooks;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;

/** Surface depth, badlands bands and similar noises are read directly, so wrap them here. */
@Mixin(MaterialSystem.class)
public class MaterialSystemMixin {
	@WrapOperation(
		method = "<init>",
		at = @At(value = "INVOKE", target = "Lnet/minecraft/world/level/levelgen/RandomState;getOrCreateNoise(Lnet/minecraft/resources/ResourceKey;)Lnet/minecraft/world/level/levelgen/synth/Noise;")
	)
	private Noise wraparound$wrapSurfaceNoise(RandomState randomState, ResourceKey<NormalNoise> key, Operation<Noise> original) {
		return GenHooks.wrapNoise(original.call(randomState, key), GenHooks.surfaceNoiseScale(key), GenHooks.CONSTRUCTING.get());
	}
}
