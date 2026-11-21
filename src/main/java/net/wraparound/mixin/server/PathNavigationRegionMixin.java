package net.wraparound.mixin.server;

import net.minecraft.core.BlockPos;
import net.minecraft.world.level.BlockGetter;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.PathNavigationRegion;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.material.FluidState;
import net.wraparound.Wrap;
import net.wraparound.server.WrappedBlockGetter;
import org.spongepowered.asm.mixin.Final;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

/** Pathfinding reads the original blocks when a path crosses a seam. */
@Mixin(PathNavigationRegion.class)
public class PathNavigationRegionMixin {
	@Shadow
	@Final
	protected Level level;

	private boolean wraparound$outside(BlockPos pos) {
		return Wrap.server(this.level) && !Wrap.of(this.level).inDomain(pos);
	}

	@Inject(method = "getBlockState", at = @At("HEAD"), cancellable = true)
	private void wraparound$blockState(BlockPos pos, CallbackInfoReturnable<BlockState> cir) {
		if (wraparound$outside(pos)) {
			cir.setReturnValue(this.level.getBlockState(pos));
		}
	}

	@Inject(method = "getFluidState", at = @At("HEAD"), cancellable = true)
	private void wraparound$fluidState(BlockPos pos, CallbackInfoReturnable<FluidState> cir) {
		if (wraparound$outside(pos)) {
			cir.setReturnValue(this.level.getFluidState(pos));
		}
	}

	@Inject(method = "getChunkForCollisions", at = @At("HEAD"), cancellable = true)
	private void wraparound$collisions(int chunkX, int chunkZ, CallbackInfoReturnable<BlockGetter> cir) {
		if (Wrap.server(this.level) && !Wrap.of(this.level).inDomainChunk(chunkX, chunkZ)) {
			cir.setReturnValue(new WrappedBlockGetter(this.level));
		}
	}
}
