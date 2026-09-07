# PBG fork notes

Fork of [Polymeta/WonderTrade](https://github.com/Polymeta/WonderTrade) (MIT), taken at
upstream `0ab9f56` ("update to cobblemon 1.7"). The pristine base is kept on the
`upstream` branch; our work lives on `main`.

```
git remote -v
  origin    https://github.com/PBG-MC/WonderTrade.git
  upstream  https://github.com/Polymeta/WonderTrade.git
```

To pull a future upstream release: fetch `upstream`, fast-forward the `upstream`
branch, then rebase `main` onto it.

## Why we forked

WonderTrade broke on PBG main after the 2026-09-06 Cobblemon 1.8 cut-over: the pool
was empty and every trade failed with `bound must be positive`.

**That was not a Cobblemon 1.8 API break.** Every Cobblemon symbol this mod touches
(`PokemonProperties.parse/create/matches`, `createPokemonProperties`,
`PokemonPropertyExtractor.ALL`, `Cobblemon.config.getMaxPokemonLevel`,
`getStorage().getParty`, `getPermissionValidator`, and the `species=random` property)
is present and unchanged in `Cobblemon-fabric-1.8.0+1.21.1.jar`. The trigger was our
own `poolSize: 0` config combined with a `pool.json` that the 1.8 world reset had
emptied — fixed separately in the server monorepo.

But getting there exposed a cluster of upstream reliability bugs that turn ordinary
misconfiguration into a hard, unrecoverable outage. Those are what this fork fixes.

## Divergence from upstream

### Build
- Targets Cobblemon `1.8.0+1.21.1` (was `1.7.0+1.21.1-SNAPSHOT`) and Architectury
  `13.0.8`, matching the jars on the PBG servers.
- **NeoForge subproject removed.** PBG is Fabric-only; this matches the convention in
  `PBG-Raid-Dens` and `PBG-Safari-Dimension`.
- Version scheme `1.6.1+cobblemon1.8`, matching the other in-house jars.
- `fabric.mod.json` now requires `cobblemon >=1.8.0`.

### `TradeUtil.doWonderTrade`
- **Empty pool no longer throws.** Upstream ran
  `pool.pokemon.remove(rng.nextInt(pool.pokemon.size()))` unguarded, so an empty pool
  meant `nextInt(0)` → `IllegalArgumentException: bound must be positive` → "An
  unexpected error occurred trying to execute that command", on every single trade.
  Worse, this was self-sealing: the only way to refill the pool through play is a
  deposit, and deposits were exactly what crashed. Now the player gets a message and
  a regeneration is kicked off, so the pool heals itself.
- **Draw + deposit is atomic** (`WonderTrade.drawAndDeposit`), so two players trading
  in the same tick cannot draw the same entry.
- **Rollback on failure.** The reward Pokemon is built *before* the party is touched,
  and if it cannot be built (malformed pool entry) or cannot be added (no room), the
  pool and the party are both restored. Upstream ignored the return of
  `playerParty.add(...)` and could silently void a player's Pokemon.
- Level clamping now happens before the deposit is snapshotted, so what is banked in
  the pool is what was advertised.

### `WonderTrade.regeneratePool`
- **The `regenerating` flag is reset in a `finally`.** Upstream set it inside the
  worker and cleared it only on the success path — one exception or one blacklist
  bail-out and regeneration stayed "in progress" for the rest of the server's uptime,
  which also permanently blocks `/wondertrade` (every command path early-returns on
  that flag).
- **The flag is claimed with `compareAndSet` before dispatch.** Upstream checked it on
  the calling thread and set it on the worker, so two calls in the same tick could
  both start.
- **Generates into a scratch list and swaps at the end.** Upstream cleared the live
  pool first, so for the whole duration of a regeneration every trade hit the empty
  pool crash above.
- **`poolSize <= 0` is refused with an explanatory log line** instead of silently
  producing an empty pool.
- **Level range fixed.** `rng.nextInt(origin, bound)` is exclusive at the top, so
  upstream could never roll `poolMaxLevel` (a 1–30 config produced 1–29 — visible in
  our live pool). It also threw when `poolMinLevel == poolMaxLevel`. Both handled.
- Retry loop rewritten; on giving up it now keeps the existing pool rather than
  leaving it cleared.

### `WonderTrade.loadConfig` / `loadPool`
- `poolSize <= 0` falls back to the default with a warning naming the consequence.
- A `pool.json` that parses to `null` no longer NPEs later.

### Concurrency
- All pool access goes through `poolLock`. Upstream shared a bare `ArrayList` between
  the regeneration worker and the server thread with no synchronization.
- `PoolGui` takes a snapshot once per GUI, so paging is stable and cannot trip over a
  concurrent trade.

### `Reload`
- Removed the `regeneratepool` redirect. `Reload` registered that literal *after*
  `RegeneratePool` did, silently replacing the real regenerate command with a config
  reload — so `/regeneratepool` never regenerated anything.

### Config
- Two new messages, `poolEmpty` and `tradeFailed`. Existing configs pick up the
  defaults on load and get them written back.

## Building

```
./gradlew :fabric:build
# -> fabric/build/libs/wondertrade-fabric-<version>.jar
```
