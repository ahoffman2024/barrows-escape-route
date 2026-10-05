package com.barrowsescaperoute;

import net.runelite.client.RuneLite;
import net.runelite.client.externalplugins.ExternalPluginManager;

public class BarrowsEscapeRoutePluginTest
{
	public static void main(String[] args) throws Exception
	{
		ExternalPluginManager.loadBuiltin(BarrowsEscapeRoutePlugin.class);
		RuneLite.main(args);
	}
}
