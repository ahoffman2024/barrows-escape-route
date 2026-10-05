package com.barrowsescaperoute;

import java.lang.reflect.Proxy;
import java.util.List;
import net.runelite.api.CollisionData;
import net.runelite.api.Scene;
import net.runelite.api.coords.LocalPoint;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;
import org.junit.Test;

public class CryptRoutePlannerTest
{
	@Test
	public void connectsWalkableTilesNearBlockedEndpoints()
	{
		int[][] flags = new int[104][104];
		// Objects occupy their own tiles.  The planner must choose nearby floor
		// tiles rather than declaring the ladder-to-chest route impossible.
		flags[20][20] = 0x100;
		flags[30][20] = 0x100;
		CollisionData collision = () -> flags;
		Scene scene = (Scene) Proxy.newProxyInstance(getClass().getClassLoader(), new Class<?>[]{Scene.class}, (proxy, method, args) ->
			method.getName().equals("getWorldViewId") ? 0 : null);

		List<LocalPoint> route = CryptRoutePlanner.find(collision, scene, LocalPoint.fromScene(20, 20), LocalPoint.fromScene(30, 20));

		assertFalse(route.isEmpty());
		assertTrue(route.get(0).distanceTo(LocalPoint.fromScene(20, 20)) < 1000);
		assertTrue(route.get(route.size() - 1).distanceTo(LocalPoint.fromScene(30, 20)) < 1500);
	}

	@Test
	public void opensBothSidesOfEachWallOrientation()
	{
		int[][] flags = new int[104][104];
		flags[50][50] = net.runelite.api.CollisionDataFlag.BLOCK_MOVEMENT_EAST | net.runelite.api.CollisionDataFlag.BLOCK_MOVEMENT_OBJECT;
		flags[51][50] = net.runelite.api.CollisionDataFlag.BLOCK_MOVEMENT_WEST | net.runelite.api.CollisionDataFlag.BLOCK_MOVEMENT_OBJECT;

		CryptRoutePlanner.openDoor(flags, 50, 50, 4);

		assertTrue((flags[50][50] & (net.runelite.api.CollisionDataFlag.BLOCK_MOVEMENT_EAST | net.runelite.api.CollisionDataFlag.BLOCK_MOVEMENT_OBJECT)) == 0);
		assertTrue((flags[51][50] & (net.runelite.api.CollisionDataFlag.BLOCK_MOVEMENT_WEST | net.runelite.api.CollisionDataFlag.BLOCK_MOVEMENT_OBJECT)) == 0);
	}
}
