package net.wraparound.mixin.server;

import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.level.BlockGetter;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.levelgen.Heightmap;
import net.minecraft.world.level.material.FluidState;
import net.wraparound.Topology;
import net.wraparound.Transform;
import net.wraparound.Wrap;
import net.wraparound.server.WrappedBlockGetter;
import org.jspecify.annotations.Nullable;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.Unique;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

/**
 * On the server, every place outside the fundamental domain is a copy of a place inside it. Reads and
 * writes there go to the original, with block states mirrored where the gluing flips.
 */
@Mixin(Level.class)
public abstract class LevelWrapMixin {
	@Shadow
	public abstract BlockState getBlockState(BlockPos pos);

	@Shadow
	public abstract FluidState getFluidState(BlockPos pos);

	@Shadow
	public abstract boolean setBlock(BlockPos pos, BlockState state, int flags, int updateLimit);

	@Shadow
	public abstract @Nullable BlockEntity getBlockEntity(BlockPos pos);

	@Shadow
	public abstract void removeBlockEntity(BlockPos pos);

	@Shadow
	public abstract boolean isLoaded(BlockPos pos);

	@Shadow
	public abstract boolean loadedAndEntityCanStandOnFace(BlockPos pos, Entity entity, Direction faceDirection);

	@Shadow
	public abstract int getHeight(Heightmap.Types type, int x, int z);

	/** The transform into the domain if this is a wrapping server level and pos is outside, else null. */
	@Unique
	private @Nullable Transform wraparound$outside(BlockPos pos) {
		Level self = (Level) (Object) this;
		if (!Wrap.server(self)) {
			return null;
		}

		Topology topology = Wrap.of(self);
		return topology.inDomain(pos) ? null : topology.toDomainBlock(pos.getX(), pos.getZ());
	}

	@Inject(method = "getBlockState", at = @At("HEAD"), cancellable = true)
	private void wraparound$getBlockState(BlockPos pos, CallbackInfoReturnable<BlockState> cir) {
		Transform t = wraparound$outside(pos);
		if (t != null) {
			cir.setReturnValue(t.apply(getBlockState(t.apply(pos))));
		}
	}

	@Inject(method = "getFluidState", at = @At("HEAD"), cancellable = true)
	private void wraparound$getFluidState(BlockPos pos, CallbackInfoReturnable<FluidState> cir) {
		Transform t = wraparound$outside(pos);
		if (t != null) {
			cir.setReturnValue(getFluidState(t.apply(pos)));
		}
	}

	@Inject(method = "setBlock(Lnet/minecraft/core/BlockPos;Lnet/minecraft/world/level/block/state/BlockState;II)Z", at = @At("HEAD"), cancellable = true)
	private void wraparound$setBlock(BlockPos pos, BlockState state, int flags, int updateLimit, CallbackInfoReturnable<Boolean> cir) {
		Transform t = wraparound$outside(pos);
		if (t != null) {
			cir.setReturnValue(setBlock(t.apply(pos), t.apply(state), flags, updateLimit));
		}
	}

	@Inject(method = "getBlockEntity", at = @At("HEAD"), cancellable = true)
	private void wraparound$getBlockEntity(BlockPos pos, CallbackInfoReturnable<BlockEntity> cir) {
		Transform t = wraparound$outside(pos);
		if (t != null) {
			cir.setReturnValue(getBlockEntity(t.apply(pos)));
		}
	}

	@Inject(method = "removeBlockEntity", at = @At("HEAD"), cancellable = true)
	private void wraparound$removeBlockEntity(BlockPos pos, org.spongepowered.asm.mixin.injection.callback.CallbackInfo ci) {
		Transform t = wraparound$outside(pos);
		if (t != null) {
			removeBlockEntity(t.apply(pos));
			ci.cancel();
		}
	}

	@Inject(method = "isLoaded", at = @At("HEAD"), cancellable = true)
	private void wraparound$isLoaded(BlockPos pos, CallbackInfoReturnable<Boolean> cir) {
		Transform t = wraparound$outside(pos);
		if (t != null) {
			cir.setReturnValue(isLoaded(t.apply(pos)));
		}
	}

	@Inject(method = "loadedAndEntityCanStandOnFace", at = @At("HEAD"), cancellable = true)
	private void wraparound$standOn(BlockPos pos, Entity entity, Direction face, CallbackInfoReturnable<Boolean> cir) {
		Transform t = wraparound$outside(pos);
		if (t != null) {
			cir.setReturnValue(t.apply(getBlockState(t.apply(pos))).entityCanStandOnFace((Level) (Object) this, pos, entity, face));
		}
	}

	@Inject(method = "getHeight", at = @At("HEAD"), cancellable = true)
	private void wraparound$getHeight(Heightmap.Types type, int x, int z, CallbackInfoReturnable<Integer> cir) {
		Level self = (Level) (Object) this;
		if (Wrap.server(self)) {
			Topology topology = Wrap.of(self);
			if (!topology.inDomainBlock(x, z)) {
				Transform t = topology.toDomainBlock(x, z);
				cir.setReturnValue(getHeight(type, t.blockX(x), t.blockZ(z)));
			}
		}
	}

	@Inject(method = "getChunkForCollisions", at = @At("HEAD"), cancellable = true)
	private void wraparound$collisions(int chunkX, int chunkZ, CallbackInfoReturnable<BlockGetter> cir) {
		Level self = (Level) (Object) this;
		if (Wrap.server(self) && !Wrap.of(self).inDomainChunk(chunkX, chunkZ)) {
			cir.setReturnValue(new WrappedBlockGetter(self));
		}
	}
}
