package net.wraparound.mixin.server;

import net.minecraft.server.level.ChunkTracker;
import net.minecraft.world.level.ChunkPos;
import net.wraparound.Topology;
import net.wraparound.Transform;
import net.wraparound.server.TopologyAware;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Unique;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Redirect;

/**
 * Lets player-distance and simulation-distance propagation flow across the seams, so a player standing
 * by an edge keeps the chunks on the other side loaded and ticking.
 */
@Mixin(ChunkTracker.class)
public class ChunkTrackerMixin implements TopologyAware {
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

	@Redirect(
		method = {"checkNeighborsAfterUpdate", "getComputedLevel"},
		at = @At(value = "INVOKE", target = "Lnet/minecraft/world/level/ChunkPos;pack(II)J")
	)
	private long wraparound$packWrapped(int x, int z) {
		Topology topology = ((TopologyAware) this).wraparound$topology();
		if (topology.active() && !topology.inDomainChunk(x, z)) {
			Transform t = topology.toDomainChunk(x, z);
			return ChunkPos.pack(t.chunkX(x), t.chunkZ(z));
		}

		return ChunkPos.pack(x, z);
	}
}
