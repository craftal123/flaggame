# FlagGame

A two-player flag guessing game for Paper. It integrates directly with
[Andavin/Images](https://github.com/Andavin/Images) to place a flag without requiring
a player to right-click.

## Requirements

- Paper 26.1.2
- The 26.1.2-compatible build of Andavin Images 2.5.10
- Java 25 for the Paper 26.1.2 server

## Install

1. Build with `mvn clean package`.
2. Put `target/FlagGame-1.0.0.jar` and the Images JAR in the server `plugins` folder.
3. Start the server once and edit `plugins/FlagGame/config.yml` if needed.

The image location and direction are configured in `plugins/FlagGame/config.yml`:

```yaml
location:
  x: 0
  y: 0
  z: 0
  direction: NORTH
```

Valid directions are `NORTH`, `SOUTH`, `EAST`, and `WEST`. The selected world is
the world where `/flaggame start` or `/flaggame test` is used. After changing the
configuration, run `/flaggame reload`.

On startup the plugin loads the current flag list from
`https://flagcdn.com/en/codes.json` and caches it as
`plugins/FlagGame/flagcdn-codes.json`. It is not limited to a manually hard-coded
country list. Original flags are downloaded once from
`https://flagcdn.com/w320/<code>.png` into `plugins/Images`. The displayed copy is
resized to 384x256 pixels, creating a 3x2 map display. Width and height remain
configurable; multiples of 128 produce exact map dimensions.

## Commands

- `/flaggame join`
- `/flaggame leave`
- `/flaggame start <easy|medium|hard>`
- `/flaggame test <easy|medium|hard>` (`flaggame.admin`, starts a solo test game)
- `/flaggame what` (`flaggame.admin`, privately shows the current answer)
- `/flaggame skip` (vote to skip the current flag)
- `/flaggame status`
- `/flaggame stop` (`flaggame.admin`)
- `/flaggame reload` (`flaggame.admin`)

Difficulty pools are cumulative:

- `easy`: 34 highly recognizable flags
- `medium`: 119 commonly encountered country flags
- `hard`: every country and territory in the live FlagCDN catalog

Leaving out the difficulty defaults to `medium`. `/fg` is an alias for
`/flaggame`, so `/fg start easy` and `/fg test hard` work identically.

Flags are drawn without replacement, so the same flag cannot appear twice during
one game. The used-flag history resets when a new game starts.

During a round, recognized wrong country guesses from participating players are
hidden from global chat and echoed privately to the player who wrote them. This
prevents country-name spam while still letting each player see their own guess.

Each round includes a clickable vote-skip message. Players can click it or run
`/flaggame skip` (or `/fg skip`). When every player in the match votes, the answer
is revealed, no point is awarded, and the next flag begins.

Exactly two players join, then either player starts the game. The first correct
English or Hebrew chat answer earns a point. Country names are case-insensitive,
ignore accents and punctuation, and include common forms such as `Turkey`,
`Turkiye`, `Türkiye`, `Cote d'Ivoire`, `UK`, and `USA`. First to 10 wins.
