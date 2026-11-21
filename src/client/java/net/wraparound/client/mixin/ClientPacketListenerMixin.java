package net.wraparound.client.mixin;

import com.llamalad7.mixinextras.injector.wrapoperation.Operation;
import com.llamalad7.mixinextras.injector.wrapoperation.WrapOperation;
import com.llamalad7.mixinextras.sugar.Local;
import java.util.Collection;
import java.util.List;
import java.util.Set;
import net.minecraft.client.Minecraft;
import net.minecraft.client.multiplayer.ClientLevel;
import net.minecraft.client.multiplayer.ClientPacketListener;
import net.minecraft.core.Direction;
import net.minecraft.core.PositionAndRotation;
import net.minecraft.network.protocol.game.ClientboundBlockDestructionPacket;
import net.minecraft.network.protocol.game.ClientboundBlockEntityDataPacket;
import net.minecraft.network.protocol.game.ClientboundBlockEventPacket;
import net.minecraft.network.protocol.game.ClientboundBlockUpdatePacket;
import net.minecraft.network.protocol.game.ClientboundChunksBiomesPacket;
import net.minecraft.network.protocol.game.ClientboundEntityPositionSyncPacket;
import net.minecraft.network.protocol.game.ClientboundExplodePacket;
import net.minecraft.network.protocol.game.ClientboundForgetLevelChunkPacket;
import net.minecraft.network.protocol.game.ClientboundLevelChunkWithLightPacket;
import net.minecraft.network.protocol.game.ClientboundLevelEventPacket;
import net.minecraft.network.protocol.game.ClientboundLevelParticlesPacket;
import net.minecraft.network.protocol.game.ClientboundLightUpdatePacket;
import net.minecraft.network.protocol.game.ClientboundMoveVehiclePacket;
import net.minecraft.network.protocol.game.ClientboundOpenSignEditorPacket;
import net.minecraft.network.protocol.game.ClientboundPlayerPositionPacket;
import net.minecraft.network.protocol.game.ClientboundSectionBlocksUpdatePacket;
import net.minecraft.network.protocol.game.ClientboundSetChunkCacheCenterPacket;
import net.minecraft.network.protocol.game.ClientboundSoundPacket;
import net.minecraft.util.ProblemReporter;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.PositionMoveRotation;
import net.minecraft.world.entity.PositionPath;
import net.minecraft.world.entity.PositionStep;
import net.minecraft.world.entity.Relative;
import net.minecraft.world.entity.vehicle.minecart.AbstractMinecart;
import net.minecraft.world.entity.vehicle.minecart.NewMinecartBehavior;
import net.minecraft.world.level.ChunkPos;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.piston.PistonBaseBlock;
import net.minecraft.world.level.storage.TagValueInput;
import net.minecraft.world.phys.Vec3;
import net.wraparound.Transform;
import net.wraparound.client.ClientWrap;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.Unique;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.ModifyVariable;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

/**
 * Translates everything the server says, in the server's frame, into the client's unrolled world.
 */
@Mixin(ClientPacketListener.class)
public abstract class ClientPacketListenerMixin {
	private static final String ENSURE =
		"Lnet/minecraft/network/protocol/PacketUtils;ensureRunningOnSameThread(Lnet/minecraft/network/protocol/Packet;Lnet/minecraft/network/PacketListener;Lnet/minecraft/network/PacketProcessor;)V";

	@Shadow
	private ClientLevel level;

	@Unique
	private boolean wraparound$active() {
		return this.level != null && ClientWrap.active(this.level);
	}

	@Unique
	private boolean wraparound$mainThread() {
		return Minecraft.getInstance().isSameThread() && wraparound$active();
	}

	// ---- Session ----

	@Inject(method = {"handleLogin", "handleRespawn"}, at = @At(value = "INVOKE", target = ENSURE, shift = At.Shift.AFTER))
	private void wraparound$resetFrame(CallbackInfo ci) {
		ClientWrap.resetFrame();
	}

	// ---- Chunks, blocks and light ----

	@Inject(method = "handleLevelChunkWithLight", at = @At(value = "INVOKE", target = ENSURE, shift = At.Shift.AFTER), cancellable = true)
	private void wraparound$chunk(ClientboundLevelChunkWithLightPacket packet, CallbackInfo ci) {
		if (wraparound$active()) {
			ClientWrap.onChunk(this.level, packet.x(), packet.z(), packet.chunkData(), packet.lightData());
			ci.cancel();
		}
	}

	@Inject(method = "handleForgetLevelChunk", at = @At(value = "INVOKE", target = ENSURE, shift = At.Shift.AFTER), cancellable = true)
	private void wraparound$forget(ClientboundForgetLevelChunkPacket packet, CallbackInfo ci) {
		if (wraparound$active()) {
			ClientWrap.onForget(this.level, packet.pos());
			ci.cancel();
		}
	}

	@Inject(method = "handleLightUpdatePacket", at = @At(value = "INVOKE", target = ENSURE, shift = At.Shift.AFTER), cancellable = true)
	private void wraparound$light(ClientboundLightUpdatePacket packet, CallbackInfo ci) {
		if (wraparound$active()) {
			ClientWrap.onLight(this.level, packet.x(), packet.z(), packet.lightData());
			ci.cancel();
		}
	}

	@Inject(method = "handleSetChunkCacheCenter", at = @At(value = "INVOKE", target = ENSURE, shift = At.Shift.AFTER), cancellable = true)
	private void wraparound$center(ClientboundSetChunkCacheCenterPacket packet, CallbackInfo ci) {
		if (wraparound$active()) {
			ClientWrap.followPlayer(this.level);
			ci.cancel();
		}
	}

	@Inject(method = "handleBlockUpdate", at = @At(value = "INVOKE", target = ENSURE, shift = At.Shift.AFTER), cancellable = true)
	private void wraparound$blockUpdate(ClientboundBlockUpdatePacket packet, CallbackInfo ci) {
		if (wraparound$active()) {
			ClientWrap.forEachBlockCopy(this.level, packet.getPos(), (t, pos) -> this.level.setServerVerifiedBlockState(pos, t.apply(packet.getBlockState()), 19));
			ci.cancel();
		}
	}

	@Inject(method = "handleChunkBlocksUpdate", at = @At(value = "INVOKE", target = ENSURE, shift = At.Shift.AFTER), cancellable = true)
	private void wraparound$sectionUpdate(ClientboundSectionBlocksUpdatePacket packet, CallbackInfo ci) {
		if (wraparound$active()) {
			packet.runUpdates((domainPos, state) -> ClientWrap.forEachBlockCopy(
				this.level, domainPos, (t, pos) -> this.level.setServerVerifiedBlockState(pos, t.apply(state), 19)
			));
			ci.cancel();
		}
	}

	@Inject(method = "handleBlockEntityData", at = @At(value = "INVOKE", target = ENSURE, shift = At.Shift.AFTER), cancellable = true)
	private void wraparound$blockEntity(ClientboundBlockEntityDataPacket packet, CallbackInfo ci) {
		if (wraparound$active()) {
			ClientWrap.forEachBlockCopy(this.level, packet.getPos(), (t, pos) -> this.level.getBlockEntity(pos, packet.getType())
				.ifPresent(be -> be.loadWithComponents(TagValueInput.create(ProblemReporter.DISCARDING, this.level.registryAccess(), packet.getTag()))));
			ci.cancel();
		}
	}

	@Inject(method = "handleBlockEvent", at = @At(value = "INVOKE", target = ENSURE, shift = At.Shift.AFTER), cancellable = true)
	private void wraparound$blockEvent(ClientboundBlockEventPacket packet, CallbackInfo ci) {
		if (wraparound$active()) {
			Block block = packet.getBlock();
			ClientWrap.forEachBlockCopy(this.level, packet.getPos(), (t, pos) -> {
				int b1 = packet.getB1();
				if (t.mirror() && block instanceof PistonBaseBlock) {
					b1 = t.apply(Direction.from3DDataValue(b1)).get3DDataValue();
				}

				this.level.blockEvent(pos, block, packet.getB0(), b1);
			});
			ci.cancel();
		}
	}

	@Inject(method = "handleChunksBiomes", at = @At(value = "INVOKE", target = ENSURE, shift = At.Shift.AFTER), cancellable = true)
	private void wraparound$biomes(ClientboundChunksBiomesPacket packet, CallbackInfo ci) {
		if (!wraparound$active()) {
			return;
		}

		for (ClientboundChunksBiomesPacket.ChunkBiomeData data : packet.chunkBiomeData()) {
			int dcx = data.pos().x();
			int dcz = data.pos().z();
			ClientWrap.forEachCopyInRange(this.level, dcx, dcz, t -> {
				if (!t.mirror()) {
					int cx = t.chunkX(dcx);
					int cz = t.chunkZ(dcz);
					this.level.getChunkSource().replaceBiomes(cx, cz, data.getReadBuffer());
					this.level.onChunkLoaded(new ChunkPos(cx, cz));
					this.level.setSectionRangeDirty(cx - 1, this.level.getMinSectionY(), cz - 1, cx + 1, this.level.getMaxSectionY(), cz + 1);
				}
			});
		}

		ci.cancel();
	}

	// ---- Entities ----

	@Inject(
		method = "handleAddEntity",
		at = @At(value = "INVOKE", target = "Lnet/minecraft/client/multiplayer/ClientLevel;addEntity(Lnet/minecraft/world/entity/Entity;)V")
	)
	private void wraparound$placeNewEntity(CallbackInfo ci, @Local Entity entity) {
		if (wraparound$active()) {
			Transform t = ClientWrap.transformFor(entity, entity.position());
			if (!t.isIdentity()) {
				ClientWrap.shift(entity, t);
			}
		}
	}

	@WrapOperation(
		method = {"handleTeleportEntity", "handleMovePlayer"},
		at = @At(value = "INVOKE", target = "Lnet/minecraft/client/multiplayer/ClientPacketListener;setValuesFromPositionPacket(Lnet/minecraft/world/entity/PositionMoveRotation;Ljava/util/Set;Lnet/minecraft/world/entity/Entity;Z)Z")
	)
	private boolean wraparound$teleport(PositionMoveRotation change, Set<Relative> relatives, Entity entity, boolean interpolate, Operation<Boolean> original) {
		if (!wraparound$active()) {
			return original.call(change, relatives, entity, interpolate);
		}

		Transform t;
		if (entity == Minecraft.getInstance().player) {
			t = ClientWrap.frame();
		} else if (!relatives.contains(Relative.X) && !relatives.contains(Relative.Z)) {
			t = ClientWrap.transformFor(entity, change.position());
		} else {
			t = ClientWrap.currentTransform(entity);
		}

		Vec3 p = change.position();
		double x = relatives.contains(Relative.X) ? t.vectorX(p.x) : t.x(p.x);
		double z = relatives.contains(Relative.Z) ? p.z : t.z(p.z);
		float yRot = relatives.contains(Relative.Y_ROT) ? t.yawDelta(change.yRot()) : t.yaw(change.yRot());
		PositionMoveRotation mapped = new PositionMoveRotation(new Vec3(x, p.y, z), t.applyVector(change.deltaMovement()), yRot, change.xRot());
		return original.call(mapped, relatives, entity, interpolate);
	}

	@Inject(method = "handleMovePlayer", at = @At("TAIL"))
	private void wraparound$followAfterTeleport(ClientboundPlayerPositionPacket packet, CallbackInfo ci) {
		if (wraparound$mainThread()) {
			ClientWrap.followPlayer(this.level);
		}
	}

	@ModifyVariable(method = "handleEntityPositionSync", at = @At("STORE"), ordinal = 0)
	private PositionPath wraparound$syncPath(PositionPath path, @Local Entity entity) {
		if (!wraparound$active() || entity == null) {
			return path;
		}

		return wraparound$map(path, ClientWrap.transformFor(entity, path.endPosition()));
	}

	@ModifyVariable(method = "handleEntityPositionSync", at = @At("STORE"), ordinal = 0)
	private float wraparound$syncYaw(float yRot, @Local Entity entity) {
		if (!wraparound$active() || entity == null) {
			return yRot;
		}

		return ClientWrap.currentTransform(entity).yaw(yRot);
	}

	@Inject(method = "handleEntityPositionSync", at = @At("TAIL"))
	private void wraparound$syncKeepServerBase(ClientboundEntityPositionSyncPacket packet, CallbackInfo ci) {
		if (wraparound$mainThread()) {
			Entity entity = this.level.getEntity(packet.id());
			if (entity != null) {
				entity.getPositionCodec().setBase(packet.position().endPosition());
			}
		}
	}

	@WrapOperation(
		method = "handleMoveEntity",
		at = @At(value = "INVOKE", target = "Lnet/minecraft/world/entity/Entity;moveOrInterpolateTo(Lnet/minecraft/world/entity/PositionPath;FF)V")
	)
	private void wraparound$movePosRot(Entity entity, PositionPath path, float yRot, float xRot, Operation<Void> original) {
		if (wraparound$active()) {
			Transform t = ClientWrap.currentTransform(entity);
			original.call(entity, wraparound$map(path, t), t.yaw(yRot), xRot);
		} else {
			original.call(entity, path, yRot, xRot);
		}
	}

	@WrapOperation(
		method = "handleMoveEntity",
		at = @At(value = "INVOKE", target = "Lnet/minecraft/world/entity/Entity;moveOrInterpolateTo(Lnet/minecraft/world/entity/PositionPath;)V")
	)
	private void wraparound$movePos(Entity entity, PositionPath path, Operation<Void> original) {
		original.call(entity, wraparound$active() ? wraparound$map(path, ClientWrap.currentTransform(entity)) : path);
	}

	@WrapOperation(method = "handleMoveEntity", at = @At(value = "INVOKE", target = "Lnet/minecraft/world/entity/Entity;moveOrInterpolateTo(FF)V"))
	private void wraparound$moveRot(Entity entity, float yRot, float xRot, Operation<Void> original) {
		original.call(entity, wraparound$active() ? ClientWrap.currentTransform(entity).yaw(yRot) : yRot, xRot);
	}

	@WrapOperation(method = "handleSetEntityMotion", at = @At(value = "INVOKE", target = "Lnet/minecraft/world/entity/Entity;lerpMotion(Lnet/minecraft/world/phys/Vec3;)V"))
	private void wraparound$motion(Entity entity, Vec3 movement, Operation<Void> original) {
		original.call(entity, wraparound$active() ? ClientWrap.currentTransform(entity).applyVector(movement) : movement);
	}

	@WrapOperation(method = "handleRotateMob", at = @At(value = "INVOKE", target = "Lnet/minecraft/world/entity/Entity;lerpHeadTo(FI)V"))
	private void wraparound$head(Entity entity, float yRot, int steps, Operation<Void> original) {
		original.call(entity, wraparound$active() ? ClientWrap.currentTransform(entity).yaw(yRot) : yRot, steps);
	}

	@WrapOperation(method = "handleMinecartAlongTrack", at = @At(value = "INVOKE", target = "Ljava/util/List;addAll(Ljava/util/Collection;)Z"))
	private boolean wraparound$minecartSteps(List<NewMinecartBehavior.MinecartStep> list, Collection<NewMinecartBehavior.MinecartStep> steps, Operation<Boolean> original, @Local AbstractMinecart minecart) {
		if (!wraparound$active()) {
			return original.call(list, steps);
		}

		Transform t = ClientWrap.currentTransform(minecart);
		List<NewMinecartBehavior.MinecartStep> mapped = steps.stream()
			.map(s -> new NewMinecartBehavior.MinecartStep(t.apply(s.position()), t.applyVector(s.movement()), t.yaw(s.yRot()), s.xRot(), s.weight()))
			.toList();
		return original.call(list, mapped);
	}

	@ModifyVariable(method = "handleMoveVehicle", at = @At("HEAD"), argsOnly = true)
	private ClientboundMoveVehiclePacket wraparound$moveVehicle(ClientboundMoveVehiclePacket packet) {
		if (!wraparound$mainThread()) {
			return packet;
		}

		Transform t = ClientWrap.frame();
		PositionAndRotation to = packet.movingTo();
		return new ClientboundMoveVehiclePacket(PositionAndRotation.of(t.apply(to.position()), t.yaw(to.yRot()), to.xRot()));
	}

	@Unique
	private static PositionPath wraparound$map(PositionPath path, Transform t) {
		if (t.isIdentity()) {
			return path;
		}

		return switch (path) {
			case PositionPath.Linear linear -> new PositionPath.Linear(t.apply(linear.endPosition()));
			case PositionPath.Stepped stepped -> new PositionPath.Stepped(
				t.apply(stepped.endPosition()),
				stepped.steps().stream().map(s -> new PositionStep(t.apply(s.position()), s.tickOffset())).toList()
			);
		};
	}

	// ---- Effects: shown at the copy nearest the player ----

	@ModifyVariable(method = "handleSoundEvent", at = @At("HEAD"), argsOnly = true)
	private ClientboundSoundPacket wraparound$sound(ClientboundSoundPacket packet) {
		if (!wraparound$mainThread()) {
			return packet;
		}

		Vec3 p = ClientWrap.nearPlayer(this.level, new Vec3(packet.getX(), packet.getY(), packet.getZ()));
		return new ClientboundSoundPacket(packet.getSound(), packet.getSource(), p.x, p.y, p.z, packet.getVolume(), packet.getPitch(), packet.getSeed());
	}

	@ModifyVariable(method = "handleParticleEvent", at = @At("HEAD"), argsOnly = true)
	private ClientboundLevelParticlesPacket wraparound$particles(ClientboundLevelParticlesPacket p) {
		if (!wraparound$mainThread()) {
			return p;
		}

		Transform t = ClientWrap.nearPlayer(this.level, p.x(), p.z());
		return new ClientboundLevelParticlesPacket(
			p.particle(), p.overrideLimiter(), p.alwaysShow(), t.x(p.x()), p.y(), t.z(p.z()),
			p.xDist(), p.yDist(), p.zDist(), t.mirror() ? -p.xMaxSpeed() : p.xMaxSpeed(), p.yMaxSpeed(), p.zMaxSpeed(), p.count(), p.randomizationType()
		);
	}

	@ModifyVariable(method = "handleLevelEvent", at = @At("HEAD"), argsOnly = true)
	private ClientboundLevelEventPacket wraparound$levelEvent(ClientboundLevelEventPacket packet) {
		if (!wraparound$mainThread() || packet.isGlobalEvent()) {
			return packet;
		}

		return new ClientboundLevelEventPacket(packet.getType(), ClientWrap.nearPlayer(this.level, packet.getPos()), packet.getData(), false);
	}

	@ModifyVariable(method = "handleBlockDestruction", at = @At("HEAD"), argsOnly = true)
	private ClientboundBlockDestructionPacket wraparound$destruction(ClientboundBlockDestructionPacket packet) {
		if (!wraparound$mainThread()) {
			return packet;
		}

		return new ClientboundBlockDestructionPacket(packet.getId(), ClientWrap.nearPlayer(this.level, packet.getPos()), packet.getProgress());
	}

	@ModifyVariable(method = "handleExplosion", at = @At("HEAD"), argsOnly = true)
	private ClientboundExplodePacket wraparound$explosion(ClientboundExplodePacket p) {
		if (!wraparound$mainThread()) {
			return p;
		}

		Transform frame = ClientWrap.frame();
		return new ClientboundExplodePacket(
			ClientWrap.nearPlayer(this.level, p.center()), p.radius(), p.blockCount(), p.playerKnockback().map(frame::applyVector),
			p.explosionParticle(), p.explosionSound(), p.blockParticles(), p.playSound()
		);
	}

	@ModifyVariable(method = "handleOpenSignEditor", at = @At("HEAD"), argsOnly = true)
	private ClientboundOpenSignEditorPacket wraparound$sign(ClientboundOpenSignEditorPacket packet) {
		if (!wraparound$mainThread()) {
			return packet;
		}

		return new ClientboundOpenSignEditorPacket(ClientWrap.nearPlayer(this.level, packet.pos()), packet.slot());
	}
}
