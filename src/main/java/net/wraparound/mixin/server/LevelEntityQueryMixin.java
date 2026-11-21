package net.wraparound.mixin.server;

import java.util.Collections;
import java.util.IdentityHashMap;
import java.util.List;
import java.util.Set;
import java.util.function.Predicate;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.entity.EntityTypeTest;
import net.minecraft.world.phys.AABB;
import net.wraparound.Wrap;
import org.jspecify.annotations.Nullable;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.Unique;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

/**
 * On the server, a search box that reaches over a seam also finds the entities on the other side. That
 * lets mobs spot players, players pick up items and so on across the edge.
 */
@Mixin(Level.class)
public abstract class LevelEntityQueryMixin {
	@Unique
	private static final ThreadLocal<Boolean> wraparound$inner = ThreadLocal.withInitial(() -> false);

	@Shadow
	public abstract List<Entity> getEntities(@Nullable Entity except, AABB bb, Predicate<? super Entity> selector);

	@Shadow
	public abstract <T extends Entity> void getEntities(EntityTypeTest<Entity, T> type, AABB bb, Predicate<? super T> selector, List<? super T> output, int maxResults);

	@Unique
	private boolean wraparound$shouldExpand() {
		return Wrap.server((Level) (Object) this) && !wraparound$inner.get();
	}

	@Inject(method = "getEntities(Lnet/minecraft/world/entity/Entity;Lnet/minecraft/world/phys/AABB;Ljava/util/function/Predicate;)Ljava/util/List;", at = @At("RETURN"))
	private void wraparound$moreEntities(@Nullable Entity except, AABB bb, Predicate<? super Entity> selector, CallbackInfoReturnable<List<Entity>> cir) {
		if (!wraparound$shouldExpand()) {
			return;
		}

		List<Entity> output = cir.getReturnValue();
		Set<Entity> seen = Collections.newSetFromMap(new IdentityHashMap<>());
		seen.addAll(output);
		wraparound$inner.set(true);
		try {
			Wrap.of((Level) (Object) this).forEachOtherDomainCopy(bb, copy -> {
				for (Entity e : getEntities(except, copy, selector)) {
					if (seen.add(e)) {
						output.add(e);
					}
				}
			});
		} finally {
			wraparound$inner.set(false);
		}
	}

	@Inject(
		method = "getEntities(Lnet/minecraft/world/level/entity/EntityTypeTest;Lnet/minecraft/world/phys/AABB;Ljava/util/function/Predicate;Ljava/util/List;I)V",
		at = @At("TAIL")
	)
	private <T extends Entity> void wraparound$moreTyped(
		EntityTypeTest<Entity, T> type, AABB bb, Predicate<? super T> selector, List<? super T> output, int maxResults, CallbackInfo ci
	) {
		if (!wraparound$shouldExpand() || output.size() >= maxResults) {
			return;
		}

		Set<Object> seen = Collections.newSetFromMap(new IdentityHashMap<>());
		seen.addAll(output);
		Predicate<? super T> unseen = e -> !seen.contains(e) && selector.test(e);
		wraparound$inner.set(true);
		try {
			Wrap.of((Level) (Object) this).forEachOtherDomainCopy(bb, copy -> {
				if (output.size() < maxResults) {
					getEntities(type, copy, unseen, output, maxResults);
					seen.addAll(output);
				}
			});
		} finally {
			wraparound$inner.set(false);
		}
	}
}
