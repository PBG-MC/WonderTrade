# WonderTrade fork 1.6.3: honour `tradeable=false`

Branch: `feat/wt-untradeable` (not merged to `main`).

## What was built

1. **Untradeable Pokemon are refused on deposit, bypass or not.**
   `TradeUtil.refuseIfForbidden` (used by `/wondertrade` and the confirm step in
   `doWonderTrade`) calls `TradeRules.checkDeposit`. An untradeable Pokemon is refused first,
   so `wondertrade.command.trade.bypass` cannot override it. The bypass still lifts the config
   blacklist. The refusal reads a new message, `messages.pokemonUntradeable`, which is added to
   `BaseConfig`. Existing configs pick up the default.
   The old `isPokemonForbidden` is replaced by `isBlacklisted` plus the combined check.

2. **The pool keeps the flag.**
   `PokemonPropertyExtractor.ALL` has no extractor for `tradeable`. I checked this in the
   Cobblemon 1.8.2 binary with `javap`: the `ALL` list contains SPECIES, FORM, SHINY, ASPECTS,
   LEVEL, GENDER, FRIENDSHIP, POKEBALL, NATURE, ABILITY, NICKNAME, STATUS, IVS and EVS, and no
   tradeable entry. So the deposit string now gets an explicit `tradeable=yes|no` appended
   (`TradeRules.withTradeable`). Before this, a deposited locked Pokemon re-entered the pool as
   tradeable.

3. **Withdrawal re-check.** After the reward is built, `doWonderTrade` checks
   `received.getTradeable()`. If it is false, the drawn entry is restored to the pool, the party
   is untouched, the player gets `tradeFailed`, and an error is logged naming the entry. The
   reason: a hand-curated `pool.json` or `/reloadpool` can hold an untradeable entry that never
   passed the deposit check.

4. **Version and build.** `mod_version=1.6.3+cobblemon1.8`. `cobblemon_version=1.8.2+1.21.1`.
   `fabric.mod.json` declares `"cobblemon": ">=1.8.1 <1.9.0"`. Fabric only.

## Tests

`./gradlew :common:test`: **BUILD SUCCESSFUL, 23 tests, 0 failures, 0 errors, 0 skipped**
(counted from `common/build/test-results/test/*.xml`):

| Suite | Tests | Result |
|---|---|---|
| `GuiConfigDefaultsTest` (existing) | 3 | pass |
| `TradeRulesTest` (new) | 7 | pass |
| `WonderTradePersistenceTest` (existing) | 13 | pass |

`TradeRulesTest` covers: untradeable refused without bypass; untradeable refused with bypass;
blacklisted refused without bypass; bypass lifts the blacklist only; tradeable unlisted allowed;
`withTradeable` writes `tradeable=no` and `tradeable=yes`.

Not covered by unit tests: the `Pokemon.getTradeable()` and `createPokemonProperties` wiring in
`TradeUtil`. Those need a Minecraft and Cobblemon runtime. They compile against 1.8.2 and are
verified only in the dev test below.

`./gradlew :fabric:build`: **BUILD SUCCESSFUL.** The packaged `fabric.mod.json` reads
`"version": "1.6.3+cobblemon1.8"` and `"cobblemon": ">=1.8.1 <1.9.0"`. The jar contains
`TradeRules.class` and `TradeRules$DepositCheck.class`.

**Jar:** `C:\Users\Connor\orca\workspaces\PBG-WonderTrade\wt-untradeable\fabric\build\libs\wondertrade-fabric-1.6.3+cobblemon1.8.jar`

(The `-dev-shadow` and `-sources` jars are in the same directory. Deploy the plain jar.)

## Deviations from the brief

- **Version string.** The brief says "1.6.3". I used `1.6.3+cobblemon1.8`, matching the
  `1.6.2+cobblemon1.8` scheme the other in-house jars use.
- **`isPokemonForbidden`.** It is replaced by `refuseIfForbidden` and `TradeRules.checkDeposit`,
  which fold the untradeable and blacklist checks together with the bypass rule. The brief allowed
  an equivalent.
- **Withdrawal policy (a decision for the owner).** An untradeable pool entry is refused and put
  back, not handed out. The alternative was to give it to the player. I chose refusal because the
  flag exists to stop locked Pokemon moving through WonderTrade.
- **Cobblemon sources.** The `-sources.jar` for 1.8.2 in the Gradle cache is a stub with no
  `.kt` files, so I checked `ALL` and `getTradeable` against the binary jar with `javap`.

## Not done / left

- **PokeSell** (separate repo, spike §3.4 FAIL) is not touched. The brief's scope here is
  WonderTrade only.
- **Blacklist belt-and-braces.** Adding `"tradeable=no"` to `blacklist` in `main.json` would now
  be redundant, since untradeable mons are refused in code. It is not needed.
- **Existing pool entries.** Entries deposited before this build carry no `tradeable=` key. They
  are still tradeable, which is correct. No pool migration is needed.
- **Not run in game.** Nothing has been deployed or run on a server.

## How to deploy and test on dev

Dev only. Do not deploy to main without owner sign-off.

1. Put the jar in the dev server's `mods/`, replacing the old `wondertrade-fabric-*` jar, and
   restart the dev server.
2. Check the log shows `WonderTrade by Polymeta starting up!` with no mixin or dependency errors.
3. Give a tester a locked Pokemon: `pokegive <player> species=pikachu tradeable=no`.
4. Run `/wondertrade <slot>` with that Pokemon. Expect the "locked and cannot be traded" message,
   and no confirm prompt.
5. Repeat step 4 as an op with the bypass permission. Expect the same refusal.
6. Give a normal Pokemon and trade it. Expect success, and `pool.json` should now contain an entry
   ending in `tradeable=yes`.
7. Optional: hand-add an entry ending in `tradeable=no` to `pool.json`, then `/reloadpool`. Draw
   it with a trade. Expect `tradeFailed`, the pool entry to remain, and the error
   `is untradeable and must not be handed out` in the log. Then remove that entry.
