package net.wraparound;

import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.core.SectionPos;
import net.minecraft.util.Mth;
import net.minecraft.world.level.ChunkPos;
import net.minecraft.world.level.block.Mirror;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.Vec3;

/**
 * One symmetry of the wrapped world: an optional east-west mirror about x = 0 followed by a
 * translation by whole world widths. Maps (x, z) to (±x + tx, z + tz).
 *
 * <p>The mirror is continuous about x = 0, so block column {@code b} maps to {@code -1 - b} and chunk
 * column {@code c} maps to {@code -1 - c}. Translations are always multiples of 16 blocks.
 */
public record Transform(boolean mirror, int tx, int tz) {
	public static final Transform IDENTITY = new Transform(false, 0, 0);

	public boolean isIdentity() {
		return !mirror && tx == 0 && tz == 0;
	}

	public double x(double x) {
		return (mirror ? -x : x) + tx;
	}

	public double z(double z) {
		return z + tz;
	}

	public int blockX(int x) {
		return (mirror ? -1 - x : x) + tx;
	}

	public int blockZ(int z) {
		return z + tz;
	}

	public int chunkX(int x) {
		return (mirror ? -1 - x : x) + (tx >> 4);
	}

	public int chunkZ(int z) {
		return z + (tz >> 4);
	}

	public Vec3 apply(Vec3 v) {
		return new Vec3(x(v.x), v.y, z(v.z));
	}

	/** Applies only the linear part, for velocities and offsets. */
	public Vec3 applyVector(Vec3 v) {
		return mirror ? new Vec3(-v.x, v.y, v.z) : v;
	}

	public double vectorX(double dx) {
		return mirror ? -dx : dx;
	}

	public BlockPos apply(BlockPos pos) {
		return new BlockPos(blockX(pos.getX()), pos.getY(), blockZ(pos.getZ()));
	}

	public ChunkPos apply(ChunkPos pos) {
		return new ChunkPos(chunkX(pos.x()), chunkZ(pos.z()));
	}

	public SectionPos apply(SectionPos pos) {
		return SectionPos.of(chunkX(pos.x()), pos.y(), chunkZ(pos.z()));
	}

	public AABB apply(AABB box) {
		double x1 = x(box.minX);
		double x2 = x(box.maxX);
		return new AABB(Math.min(x1, x2), box.minY, z(box.minZ), Math.max(x1, x2), box.maxY, z(box.maxZ));
	}

	public BlockState apply(BlockState state) {
		return mirror ? state.mirror(Mirror.FRONT_BACK) : state;
	}

	public Direction apply(Direction direction) {
		return mirror && direction.getAxis() == Direction.Axis.X ? direction.getOpposite() : direction;
	}

	/** Yaw in Minecraft convention: facing (-sin yaw, cos yaw). Mirroring x negates yaw. */
	public float yaw(float yaw) {
		return mirror ? Mth.wrapDegrees(-yaw) : yaw;
	}

	public float yawDelta(float delta) {
		return mirror ? -delta : delta;
	}

	/** this ∘ other: apply {@code other} first, then this. */
	public Transform compose(Transform other) {
		int s = mirror ? -1 : 1;
		return new Transform(mirror != other.mirror, s * other.tx + tx, other.tz + tz);
	}

	public Transform inverse() {
		int s = mirror ? -1 : 1;
		return new Transform(mirror, -s * tx, -tz);
	}
}
