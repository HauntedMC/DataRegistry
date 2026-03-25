# DataRegistry

DataRegistry keeps up-to-date player metadata and session state for HauntedMC plugins across Bukkit and Velocity.

## Features

- Unified `DataRegistry` API for platform modules.
- Player upsert/caching by UUID.
- Player online status tracking.
- Player connection info tracking (IP/virtual host/timestamps).
- Player session lifecycle tracking (open, switch backend, close).

## Requirements

- Java 21
- Maven 3.9+
- HauntedMC `DataProvider` dependency (`nl.hauntedmc.dataprovider:dataprovider:1.19.0`)
