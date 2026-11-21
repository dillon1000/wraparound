package net.wraparound.mixin;

import net.minecraft.world.level.Level;
import net.wraparound.Topology;
import net.wraparound.Wrap;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Unique;

@Mixin(Level.class)
public class LevelTopologyMixin implements Wrap.Holder {
	@Unique
	private Topology wraparound$topology = Topology.FLAT;

	@Override
	public Topology wraparound$topology() {
		return wraparound$topology;
	}

	@Override
	public void wraparound$setTopology(Topology topology) {
		this.wraparound$topology = topology;
	}
}
