package net.wraparound.mixin.server;

import java.util.List;
import net.minecraft.network.protocol.Packet;
import net.minecraft.resources.ResourceKey;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.server.players.PlayerList;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.level.Level;
import net.wraparound.Topology;
import net.wraparound.Wrap;
import org.jspecify.annotations.Nullable;
import org.spongepowered.asm.mixin.Final;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

/** Sounds and effects reach players who are near them through a seam. */
@Mixin(PlayerList.class)
public class PlayerListBroadcastMixin {
	@Shadow
	@Final
	private MinecraftServer server;

	@Shadow
	@Final
	private List<ServerPlayer> players;

	@Inject(method = "broadcast", at = @At("HEAD"), cancellable = true)
	private void wraparound$broadcastWrapped(
		@Nullable Player except, double x, double y, double z, double range, ResourceKey<Level> dimension, Packet<?> packet, CallbackInfo ci
	) {
		Topology topology = Wrap.of(this.server.getLevel(dimension));
		if (!topology.active()) {
			return;
		}

		for (int i = 0; i < this.players.size(); i++) {
			ServerPlayer player = this.players.get(i);
			if (player != except && player.level().dimension() == dimension
				&& topology.distanceSqr(player.getX(), player.getY(), player.getZ(), x, y, z) < range * range) {
				player.connection.send(packet);
			}
		}

		ci.cancel();
	}
}
