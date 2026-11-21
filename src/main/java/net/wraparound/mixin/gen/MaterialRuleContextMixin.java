package net.wraparound.mixin.gen;

import com.llamalad7.mixinextras.injector.wrapoperation.Operation;
import com.llamalad7.mixinextras.injector.wrapoperation.WrapOperation;
import net.minecraft.resources.ResourceKey;
import net.minecraft.world.level.levelgen.RandomState;
import net.minecraft.world.level.levelgen.material.MaterialRuleContext;
import net.minecraft.world.level.levelgen.synth.Noise;
import net.minecraft.world.level.levelgen.synth.NormalNoise;
import net.wraparound.gen.GenHooks;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;

/** Noise conditions in surface rules read noise at block coordinates. */
@Mixin(MaterialRuleContext.class)
public class MaterialRuleContextMixin {
	@WrapOperation(
		method = {"createNoiseSampler2d", "createNoiseSampler3d"},
		at = @At(value = "INVOKE", target = "Lnet/minecraft/world/level/levelgen/RandomState;getOrCreateNoise(Lnet/minecraft/resources/ResourceKey;)Lnet/minecraft/world/level/levelgen/synth/Noise;")
	)
	private Noise wraparound$wrapRuleNoise(RandomState randomState, ResourceKey<NormalNoise> key, Operation<Noise> original) {
		return GenHooks.wrapNoise(original.call(randomState, key), 1.0, GenHooks.periodizer(randomState));
	}
}
