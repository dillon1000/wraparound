package net.wraparound.server;

import java.util.ArrayList;
import java.util.List;
import net.fabricmc.fabric.api.entity.event.v1.ServerPlayerEvents;
import net.fabricmc.fabric.api.networking.v1.ServerPlayNetworking;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.Mob;
import net.minecraft.world.level.pathfinder.Node;
import net.minecraft.world.level.pathfinder.Path;
import net.minecraft.world.phys.Vec3;
import net.wraparound.Topology;
import net.wraparound.Transform;
import net.wraparound.Wrap;
import net.wraparound.net.FramePayload;

/**
 * Keeps every entity inside the fundamental domain on the server.
 *
 * <p>Each player also has a frame: the transform from the client's unrolled coordinates to the
 * server's. The client never sees itself teleport at a seam. Instead the server moves its copy of the
 * player back into the domain and updates the frame, and the client keeps walking in a straight line.
 */
public final class ServerWrap {
	private ServerWrap() {
	}

	public interface FrameHolder {
		Transform wraparound$clientToServer();

		void wraparound$setClientToServer(Transform transform);
	}

	public static void init() {
		ServerPlayerEvents.AFTER_RESPAWN.register((oldPlayer, newPlayer, alive) -> resetFrame(newPlayer));
	}

	public static Transform clientToServer(ServerPlayer player) {
		return ((FrameHolder) player).wraparound$clientToServer();
	}

	public static void resetFrame(ServerPlayer player) {
		setFrame(player, Transform.IDENTITY);
	}

	private static void setFrame(ServerPlayer player, Transform clientToServer) {
		((FrameHolder) player).wraparound$setClientToServer(clientToServer);
		if (ServerPlayNetworking.canSend(player, FramePayload.TYPE)) {
			ServerPlayNetworking.send(player, new FramePayload(clientToServer.inverse()));
		}
	}

	/** Called after an entity has moved. Moves it, its vehicle and passengers back into the domain. */
	public static void wrapIfOutside(Entity entity) {
		Entity root = entity.getRootVehicle();
		if (!(root.level() instanceof ServerLevel level) || root.isRemoved()) {
			return;
		}

		Topology topology = Wrap.of(level);
		if (!topology.active() || topology.inDomain(root.getX(), root.getZ())) {
			return;
		}

		Transform w = topology.toDomain(root.getX(), root.getZ());
		List<Entity> all = new ArrayList<>();
		all.add(root);
		for (Entity passenger : root.getIndirectPassengers()) {
			all.add(passenger);
		}
		for (Entity e : all) {
			move(e, w);
		}

		for (Entity e : all) {
			if (e instanceof ServerPlayer player) {
				((ServerGamePacketListenerAccess) player.connection).wraparound$shift(w);
				player.level().getChunkSource().move(player);
				setFrame(player, w.compose(clientToServer(player)));
			}
		}
	}

	/** A mob halfway along a path keeps following it after being moved back into the domain. */
	private static void movePath(Mob mob, Transform w) {
		var navigation = mob.getNavigation();
		Path path = navigation.getPath();
		if (path == null || path.isDone()) {
			return;
		}

		List<Node> nodes = new ArrayList<>(path.getNodeCount());
		for (int i = 0; i < path.getNodeCount(); i++) {
			Node node = path.getNode(i);
			nodes.add(node.cloneAndMove(w.blockX(node.x), node.y, w.blockZ(node.z)));
		}

		Path moved = new Path(nodes, w.apply(path.getTarget()), path.canReach());
		moved.setNextNodeIndex(path.getNextNodeIndex());
		double speed = ((net.wraparound.mixin.server.PathNavigationAccessor) navigation).wraparound$speedModifier();
		navigation.moveTo(moved, speed);
	}

	private static void move(Entity e, Transform w) {
		Vec3 pos = w.apply(e.position());
		float yaw = w.yaw(e.getYRot());
		Vec3 velocity = w.applyVector(e.getDeltaMovement());
		if (e instanceof ServerPlayer) {
			e.absSnapTo(pos.x, pos.y, pos.z, yaw, e.getXRot());
		} else {
			e.snapTo(pos.x, pos.y, pos.z, yaw, e.getXRot());
		}

		e.setDeltaMovement(velocity);
		if (e instanceof LivingEntity living) {
			living.setYHeadRot(w.yaw(living.getYHeadRot()));
			living.setYBodyRot(w.yaw(living.yBodyRot));
		}

		if (e instanceof Mob mob) {
			movePath(mob, w);
		}
	}
}
