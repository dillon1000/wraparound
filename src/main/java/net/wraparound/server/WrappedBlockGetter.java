package net.wraparound.server;

import net.minecraft.core.BlockPos;
import net.minecraft.world.level.BlockGetter;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.material.FluidState;
import org.jspecify.annotations.Nullable;

/** Collision lookups outside the domain read through the level, which maps them inside. */
public record WrappedBlockGetter(Level level) implements BlockGetter {
	@Override
	public @Nullable BlockEntity getBlockEntity(BlockPos pos) {
		return level.getBlockEntity(pos);
	}

	@Override
	public BlockState getBlockState(BlockPos pos) {
		return level.getBlockState(pos);
	}

	@Override
	public FluidState getFluidState(BlockPos pos) {
		return level.getFluidState(pos);
	}

	@Override
	public int getHeight() {
		return level.getHeight();
	}

	@Override
	public int getMinY() {
		return level.getMinY();
	}
}
