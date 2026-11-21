package net.wraparound.mixin.server;

import net.minecraft.server.level.DistanceManager;
import net.wraparound.Topology;
import net.wraparound.server.TopologyAware;
import org.spongepowered.asm.mixin.Final;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;

/** The per-player trackers (natural spawning and view tickets) follow their distance manager. */
@Mixin(targets = "net.minecraft.server.level.DistanceManager$FixedPlayerDistanceChunkTracker")
public class PlayerDistanceTrackerMixin extends ChunkTrackerMixin {
	@Shadow
	@Final
	DistanceManager this$0;

	@Override
	public Topology wraparound$topology() {
		return ((TopologyAware) this.this$0).wraparound$topology();
	}
}
