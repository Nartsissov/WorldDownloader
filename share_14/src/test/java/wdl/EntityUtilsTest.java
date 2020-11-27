/*
 * This file is part of World Downloader: A mod to make backups of your multiplayer worlds.
 * https://www.minecraftforum.net/forums/mapping-and-modding-java-edition/minecraft-mods/2520465-world-downloader-mod-create-backups-of-your-builds
 *
 * Copyright (c) 2014 nairol, cubic72
 * Copyright (c) 2018-2020 Pokechu22, julialy
 *
 * This project is licensed under the MMPLv2.  The full text of the MMPL can be
 * found in LICENSE.md, or online at https://github.com/iopleke/MMPLv2/blob/master/LICENSE.md
 * For information about this the MMPLv2, see https://stopmodreposts.org/
 *
 * Do not redistribute (in modified or unmodified form) without prior permission.
 */
package wdl;

import static org.hamcrest.Matchers.*;
import static org.junit.Assert.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;

import java.lang.reflect.Constructor;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Iterator;
import java.util.List;
import java.util.concurrent.Executor;
import java.util.function.BiPredicate;
import java.util.function.Function;
import java.util.function.IntFunction;

import org.junit.Test;
import org.mockito.AdditionalAnswers;

import com.mojang.authlib.GameProfile;

import it.unimi.dsi.fastutil.ints.Int2ObjectMap;
import it.unimi.dsi.fastutil.ints.Int2ObjectOpenHashMap;
import it.unimi.dsi.fastutil.longs.Long2ObjectLinkedOpenHashMap;
import net.minecraft.entity.Entity;
import net.minecraft.entity.EntityType;
import net.minecraft.entity.item.ArmorStandEntity;
import net.minecraft.entity.monster.CreeperEntity;
import net.minecraft.entity.monster.ZombieEntity;
import net.minecraft.entity.passive.PigEntity;
import net.minecraft.entity.player.ServerPlayerEntity;
import net.minecraft.network.play.ServerPlayNetHandler;
import net.minecraft.server.management.PlayerInteractionManager;
import net.minecraft.util.math.vector.Vector3d;
import net.minecraft.world.World;
import wdl.TestWorld.MockableChunkManager;
import wdl.versioned.VersionedFunctions;

/**
 * An experimental test around the entity tracking code.  Not particularly complete.
 */
public class EntityUtilsTest extends MaybeMixinTest {

	/**
	 * Some basic tests, with varying paths but no entity removal.
	 */
	@Test
	public void testTrackerSimple() throws Exception  {
		runTrackerTest(world -> new PigEntity(EntityType.PIG, world), 80, 10, 300,
				(tick, entity) -> true,
				(tick) -> new Vector3d(-150 + tick, tick, -150 + tick));
		runTrackerTest(world -> new ArmorStandEntity(EntityType.ARMOR_STAND, world), 160, 10, 300,
				(tick, entity) -> true,
				(tick) -> new Vector3d(150 * Math.sin(tick * 300 / (2 * Math.PI)), tick,
						150 * Math.cos(tick * 300 / (2 * Math.PI))));
	}

	/**
	 * Tracker test, where some entities are removed.
	 */
	@Test
	public void testTrackerRemove() throws Exception {
		runTrackerTest(ZombieEntity::new, 80, 10, 110, // Why does this still work?
				(tick, entity) -> tick <= 100,
				(tick) -> new Vector3d(-150 + tick, tick, -150 + tick));
		runTrackerTest(world -> new CreeperEntity(EntityType.CREEPER, world), 80, 10, 110,
				(tick, entity) -> tick <= 100 || VersionedFunctions.getEntityX(entity) <= (-150 + tick),
				(tick) -> new Vector3d(-150 + tick, tick, -150 + tick));
	}

	/**
	 * A generalized test for the entity tracker.
	 *
	 * @param entitySupplier     Produces entities.
	 * @param threshold          The track distance for the produced entities.
	 * @param serverViewDistance The view distance (in chunks) that is used.
	 * @param numTicks           Number of ticks to simulate.
	 * @param keepEntity         Predicate taking the tick and the entity, to see if
	 *                           it should be "killed" on a tick.
	 * @param posFunc            Function providing player position by tick.
	 */
	protected void runTrackerTest(Function<World, ? extends Entity> entitySupplier, int threshold,
			int serverViewDistance, int numTicks, BiPredicate<Integer, Entity> keepEntity, IntFunction<Vector3d> posFunc) throws Exception {
		TestWorld.ServerWorld world = TestWorld.makeServer();

		GameProfile profile = mock(GameProfile.class);
		when(profile.getName()).thenReturn("Nobody");
		ServerPlayerEntity player = mock(ServerPlayerEntity.class, withSettings().useConstructor(
				world.getServer(), world, profile, mock(PlayerInteractionManager.class)).defaultAnswer(CALLS_REAL_METHODS));
		List<Entity> trackedEntities = new ArrayList<>();
		when(player.toString()).thenCallRealMethod();
		doAnswer(AdditionalAnswers.<Entity>answerVoid(trackedEntities::add)).when(player).addEntity(any());
		doAnswer(AdditionalAnswers.<Entity>answerVoid(trackedEntities::remove)).when(player).removeEntity(any());

		List<Entity> entities = new ArrayList<>(); // all known entities; if killed they're removed from this list
		List<Entity> tracked = new ArrayList<>(); // entities being tracked by the mock player
		player.connection = mock(ServerPlayNetHandler.class);

		doAnswer(AdditionalAnswers.<Entity>answerVoid((e) -> {
			assertThat("Tried to track an entity that was already tracked", tracked, not(hasItem(e)));
			tracked.add(e);
		})).when(player).addEntity(any());
		doAnswer(AdditionalAnswers.<Entity>answerVoid((e) -> {
			assertThat("Tried to untrack an entity that was not tracked", tracked, hasItem(e));
			tracked.remove(e);

			boolean keep = EntityUtils.isWithinSavingDistance(e, player, threshold, serverViewDistance);
			if (entities.contains(e)) {
				assertTrue(e + " should have been saved for " + player + " @ " + threshold, keep);
			} else {
				assertFalse(e + " should not have been saved for " + player + " @ " + threshold, keep);
			}
		})).when(player).removeEntity(any());

		world.addNewPlayer(player);

		MockableChunkManager tracker = mock(MockableChunkManager.class);
		doCallRealMethod().when(tracker).setViewDistance(anyInt());
		doCallRealMethod().when(tracker).track(any());
		doCallRealMethod().when(tracker).untrack(any());
		doCallRealMethod().when(tracker).tickEntityTracker();
		// We bypass the constructor, so this needs to be manually set
		Class<?> ticketManagerClass = Arrays
				.stream(MockableChunkManager.CHUNK_MANAGER_CLASS.getDeclaredClasses())
				.filter(MockableChunkManager.TICKET_MANAGER_CLASS::isAssignableFrom)
				.findAny().get();
		setTicketManager(tracker, MockableChunkManager.CHUNK_MANAGER_CLASS, ticketManagerClass);
		Long2ObjectLinkedOpenHashMap<?> chunkHolders1 = new Long2ObjectLinkedOpenHashMap<>();
		ReflectionUtils.findAndSetPrivateField(tracker, MockableChunkManager.CHUNK_MANAGER_CLASS, Long2ObjectLinkedOpenHashMap.class, chunkHolders1);
		Int2ObjectMap<?> trackerTrackedEntities = new Int2ObjectOpenHashMap<>();
		ReflectionUtils.findAndSetPrivateField(tracker, MockableChunkManager.CHUNK_MANAGER_CLASS, Int2ObjectMap.class, trackerTrackedEntities);
		ReflectionUtils.findAndSetPrivateField(tracker, MockableChunkManager.CHUNK_MANAGER_CLASS, TestWorld.ServerWorld.SERVER_WORLD_CLASS, world);
		// Required because world doesn't set it up right for a mock, and mocking it
		// would be making assumptions about how this is calculated
		// (NOTE: I'm not sure what the difference between the two parameters are)
		tracker.setViewDistance(serverViewDistance);

		int eid = 0;
		for (int x = -100; x <= 100; x += 10) {
			for (int z = -100; z <= 100; z += 10) {
				Entity e = entitySupplier.apply(world);
				entities.add(e);
				e.setEntityId(eid++);
				VersionedFunctions.setEntityPos(e, x, 0, z);
				tracker.track(e);
			}
		}

		for (int tick = 0; tick <= numTicks; tick++) {
			Vector3d pos = posFunc.apply(tick);
			VersionedFunctions.setEntityPos(player, pos.x, pos.y, pos.z);
			for (Iterator<Entity> itr = entities.iterator(); itr.hasNext();) {
				Entity e = itr.next();
				if (!keepEntity.test(tick, e)) {
					itr.remove();
					tracker.untrack(e);
				}
			}
			tracker.tickEntityTracker();
		}

		tracker.close();
		world.close();
	}

	/**
	 * This method exists because the actual types are unknown (and thus local
	 * variables are awkward to use normally), but generics let us get away this.
	 *
	 * @param <T_ChunkManager>       Inferred class for ChunkManager
	 * @param <T_ProxyTicketManager> Inferred subclass of TicketManager
	 * @param chunkManager           ChunkManager instance
	 * @param chunkManagerClass      Class for ChunkManager
	 * @param ticketManagerClass     Class for TicketManager (specifically the
	 *                               ProxyClassManager inner class)
	 */
	private static <T_ChunkManager, T_ProxyTicketManager> void setTicketManager(T_ChunkManager chunkManager,
			Class<T_ChunkManager> chunkManagerClass, Class<T_ProxyTicketManager> ticketManagerClass) throws Exception {
		// Unfortunately Mockito doesn't like mocking non-public classes when used with
		// LWTS, so we need to use the constructor directly.
		// The first ChunkManager parameter is synthetic.
		Constructor<T_ProxyTicketManager> constructor = ticketManagerClass.getDeclaredConstructor(chunkManagerClass,
				Executor.class, Executor.class);
		constructor.setAccessible(true);
		// We can't just ignore the tasks, as even during the constructor it's expected
		// that they run.
		// Just executing them on the same thread seems to be fine though.
		Executor executor = Runnable::run;
		T_ProxyTicketManager ticketManager = constructor.newInstance(chunkManager, executor, executor);

		ReflectionUtils.findAndSetPrivateField(chunkManager, chunkManagerClass, ticketManagerClass, ticketManager);
	}
}
