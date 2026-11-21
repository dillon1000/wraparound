package net.wraparound.mixin.server;

import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.world.level.chunk.LightChunkGetter;
import net.minecraft.world.level.lighting.BlockLightEngine;
import net.minecraft.world.level.lighting.LightEngine;
import net.minecraft.world.level.lighting.SkyLightEngine;
import net.wraparound.Topology;
import net.wraparound.server.LightWrap;
import org.spongepowered.asm.mixin.Final;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.ModifyVariable;
import org.spongepowered.asm.mixin.injection.Redirect;

/** Light on the server spreads across seams, and reads outside the domain see the original. */
public final class LightEngineMixins {
	private LightEngineMixins() {
	}

	@Mixin(LightEngine.class)
	public static class Base {
		@Shadow
		@Final
		protected LightChunkGetter chunkSource;

		@ModifyVariable(method = "getLightValue", at = @At("HEAD"), argsOnly = true)
		private BlockPos wraparound$wrapRead(BlockPos pos) {
			Topology topology = LightWrap.topology(this.chunkSource);
			return topology.active() && !topology.inDomain(pos) ? topology.wrap(pos) : pos;
		}
	}

	@Mixin(BlockLightEngine.class)
	public static abstract class Block extends Base {
		@Redirect(method = {"propagateIncrease", "propagateDecrease"}, at = @At(value = "INVOKE", target = "Lnet/minecraft/core/BlockPos;offset(JLnet/minecraft/core/Direction;)J"))
		private long wraparound$step(long node, Direction direction) {
			return LightWrap.wrapNode(LightWrap.topology(this.chunkSource), BlockPos.offset(node, direction));
		}
	}

	@Mixin(SkyLightEngine.class)
	public static abstract class Sky extends Base {
		@Redirect(method = {"propagateIncrease", "propagateDecrease"}, at = @At(value = "INVOKE", target = "Lnet/minecraft/core/BlockPos;offset(JLnet/minecraft/core/Direction;)J"))
		private long wraparound$step(long node, Direction direction) {
			return LightWrap.wrapNode(LightWrap.topology(this.chunkSource), BlockPos.offset(node, direction));
		}
	}
}
