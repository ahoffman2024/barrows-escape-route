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

	@ConfigItem(keyName = "showExitLadder", name = "Highlight exit ladder", description = "Highlight the ladder used to enter the crypt")
	default boolean showExitLadder() { return true; }

	@ConfigItem(keyName = "showDoor", name = "Highlight next door", description = "Highlight the next openable door on the route to the chest")
	default boolean showDoor() { return true; }

	@ConfigItem(keyName = "routeColor", name = "Route color", description = "Color used for the route and markers")
	default Color routeColor() { return new Color(0, 140, 255); }
}
