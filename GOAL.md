# Veyra 1.0.0

User request (2026-11-02): study weaknesses, compare with Iris shaderpacks, finish the first stable 1.0.0 release, and send a substantial integrated change set.

Continue from main `0110146` (v0.2.3 tag at `ca95278`). Preserve existing implementations and validation fixtures. Keep user-local `logs/` untouched.

User launch preference: all unattended Veyra and Iris clients must leave the desktop cursor ungrabbed and ALL audio muted (master/category volume 0 and a non-output OpenAL backend).

## Required outcome

- Compare implementation and rendering with Iris for Minecraft 1.21.11; record concrete differences and their effect.
- Fix compatibility and stability defects in one integrated package, covering affected callers, adapters, configuration, fixtures and tests.
- Reproduce comparisons for pinned Complementary Reimagined/Unbound r5.9.3, BSL 10.1.8 and Sildur v2.02. Match world, camera, time and profile; save captures and explain remaining differences. Historical non-black smoke results do not prove visual parity.
- Validate world entry, resize, dimension round-trip, pack switching, failed replacement rollback, disable/re-enable and resource reload. Check Vulkan validation and resource cleanup.
- Run JDK 21 tests/build, Python tests, shader/SPIR-V validation, pinned source/profile audits and graphics fixtures on the final candidate. Investigate failures without weakening gates.
- Send a coherent change set and ship 1.0.0 only after stability gates pass. Verify the downloaded GitHub Release JAR checksum and metadata.
- Keep WORK_SUMMARY.md current. Missing evidence must not be replaced by a version bump or a broad compatibility claim.

See STABILITY_1_0.md for gaps and the execution backlog.
