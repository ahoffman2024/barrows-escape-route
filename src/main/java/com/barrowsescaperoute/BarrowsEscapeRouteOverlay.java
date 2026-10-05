package com.barrowsescaperoute;

import java.awt.BasicStroke;
import java.awt.Color;
import java.awt.Dimension;
import java.awt.Graphics2D;
import java.awt.Polygon;
import java.util.List;
import javax.inject.Inject;
import net.runelite.api.Client;
import net.runelite.api.GameObject;
import net.runelite.api.Perspective;
import net.runelite.api.TileObject;
import net.runelite.api.coords.LocalPoint;
import net.runelite.api.coords.WorldPoint;
import net.runelite.client.ui.overlay.Overlay;
import net.runelite.client.ui.overlay.OverlayLayer;
import net.runelite.client.ui.overlay.OverlayPosition;

class BarrowsEscapeRouteOverlay extends Overlay
{
	private final Client client;
	private final BarrowsEscapeRoutePlugin plugin;
	private final BarrowsEscapeRouteConfig config;

	@Inject
	private BarrowsEscapeRouteOverlay(Client client, BarrowsEscapeRoutePlugin plugin, BarrowsEscapeRouteConfig config)
	{
		this.client = client;
		this.plugin = plugin;
		this.config = config;
		setPosition(OverlayPosition.DYNAMIC);
		setLayer(OverlayLayer.ABOVE_SCENE);
	}

	@Override
	public Dimension render(Graphics2D graphics)
	{
		if (!plugin.isMazeActive()) { return null; }
		if (config.showRoute()) { renderRoute(graphics, routeForCurrentPhase()); }
		if (config.showExitLadder()) { renderPoint(graphics, plugin.toCurrentLocal(plugin.getEntryAnchor(), null), "Exit ladder"); }
		if (config.showRoute()) { renderObject(graphics, plugin.getChest(), "Chest"); }
		if (config.showDoor()) { renderObject(graphics, plugin.getNextDoor(), plugin.isChestOpened() ? "Return route" : "Open door"); }
		return null;
	}
	private List<LocalPoint> routeForCurrentPhase()
	{
		List<WorldPoint> stableRoute = new java.util.ArrayList<>(plugin.getRoute());
		if (plugin.isChestOpened()) { java.util.Collections.reverse(stableRoute); }
		List<LocalPoint> localRoute = new java.util.ArrayList<>();
		LocalPoint previous = null;
		for (WorldPoint point : stableRoute)
		{
			LocalPoint local = plugin.toCurrentLocal(point, previous);
			localRoute.add(local);
			if (local != null) { previous = local; }
		}
		return localRoute;
	}

	private void renderRoute(Graphics2D graphics, List<LocalPoint> points)
	{
		Color color = config.routeColor();
		graphics.setColor(new Color(color.getRed(), color.getGreen(), color.getBlue(), 180));
		graphics.setStroke(new BasicStroke(3f, BasicStroke.CAP_ROUND, BasicStroke.JOIN_ROUND));
		java.awt.Point previous = null;
		for (LocalPoint point : points)
		{
			if (point == null) { previous = null; continue; }
			Polygon tile;
			try
			{
				tile = Perspective.getCanvasTilePoly(client, point);
			}
			catch (RuntimeException ignored)
			{
				previous = null;
				continue;
			}
			if (tile == null) { previous = null; continue; }
			java.awt.Rectangle bounds = tile.getBounds();
			java.awt.Point center = new java.awt.Point(bounds.x + bounds.width / 2, bounds.y + bounds.height / 2);
			if (previous != null) { graphics.drawLine(previous.x, previous.y, center.x, center.y); }
			previous = center;
		}
	}

	private void renderObject(Graphics2D graphics, TileObject object, String label)
	{
		if (object == null) { return; }
		LocalPoint localLocation = object.getLocalLocation();
		if (localLocation == null) { return; }
		java.awt.Shape polygon;
		try
		{
			// Scene objects can be disposed between a game tick and an overlay
			// frame while an instance template is being swapped.
			polygon = object.getClickbox();
		}
		catch (RuntimeException ignored)
		{
			// Some instanced ladder models have no stable 3D clickbox.  The tile
			// polygon is still valid and provides a reliable visible fallback.
			polygon = null;
		}
		if (polygon == null)
		{
			try
			{
				// TileObject owns the correct instanced WorldView.  The legacy
				// client-level projection can put a dynamic-map tile in another
				// template chunk.
				polygon = object.getCanvasTilePoly();
			}
			catch (RuntimeException ignored)
			{
				return;
			}
		}
		if (polygon == null) { return; }
		graphics.setColor(config.routeColor());
		graphics.setStroke(new BasicStroke(2f));
		graphics.draw(polygon);
		graphics.drawString(label, polygon.getBounds().x, polygon.getBounds().y - 4);
	}
	private void renderPoint(Graphics2D graphics, LocalPoint point, String label)
	{
		if (point == null) { return; }
		Polygon polygon;
		try
		{
			polygon = Perspective.getCanvasTilePoly(client, point);
		}
		catch (RuntimeException ignored)
		{
			return;
		}
		if (polygon == null) { return; }
		graphics.setColor(config.routeColor());
		graphics.setStroke(new BasicStroke(2f));
		graphics.draw(polygon);
		graphics.drawString(label, polygon.getBounds().x, polygon.getBounds().y - 4);
	}
}
