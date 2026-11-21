package net.wraparound.net;

import net.minecraft.core.registries.Registries;
import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.resources.ResourceKey;
import net.minecraft.world.level.Level;
import net.wraparound.Topology;
import net.wraparound.Wraparound;

/** Tells the client how a dimension wraps. */
public record TopologyPayload(ResourceKey<Level> dimension, Topology topology) implements CustomPacketPayload {
	public static final Type<TopologyPayload> TYPE = new Type<>(Wraparound.id("topology"));
	public static final StreamCodec<RegistryFriendlyByteBuf, TopologyPayload> CODEC = StreamCodec.of(
		(buf, payload) -> {
			buf.writeResourceKey(payload.dimension);
			payload.topology.write(buf);
		},
		buf -> new TopologyPayload(buf.readResourceKey(Registries.DIMENSION), Topology.read(buf))
	);

	@Override
	public Type<TopologyPayload> type() {
		return TYPE;
	}
}
