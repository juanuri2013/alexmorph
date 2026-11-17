# Alex's Morph (Forge 1.20.1)

Version 1.0.1

Kill any **Alex's Mobs** creature to unlock it, then morph into it.

## How to play
- Kill an Alex's Mobs mob (including with a player-owned projectile) -> "Unlocked morph: ..." appears in chat.
- Press **V** to cycle to the next morph (human -> mob 1 -> mob 2 -> ... -> human). **Shift+V** goes backwards. Rebind in Controls.
- You look like the mob, your hitbox and eye height match it, and other players see it too.
- Abilities are read from the mob itself, so they work for every Alex's Mobs creature:
  - Flyers (detected from the mob's flying AI/navigation) can fly (double-tap jump) and take no fall damage.
  - Water mobs get water breathing everywhere and Dolphin's Grace while swimming.
  - Fire-immune mobs get fire resistance.
  - Your walking speed and max health follow the mob's (clamped: speed -30%..+50%, health 5..20 hearts).
  - `MorphAbilities.FORCE_FLY` lists mobs that always fly if auto-detection misses one.
- Animations: the model is ticked every client tick, so walk/idle/flap animations that depend on entity state run. Flyers switch to their flying pose while airborne.

## Progression (advancement tab "Alex's Morph")
- Trophy Hunter (first unlock) -> Budding Collector (5) -> Menagerie (15) -> Zookeeper (30) -> Gotta Morph 'Em All (every morph, challenge)
- Shapeshifter (first morph) branches into Up in the Air (flyer), Fish Out of Water (water mob), Fireproof (fire-immune mob) and Identity Crisis (change forms 25 times, including returning to human)
- Goal and challenge tiers also give XP. Edit the JSON files in `data/alexmorph/advancements/` to change icons, thresholds' rewards or text.

## Commands
- `/morph <mob>` - morph into an unlocked mob (e.g. `/morph alexsmobs:grizzly_bear`)
- `/morph none` - back to human
- `/morph list` - what you've unlocked
- `/morph unlock <mob>` and `/morph unlockall` - OP only, handy for testing

## Build
Needs JDK 17 (the wrapper downloads Gradle 8.1.1 itself).

    ./gradlew build        (Windows: gradlew.bat build)

The jar ends up in `build/libs/`. To test in the dev client, run `./gradlew runClient` and drop the Alex's Mobs 1.20.1 jar (plus its required dependency Citadel) into `run/mods/`.

Install: put the built jar in your `mods` folder next to Alex's Mobs and Citadel (server and clients).

## Notes / limitations
- Flight is only marked as granted by AlexMorph when AlexMorph itself changes `mayfly` from false to true; pre-existing creative/spectator or other-mod flight is not claimed by the morph. If the player changes to Creative/Spectator while AlexMorph owns the flight grant, the ownership marker is kept until they return to a non-flight game mode.
- Player-owned projectile kills are credited to the projectile owner where available.
- Stale saved morph IDs from removed/incompatible entity registrations are ignored.
- The morphed mob is a client-side dummy with no AI, so Alex's Mobs creatures with special animations (attacking, sleeping, flying poses) mostly play their idle/walk animation.
- The code looks mobs up by registry id (`alexsmobs:*`), so it doesn't need Alex's Mobs at compile time. To morph into mobs from another mod, change `MorphManager.NS` and the `alexsmobs` dependency in `mods.toml`.
- The source has been statically reviewed and the resource JSON files validated. A full Forge build and in-game test are still required before release; those checks cannot be replaced by source inspection.

## Building online (no install needed)
Upload this project to a GitHub repository (make sure the hidden `.github` folder is included). GitHub Actions builds the jar automatically; download it from the **Actions** tab -> latest run -> **Artifacts**.
