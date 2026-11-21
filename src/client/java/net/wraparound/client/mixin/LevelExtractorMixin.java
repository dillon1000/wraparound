package net.wraparound.client.mixin;

import java.util.ArrayList;
import java.util.List;
import net.minecraft.client.Camera;
import net.minecraft.client.DeltaTracker;
import net.minecraft.client.Minecraft;
import net.minecraft.client.multiplayer.ClientLevel;
import net.minecraft.client.player.LocalPlayer;
import net.minecraft.client.renderer.LevelRenderer;
import net.minecraft.client.renderer.culling.Frustum;
import net.minecraft.client.renderer.entity.state.EntityRenderState;
import net.minecraft.client.renderer.entity.state.LivingEntityRenderState;
import net.minecraft.client.renderer.extract.LevelExtractor;
import net.minecraft.client.renderer.state.level.LevelRenderState;
import net.minecraft.core.BlockPos;
import net.minecraft.core.SectionPos;
import net.minecraft.util.Mth;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.Vec3;
import net.wraparound.Topology;
import net.wraparound.Transform;
import net.wraparound.Wrap;
import org.jspecify.annotations.Nullable;
import org.spongepowered.asm.mixin.Final;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

/**
 * When the world is small enough to see around, an entity is also drawn at every other copy of its
 * position in view, mirrored where the gluing flips.
 */
@Mixin(LevelExtractor.class)
public class LevelExtractorMixin {
	@Shadow
	@Final
	private LevelRenderer levelRenderer;

	@Shadow
	private @Nullable ClientLevel level;

	@Inject(
		method = "extractVisibleEntities",
		at = @At(value = "FIELD", target = "Lnet/minecraft/client/renderer/state/level/LevelRenderState;lastEntityRenderStateCount:I", opcode = org.objectweb.asm.Opcodes.PUTFIELD)
	)
	private void wraparound$ghosts(Camera camera, Frustum frustum, DeltaTracker deltaTracker, LevelRenderState output, CallbackInfo ci) {
		ClientLevel level = this.level;
		if (level == null || !Wrap.of(level).active()) {
			return;
		}

		Topology topology = Wrap.of(level);
		Minecraft mc = Minecraft.getInstance();
		Vec3 cam = camera.position();
		int renderChunks = mc.options.getEffectiveRenderDistance();
		int camCx = SectionPos.blockToSectionCoord(cam.x);
		int camCz = SectionPos.blockToSectionCoord(cam.z);
		long fade = net.minecraft.util.Util.toMillis(mc.options.chunkSectionFadeInTime().get());
		List<EntityRenderState> ghosts = new ArrayList<>();

		for (Entity entity : level.entitiesForRendering()) {
			if (entity instanceof LocalPlayer && !camera.isDetached()) {
				continue;
			}

			float partial = deltaTracker.getGameTimeDeltaPartialTick(!level.tickRateManager().isEntityFrozen(entity));
			Vec3 pos = entity.getPosition(partial);
			Transform toDomain = topology.toDomain(pos.x, pos.z);
			int dcx = SectionPos.blockToSectionCoord(toDomain.x(pos.x));
			int dcz = SectionPos.blockToSectionCoord(toDomain.z(pos.z));
			AABB box = entity.getBoundingBox();
			topology.forEachChunkCopy(dcx, dcz, camCx - renderChunks, camCz - renderChunks, camCx + renderChunks, camCz + renderChunks, t -> {
				Transform delta = t.compose(toDomain);
				if (delta.isIdentity()) {
					return;
				}

				Vec3 copyPos = delta.apply(pos);
				if (copyPos.distanceToSqr(cam) > (double) (renderChunks * 16) * (renderChunks * 16)) {
					return;
				}

				if (!frustum.isVisible(delta.apply(box).inflate(0.5)) || !this.levelRenderer.isSectionCompiledAndVisible(BlockPos.containing(copyPos), fade)) {
					return;
				}

				EntityRenderState state = this.levelRenderer.entityRenderDispatcher().extractEntity(entity, partial);
				state.x = delta.x(state.x);
				state.z = delta.z(state.z);
				if (delta.mirror() && state instanceof LivingEntityRenderState living) {
					living.bodyRot = Mth.wrapDegrees(-living.bodyRot);
					living.yRot = -living.yRot;
				}

				ghosts.add(state);
			});
		}

		output.entityRenderStates.addAll(ghosts);
	}
}
