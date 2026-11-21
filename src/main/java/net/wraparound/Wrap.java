package net.wraparound;

import net.minecraft.world.level.Level;
import org.jspecify.annotations.Nullable;

/** Access to the topology attached to a level. */
public final class Wrap {
	private Wrap() {
	}

	public interface Holder {
		Topology wraparound$topology();

		void wraparound$setTopology(Topology topology);
	}

	public static Topology of(@Nullable Level level) {
		return level == null ? Topology.FLAT : ((Holder) level).wraparound$topology();
	}

	/** Whether this is a server level that wraps. The client never wraps lookups: its world is unrolled. */
	public static boolean server(@Nullable Level level) {
		return level != null && !level.isClientSide() && of(level).active();
	}
}
