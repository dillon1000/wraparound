package net.wraparound.client.mixin;

import net.minecraft.client.multiplayer.ClientLevel;
import net.wraparound.Wrap;
import net.wraparound.client.ClientWrap;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

@Mixin(ClientLevel.class)
public class ClientLevelMixin {
	@Inject(method = "<init>", at = @At("RETURN"))
	private void wraparound$init(CallbackInfo ci) {
		ClientLevel self = (ClientLevel) (Object) this;
		((Wrap.Holder) self).wraparound$setTopology(ClientWrap.topologyFor(self.dimension()));
		ClientWrap.resetLevelState();
	}
}
