package net.wraparound.mixin.server;

import net.minecraft.network.protocol.game.ServerboundAcceptTeleportationPacket;
import net.minecraft.network.protocol.game.ServerboundBlockEntityTagQueryPacket;
import net.minecraft.network.protocol.game.ServerboundInteractPacket;
import net.minecraft.network.protocol.game.ServerboundMovePlayerPacket;
import net.minecraft.network.protocol.game.ServerboundMoveVehiclePacket;
import net.minecraft.network.protocol.game.ServerboundPickItemFromBlockPacket;
import net.minecraft.network.protocol.game.ServerboundPlayerActionPacket;
import net.minecraft.network.protocol.game.ServerboundSignUpdatePacket;
import net.minecraft.network.protocol.game.ServerboundUseItemOnPacket;
import net.minecraft.network.protocol.game.ServerboundUseItemPacket;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.server.network.ServerGamePacketListenerImpl;
import net.minecraft.world.entity.PositionMoveRotation;
import net.minecraft.world.phys.BlockHitResult;
import net.minecraft.world.phys.Vec3;
import net.wraparound.Transform;
import net.wraparound.Wrap;
import net.wraparound.server.ServerGamePacketListenerAccess;
import net.wraparound.server.ServerWrap;
import org.jspecify.annotations.Nullable;
import org.spongepowered.asm.mixin.Final;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.Unique;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.ModifyVariable;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

/**
 * Clients speak in their unrolled coordinates. Everything they send that names a place is carried into
 * the server's frame for that player before the vanilla handler sees it.
 */
@Mixin(ServerGamePacketListenerImpl.class)
public abstract class ServerGamePacketListenerMixin implements ServerGamePacketListenerAccess {
	@Shadow
	public ServerPlayer player;

	@Shadow
	private double firstGoodX;

	@Shadow
	private double firstGoodZ;

	@Shadow
	private double lastGoodX;

	@Shadow
	private double lastGoodZ;

	@Shadow
	private double vehicleFirstGoodX;

	@Shadow
	private double vehicleFirstGoodZ;

	@Shadow
	private double vehicleLastGoodX;

	@Shadow
	private double vehicleLastGoodZ;

	@Shadow
	private @Nullable Vec3 awaitingPositionFromClient;

	@Override
	public void wraparound$shift(Transform t) {
		this.firstGoodX = t.x(this.firstGoodX);
		this.firstGoodZ = t.z(this.firstGoodZ);
		this.lastGoodX = t.x(this.lastGoodX);
		this.lastGoodZ = t.z(this.lastGoodZ);
		this.vehicleFirstGoodX = t.x(this.vehicleFirstGoodX);
		this.vehicleFirstGoodZ = t.z(this.vehicleFirstGoodZ);
		this.vehicleLastGoodX = t.x(this.vehicleLastGoodX);
		this.vehicleLastGoodZ = t.z(this.vehicleLastGoodZ);
		if (this.awaitingPositionFromClient != null) {
			this.awaitingPositionFromClient = t.apply(this.awaitingPositionFromClient);
		}
	}

	/** The client-to-server transform, or null when there is nothing to translate on this thread. */
	@Unique
	private @Nullable Transform wraparound$frame() {
		if (!wraparound$onServerThread() || !Wrap.server(this.player.level())) {
			return null;
		}

		Transform t = ServerWrap.clientToServer(this.player);
		return t.isIdentity() ? null : t;
	}

	@ModifyVariable(method = "handleMovePlayer", at = @At("HEAD"), argsOnly = true)
	private ServerboundMovePlayerPacket wraparound$movePlayer(ServerboundMovePlayerPacket packet) {
		Transform t = wraparound$frame();
		if (t == null) {
			return packet;
		}

		boolean pos = packet.hasPosition();
		boolean rot = packet.hasRotation();
		double x = t.x(packet.getX(0));
		double y = packet.getY(0);
		double z = t.z(packet.getZ(0));
		float yRot = t.yaw(packet.getYRot(0));
		float xRot = packet.getXRot(0);
		boolean ground = packet.isOnGround();
		boolean collision = packet.horizontalCollision();
		if (pos && rot) {
			return new ServerboundMovePlayerPacket.PosRot(x, y, z, yRot, xRot, ground, collision);
		} else if (pos) {
			return new ServerboundMovePlayerPacket.Pos(x, y, z, ground, collision);
		} else if (rot) {
			return new ServerboundMovePlayerPacket.Rot(yRot, xRot, ground, collision);
		}

		return packet;
	}

	@ModifyVariable(method = "handleMoveVehicle", at = @At("HEAD"), argsOnly = true)
	private ServerboundMoveVehiclePacket wraparound$moveVehicle(ServerboundMoveVehiclePacket packet) {
		Transform t = wraparound$frame();
		if (t == null) {
			return packet;
		}

		var moving = packet.movingTo();
		return new ServerboundMoveVehiclePacket(
			net.minecraft.core.PositionAndRotation.of(t.apply(moving.position()), t.yaw(moving.yRot()), moving.xRot()), packet.onGround()
		);
	}

	@ModifyVariable(method = "handleAcceptTeleportPacket", at = @At("HEAD"), argsOnly = true)
	private ServerboundAcceptTeleportationPacket wraparound$acceptTeleport(ServerboundAcceptTeleportationPacket packet) {
		Transform t = wraparound$frame();
		if (t == null) {
			return packet;
		}

		return new ServerboundAcceptTeleportationPacket(packet.id(), t.x(packet.x()), packet.y(), t.z(packet.z()), t.yaw(packet.yRot()), packet.xRot());
	}

	@ModifyVariable(method = "handleUseItemOn", at = @At("HEAD"), argsOnly = true)
	private ServerboundUseItemOnPacket wraparound$useItemOn(ServerboundUseItemOnPacket packet) {
		Transform t = wraparound$frame();
		if (t == null) {
			return packet;
		}

		BlockHitResult hit = packet.hitResult();
		BlockHitResult mapped = new BlockHitResult(
			t.apply(hit.getLocation()), t.apply(hit.getDirection()), t.apply(hit.getBlockPos()), hit.isInside(), hit.isWorldBorderHit()
		);
		return new ServerboundUseItemOnPacket(packet.hand(), mapped, packet.sequence());
	}

	@ModifyVariable(method = "handleUseItem", at = @At("HEAD"), argsOnly = true)
	private ServerboundUseItemPacket wraparound$useItem(ServerboundUseItemPacket packet) {
		Transform t = wraparound$frame();
		if (t == null) {
			return packet;
		}

		return new ServerboundUseItemPacket(packet.hand(), packet.sequence(), t.yaw(packet.yRot()), packet.xRot());
	}

	@ModifyVariable(method = "handlePlayerAction", at = @At("HEAD"), argsOnly = true)
	private ServerboundPlayerActionPacket wraparound$playerAction(ServerboundPlayerActionPacket packet) {
		Transform t = wraparound$frame();
		if (t == null) {
			return packet;
		}

		return new ServerboundPlayerActionPacket(packet.getAction(), t.apply(packet.getPos()), t.apply(packet.getDirection()), packet.getSequence());
	}

	@ModifyVariable(method = "handleSignUpdate", at = @At("HEAD"), argsOnly = true)
	private ServerboundSignUpdatePacket wraparound$signUpdate(ServerboundSignUpdatePacket packet) {
		Transform t = wraparound$frame();
		if (t == null) {
			return packet;
		}

		return new ServerboundSignUpdatePacket(t.apply(packet.pos()), packet.lines(), packet.slot());
	}

	@ModifyVariable(method = "handlePickItemFromBlock", at = @At("HEAD"), argsOnly = true)
	private ServerboundPickItemFromBlockPacket wraparound$pickItem(ServerboundPickItemFromBlockPacket packet) {
		Transform t = wraparound$frame();
		if (t == null) {
			return packet;
		}

		return new ServerboundPickItemFromBlockPacket(t.apply(packet.pos()), packet.includeData());
	}

	@ModifyVariable(method = "handleBlockEntityTagQuery", at = @At("HEAD"), argsOnly = true)
	private ServerboundBlockEntityTagQueryPacket wraparound$tagQuery(ServerboundBlockEntityTagQueryPacket packet) {
		Transform t = wraparound$frame();
		if (t == null) {
			return packet;
		}

		return new ServerboundBlockEntityTagQueryPacket(packet.getTransactionId(), t.apply(packet.getPos()));
	}

	@ModifyVariable(method = "handleInteract", at = @At("HEAD"), argsOnly = true)
	private ServerboundInteractPacket wraparound$interact(ServerboundInteractPacket packet) {
		Transform t = wraparound$frame();
		if (t == null || !t.mirror()) {
			return packet;
		}

		return new ServerboundInteractPacket(packet.entityId(), packet.hand(), t.applyVector(packet.location()), packet.usingSecondaryAction());
	}

	@Inject(method = {"handleMovePlayer", "handleMoveVehicle"}, at = @At("TAIL"))
	private void wraparound$wrapAfterMove(CallbackInfo ci) {
		if (wraparound$onServerThread()) {
			ServerWrap.wrapIfOutside(this.player);
		}
	}

	@Unique
	private boolean wraparound$onServerThread() {
		MinecraftServer server = this.player.level().getServer();
		return server != null && server.isSameThread();
	}
}
