# Spectrum

Chat colours and name gradients for Paper 1.21+, each defined in its own simple file. There is no built-in menu:
build one with DeluxeMenus (or any menu plugin) using the commands and placeholders below.

## Files

| File | What it holds |
| --- | --- |
| `chatcolors.yml` | The colours of chat messages |
| `namegradients.yml` | The gradients of player names |
| `config.yml` | Permissions, name source, permission commands |
| `messages.yml` | Every message the plugin sends |

Both style files work the same way. A style is written in one of two ways.

**With colours**

```yaml
ocean:
  display: "Ocean"
  mode: GRADIENT          # SINGLE, GRADIENT, LINEAR, RANDOM or RAINBOW
  colors: ["#00c6ff", "#0072ff"]
  bold: true              # also italic, underline, strikethrough, obfuscated
```

**With a MiniMessage format** (`{text}` in chatcolors.yml, `{name}` in namegradients.yml)

```yaml
frost:
  display: "Frost"
  format: "<gradient:#e6f7ff:#7fd0ff:#e6f7ff>{text}</gradient>"
```

Both files come with examples: gradients, single colours, rainbow, alternating and random colours, and matrix, frost,
lava and neon formats.

### Glitch colours

A glitch chat colour has white letters with a shadow in the colour of the style. Turn it on for one style:

```yaml
red_glitch:
  display: "Red Glitch"
  colors: ["#f13a3a"]
  glitch: true
```

or for every chat colour that is made of colours, in `config.yml`:

```yaml
glitch:
  all: true      # a style can still say glitch: false
  text: white    # the colour of the letters
```

Styles with a `format:` are never changed, and glitch only works for chat colours, because a player name goes through
colour codes that cannot carry a shadow. Glitch previews in menus (`%spectrum_chat_preview_<id>%`) are written as
MiniMessage (`<shadow:#f13a3aff><white>text`), so the menu plugin has to read MiniMessage.

In chat the shadow needs the message to stay a component all the way to the player. A plugin that sets the chat format with the old chat event (`AsyncPlayerChatEvent#setFormat`) makes Paper turn the message into a plain string, and the shadow is lost while the colour stays. Tested: with Quill or no formatter the shadow arrives, with a legacy `setFormat` plugin it does not. When a glitch style exists, Spectrum lists the plugins that use the old chat event in the console at startup.

## Commands

| Command | Use |
| --- | --- |
| `/chatcolor equip <id>`, `unequip`, `list` | Pick a chat colour |
| `/namegradient equip <id>`, `unequip`, `list` | Pick a name gradient |
| `/namegradient admin give\|remove <player> <id>` | Give or take a style (runs the commands set in `config.yml`) |
| `/spectrum reload` | Reload all files |
| `/spectrum preview <chat\|name> <id>` | Preview a style |

## Permissions

- `spectrum.chat.<id>` and `spectrum.name.<id>`: use one style (or set your own with `permission:` on the style)
- `spectrum.chat.*` and `spectrum.name.*`: use all of them (op by default)
- `spectrum.admin`: reload, preview, give and remove
- `spectrum.chatcodes`: write colour codes and MiniMessage in chat (needs `chat.allow-color-codes: true`) - deliberately outside the `spectrum.chat.*` namespace, so granting "every chat colour" never also grants this

Set `use-permissions: false` in `config.yml` to let everybody use every style.

## Placeholders (PlaceholderAPI)

Every placeholder gives colours as `&#rrggbb` codes, which DeluxeMenus and most chat, tab and scoreboard plugins understand.

**Chat colours** need no placeholder: a player's message is coloured as it is sent, whatever chat plugin you use.

**Name gradients** are not applied by Spectrum itself. Put `%spectrum_name%` where the name should go in your chat, tab or scoreboard plugin. A player without a gradient just gets their plain name.

A nickname with colours of its own (a `/nick &e...` with Essentials) is shown as it was typed instead of in the gradient, `nickname-colors-win: false` in `config.yml` puts the gradient on it anyway. `%spectrum_realname%` is the player's own name with no colours.

**For menus**, replace `<kind>` with `chat` or `name`:

- `%spectrum_<kind>_id%` and `%spectrum_<kind>_display%`: what the player has equipped
- `%spectrum_<kind>_equipped_<id>%` and `%spectrum_<kind>_owned_<id>%`: `true` or `false`
- `%spectrum_<kind>_preview_<id>%`: a preview of a style, using the player's name for `name` and the sample text from `config.yml` for `chat`

## DeluxeMenus example

```yaml
items:
  ocean:
    material: LIGHT_BLUE_DYE
    slot: 10
    display_name: "%spectrum_chat_preview_ocean%"
    left_click_commands:
      - "[player] chatcolor equip ocean"
```

## Building

```
./gradlew build
```

The jar is in `build/libs`. Licensed under MIT.
