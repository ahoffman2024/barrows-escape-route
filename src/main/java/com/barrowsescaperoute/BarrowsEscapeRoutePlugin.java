package com.barrowsescaperoute;

import com.google.inject.Provides;
import java.util.HashSet;
import java.util.List;
import java.util.Set;
import javax.inject.Inject;
import lombok.Getter;
import lombok.extern.slf4j.Slf4j;
import net.runelite.api.Client;
import net.runelite.api.ChatMessageType;
import net.runelite.api.CollisionData;
import net.runelite.api.GameObject;
import net.runelite.api.GameState;
import net.runelite.api.ObjectComposition;
import net.runelite.api.Player;
import net.runelite.api.coords.LocalPoint;
import net.runelite.api.coords.WorldPoint;
import net.runelite.api.Tile;
import net.runelite.api.TileObject;
import net.runelite.api.events.GameObjectDespawned;
import net.runelite.api.events.GameObjectSpawned;
import net.runelite.api.events.ChatMessage;
import net.runelite.api.events.GameStateChanged;
import net.runelite.api.events.GameTick;
import net.runelite.api.events.WidgetLoaded;
import net.runelite.api.events.WallObjectDespawned;
import net.runelite.api.events.WallObjectSpawned;
import net.runelite.api.gameval.InterfaceID;
import net.runelite.api.gameval.ObjectID;
import net.runelite.client.config.ConfigManager;
import net.runelite.client.eventbus.Subscribe;
import net.runelite.client.plugins.Plugin;
import net.runelite.client.plugins.PluginDescriptor;
import net.runelite.client.ui.overlay.OverlayManager;

/** A display-only record of the route actually walked during the current Barrows tunnel run. */
@Slf4j
@PluginDescriptor(name = "Barrows Escape Route", description = "Keeps a visible route back to your Barrows tunnel entry ladder", tags = {"barrows", "crypt", "maze", "route", "navigation"})
public class BarrowsEscapeRoutePlugin extends Plugin
{
	static final int CRYPT_REGION_ID = 14231;
	@Inject private Client client;
	@Inject private OverlayManager overlayManager;
	@Inject private BarrowsEscapeRouteOverlay overlay;
	@Inject private BarrowsEscapeRouteConfig config;
	/** Template/world coordinates survive RuneLite rebuilding the instance scene. */
	@Getter private List<WorldPoint> route = java.util.Collections.emptyList();
	private final List<WorldPoint> travelledRoute = new java.util.ArrayList<>();
	@Getter private final Set<TileObject> doors = new HashSet<>();
	@Getter private TileObject nextDoor;
	@Getter private TileObject chest;
	@Getter private boolean chestOpened;
	@Getter private WorldPoint entryAnchor;
	private int sceneScanTicks;
	@Getter private boolean mazeActive;
	private boolean awaitingMazeLoad;
	private boolean captureEntryAfterLoad;
	private boolean routeLocked;

	@Provides BarrowsEscapeRouteConfig provideConfig(ConfigManager configManager) { return configManager.getConfig(BarrowsEscapeRouteConfig.class); }
	@Override protected void startUp() { overlayManager.add(overlay); }
	@Override protected void shutDown() { overlayManager.remove(overlay); resetRun(); }

	@Subscribe public void onGameStateChanged(GameStateChanged event)
	{
		if (event.getGameState() == GameState.LOADING && awaitingMazeLoad)
		{
			// A hidden-tunnel click can cause one or more scene transitions.  Keep
			// the signal armed through all of them: only seeing the real exit ladder
			// beside the player is sufficient to begin a maze run.
			clearMazeState();
		}
		else if (event.getGameState() == GameState.LOADING && mazeActive)
		{
			// Door state changes rebuild the scene.  Discard the old wall objects
			// so the following tick promotes the newly-openable door instead.
			doors.clear();
			nextDoor = null;
			sceneScanTicks = 9;
		}
		else if (event.getGameState() == GameState.LOGGED_IN && !isInCrypt()) { resetRun(); }
	}
	@Subscribe public void onChatMessage(ChatMessage event)
	{
		// Opening the correct sarcophagus produces this message box before the
		// player clicks through to the actual tunnel maze.  It is the reliable
		// boundary between a brother's tomb and the maze instance.
		if (!mazeActive && event.getType() == ChatMessageType.MESBOX
			&& event.getMessage() != null
			&& event.getMessage().contains("You've found a hidden tunnel"))
		{
			awaitingMazeLoad = true;
			log.debug("Barrows hidden tunnel found; waiting for maze load");
		}
	}
	@Subscribe public void onGameObjectSpawned(GameObjectSpawned event) { trackObject(event.getGameObject()); }
	@Subscribe public void onGameObjectDespawned(GameObjectDespawned event) { untrackObject(event.getGameObject()); }
	@Subscribe public void onWallObjectSpawned(WallObjectSpawned event) { trackObject(event.getWallObject()); }
	@Subscribe public void onWallObjectDespawned(WallObjectDespawned event) { untrackObject(event.getWallObject()); }

	@Subscribe public void onGameTick(GameTick event)
	{
		// The hidden-tunnel continuation does not consistently emit a client
		// game-state event.  The first unambiguous maze-only condition is the
		// player spawning beside the maze's exit ladder.
		if (!mazeActive && awaitingMazeLoad && isBesideMazeExitLadder())
		{
			clearMazeState();
			mazeActive = true;
			awaitingMazeLoad = false;
			captureEntryAfterLoad = false;
			log.debug("Barrows maze entry ladder detected; capturing entry tile");
		}
		if (!mazeActive || !isInCrypt()) { return; }
		Player player = client.getLocalPlayer();
		if (player == null) { return; }
		LocalPoint location = player.getLocalLocation();
		// This is deliberately the *only* exit reference.  Barrows' instance
		// contains template copies of ladders, which can appear closer as the
		// player walks the maze.  The tile occupied immediately after the hidden
		// tunnel load is stable and is the only tile that can represent the entry.
		if (entryAnchor == null)
		{
			entryAnchor = toStablePoint(location);
			log.debug("Captured Barrows maze entry tile at {}", entryAnchor);
		}
		recordTravelledLocation(location);
		// Instanced tunnel chunks can arrive a few ticks after the player.  Re-scan
		// while either endpoint is absent and periodically after a door reshuffle.
		if (chest == null || ++sceneScanTicks >= 10)
		{
			scanScene();
			sceneScanTicks = 0;
		}
		// The initial planner result is the known-good ladder-to-chest route.
		// Do not mutate it as player position and room objects update.
		if (!chestOpened && !routeLocked && rebuildGuidance(location)) { routeLocked = true; }
	}

	@Subscribe public void onWidgetLoaded(WidgetLoaded event)
	{
		if (event.getGroupId() == InterfaceID.BARROWS_REWARD && mazeActive && isInCrypt())
		{
			Player player = client.getLocalPlayer();
			if (player != null) { recordTravelledLocation(player.getLocalLocation()); }
			if (chest != null) { recordTravelledLocation(chest.getLocalLocation()); }
			if (route.isEmpty()) { route = new java.util.ArrayList<>(travelledRoute); }
			chestOpened = true;
			log.debug("Barrows chest opened; route now points toward entry ladder");
		}
	}

	void resetRun() { clearMazeState(); mazeActive = false; awaitingMazeLoad = false; captureEntryAfterLoad = false; }
	private void clearMazeState() { route = java.util.Collections.emptyList(); travelledRoute.clear(); doors.clear(); nextDoor = null; chest = null; chestOpened = false; entryAnchor = null; sceneScanTicks = 0; routeLocked = false; }
	/**
	 * Barrows tunnels are a dynamic map instance.  A player's WorldPoint can
	 * retain the template coordinate during a transition, so use the map regions
	 * actually loaded by the client rather than comparing that WorldPoint.
	 */
	private boolean isInCrypt()
	{
		int[] regions = client.getMapRegions();
		if (regions == null) { return false; }
		for (int region : regions)
		{
			if (region == CRYPT_REGION_ID) { return true; }
		}
		return false;
	}
	private boolean isBesideMazeExitLadder()
	{
		Player player = client.getLocalPlayer();
		if (player == null || player.getLocalLocation() == null || client.getScene() == null) { return false; }
		LocalPoint playerLocation = player.getLocalLocation();
		for (Tile[][] plane : client.getScene().getTiles()) for (Tile[] column : plane) for (Tile tile : column)
		{
			if (tile == null) { continue; }
			if (tile.getGameObjects() != null) for (GameObject object : tile.getGameObjects())
			{
				if (isNearbyExitLadder(object, playerLocation)) { return true; }
			}
			if (isNearbyExitLadder(tile.getWallObject(), playerLocation)
				|| isNearbyExitLadder(tile.getGroundObject(), playerLocation)
				|| isNearbyExitLadder(tile.getDecorativeObject(), playerLocation)) { return true; }
		}
		return false;
	}
	private boolean isNearbyExitLadder(TileObject object, LocalPoint playerLocation)
	{
		return object != null && isBarrowsExitLadder(object.getId()) && object.getLocalLocation() != null
			&& object.getLocalLocation().distanceTo(playerLocation) <= net.runelite.api.Perspective.LOCAL_TILE_SIZE * 12;
	}

	private void trackObject(TileObject object)
	{
		if (!mazeActive || !isInCrypt() || object == null) { return; }
		// Keep the endpoint after the chest changes model on opening so the
		// return path remains visible throughout the reward screen.
		if (isBarrowsChest(object.getId())) { chest = object; }
		else
		{
			ObjectComposition definition = client.getObjectDefinition(object.getId());
			if (definition == null) { return; }
			ObjectComposition state = definition.getImpostorIds() != null ? definition.getImpostor() : definition;
			if (state == null) { state = definition; }
			String name = state.getName();
			if (name != null && name.toLowerCase().contains("door") && hasAction(state.getActions(), "Open")) { doors.add(object); }
		}
	}
	private void untrackObject(TileObject object)
	{
		doors.remove(object);
		if (object == nextDoor) { nextDoor = null; }
		if (object == chest) { chest = null; }
	}
	private void scanScene()
	{
		doors.clear();
		for (Tile[][] plane : client.getScene().getTiles()) for (Tile[] column : plane) for (Tile tile : column)
		{
			if (tile == null) { continue; }
			if (tile.getGameObjects() != null) for (GameObject object : tile.getGameObjects()) { trackObject(object); }
			trackObject(tile.getWallObject());
			trackObject(tile.getGroundObject());
			trackObject(tile.getDecorativeObject());
		}
	}
	private void recordTravelledLocation(LocalPoint location)
	{
		WorldPoint stableLocation = toStablePoint(location);
		if (stableLocation == null) { return; }
		// Record template/world points rather than scene-local points.  LocalPoint
		// coordinates are recycled whenever a Barrows room reloads.
		if (travelledRoute.isEmpty() || !travelledRoute.get(travelledRoute.size() - 1).equals(stableLocation))
		{
			travelledRoute.add(stableLocation);
		}
	}
	private boolean rebuildGuidance(LocalPoint playerLocation)
	{
		if (playerLocation == null || chest == null) { return false; }
		CollisionData[] maps = client.getCollisionMaps();
		int plane = client.getPlane();
		if (maps == null || plane < 0 || plane >= maps.length) { return false; }
		List<LocalPoint> onward = CryptRoutePlanner.find(maps[plane], client.getScene(), playerLocation, chest.getLocalLocation(), doors);
		if (onward.isEmpty()) { return false; }
		appendRouteTail(onward);
		nextDoor = firstDoorOnRoute(onward);
		return !route.isEmpty();
	}
	private void appendRouteTail(List<LocalPoint> onward)
	{
		List<WorldPoint> combined = new java.util.ArrayList<>(travelledRoute);
		for (LocalPoint point : onward)
		{
			WorldPoint stablePoint = toStablePoint(point);
			if (stablePoint != null && (combined.isEmpty() || !combined.get(combined.size() - 1).equals(stablePoint))) { combined.add(stablePoint); }
		}
		route = combined;
	}
	private WorldPoint toStablePoint(LocalPoint point)
	{
		if (point == null) { return null; }
		try { return WorldPoint.fromLocalInstance(client, point); }
		catch (RuntimeException ignored) { return null; }
	}
	/** Resolves a saved template point into its current instance-scene position. */
	LocalPoint toCurrentLocal(WorldPoint point, LocalPoint preferred)
	{
		if (point == null) { return null; }
		LocalPoint best = null;
		int bestDistance = Integer.MAX_VALUE;
		try
		{
			for (WorldPoint instancePoint : WorldPoint.toLocalInstance(client, point))
			{
				LocalPoint local = LocalPoint.fromWorld(client, instancePoint);
				if (local == null || !local.isInScene()) { continue; }
				int distance = preferred == null ? 0 : local.distanceTo(preferred);
				if (best == null || distance < bestDistance)
				{
					best = local;
					bestDistance = distance;
				}
			}
		}
		catch (RuntimeException ignored) { return null; }
		return best;
	}
	private TileObject firstDoorOnRoute(List<LocalPoint> onward)
	{
		TileObject result = null;
		int bestIndex = Integer.MAX_VALUE;
		for (TileObject door : doors)
		{
			LocalPoint location = door.getLocalLocation();
			if (location == null) { continue; }
			for (int index = 2; index < onward.size(); index++)
			{
				// A wall door's tile lies beside the actual traversable tile.
				if (onward.get(index).distanceTo(location) <= net.runelite.api.Perspective.LOCAL_TILE_SIZE * 2 && index < bestIndex)
				{
					result = door;
					bestIndex = index;
					break;
				}
			}
		}
		return result;
	}
	private TileObject nearest(Set<TileObject> objects, LocalPoint point)
	{
		if (point == null) { return null; }
		TileObject nearest = null;
		int nearestDistance = Integer.MAX_VALUE;
		for (TileObject object : objects)
		{
			if (object.getLocalLocation() == null) { continue; }
			int distance = object.getLocalLocation().distanceTo(point);
			if (distance < nearestDistance)
			{
				nearest = object;
				nearestDistance = distance;
			}
		}
		return nearest;
	}
	private static boolean hasAction(String[] actions, String wanted)
	{
		if (actions == null) { return false; }
		for (String action : actions) { if (wanted.equalsIgnoreCase(action)) { return true; } }
		return false;
	}
	/**
	 * Recognizes the Barrows ladder object family for diagnostics/tests only.
	 * Route anchoring intentionally never chooses the nearest result from this.
	 */
	static boolean isBarrowsExitLadder(int id)
	{
		return id == ObjectID.BARROWS_LADDER
			|| id == ObjectID.BARROWS_LADDER_A
			|| id == ObjectID.BARROWS_LADDER_C
			|| id == ObjectID.BARROWS_LADDER_G
			|| id == ObjectID.BARROWS_LADDER_I;
	}
	static boolean isBarrowsChest(int id)
	{
		return id == ObjectID.BARROWS_STONE_CHEST_CLOSED
			|| id == ObjectID.BARROWS_STONE_CHEST_OPEN
			|| id == ObjectID.BARROWS_STONE_CHEST;
	}
}
