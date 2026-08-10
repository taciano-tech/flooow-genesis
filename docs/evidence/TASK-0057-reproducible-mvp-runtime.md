# TASK-0057 Reproducible MVP Runtime

**Date:** 2026-08-10

## Result

**PASS**

The persistent MVP now has one reproducible Docker Compose package and a
Windows-safe PowerShell distribution launcher.

## Package

- multi-stage Java 21 Dockerfile;
- final image contains the runtime distribution, not the build toolchain;
- fixed non-root runtime identity `10001:10001`;
- PostgreSQL pinned to 18.4;
- PostgreSQL 18 volume mounted at `/var/lib/postgresql`;
- database health check gates API startup;
- local port and password overrides through environment variables;
- Docker context excludes repository metadata, caches, build output, IDE state,
  and logs;
- CI validates Compose, builds the image, waits for API readiness, and always
  removes its containers and volume;
- installed PowerShell launcher uses `lib/*`, avoiding the Windows batch
  command-line limit.

## Compose reproduction

The complete package produced:

```text
health=UP
postStatus=201
getStatus=200
bodiesEqual=true
databaseRows=1
apiUid=10001
apiGid=10001
```

The first reproduction found two platform-specific conditions and proved their
corrections:

1. a Windows checkout gives `gradlew` CRLF line endings, so the Linux build
   stage normalizes that file before executing it;
2. the PostgreSQL 18 image requires the durable mount at
   `/var/lib/postgresql`, rather than the older direct data directory mount.

Both failed Compose projects and their volumes were removed before the final
successful reproduction.

## Windows launcher reproduction

The installed file
`bin/marketplace-operations-api.ps1` started the persistent API against a
temporary PostgreSQL 18.4 container and returned:

```text
launcherPresent=true
health=UP
processExited=false
```

The API process and temporary database were then stopped.

## Repository validation

```text
./gradlew clean build :applications:marketplace-operations-api:installDist \
  --rerun-tasks --no-daemon
BUILD SUCCESSFUL
43 actionable tasks: 43 executed
Configuration cache entry stored.
```

The pre-existing Kotlin generated `copy()` visibility warning is unchanged.
