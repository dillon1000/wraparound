package net.wraparound.mixin.server;

import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.entity.Entity;
import net.wraparound.server.ServerWrap;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

@Mixin(ServerLevel.class)
public class ServerLevelTickMixin {
	@Inject(method = "tickNonPassenger", at = @At("TAIL"))
	private void wraparound$wrapAfterTick(Entity entity, CallbackInfo ci) {
		ServerWrap.wrapIfOutside(entity);
	}
}
