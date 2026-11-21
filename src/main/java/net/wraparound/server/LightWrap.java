package net.wraparound.server;

import net.minecraft.core.BlockPos;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.chunk.LightChunkGetter;
import net.wraparound.Topology;
import net.wraparound.Transform;
import net.wraparound.Wrap;

public final class LightWrap {
	private LightWrap() {
	}

	public static Topology topology(LightChunkGetter source) {
		return source.getLevel() instanceof Level level && !level.isClientSide() ? Wrap.of(level) : Topology.FLAT;
	}

	public static long wrapNode(Topology topology, long node) {
		if (!topology.active()) {
			return node;
		}

		int x = BlockPos.getX(node);
		int z = BlockPos.getZ(node);
		if (topology.inDomainBlock(x, z)) {
			return node;
		}

		Transform t = topology.toDomainBlock(x, z);
		return BlockPos.asLong(t.blockX(x), BlockPos.getY(node), t.blockZ(z));
	}
}
