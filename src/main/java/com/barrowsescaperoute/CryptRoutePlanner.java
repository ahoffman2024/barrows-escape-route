package com.barrowsescaperoute;

import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.Collection;
import java.util.Collections;
import java.util.List;
import net.runelite.api.CollisionData;
import net.runelite.api.CollisionDataFlag;
import net.runelite.api.Perspective;
import net.runelite.api.Scene;
import net.runelite.api.TileObject;
import net.runelite.api.WallObject;
import net.runelite.api.coords.LocalPoint;

/** Builds a route from the live scene collision map; closed doors remain impassable. */
final class CryptRoutePlanner
{
	private static final int[] DX = {1, -1, 0, 0};
	private static final int[] DY = {0, 0, 1, -1};
	private static final int[] OUT = {CollisionDataFlag.BLOCK_MOVEMENT_EAST, CollisionDataFlag.BLOCK_MOVEMENT_WEST, CollisionDataFlag.BLOCK_MOVEMENT_NORTH, CollisionDataFlag.BLOCK_MOVEMENT_SOUTH};
	private static final int[] IN = {CollisionDataFlag.BLOCK_MOVEMENT_WEST, CollisionDataFlag.BLOCK_MOVEMENT_EAST, CollisionDataFlag.BLOCK_MOVEMENT_SOUTH, CollisionDataFlag.BLOCK_MOVEMENT_NORTH};

	private CryptRoutePlanner() { }

	static List<LocalPoint> find(CollisionData collision, Scene scene, LocalPoint from, LocalPoint destination)
	{
		return find(collision, scene, from, destination, Collections.emptyList());
	}

	/**
	 * Finds a walk route while treating currently openable Barrows doors as
	 * passages. The live collision grid correctly supplies every wall and room
	 * shape; only the selected door barriers are temporarily opened in a copy.
	 */
	static List<LocalPoint> find(CollisionData collision, Scene scene, LocalPoint from, LocalPoint destination, Collection<? extends TileObject> openableDoors)
	{
		if (collision == null || scene == null || from == null || destination == null) { return Collections.emptyList(); }
		int[][] flags = copyFlags(collision.getFlags());
		if (flags == null) { return Collections.emptyList(); }
		for (TileObject door : openableDoors)
		{
			if (!(door instanceof WallObject) || door.getLocalLocation() == null) { continue; }
			WallObject wall = (WallObject) door;
			openDoor(flags, wall.getLocalLocation().getSceneX(), wall.getLocalLocation().getSceneY(), wall.getOrientationA() | wall.getOrientationB());
		}
		List<Integer> starts = walkableNear(flags, from.getSceneX(), from.getSceneY(), 6);
		List<Integer> goals = walkableNear(flags, destination.getSceneX(), destination.getSceneY(), 10);
		if (starts.isEmpty() || goals.isEmpty()) { return Collections.emptyList(); }
		int[] previous = new int[Perspective.SCENE_SIZE * Perspective.SCENE_SIZE];
		java.util.Arrays.fill(previous, -1);
		boolean[] isGoal = new boolean[previous.length];
		for (int goal : goals) { isGoal[goal] = true; }
		ArrayDeque<Integer> queue = new ArrayDeque<>();
		for (int start : starts)
		{
			queue.add(start);
			previous[start] = start;
		}
		int goal = -1;
		while (!queue.isEmpty() && goal == -1)
		{
			int current = queue.remove(); int x = current / Perspective.SCENE_SIZE; int y = current % Perspective.SCENE_SIZE;
			if (isGoal[current]) { goal = current; break; }
			for (int direction = 0; direction < DX.length; direction++)
			{
				int nx = x + DX[direction]; int ny = y + DY[direction];
				if (nx < 0 || ny < 0 || nx >= Perspective.SCENE_SIZE || ny >= Perspective.SCENE_SIZE) { continue; }
				int next = nx * Perspective.SCENE_SIZE + ny;
				if (previous[next] != -1 || blocked(flags, x, y, nx, ny, direction)) { continue; }
				previous[next] = current; queue.add(next);
			}
		}
		if (goal == -1) { return Collections.emptyList(); }
		List<LocalPoint> route = new ArrayList<>();
		int current = goal;
		while (previous[current] != current)
		{
			route.add(LocalPoint.fromScene(current / Perspective.SCENE_SIZE, current % Perspective.SCENE_SIZE, scene));
			current = previous[current];
		}
		route.add(LocalPoint.fromScene(current / Perspective.SCENE_SIZE, current % Perspective.SCENE_SIZE, scene));
		Collections.reverse(route); return route;
	}
	private static int[][] copyFlags(int[][] source)
	{
		if (source == null || source.length != Perspective.SCENE_SIZE) { return null; }
		int[][] copy = new int[source.length][];
		for (int x = 0; x < source.length; x++)
		{
			if (source[x] == null || source[x].length != Perspective.SCENE_SIZE) { return null; }
			copy[x] = java.util.Arrays.copyOf(source[x], source[x].length);
		}
		return copy;
	}
	/** Opens the movement flags on both sides of a wall object. */
	static void openDoor(int[][] flags, int x, int y, int orientations)
	{
		if ((orientations & 1) != 0) { clearBarrier(flags, x, y, x - 1, y, CollisionDataFlag.BLOCK_MOVEMENT_WEST, CollisionDataFlag.BLOCK_MOVEMENT_EAST); }
		if ((orientations & 2) != 0) { clearBarrier(flags, x, y, x, y + 1, CollisionDataFlag.BLOCK_MOVEMENT_NORTH, CollisionDataFlag.BLOCK_MOVEMENT_SOUTH); }
		if ((orientations & 4) != 0) { clearBarrier(flags, x, y, x + 1, y, CollisionDataFlag.BLOCK_MOVEMENT_EAST, CollisionDataFlag.BLOCK_MOVEMENT_WEST); }
		if ((orientations & 8) != 0) { clearBarrier(flags, x, y, x, y - 1, CollisionDataFlag.BLOCK_MOVEMENT_SOUTH, CollisionDataFlag.BLOCK_MOVEMENT_NORTH); }
	}
	private static void clearBarrier(int[][] flags, int x, int y, int otherX, int otherY, int firstFlag, int secondFlag)
	{
		if (!inScene(x, y) || !inScene(otherX, otherY)) { return; }
		flags[x][y] &= ~(firstFlag | CollisionDataFlag.BLOCK_MOVEMENT_OBJECT);
		flags[otherX][otherY] &= ~(secondFlag | CollisionDataFlag.BLOCK_MOVEMENT_OBJECT);
	}
	private static boolean inScene(int x, int y) { return x >= 0 && y >= 0 && x < Perspective.SCENE_SIZE && y < Perspective.SCENE_SIZE; }

	private static boolean blocked(int[][] flags, int x, int y, int nx, int ny, int direction)
	{
		return (flags[x][y] & (CollisionDataFlag.BLOCK_MOVEMENT_FULL | OUT[direction])) != 0
			|| (flags[nx][ny] & (CollisionDataFlag.BLOCK_MOVEMENT_FULL | IN[direction])) != 0;
	}
	private static List<Integer> walkableNear(int[][] flags, int x, int y, int maximumRadius)
	{
		List<Integer> points = new ArrayList<>();
		for (int radius = 0; radius <= maximumRadius; radius++) for (int dx = -radius; dx <= radius; dx++) for (int dy = -radius; dy <= radius; dy++)
		{
			int nx = x + dx, ny = y + dy;
			if (Math.max(Math.abs(dx), Math.abs(dy)) != radius) { continue; }
			if (nx >= 0 && ny >= 0 && nx < Perspective.SCENE_SIZE && ny < Perspective.SCENE_SIZE && (flags[nx][ny] & CollisionDataFlag.BLOCK_MOVEMENT_FULL) == 0)
			{
				points.add(nx * Perspective.SCENE_SIZE + ny);
			}
		}
		return points;
	}
}
