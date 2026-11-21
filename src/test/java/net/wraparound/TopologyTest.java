package net.wraparound;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.ArrayList;
import java.util.List;
import java.util.Random;
import net.wraparound.gen.Periodizer;
import org.junit.jupiter.api.Test;

class TopologyTest {
	private static final int R = 64;
	private static final Shape[] SHAPES = {Shape.CYLINDER, Shape.TORUS, Shape.KLEIN_BOTTLE};

	/** A smooth, non-periodic, non-symmetric field, like terrain noise. */
	private static double field(double x, double y, double z) {
		return Math.sin(x * 0.037 + 1.3) * Math.cos(z * 0.051 - 0.4) + 0.3 * Math.sin((x + 2 * z) * 0.013) + x * 0.001 - z * 0.0007 + y * 0.01;
	}

	/** The generators of each gluing, as maps on block columns. */
	private static List<int[]> neighbours(Shape shape, int x, int z) {
		int p = 2 * R;
		List<int[]> list = new ArrayList<>();
		list.add(new int[] {x + p, z});
		list.add(new int[] {x - p, z});
		if (shape.wrapsZ()) {
			boolean mirror = shape.mirrorsAcrossZ();
			list.add(new int[] {mirror ? -1 - x : x, z + p});
			list.add(new int[] {mirror ? -1 - x : x, z - p});
		}

		return list;
	}

	@Test
	void toDomainLandsInside() {
		Random random = new Random(1);
		for (Shape shape : SHAPES) {
			Topology t = new Topology(shape, R);
			for (int i = 0; i < 10000; i++) {
				double x = random.nextDouble() * 4000 - 2000;
				double z = random.nextDouble() * 4000 - 2000;
				Transform w = t.toDomain(x, z);
				assertTrue(t.inDomain(w.x(x), shape.wrapsZ() ? w.z(z) : 0), shape + " " + x + "," + z);
				int bx = (int) Math.floor(x);
				int bz = (int) Math.floor(z);
				Transform wb = t.toDomainBlock(bx, bz);
				assertTrue(t.inDomainBlock(wb.blockX(bx), shape.wrapsZ() ? wb.blockZ(bz) : 0));
			}
		}
	}

	@Test
	void periodizedFieldIsInvariantUnderGluing() {
		Random random = new Random(2);
		for (Shape shape : SHAPES) {
			Periodizer p = new Periodizer(new Topology(shape, R));
			for (int i = 0; i < 5000; i++) {
				int x = random.nextInt(2000) - 1000;
				int z = random.nextInt(2000) - 1000;
				double v = p.sample(x, 10, z, TopologyTest::field);
				for (int[] n : neighbours(shape, x, z)) {
					assertEquals(v, p.sample(n[0], 10, n[1], TopologyTest::field), 1e-9, shape + " at " + x + "," + z);
				}
			}
		}
	}

	@Test
	void periodizedFieldIsContinuousAcrossSeams() {
		for (Shape shape : SHAPES) {
			Periodizer p = new Periodizer(new Topology(shape, R));
			double maxJump = 0;
			for (int z = -3 * R; z < 3 * R; z++) {
				for (int x = -3 * R; x < 3 * R; x++) {
					double v = p.sample(x, 0, z, TopologyTest::field);
					maxJump = Math.max(maxJump, Math.abs(v - p.sample(x + 1, 0, z, TopologyTest::field)));
					maxJump = Math.max(maxJump, Math.abs(v - p.sample(x, 0, z + 1, TopologyTest::field)));
				}
			}

			assertTrue(maxJump < 0.12, shape + " jumps by " + maxJump);
		}
	}

	@Test
	void periodizedFieldMatchesOriginalAwayFromSeams() {
		Periodizer p = new Periodizer(new Topology(Shape.TORUS, 1024));
		assertEquals(field(10, 0, -20), p.sample(10, 0, -20, TopologyTest::field), 1e-12);
	}

	@Test
	void nearestCopyIsNearest() {
		Random random = new Random(3);
		for (Shape shape : SHAPES) {
			Topology t = new Topology(shape, R);
			for (int i = 0; i < 5000; i++) {
				double px = random.nextDouble() * 1000 - 500;
				double pz = random.nextDouble() * 1000 - 500;
				double qx = random.nextDouble() * 1000 - 500;
				double qz = random.nextDouble() * 1000 - 500;
				Transform best = t.nearest(px, pz, qx, qz);
				double d = Math.hypot(best.x(px) - qx, best.z(pz) - qz);
				// The copy is the same point of the glued world.
				Transform a = t.toDomain(px, pz);
				Transform b = t.toDomain(best.x(px), best.z(pz));
				assertEquals(a.x(px), b.x(best.x(px)), 1e-6);
				if (shape.wrapsZ()) {
					assertEquals(a.z(pz), b.z(best.z(pz)), 1e-6);
				}
				// The nearest copy is within half a period on each wrapped axis.
				double limit = shape.wrapsZ() ? Math.sqrt(2) * R : R;
				double measured = shape.wrapsZ() ? d : Math.abs(best.x(px) - qx);
				assertTrue(measured <= limit + 1e-6, shape + " distance " + measured);
			}
		}
	}

	@Test
	void composeAndInverse() {
		Random random = new Random(4);
		for (int i = 0; i < 1000; i++) {
			Transform a = new Transform(random.nextBoolean(), (random.nextInt(9) - 4) * 128, (random.nextInt(9) - 4) * 128);
			Transform b = new Transform(random.nextBoolean(), (random.nextInt(9) - 4) * 128, (random.nextInt(9) - 4) * 128);
			int x = random.nextInt(2000) - 1000;
			assertEquals(a.blockX(b.blockX(x)), a.compose(b).blockX(x));
			assertEquals(x, a.inverse().blockX(a.blockX(x)));
			assertEquals(a.chunkX(b.chunkX(x >> 4)), a.compose(b).chunkX(x >> 4));
			assertTrue(a.compose(a.inverse()).isIdentity());
		}
	}

	@Test
	void chunkCopiesAreAliases() {
		for (Shape shape : SHAPES) {
			Topology t = new Topology(shape, R);
			List<Transform> found = new ArrayList<>();
			t.forEachChunkCopy(1, -2, -20, -20, 20, 20, found::add);
			assertTrue(found.size() > 1);
			for (Transform tr : found) {
				int cx = tr.chunkX(1);
				int cz = tr.chunkZ(-2);
				assertTrue(cx >= -20 && cx <= 20 && cz >= -20 && cz <= 20);
				Transform back = t.toDomainChunk(cx, cz);
				assertEquals(1, back.chunkX(cx), shape + " " + tr);
				assertEquals(-2, back.chunkZ(cz), shape + " " + tr);
			}
		}
	}
}
