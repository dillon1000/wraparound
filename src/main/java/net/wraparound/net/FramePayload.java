package net.wraparound.net;

import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.wraparound.Transform;
import net.wraparound.Wraparound;

/**
 * The transform from the server's coordinates for this player to the client's unrolled coordinates.
 * Sent whenever the server quietly moves the player back into the fundamental domain.
 */
public record FramePayload(Transform serverToClient) implements CustomPacketPayload {
	public static final Type<FramePayload> TYPE = new Type<>(Wraparound.id("frame"));
	public static final StreamCodec<RegistryFriendlyByteBuf, FramePayload> CODEC = StreamCodec.of(
		(buf, payload) -> {
			buf.writeBoolean(payload.serverToClient.mirror());
			buf.writeInt(payload.serverToClient.tx());
			buf.writeInt(payload.serverToClient.tz());
		},
		buf -> new FramePayload(new Transform(buf.readBoolean(), buf.readInt(), buf.readInt()))
	);

	@Override
	public Type<FramePayload> type() {
		return TYPE;
	}
}
