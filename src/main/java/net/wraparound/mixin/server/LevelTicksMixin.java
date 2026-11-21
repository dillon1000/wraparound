package net.wraparound.mixin.server;

import net.minecraft.core.BlockPos;
import net.minecraft.world.ticks.LevelTicks;
import net.minecraft.world.ticks.ScheduledTick;
import net.wraparound.Topology;
import net.wraparound.server.TopologyAware;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Unique;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.ModifyVariable;

/** A tick scheduled outside the domain (say, water flowing over a seam) runs at the original block. */
@Mixin(LevelTicks.class)
public class LevelTicksMixin<T> implements TopologyAware {
	@Unique
	private Topology wraparound$topology = Topology.FLAT;

	@Override
	public Topology wraparound$topology() {
		return this.wraparound$topology;
	}

	@Override
	public void wraparound$setTopology(Topology topology) {
		this.wraparound$topology = topology;
	}

	@ModifyVariable(method = "schedule", at = @At("HEAD"), argsOnly = true)
	private ScheduledTick<T> wraparound$wrapTick(ScheduledTick<T> tick) {
		Topology topology = this.wraparound$topology;
		if (topology.active() && !topology.inDomain(tick.pos())) {
			return new ScheduledTick<>(tick.type(), topology.wrap(tick.pos()), tick.triggerTick(), tick.priority(), tick.subTickOrder());
		}

		return tick;
	}

	@ModifyVariable(method = {"hasScheduledTick", "willTickThisTick"}, at = @At("HEAD"), argsOnly = true)
	private BlockPos wraparound$wrapQuery(BlockPos pos) {
		Topology topology = this.wraparound$topology;
		return topology.active() && !topology.inDomain(pos) ? topology.wrap(pos) : pos;
	}
}
