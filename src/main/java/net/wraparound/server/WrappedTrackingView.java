package net.wraparound.server;

import it.unimi.dsi.fastutil.longs.LongOpenHashSet;
import java.util.function.Consumer;
import net.minecraft.server.level.ChunkTrackingView;
import net.minecraft.world.level.ChunkPos;
import net.wraparound.Topology;
import net.wraparound.Transform;

/**
 * The chunks a player can see, measured through the seams. Only chunks inside the fundamental domain
 * are ever tracked; positions outside it are mapped in before checking.
 */
public record WrappedTrackingView(Topology topology, ChunkPos center, int viewDistance) implements ChunkTrackingView {
	public static WrappedTrackingView of(Topology topology, ChunkPos rawCenter, int viewDistance) {
		Transform t = topology.toDomainChunk(rawCenter.x(), rawCenter.z());
		return new WrappedTrackingView(topology, t.apply(rawCenter), viewDistance);
	}

	@Override
	public boolean contains(int chunkX, int chunkZ, boolean includeNeighbors) {
		Transform near = topology.nearest((chunkX << 4) + 8, (chunkZ << 4) + 8, (center.x() << 4) + 8, (center.z() << 4) + 8);
		int x = near.chunkX(chunkX);
		int z = near.chunkZ(chunkZ);
		return ChunkTrackingView.isWithinDistance(center.x(), center.z(), viewDistance, x, z, includeNeighbors);
	}

	@Override
	public void forEach(Consumer<ChunkPos> consumer) {
		LongOpenHashSet seen = new LongOpenHashSet();
		int r = viewDistance + 1;
		for (int x = center.x() - r; x <= center.x() + r; x++) {
			for (int z = center.z() - r; z <= center.z() + r; z++) {
				if (!ChunkTrackingView.isWithinDistance(center.x(), center.z(), viewDistance, x, z, true)) {
					continue;
				}

				Transform t = topology.toDomainChunk(x, z);
				int dx = t.chunkX(x);
				int dz = t.chunkZ(z);
				if (seen.add(ChunkPos.pack(dx, dz))) {
					consumer.accept(new ChunkPos(dx, dz));
				}
			}
		}
	}

	public LongOpenHashSet toSet() {
		LongOpenHashSet set = new LongOpenHashSet();
		forEach(pos -> set.add(pos.pack()));
		return set;
	}
}
