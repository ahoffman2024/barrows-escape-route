package com.barrowsescaperoute;

import net.runelite.api.gameval.ObjectID;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;
import org.junit.Test;

public class BarrowsObjectDetectionTest
{
	@Test
	public void recognizesEveryTunnelLadderModelButNotCryptStairs()
	{
		assertTrue(BarrowsEscapeRoutePlugin.isBarrowsExitLadder(ObjectID.BARROWS_LADDER));
		assertTrue(BarrowsEscapeRoutePlugin.isBarrowsExitLadder(ObjectID.BARROWS_LADDER_A));
		assertTrue(BarrowsEscapeRoutePlugin.isBarrowsExitLadder(ObjectID.BARROWS_LADDER_C));
		assertTrue(BarrowsEscapeRoutePlugin.isBarrowsExitLadder(ObjectID.BARROWS_LADDER_G));
		assertTrue(BarrowsEscapeRoutePlugin.isBarrowsExitLadder(ObjectID.BARROWS_LADDER_I));
		assertFalse(BarrowsEscapeRoutePlugin.isBarrowsExitLadder(ObjectID.BARROWS_STAIRS_DHAROK));
	}

	@Test
	public void recognizesClosedAndOpenedRewardChest()
	{
		assertTrue(BarrowsEscapeRoutePlugin.isBarrowsChest(ObjectID.BARROWS_STONE_CHEST_CLOSED));
		assertTrue(BarrowsEscapeRoutePlugin.isBarrowsChest(ObjectID.BARROWS_STONE_CHEST_OPEN));
		assertTrue(BarrowsEscapeRoutePlugin.isBarrowsChest(ObjectID.BARROWS_STONE_CHEST));
		assertFalse(BarrowsEscapeRoutePlugin.isBarrowsChest(ObjectID.ARENA_GUARD_CHEST_SHUT));
	}
}
