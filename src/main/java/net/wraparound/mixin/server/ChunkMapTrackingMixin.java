package net.wraparound.mixin.server;

import it.unimi.dsi.fastutil.longs.LongIterator;
import it.unimi.dsi.fastutil.longs.LongOpenHashSet;
import net.minecraft.network.protocol.game.ClientboundSetChunkCacheCenterPacket;
import net.minecraft.server.level.ChunkMap;
import net.minecraft.server.level.ChunkTrackingView;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.level.ChunkPos;
import net.minecraft.world.phys.Vec3;
import net.wraparound.Topology;
import net.wraparound.Wrap;
import net.wraparound.server.TopologyAware;
import net.wraparound.server.WrappedTrackingView;
import org.spongepowered.asm.mixin.Final;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.Unique;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.Redirect;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

@Mixin(ChunkMap.class)
public abstract class ChunkMapTrackingMixin {
	@Shadow
	@Final
	private ServerLevel level;

	@Shadow
	public abstract net.minecraft.server.level.DistanceManager getDistanceManager();

	@Shadow
	private int getPlayerViewDistance(ServerPlayer player) {
		throw new AssertionError();
	}

	@Shadow
	private void markChunkPendingToSend(ServerPlayer player, ChunkPos pos) {
		throw new AssertionError();
	}

	@Shadow
	private static void dropChunk(ServerPlayer player, ChunkPos pos) {
		throw new AssertionError();
	}

	@Inject(method = "<init>", at = @At("RETURN"))
	private void wraparound$shareTopology(CallbackInfo ci) {
		((TopologyAware) getDistanceManager()).wraparound$setTopology(Wrap.of(this.level));
	}

	@Inject(method = "updateChunkTracking", at = @At("HEAD"), cancellable = true)
	private void wraparound$updateWrapped(ServerPlayer player, CallbackInfo ci) {
		Topology topology = Wrap.of(this.level);
		if (!topology.active()) {
			return;
		}

		WrappedTrackingView view = WrappedTrackingView.of(topology, player.chunkPosition(), getPlayerViewDistance(player));
		if (!view.equals(player.getChunkTrackingView())) {
			wraparound$apply(player, view);
		}

		ci.cancel();
	}

	@Inject(method = "applyChunkTrackingView", at = @At("HEAD"), cancellable = true)
	private void wraparound$applyWrapped(ServerPlayer player, ChunkTrackingView next, CallbackInfo ci) {
		if (Wrap.of(this.level).active()) {
			wraparound$apply(player, next);
			ci.cancel();
		}
	}

	@Unique
	private void wraparound$apply(ServerPlayer player, ChunkTrackingView next) {
		if (player.level() != this.level) {
			return;
		}

		ChunkTrackingView previous = player.getChunkTrackingView();
		if (next instanceof WrappedTrackingView to && !(previous instanceof WrappedTrackingView from && from.center().equals(to.center()))) {
			player.connection.send(new ClientboundSetChunkCacheCenterPacket(to.center().x(), to.center().z()));
		}

		LongOpenHashSet before = wraparound$set(previous);
		LongOpenHashSet after = wraparound$set(next);
		player.setChunkTrackingView(next);

		for (LongIterator it = after.iterator(); it.hasNext(); ) {
			long pos = it.nextLong();
			if (!before.contains(pos)) {
				markChunkPendingToSend(player, ChunkPos.unpack(pos));
			}
		}

		for (LongIterator it = before.iterator(); it.hasNext(); ) {
			long pos = it.nextLong();
			if (!after.contains(pos)) {
				dropChunk(player, ChunkPos.unpack(pos));
			}
		}
	}

	@Unique
	private static LongOpenHashSet wraparound$set(ChunkTrackingView view) {
		if (view instanceof WrappedTrackingView wrapped) {
			return wrapped.toSet();
		}

		LongOpenHashSet set = new LongOpenHashSet();
		view.forEach(pos -> set.add(pos.pack()));
		return set;
	}

	@Redirect(
		method = "playerIsCloseEnoughForSpawning",
		at = @At(value = "INVOKE", target = "Lnet/minecraft/server/level/ChunkMap;euclideanDistanceSquared(Lnet/minecraft/world/level/ChunkPos;Lnet/minecraft/world/phys/Vec3;)D")
	)
	private double wraparound$spawnDistance(ChunkPos pos, Vec3 playerPos) {
		Topology topology = Wrap.of(this.level);
		if (topology.active()) {
			double cx = (pos.x() << 4) + 8;
			double cz = (pos.z() << 4) + 8;
			Vec3 near = topology.nearest(playerPos.x, playerPos.z, cx, cz).apply(playerPos);
			double dx = near.x - cx;
			double dz = near.z - cz;
			return dx * dx + dz * dz;
		}

		return euclideanDistanceSquared(pos, playerPos);
	}

	@Shadow
	private static double euclideanDistanceSquared(ChunkPos pos, Vec3 position) {
		throw new AssertionError();
	}

	@Redirect(
		method = "playerIsCloseEnoughTo",
		at = @At(value = "INVOKE", target = "Lnet/minecraft/world/phys/Vec3;distanceTo(Lnet/minecraft/world/phys/Vec3;)D")
	)
	private double wraparound$closeDistance(Vec3 playerPos, Vec3 target) {
		Topology topology = Wrap.of(this.level);
		if (topology.active()) {
			return Math.sqrt(topology.distanceSqr(target.x, target.y, target.z, playerPos.x, playerPos.y, playerPos.z));
		}

		return playerPos.distanceTo(target);
	}
}
