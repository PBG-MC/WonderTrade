# GUI styling / titles report

Window titles were already configurable (`gui.mainWindowTitle`, `gui.confirmationWindowTitle`,
`gui.poolWindowTitle`, MiniMessage). This change makes the decorative panes configurable too.

## New keys (all under `gui` in `config/wondertrade/main.json`, all optional)

| Key | Default | Notes |
|---|---|---|
| `borderItem` | `minecraft:red_stained_glass_pane` | row 0 of the trade/confirm GUIs |
| `accentBorderItem` | `minecraft:black_stained_glass_pane` | row 1 |
| `fillerItem` | `minecraft:white_stained_glass_pane` | row 2 |
| `hideFillers` | `false` | `true` = place no border/filler/pool-footer panes, so the texture shows through |
| `poolFooterItem` | `minecraft:red_stained_glass_pane` | pool GUI bottom row (slots 45-53) |
| `poolFooterName` | `""` | MiniMessage; blank = empty name (as today) |

Unknown item ids fall back to the default pane. `generateBorders=false` still disables the rows.

## Kit title example

A textured title is a negative-space shift glyph, then the background glyph from the resource
pack's font, then the normal title text. In JSON these are `\u` escapes (codepoints below are
placeholders - use the pack's real ones):

```json
"gui": {
  "mainWindowTitle": "<reset><white>Wonder<red>Trade",
  "hideFillers": true
}
```

## Verification
`./gradlew build` green; new `GuiConfigDefaultsTest` covers defaults, legacy config loading and
round-trip. Item placement needs a running server and was not exercised in-game.
