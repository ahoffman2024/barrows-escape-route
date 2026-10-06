package com.barrowsescaperoute;

import java.awt.Color;
import net.runelite.client.config.Config;
import net.runelite.client.config.ConfigGroup;
import net.runelite.client.config.ConfigItem;

@ConfigGroup("barrows-escape-route")
public interface BarrowsEscapeRouteConfig extends Config
{
	@ConfigItem(keyName = "showRoute", name = "Show route", description = "Show the route walked from the entry ladder")
	default boolean showRoute() { return true; }

	@ConfigItem(keyName = "routeColor", name = "Route color", description = "Color used for the ground route")
	default Color routeColor() { return new Color(0, 140, 255); }
}
