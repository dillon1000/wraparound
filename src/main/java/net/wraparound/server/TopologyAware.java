package net.wraparound.server;

import net.wraparound.Topology;

public interface TopologyAware {
	Topology wraparound$topology();

	void wraparound$setTopology(Topology topology);
}
