package net.wraparound.mixin.server;

import net.minecraft.server.level.ServerPlayer;
import net.wraparound.Transform;
import net.wraparound.server.ServerWrap;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Unique;

@Mixin(ServerPlayer.class)
public class ServerPlayerFrameMixin implements ServerWrap.FrameHolder {
	@Unique
	private Transform wraparound$clientToServer = Transform.IDENTITY;

	@Override
	public Transform wraparound$clientToServer() {
		return this.wraparound$clientToServer;
	}

	@Override
	public void wraparound$setClientToServer(Transform transform) {
		this.wraparound$clientToServer = transform;
	}
}
