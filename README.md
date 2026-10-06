# Barrows Escape Route

Barrows Escape Route draws a ground route through the Barrows tunnel maze: from the entrance toward the reward chest, then back toward the entrance after the chest is opened.

## Features

- Draws a blue route through tiles currently reachable through open maze doors.
- Reverses the route after the Barrows reward interface opens, guiding the return to the tunnel entrance.
- Uses a display-only overlay. It does not click, move, alter menus, send input, or communicate with external services.

## Configuration

- Show route
- Route color

## Development

Run automated checks:

```sh
./gradlew test check
```

Launch a development RuneLite client:

```sh
./gradlew run
```

In game, enter a Barrows tunnel, confirm the route appears on the ground, open the reward chest, and confirm the route points back toward the entrance. Finally, leave the tunnel and confirm the overlay clears.
