# TimeScheduler (Paper/Spigot 1.20–1.21+)

Schedule *any* command at a specific date & time from `config.yml`. Supports console or player execution and simple repeats.

## Features
- Multiple schedules
- Timezone per schedule (default configurable)
- Run as **CONSOLE** or **PLAYER:Name**
- Repeats: `NONE`, `DAILY`, `WEEKLY`, `MONTHLY`
- No double execution (persisted in `data.yml`)
- `/timescheduler reload`

## Quick start
```bash
# 1) Build
mvn -q -e -DskipTests package

# 2) Copy JAR to your server
cp target/timescheduler-1.0.0.jar /path/to/server/plugins/

# 3) Start server once, then edit plugins/TimeScheduler/config.yml
# 4) /timescheduler reload
```

## Config example
See `src/main/resources/config.yml` for examples.

## Permissions
- `timescheduler.reload` (default: op)

## License
MIT
