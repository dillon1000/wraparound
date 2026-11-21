package net.wraparound.server;

import net.wraparound.Transform;

public interface ServerGamePacketListenerAccess {
	/** Moves the movement-validation anchors along with a player who was wrapped. */
	void wraparound$shift(Transform transform);
}
