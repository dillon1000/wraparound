package net.wraparound.mixin.gen;

import net.minecraft.world.level.levelgen.RandomState;
import net.wraparound.gen.GenHooks;
import net.wraparound.gen.Periodizer;
import org.jspecify.annotations.Nullable;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Unique;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

@Mixin(RandomState.class)
public class RandomStateMixin implements GenHooks.PeriodizerHolder {
	@Unique
	private @Nullable Periodizer wraparound$periodizer;

	@Inject(method = "<init>", at = @At("RETURN"))
	private void wraparound$capture(CallbackInfo ci) {
		this.wraparound$periodizer = GenHooks.CONSTRUCTING.get();
	}

	@Override
	public @Nullable Periodizer wraparound$periodizer() {
		return this.wraparound$periodizer;
	}
}
