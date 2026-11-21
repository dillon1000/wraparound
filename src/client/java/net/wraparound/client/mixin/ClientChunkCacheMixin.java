package net.wraparound.client.mixin;

import net.minecraft.client.multiplayer.ClientChunkCache;
import net.minecraft.client.multiplayer.ClientLevel;
import net.wraparound.client.ClientWrap;
import org.jspecify.annotations.Nullable;
import org.spongepowered.asm.mixin.Final;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.Unique;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

@Mixin(ClientChunkCache.class)
public class ClientChunkCacheMixin implements ClientWrap.CacheState {
	@Shadow
	@Final
	private ClientLevel level;

	@Unique
	private int wraparound$centerX;

	@Unique
	private int wraparound$centerZ;

	@Unique
	private int wraparound$viewRadius;

	@Unique
	private ClientWrap.@Nullable Recenter wraparound$pending;

	@Override
	public int wraparound$centerX() {
		return wraparound$centerX;
	}

	@Override
	public int wraparound$centerZ() {
		return wraparound$centerZ;
	}

	@Override
	public int wraparound$viewRadius() {
		return wraparound$viewRadius;
	}

	@Inject(method = "<init>", at = @At("RETURN"))
	private void wraparound$init(ClientLevel level, int serverChunkRadius, CallbackInfo ci) {
		this.wraparound$viewRadius = Math.max(2, serverChunkRadius);
	}

	@Inject(method = "updateViewCenter", at = @At("HEAD"))
	private void wraparound$beforeCenter(int x, int z, CallbackInfo ci) {
		if (ClientWrap.active(this.level)) {
			this.wraparound$pending = ClientWrap.beforeRecenter(this.level, x, z, this.wraparound$viewRadius);
		}

		this.wraparound$centerX = x;
		this.wraparound$centerZ = z;
	}

	@Inject(method = "updateViewCenter", at = @At("TAIL"))
	private void wraparound$afterCenter(int x, int z, CallbackInfo ci) {
		wraparound$runPending();
	}

	@Inject(method = "updateViewRadius", at = @At("HEAD"))
	private void wraparound$beforeRadius(int viewRange, CallbackInfo ci) {
		int radius = Math.max(2, viewRange);
		if (ClientWrap.active(this.level)) {
			this.wraparound$pending = ClientWrap.beforeRecenter(this.level, this.wraparound$centerX, this.wraparound$centerZ, radius);
		}

		this.wraparound$viewRadius = radius;
	}

	@Inject(method = "updateViewRadius", at = @At("TAIL"))
	private void wraparound$afterRadius(int viewRange, CallbackInfo ci) {
		wraparound$runPending();
	}

	@Unique
	private void wraparound$runPending() {
		ClientWrap.Recenter pending = this.wraparound$pending;
		this.wraparound$pending = null;
		if (pending != null) {
			pending.run();
		}
	}
}
