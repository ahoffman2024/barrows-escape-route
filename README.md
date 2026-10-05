# Barrows Escape Route

Barrows Escape Route is a display-only RuneLite Plugin Hub plugin for the Barrows tunnel maze. It identifies the tunnel's return ladder and reward chest, then draws a blue route through the tiles currently reachable via the maze's open doors. After the chest opens, the same route points back to the ladder.

## Features

- Highlights the Barrows return ladder in blue.
- Highlights the Barrows reward chest in blue.
- Draws a blue ladder-to-chest route over the live collision map, so closed doors cannot be used by the route.
- Recalculates the route whenever the scene changes, including a maze rearrangement after a failed lock.
- Reverses the route after the Barrows reward interface opens, directing the player back to the exit ladder.
- Stays inactive in a brother's crypt; overlays begin only when the reward chest confirms the player is in the tunnel maze.

The plugin does not click, move, alter menus, send input, or communicate with any external service.

## Configuration

- Show route
- Highlight exit ladder
- Highlight nearby openable door
- Route color

## Development and testing

Run the automated tests with:

```sh
./gradlew test check
```

Launch a development RuneLite client with:

```sh
./gradlew run
```

In game, enter a Barrows tunnel and confirm that the correct floor ladder and chest are outlined in blue and joined by a blue route. Open a wrong puzzle door to confirm the route changes with the maze, then open the reward chest and confirm the blue route leads back to the ladder. Finally, leave the tunnel and verify the overlay clears.
