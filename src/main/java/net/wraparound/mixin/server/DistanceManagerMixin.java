package net.wraparound.mixin.server;

import net.minecraft.core.SectionPos;
import net.minecraft.server.level.DistanceManager;
import net.minecraft.server.level.SimulationChunkTracker;
import net.wraparound.Topology;
import net.wraparound.server.TopologyAware;
import org.spongepowered.asm.mixin.Final;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.Unique;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.ModifyVariable;

/**
 * Player and simulation distances wrap. Chunk loading levels do not, so chunks just outside the domain
 * can still be generated as neighbours of chunks inside it.
 */
@Mixin(DistanceManager.class)
public abstract class DistanceManagerMixin implements TopologyAware {
	@Shadow
	@Final
	private SimulationChunkTracker simulationChunkTracker;

	@Unique
	private Topology wraparound$topology = Topology.FLAT;

	@Override
	public Topology wraparound$topology() {
		return this.wraparound$topology;
	}

	@Override
	public void wraparound$setTopology(Topology topology) {
		this.wraparound$topology = topology;
		((TopologyAware) this.simulationChunkTracker).wraparound$setTopology(topology);
	}

	@ModifyVariable(method = {"addPlayer", "removePlayer"}, at = @At("HEAD"), argsOnly = true)
	private SectionPos wraparound$wrapPlayerSection(SectionPos pos) {
		Topology topology = this.wraparound$topology;
		if (topology.active() && !topology.inDomainChunk(pos.x(), pos.z())) {
			return topology.toDomainChunk(pos.x(), pos.z()).apply(pos);
		}

		return pos;
	}
}
