# MoonXReplay

**MoonXReplay** is a Minecraft replay plugin for servers running Minecraft 1.8 through 1.21. It records player actions and stores replay data so that replays can be watched later. Replay data can be stored locally or through the configured database/S3 backend.

## Features

- Record player actions and replay them later.
- Support for timed recordings using seconds or minutes.
- Personal Replay management GUI.
- Left-click a Replay to play it.
- Right-click a Replay to open the delete confirmation menu.
- Green glass confirms deletion; red glass cancels it.
- Emerald button starts a new recording flow.
- Creator-prefixed local files, such as `Zirox-hello.replay`.
- Optional `-force` mode to overwrite an existing Replay.
- Configurable messages in `plugins/AdvancedReplay/lang.yml`.
- Java 21 GitHub Actions build workflow that publishes the generated `Replay.jar`.

## Installation

1. Install [ProtocolLib](https://www.spigotmc.org/resources/protocollib.1997/).
2. Download the latest `Replay.jar` and place it in the server's `plugins` directory.
3. Start or restart the server.
4. Configure messages in `plugins/AdvancedReplay/lang.yml` if needed.

## Commands

The main command is `/replay`, with `/rp` available as an alias.

| Command | Description |
| --- | --- |
| `/replay gui` | Opens the personal Replay management GUI. |
| `/replay start <Name>[:Duration] [Players...]` | Starts recording a Replay. |
| `/replay stop <Name> [-force\|-nosave]` | Stops and saves a recording, optionally overwriting an existing Replay or discarding the recording. |
| `/replay play <Name>` | Plays a saved Replay for the executing player. |
| `/replay delete <Name>` | Deletes a saved Replay. |
| `/replay leave` | Leaves the Replay currently being watched. |
| `/replay info <Name>` | Displays information about a Replay. |
| `/replay list [Page]` | Lists saved Replays. |
| `/replay version` | Displays `MoonXReplay by Zirox`. |

### Personal Replay GUI

Run:

```text
/replay gui
```

The GUI lists Replays created by the executing player. Left-click a Replay to play it. Right-click to open the delete confirmation screen. The green glass button deletes the Replay, while the red glass button cancels the operation. Click the Emerald button at the bottom to start recording a new Replay.

The recording flow asks for the Replay name and duration in chat. Enter durations using one of these formats:

```text
60s
120m
```

Type `cancel` at any stage to cancel the flow. To overwrite an existing Replay, run:

```text
/replay gui -force
```

## Permissions

The primary permission is `replay.command`. Subcommands use the following permissions:

- `replay.command.gui`
- `replay.command.start`
- `replay.command.stop`
- `replay.command.play`
- `replay.command.delete`
- `replay.command.leave`
- `replay.command.info`
- `replay.command.list`
- `replay.command.version`

## Downloads

- **Modrinth:** <https://modrinth.com/plugin/advancedreplay>
- **Spigot:** <https://www.spigotmc.org/resources/advancedreplay-1-8-1-21.52849/>

## API

Some API usage examples are available on the plugin's Spigot page. The existing Java API remains available under the project's current Maven coordinates.

### Maven

Add the repositories:

```xml
<repository>
    <id>jitpack.io</id>
    <url>https://jitpack.io</url>
</repository>

<repository>
    <id>maven-snapshots</id>
    <url>https://s01.oss.sonatype.org/content/repositories/snapshots/</url>
</repository>
```

Add the dependency:

```xml
<dependency>
    <groupId>com.github.Jumper251</groupId>
    <artifactId>AdvancedReplay</artifactId>
    <version>VERSION</version>
    <scope>provided</scope>
</dependency>
```

### Gradle

```gradle
repositories {
    maven { url 'https://jitpack.io' }
}

dependencies {
    compileOnly 'com.github.Jumper251:AdvancedReplay:VERSION'
}
```
