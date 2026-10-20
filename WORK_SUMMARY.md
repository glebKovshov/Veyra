# Veyra — передача контекста разработки

Проверено: **2026-10-20**. Репозиторий: `https://github.com/glebKovshov/Veyra`. Это рабочая сводка для следующего агента, а не заявление о релизной готовности.

Приоритет источников фактов: текущий исходный код и Git → отчёты/ссылки GitHub Actions → документы репозитория → история переписки. Исторические сведения, не подтверждённые текущим кодом, отмечены отдельно.

## Текущее состояние: beta.10 prerelease опубликован; цель stable release не завершена — 2026-10-20

- Последний опубликованный prerelease — [beta.10](https://github.com/glebKovshov/Veyra/releases/tag/v0.2.0-beta.10%2Bmc1.21.11), tag `v0.2.0-beta.10+mc1.21.11`. Release workflow [39349523440](https://github.com/glebKovshov/Veyra/actions/runs/39349523440) прошёл pinned source audit, все 15 Lavapipe fixtures, сборку/тесты, JAR verifier и публикацию. Скачанный JAR: 571,261 bytes, SHA-256 `ac04ffcce1d49a53fdd2cc66965be0db5d470f8399d62ec8290fda9e04148b7d`; checksum совпал с `SHA256SUMS.txt`, JAR прошёл verifier.
- PR #17 (`feature/terrain-mid-texcoord`) слит в `main` merge-коммитом `24a2a9ae19d2cbd46d2112f9d5a334d1e1c04093`; последний feature head `91ebcf7` прошёл [Build 39348939420](https://github.com/glebKovshov/Veyra/actions/runs/39348939420), [source audit 39348939418](https://github.com/glebKovshov/Veyra/actions/runs/39348939418) и [все 15 Graphics smoke fixtures 39348939401](https://github.com/glebKovshov/Veyra/actions/runs/39348939401). Предыдущие готовые feature ветки уже были интегрированы; неслитых готовых веток не осталось.
- Добавлен terrain vertex format с per-quad midpoint UV, bridge для `signMidCoordPos`, terrain aliases `vertexPos`/`playerPos`/`glColorRaw` и отдельная Lavapipe fixture. Полная локальная команда на JDK 21.0.12.1 прошла уже при beta.10: **199 тестов, 0 failures/errors/skips; 43 example stages, 40 direct Vulkan SPIR-V; build и example-pack package успешны**. Remapped JAR 571,279 bytes, SHA-256 `0cc561a569e0e2ce8d20955ec6c271b41a5e616d5adac2b006e660edcde08a8c`, прошёл `scripts/verify_jar.py`.
- Новый `geometry-mid-texcoord` fixture подтвердил обе области midpoint-сигнала: 53,648 активных magenta pixels и 168,711 активных opposite-midpoint pixels; при отключённом shader pass — 0, runtime Vulkan validation errors — 0. При shutdown fixture записал 10,031 Vulkan diagnostics; чистый teardown не заявляется. Source audit: 75 cases / 5,178 sources, 126 geometry fragments accepted / 591 rejected; Complementary terrain проходит адаптацию, water остаётся заблокирована на `viewVector`. Во всех audit cases `visualValidation=false`.
- `GOAL.md` остаётся шире автоматических fixture: все популярные third-party packs и физический GPU не прошли визуальную проверку, полная Iris parity отсутствует, а shutdown diagnostics VulkanMod требуют отдельного анализа. Beta.10 — экспериментальный prerelease, не stable.
- Пользовательские скриншоты, `assets/`, `logs/`, текстовые GPU дампы, `GOAL.md`, `scripts/analyze_vulkan_shutdown.py` и stash `WIP cloud colortex0 scene snapshot` сохранены; не добавлять их в staging.

## Beta.10 published prerelease — per-quad terrain midpoint — 2026-10-20

- После исправления format initialization повторена локальная команда `test validateShaders build packageExamplePacks --rerun-tasks`: 199/199 тестов, 43 example stages и 40 прямых Vulkan SPIR-V компиляций. Beta.10 опубликована после успешных CI gates на PR и tag.

- Проверен VulkanMod 0.6.8 bytecode: compressed terrain vertex имеет 16 bytes (position/light 8, UV 4, color 4), TerrainBuilder берёт stride из `PipelineManager.terrainVertexFormat`, а Vulkan input locations следуют порядку атрибутов. Расширенный формат занимает 20 bytes, сохраняя все прежние offsets и добавляя midpoint на offset 16. `TerrainBufferBuilder` пишет подряд четыре вершины quads, поэтому mixin записывает среднее их UV в четыре appended поля; shader вычисляет Iris `signMidCoordPos`.
- `glColorRaw` использует unpacked terrain `Color` до lightmap multiplication; `vertexPos` и `playerPos` используют существующую camera-relative terrain position. `viewVector` не подменяется: его pack-источник требует tangent basis, которого VulkanMod terrain input не предоставляет.
- Добавлены mixins для shared compressed format initialization и terrain buffer/builder, регрессии адаптера и original fixture `geometry-mid-texcoord`, проверяющий две противоположные sign-регионы. Фикстура добавлена в PR-triggered 15-job Lavapipe matrix; итоговый JSON сохраняет второй pixel count. В локальной папке `run-graphics-smoke` подготовительные scripts не запускались.
- На JDK 21.0.12.1 локально прошли `test validateShaders build packageExamplePacks --rerun-tasks`: 199 tests, 0 failures/errors/skips; 43 shader stages, 40 direct Vulkan SPIR-V. Пересобранный beta.10 JAR (571,279 bytes; SHA-256 `0cc561a569e0e2ce8d20955ec6c271b41a5e616d5adac2b006e660edcde08a8c`) прошёл `scripts/verify_jar.py`. Versioned compatibility/source-audit reports обновлены. PR #17 привязан к T3 thread. Build run 39347890325 и source audit run 39347890443 прошли. Первый graphics run 39347890387 упал во всех 15 job до рендера: `PipelineManagerMixin` не нашёл call-site target для обычного terrain pipeline. Заменил этот call-site injection на замену `CustomVertexFormat.COMPRESSED_TERRAIN` после статической инициализации. После повторного build локальная сборка и JAR verifier прошли; новая Lavapipe matrix ещё ожидает запуска.

## Продолжение после beta.8: terrain `viewPos` — кандидат beta.9, 2026-10-20

- Проверил фактический Git: `main`/`origin/main` указывали на `3735649`; все локальные и удалённые feature/docs/fix ветки уже являлись предками `main`. Новых готовых веток для слияния не было. Создана отдельная `feature/terrain-view-position`; исходные 121 user-owned untracked paths и stash `WIP cloud colortex0 scene snapshot` оставлены нетронутыми.
- Реализован terrain-family varying Iris `viewPos` без изменения общего VulkanMod vertex format: Veyra передаёт camera-relative `pos` через текущую `gbufferModelView` в `vec3` на location 10. Terrain binding-1 vertex UBO теперь объявляет Iris/custom fields в том же порядке, что `PipelineConfig`; матрица уже зарегистрирована и обновляется Veyra каждый кадр. Фиктивный `mc_midTexCoord` alias не добавлялся.
- Добавлен terrain пример в `examples/geometry-view-space`, regression в `ShaderAdapterTest` и shaderc SPIR-V compilation для адаптированного terrain VSH. Полная команда JDK 21 `test validateShaders build packageExamplePacks --rerun-tasks` прошла: **194 tests, 0 failures/errors/skips; 42 example shaders processed / 39 direct SPIR-V; build и example ZIP успешны**. Candidate JAR beta.9 (565,229 bytes, SHA-256 `d8812d4c0388f59e56a8f67a34f04ccd5e8e5e4a54bd44eee0be17ed85f11309`) прошёл `scripts/verify_jar.py`.
- Повторный read-only audit четырёх pinned pack archives завершился для 12 default и 63 profile/dimension cases, 5,178 sources: **90 accepted / 627 rejected** против beta.8 72/645. BSL terrain проходит в Overworld/Nether/End и declared profiles; BSL water остаётся на `mat`. Все 75 `visualValidation=false`. SHA-512 inputs совпадают с pinned metadata; оба tracked audit JSON обновлены и совпадают со свежими records по pack/dimension/profile и полному содержимому.
- Обновлены `COMPATIBILITY.md`, `README.md`, `CHANGELOG.md`, `RELEASE_NOTES.md`, `gradle.properties` (`0.2.0-beta.9+mc1.21.11`). PR #16 открыт, привязан к этому T3 thread. На code commit `4c19643` Build `39341485722`, source audit `39341485708` и 13/13 Lavapipe fixtures `39341485927` прошли. Скачанный source artifact совпал по всем 75 records и pinned SHA-512 с локальным audit: 90/627, всё `visualValidation=false`. В `geometry-mrt` runtime validation errors=0, но teardown сохранил 10,351 diagnostics (VUID `vkDestroyDevice-device-05137`); GPU teardown по-прежнему не объявляется чистым. После этого добавлен отдельный `geometry-view-space` Lavapipe readback, который проверяет новый terrain signal активным и отключённым; его обновлённая CI matrix ещё ожидается. Физический GPU и реальные pack visuals не проверены; beta.9 не stable и не завершает `GOAL.md`.

## Продолжение после beta.7: terrain varying aliases — 2026-10-20

- В ветке `feature/iris-terrain-lmcoord` от `main` `a9b11f9` добавлены aliases: Iris `lmCoord` → VulkanMod lightmap input (`veyraLightmapCoord`, location 5) и BSL `color` → terrain vertex color (`veyraVertexColor`, location 0). Существующие имена сохранены. Каждый alias покрыт regression-тестом, проверяющим объявление input и замену varying.
- Добавлен terrain пример в `examples/geometry-lightmap`, использующий оба alias и lightmap sampler; он проходит adapter/shaderc проверку и включается в ZIP примеров. На JDK 21.0.12.1 прошёл полный `test validateShaders build packageExamplePacks`: **190 тестов, 0 failures/errors/skips**, 41 пример обработан, 38 скомпилировано прямо в Vulkan SPIR-V; distributable verifier прошёл.
- На окончательном PR head `cc56216` все CI прошли: Build `39334381472`, source audit `39334381384`, Graphics smoke `39334381363` (13/13 Lavapipe fixtures). Аудит повторно обработал 75 cases / 5,178 stages: 72 accepted / 645 rejected; `visualValidation=false` во всех записях. Общий accepted count не изменился: Complementary terrain/water теперь останавливаются на `signMidCoordPos` (`mc_midTexCoord`), BSL terrain проходит `color`, но останавливается на одном из пока сгруппированных entity-metadata / midpoint-UV / view-space / tangent inputs; BSL water — на `mat`.
- `signMidCoordPos` требует `mc_midTexCoord`, которого нет во входе VulkanMod terrain; корректное получение этих midpoint-координат требует отдельного изменения vertex-data path. Следующий шаг — сделать диагностику terrain adapter точнее и оценить наличие необходимых данных. Физический GPU и визуальная совместимость реальных паков не проверены; `GOAL.md` остаётся незавершённым.

## Продолжение — terrain input diagnostics (2026-10-20)

- Проверил локальный `main` и refs: на момент начала PR #12 `HEAD` совпадал с `origin/main` на merge PR #11 (`6c04570`). Все удалённые рабочие ветки — предки `main` и уникальных коммитов не содержат; незавершённый stash и 121 пользовательский untracked path сохранены. Изменения выполнены в `fix/terrain-interface-diagnostics`, смержены PR #12.
- Инспекция VulkanMod 0.6.8 jar показала, что `TerrainBufferBuilder.vertex(x,y,z,color,u,v,light1,light2)` и его `VertexBuilder` принимают позицию, цвет, UV0 и два lightmap значения; midpoint-UV атрибута нет. Правильный `mc_midTexCoord` потребует расширения terrain vertex write/format path и quad midpoint calculation, поэтому фиктивный alias не добавлялся.
- Terrain adapter теперь сообщает точный первый неподдерживаемый input вместо общей группы; добавлены регрессии для `mc_Entity`, `mc_midTexCoord`, `midTexCoord`, `viewPos`, `viewNormal`, `tangent`, `at_tangent`. Дополнительный тест защищает существующий rewrite legacy `gl_FragData`.
- Целевой `ShaderAdapterTest` и полная команда `test validateShaders build packageExamplePacks --rerun-tasks` прошли на JDK 21.0.12.1: **192 теста**, 41 shader example обработан / 38 собраны прямо в Vulkan SPIR-V; example packs собраны. `scripts/verify_jar.py` прошёл для beta.8 candidate (565,160 bytes, SHA-256 `26dc7ca90bc5f2db5190379747b5c3c7c27264d7caf9700da04111dfaaf041e2`). Default `verify_jar.py` не использовался без пути, так как `build/libs` содержит оставшиеся локальные beta.5/6/7 artifacts; кандидат передан явно, старые артефакты не удалялись.
- Свежий pinned source audit четырёх паков прошёл: 12 default + 63 profile/dimension cases, 5,178 stage sources, 72 geometry fragments приняты / 645 отклонены, все `visualValidation=false`. Default Overworld Complementary terrain/water называют `signMidCoordPos`, BSL terrain — `viewPos`, BSL water — `mat`. JSON-отчёты в `validation/` обновлены. GitHub CI, merge PR #12 и публикация beta.8 завершены.

## Историческое состояние после beta.5 — 2026-10-19 (вечер)

- Опубликованный последний prerelease — **beta.5** (`main` = `b19f96e`); история beta.2–beta.5 ниже в разделах «Beta.2 опубликована» … «Beta.5 cloud vertex bridge». Эти выпуски сделала параллельная T3-сессия `[GPT] Veyra`; она прервана пользователем в 22:11, работу продолжает эта сессия.
- **Незавершённая работа той сессии сохранена:** облачный `colortex0` через snapshot сцены (адаптер облаков готов, в рендерере начат). Она в `git stash` («WIP cloud colortex0 scene snapshot …», сделан с ветки `feature/cloud-scene-color-input`), копии patch и нового `VeyraColorTextureSnapshot.java` лежат в `%TEMP%\veyra-wip-backup`. Не удалять до переноса в ветку.
- Ветка `feature/iris-velocity-smooth` (от `main`): встроенный Iris `velocity` (с приоритетом выражения пака), общий механизм `CustomUniforms.PACK_OVERRIDABLE`; исправлен дефект, из-за которого после пака с собственным `shadowFade` geometry-программы других паков получали 0. `smooth()` переведён на семантику Iris `SmoothFloat` (half-life в десятых секунды), `fmod` — floored, добавлены `edge`, `log(base,x)`, `log10`, `exp10`, `random`, `randomInt`.
- Неопределённые `BIOME_*` (Complementary `BIOME_SULFUR_CAVES`, биома нет в 1.21.11) ведут себя как в Iris: uniform не разрешается, равен 0, пишется предупреждение; Iris-входы, которых нет в Veyra, по-прежнему отклоняют программу. Аудит засевает ванильные id биомов из класса `Biomes` (65 id), поэтому audit-only отказов по `BIOME_*` больше нет. Диагностика terrain называет конкретное неподдерживаемое объявление.
- Локально JDK 21: `clean test validateShaders build packageExamplePacks` — **172 теста, 0 failures/errors/skips**, verify_jar прошёл. Полный pinned audit: 75 случаев, 18 accepted / 699 rejected; `validation/*.json` перегенерированы (прежние файлы устарели ещё до beta.4). Фикстура `custom-uniforms` проверяет `velocity` в terrain и выражение на его основе в post; GPU CI для этой ветки ещё не прогонялся.
- Следующие реальные блокеры (default Overworld): Complementary terrain/water — Iris `heavyFog`; entity/block/hand — `currentRenderedItemId`; clouds — `colortex0` (stash). BSL — vertex varyings `color`/`mat`, `entityColor`, `isMainHand`, clouds `texture`. Sildur — `colortex4` feedback. Сравнение с исходниками Iris (bff1e69) показало ~60 нереализованных Iris uniforms (`heavyFog`, `hasCeiling`, `sunPosition`/`moonPosition`/`shadowLightPosition`/`upPosition`, здоровье/голод/воздух игрока, `temperature`/`rainfall`, hardcoded `timeAngle`/`timeBrightness` и др.) — план: единый реестр Iris uniforms вместо разрозненных списков.

## Текущая работа: выпуск 0.2.0-beta.1 — 2026-10-19

- Teardown audit завершён: registry snapshot совпадает с vanilla baseline (46/667/97), Veyra-named объектов нет; ~10031 diagnostics относятся к VulkanMod и присутствуют без shaderpack.
- Версия поднята до `0.2.0-beta.1+mc1.21.11` (prerelease, не stable/не Iris parity). Локально JDK 21: `clean test validateShaders build packageExamplePacks` success, **139 tests / 0 failures/errors/skips**, verify_jar прошёл.
- **Опубликовано.** PR #2 смержен в main (merge commit `c4dcde4`). Lightweight tag `v0.2.0-beta.1+mc1.21.11` → `c4dcde4` (первый annotated tag провалил SHA-check publish step в run 39253625564; заменён). Release run https://github.com/glebKovshov/Veyra/actions/runs/39254383192 — success (source audit, 11 Lavapipe fixtures, build/tests, verify_jar, publish).
- Release (prerelease): https://github.com/glebKovshov/Veyra/releases/tag/v0.2.0-beta.1%2Bmc1.21.11 ; JAR скачан заново, SHA-256 совпадает с SHA256SUMS.txt.
- RELEASE_NOTES.md, README, CHANGELOG обновлены. Ранее запланировано: push → PR #2 ready+merge в main → tag `v0.2.0-beta.1+mc1.21.11` → Release workflow публикует JAR.
- Следующие backend задачи после beta.1: per-draw entityId/blockEntityId, shadowFade, произвольные geometry vertex stages, non-terrain MRT, проверка на физическом GPU и реальных паках.

## Текущая работа: teardown ownership audit — 2026-10-19

- Добавлен original vanilla-baseline без выбранного shaderpack, с readback после 240 world ticks. Он требует отсутствие selected pack/rendering и отсутствие shader signal; predicates остальных 10 fixtures не изменены.
- Test-only shutdown mixins снимают native registry после vkDeviceWaitIdle и перед allocator destruction: buffer/image handles и names, pipeline layouts/variants, descriptor pools/sets, pending frame operations/frees. Workflow требует оба JSON, чтобы диагностика не могла молча пропасть. Diagnostic classes исключены из release JAR.
- В pinned VulkanMod `VkDebugLabel` и `Vulkan.setDebugLabel` — no-op; CLI renderDebugLabels распознаётся Minecraft, но здесь не даёт объектных имён. Поэтому выбран explicit JSON handle→owner trace.
- Локально `test build` success с compileGraphicsSmokeJava. Новый GPU baseline/registry trace ещё не проверен. Сначала сопоставить оставшиеся VUID с snapshots, затем исправлять доказанные cleanup defects; не приписывать все objects Veyra или backend по одному count.

## Предыдущий подтверждённый срез: post shadow inputs + borrowed depth ID fix — 2026-10-19

- Runtime HEAD **317fb00b3a7387134826317320b4b9947fddf887**. Build push/PR **39249394264 / 39249400344** success. Скачанные CI XML artifact **12184757885** подтверждают **139 tests, 0 failures/errors/skips**, shaderc **38 examples preprocess / 35 direct SPIR-V**, plus post comparison fixture. Скачанный mod JAR artifact **12185470779** прошёл verifier.
- Graphics push/PR **39249394235 / 39249400249** success, все **10 fixtures**. Все exact-commit artifacts скачаны. MRT **222757/0**, water-depth **222757/0**, water-color **222757/0**, specular-atlas **222577/0**, specular-entity **18410/0**, shadow-color-terrain **222496/0**, shadow-color-entity **18293/0**, separate-depth water **226902/0**, entity **16358/0**, post-shadow-chain **230400/0**. Runtime validation errors **0 у всех**. Новые active/disabled кадры просмотрены.
- Post prepare/deferred/composite/final читают реальные depth0/depth1/color0 views, aliases цвета дедуплицированы, фильтрация depth nearest/color linear, budget 12 descriptors. Четыре фазы передают sentinel через colortex4/5/6 и проверяют distinct depths/authored color. Modern float/legacy vec4 helpers, unused-noise header и по 60000 blank lines включены в GPU fixture. Post/vertex regex starts bounded horizontal whitespace.
- Реальная старая ошибка **VeyraDepthTextureView** устранена: VkGpuTexture constructor принимает usage первым и alias ID после mip levels; они были перепутаны. Обычный encoder binding оставлял Light Texture в slot 1, manual geometry/depth hooks маскировали дефект. После fix descriptor trace во всех четырёх фазах указывает **Veyra shadowtex1 snapshot**. Alias close теперь использует правильный numeric ID, не source usage. Predicate/threshold не ослаблены.
- Source CI **39249400296** success, artifact **12185495677**: все **75 records совпали с local и committed JSON**, 5178 sources, 18 accepted / 699 rejected, visualValidation=false. Это preprocessing + geometry adapter audit, не full post pipeline validation. Complementary блокирует blockEntityId; BSL entityId/blockEntityId/shadowFade; Sildur feedback/other geometry inputs.
- Teardown НЕ чистый: в порядке десяти fixtures выше diagnostics **10031 / 10049 / 10050 / 10031 / 10035 / 10032 / 10356 / 10370 / 10053 / 10034**. Counts варьируются; constructor fix не объявлять устранением всех leaks.
- **Следующий приоритет — ownership/lifecycle audit.** В mapped MC Main подтверждён CLI flag `--renderDebugLabels`. Pinned VulkanMod shutdown: Vulkan.cleanUp waits device idle → Renderer.cleanUpResources → PipelineManager.destroyPipelines (только 4 built-ins) → MemoryManager.freeAllBuffers → allocator/device destruction. freeAllBuffers делает freeBuffers/freeImages/doFrameOps однократно по frame slots; nested retirement может оставлять работу. Это кандидаты, не доказанные причины всех VUID. Нужны baseline без выбранного pack, registry/resource traces перед allocator destruction, затем исправления по доказанному ownership. Доступны локальные decompiled sources в `%TEMP%/veyra-vulkanmod-decompiled` и existing NativePipelineRegistryAccessor; не ждать native UI для software Vulkan диагностики.
- Затем реальные per-draw IDs/shadowFade, wider vertex/MRT paths и real-pack validation. Native UI/physical GPU не подтверждены. `git fetch origin` подтвердил main **e37f188/0.1.1**; PR #2 API подтвердил open/draft. Published alpha.1 старый tag 2b5acb0, этих fixes в нём нет. Stable merge/new Release ещё не выполнены; цель активна.

## История реализации post shadow inputs и диагностики borrowed views — 2026-10-19

- Post parser/planning расширены с shadowtex0 на отдельный shadowtex1 и shadowcolor0/legacy shadowcolor. Набор inputs хранится в metadata program/pass, aliases цвета дедуплицируются. Frame graph получает существующие producer views, без новых GPU images; color linear/clamp, depth nearest/clamp.
- Новый pure PostShadowSamplers проверяет shadow pass availability, sampler types, дубли, unsupported shadow names и общий native budget (12 descriptors). Comparison uniforms/helper params сохраняют float-результат modern texture; legacy shadow2D остаётся vec4. Добавлены JUnit и shaderc fixtures.
- Начальные multiline scans в post/vertex parser переведены с `^\s*` на horizontal whitespace. Original water-post-shadow-inputs fixture проверяет prepare → deferred → composite → final: каждая фаза читает обе depth-карты/оба color alias и передаёт sentinel через colortex4/5/6. Modern/legacy comparison helpers, unused noisetex header и по 60000 expanded blank lines включены в runtime fixture. Matrix теперь 10 cases.
- Финально локально `test validateShaders build` success: **139 tests, 0 failures/errors/skips**, shaderc **38 examples preprocess / 35 direct SPIR-V**, plus post comparison helper fixture; mod JAR verifier прошёл. Unused-noise detection согласован с удалением unused declarations. GPU/source CI ещё не проверены; не объявлять post runtime поддержку подтверждённой до их завершения.
- Предыдущий подтверждённый checkpoint ниже сохранён. Stable integration/release пока не готовы; next steps остаются реальные per-draw IDs/shadowFade и lifecycle/real-pack validation.
- Первый post graphics artifact **12184692102**, run **39248744648**, HEAD **ff51398**: failed, 0 active/0 disabled pixels, runtime VUID errors=0. Descriptor trace во всех четырёх фазах показывает **VeyraShadowOpaqueDepthSampler → Light Texture**, а не snapshot. По pinned VulkanMod constructor подтверждена реальная старая ошибка: DepthSampleTexture передавал texture ID как usage, а source usage как native ID. Geometry/manual depth hooks маскировали её; обычный post setupUniforms оставлял stale slot. Исправлен порядок аргументов; повторный GPU run обязателен. Это также исправляет ownership alias при close, который ранее мог использовать чужой numeric texture ID.

## Предыдущий подтверждённый срез: separate shadowtex1 — 2026-10-19

- Runtime HEAD **cca001973c906da89f461c30998e121a88e27405**. Build push/PR **39246874678 / 39246882115** success. Скачанные CI XML artifact **12184271081** подтверждают **131 tests, 0 failures/errors/skips**; shaderc **36 examples preprocess / 33 direct SPIR-V**, plus separate shadowtex1 comparison fixture. Скачанный mod JAR artifact **12184111327** прошёл verifier.
- Graphics push/PR **39246874812 / 39246882139** success, все **9 fixtures**. Все exact-commit artifacts скачаны: MRT **221976/0**, water-depth **222757/0**, water-color **222757/0**, specular-atlas **222590/0**, specular-entity **18293/0**, shadow-color-terrain **222445/0**, shadow-color-entity **18410/0**, separate-shadow-depth water **226902/0**, entity **16456/0**. Runtime validation errors **0 у всех**. Обе новые active screenshots просмотрены.
- Shadowtex1 — отдельный graphics-queue depth copy после opaque terrain + entity/block features, до translucent terrain. GPU predicate требует **shadowtex0=0.25 и shadowtex1=0.75** в одной координате; alias/late snapshot/fallback не проходит. Fixtures явно включают shadowTranslucent. Entity producer final-layout defect исправлен, main MVP восстанавливается в finally. Input failure отключает и main, и последующую shadow работу.
- Snapshot images/views и cached native variants retiring shadow passes освобождаются через completed-frame retirement. Это НЕ доказывает чистоту всего teardown: в порядке девяти fixtures выше diagnostics **10031 / 10050 / 10370 / 10031 / 10035 / 10032 / 10676 / 10049 / 10053**. Counts варьируются, ownership/stress ещё не закрыты.
- Source push/PR **39246874561 / 39246882138** success. Скачанный artifact **12182953782** совпал со всеми **75 local и committed JSON records**: 5178 sources, 18 accepted / 699 rejected, visualValidation=false у всех. Complementary теперь блокирует **blockEntityId**; BSL **entityId/blockEntityId/shadowFade**; Sildur прежние color feedback/other geometry inputs.
- Следующий backend шаг: подключить shadowtex1/shadow color к **post programs** и реализовать реальные per-draw IDs / shadowFade по Iris semantics, без фиктивных zero uniforms. Затем расширять нужные MRT/vertex paths и проводить real-pack/lifecycle validation. Context-dependent legacy shadow alias, wider sampler properties, normal/animated PBR maps ещё неполные.
- `main` остаётся e37f188/0.1.1, PR #2 draft; alpha.1 tag остаётся старым 2b5acb0. Эти fixes пока development artifacts. Stable merge/new Release не выполнены: critical cleanup/real-pack validation и backend gaps остаются. Physical GPU/native UI недоступны ранее; не объявлять проверенными. Цель активна.

## История реализации separate shadow depth и исправления fixtures — 2026-10-19

- Shadow draw order исправлен на opaque terrain → entity/block features → отдельный graphics-queue depth copy → translucent terrain с LOAD/STORE resume pass. Добавлен настоящий snapshot shadowtex1; native usage/format transfer support проверяются, layout barriers выполняются вне render pass. Ошибка shadow rendering теперь отключает shader rendering с диагностикой вместо продолжения с неполными данными.
- Terrain/entity adapters принимают shadowtex1 sampler2D/sampler2DShadow, включая comparison helper, когда shadowtex0 не читается. Shadow feedback отклоняется. Terrain резервирует slot 10; auxiliary water allocation пропускает занятые slots 10/11, бюджет учитывает shadow color.
- Snapshot views/images используют существующее fence retirement. При release дополнительно удаляются cached VkPipeline variants для retiring shadow render passes через completed-frame queue. Это пока не доказательство чистого всего Vulkan teardown.
- Локально `test validateShaders build` success: **131 tests, 0 failures/errors/skips**, 36 examples preprocess / 33 direct SPIR-V. Matrix расширена до 9 fixtures: opaque shader пишет 0.75, shadow_water 0.25; основной water/entity shader требует одновременно 0.75 в shadowtex1 и 0.25 в shadowtex0. Alias/snapshot после water/default image не проходит.
- Новый полный source audit и GPU CI ещё не проверены. Предыдущий подтверждённый checkpoint ниже сохранён. Post-program shadowtex1 ещё не подключён; полный real-pack pipeline не подтверждён, stable merge/release не готовы.
- Первый graphics run `39217197994` (`69d2047`) завершился failure. Скачаны artifacts 12170007737/12170340994: новые fixtures дают 0 signal, runtime VUID errors 1174/326. Найдены две причины: fixtures не включали `shadowTranslucent=true`; entity producer pass при выключенной translucency оставлял **shadowtex0** (handle 5861..., не snapshot) в DEPTH_STENCIL_ATTACHMENT layout. Добавлены fixture properties и завершающие graphics-queue read-only transitions обоих shadow attachments. Требуется повторный CI.
- После прерывания старый local audit handle отсутствует, Java процессов нет, report остаётся от 10:28; это не свежий depth audit. Новый локальный audit следует выполнить один раз, не считать старый JSON подтверждением новых adapters.
- При переносе translucent terrain после entity features добавлено обязательное восстановление main MVP в outer finally: до изменения порядка это делал entity phase, теперь он больше не последний. Новый local audit запущен после подтверждённого завершения старого процесса.
- Retry `37e3903`, graphics run **39246239356** success, все 9 cases. Скачанные depth artifacts: terrain **226902/0**, entity **16256/0**, runtime errors=0, teardown **10034/10053**; оба active кадра просмотрены. Это доказывает отдельные 0.75/0.25 карты после исправления producer layouts.
- Текущий runtime HEAD **cca0019** дополнительно восстанавливает main MVP и прекращает shadow work после input failure (selected pack при ошибке сохраняется, так что нужен renderingEnabled guard). Build push **39246874678** success; скачанные XML artifact **12184271081**: **131 tests, 0 failures/errors/skips**, mod JAR artifact **12184111327** прошёл verifier. Latest graphics **39246874812** и source **39246874561** ещё выполняются.
- Новый local audit завершён: 75 configurations / 5178 sources / 18 accepted / 699 rejected. Оба validation JSON обновлены. Complementary теперь доходит до blockEntityId, BSL до entityId/blockEntityId/shadowFade; Sildur прежний feedback/other bridges. CI сравнение нового HEAD ещё предстоит.

## Предыдущий подтверждённый срез: shadow color inputs — 2026-10-19

- Добавлено чтение существующего completed shadow color attachment через `shadowcolor0` и legacy `shadowcolor` в terrain/entity bridges. Linear/clamp соответствует default Iris color sampler; shadowtex1, дополнительные shadow colors, format/mipmap properties пока не реализованы.
- Shadow-pass feedback отклоняется адаптерами. Terrain использует slot 11; вместе с этим sampler доступны максимум 5 auxiliary water images, шестой явно отклоняется до native binding. Entity использует фактический descriptor index. Color view заимствован у существующего shadow target; отдельное изображение не создаётся, cleanup очищает только owned binding identity.
- Runtime HEAD **ad69519ba00bc473c8724818a7dfad4f622edb69**. Build push/PR **39215900899 / 39215906457** success. Скачанные CI XML: **122 tests, 0 failures/errors/skips**; `test validateShaders build` локально success, shaderc **31 examples / 28 direct SPIR-V**. Скачанный CI JAR artifact 12169124438 повторно прошёл verifier.
- Graphics push/PR **39215900818 / 39215906459** success, все **7 fixtures**. Скачаны все exact-commit evidence. MRT **223272/0**, water-depth **222757/0**, water-color **222757/0**, specular-atlas **222561/0**, specular-entity **18410/0**, shadow-color-terrain **222509/0**, shadow-color-entity **18532/0**. Runtime validation errors **0 у всех**. Обе новые active screenshots просмотрены; white/zero fallback не проходит predicate.
- Полный source push/PR **39215900786 / 39215906467** success. Artifact **12169363995**: все **75 records совпали с локальным и обоими committed reports**, 5178 sources, 18 accepted / 699 rejected, visualValidation=false у всех. Complementary теперь блокирует shadowtex1 (и blockEntityId в других вариантах), а не shadowcolor0. BSL/Sildur blockers остаются.
- Teardown всё ещё НЕ чистый. В порядке семи fixtures выше diagnostics **10031 / 10049 / 10049 / 10671 / 10035 / 10672 / 10036**. Значения варьируются; ownership не установлен. Не приписывать все объекты Veyra/VulkanMod без доказательств.
- Следующий конкретный шаг: **настоящий pre-translucent shadowtex1**. Проверенный Iris `ShadowRenderer` (0a1fcaae) делает opaque terrain → entities/block entities → depth copy → translucent terrain. В Veyra translucent terrain сейчас раньше entities; исправить порядок, добавить отдельный snapshot с fence retirement и GPU fixture, доказывающий отличие от all-geometry shadowtex0. Не создавать alias обеих карт.
- `main` остаётся e37f188/0.1.1, PR #2 draft, alpha.1 tag остаётся старым 2b5acb0. Эти новые water/PBR/shadow fixes пока development artifacts. Stable merge/release не готовы; physical GPU/UI, real-pack visuals и длительный lifecycle stress не подтверждены. Цель активна.

## Предыдущий подтверждённый срез: static specular materials — 2026-10-19

- Runtime/test HEAD `3c1d749`: Build push/PR `39214797686` / `39214805186` success; скачанные XML подтверждают **115 tests, 0 failures/errors/skips**, 28 example preprocesses / 25 direct SPIR-V compilations. Скачанный mod JAR artifact 12168259361 прошёл verifier.
- Graphics push/PR `39214797662` / `39214805071` success, все **5 fixtures**. Скачанные exact-commit evidence: MRT **223595/0**, water-depth **222757/0**, water-color **222757/0**, specular-atlas **222914/0**, specular-entity **18532/0** active/disabled magenta pixels. Runtime validation errors **0 у всех**. Entity active screenshot просмотрен; проверка требует одновременно реального material и отличающегося albedo.
- Реальные static resource-pack `_s.png` работают в terrain atlas и entity bridges; atlas mip level 2 / минимум 3 image levels проверены GPU. CPU regressions проверяют straight alpha/data, padding/rescaling, LabPBR category-aware mipmaps и descriptor layout. Shadow binding реализован, отдельного specular-shadow GPU fixture пока нет.
- Первый entity failure оказался ошибкой fixed test subject при randomized player spawn. Положение исправлено относительно фактической camera; threshold не снижен. Upload использует native image queue без encoder slot-0 mutation. Cache bounded 512 MiB/256 base textures; invalidate использует frame retirement.
- Source run **39214805068** для `3c1d749` success. Скачанный artifact **12168784638** полностью совпадает со всеми **75 локальными и committed JSON records**: 5178 sources, 18 accepted / 699 rejected, visualValidation=false у всех. Complementary блокируют shadowcolor0/blockEntityId; BSL shadowtex1/shadowFade; Sildur feedback/other geometry colortex4.
- Teardown остаётся НЕ чистым: для пяти fixtures выше **10031 / 10370 / 10049 / 10031 / 10355** diagnostics. Ownership/stress не закрыты. Normal maps, animated materials, physical GPU/UI и popular-pack visual support не подтверждены. Stable merge/release пока не готов.
- `main` остаётся e37f188/0.1.1; PR #2 draft. Опубликованный alpha.1 tag остаётся 2b5acb0, текущих water/PBR fixes в нём нет. Следующий backend приоритет: отдельная opaque shadowtex1/shadowcolor inputs, pack-specific uniforms, затем lifecycle/real-pack validation.

## История реализации specular и диагностики fixtures — 2026-10-19

- Commit `c4e7406` добавляет настоящие static `_s.png` из активных resource packs: standalone entity textures и atlas с живыми sprite rectangles/padding. Отсутствующая карта получает zero default; повреждённая/animated карта выдаёт явную диагностику. Normal maps и animation ещё не реализованы.
- Terrain/shadow/entity bridges получают отдельный specular descriptor; entity использует фактический native image index. Cache ограничен 512 MiB/256 base textures, reload/disable/shutdown используют frame retirement.
- UNORM data сохраняют четыре канала, включая RGB при alpha=0. Mipmap chain совпадает с albedo, поддержаны linear и category-aware LabPBR specular mipmaps. Добавлены CPU regression tests.
- Локально `test validateShaders build` прошли: **115 tests, 0 failures/errors**, 28 example preprocesses / 25 direct SPIR-V compilations; JAR verifier прошёл. Это ещё не доказательство runtime корректности.
- Graphics matrix расширена двумя original fixtures: atlas проверяет настоящий material и mip level 2, entity проверяет material текстуры cow. Порог 2000 magenta pixels и disabled baseline сохранены. GPU CI и свежий полный source audit ещё выполняются; не считать их зелёными до просмотра результатов.
- PR #2 остаётся draft, stable merge/release не готовы. Существующий опубликованный alpha.1 не содержит этих изменений.
- Первый CI run `39213650838` (`c4e7406`): скачаны обе новые graphics evidence. Atlas 223051 active / 0 disabled, entity 4688 / 0; оба result.json passed. Полную matrix/validation ещё нужно сверить. После этого upload переведён с encoder на direct image upload queue, чтобы не менять глобальный slot 0; entity fixture дополнительно проверяет отличающуюся albedo. Этот последующий fix требует отдельного CI.
- Полный локальный audit завершён: 75 configurations, 5178 sources, 18 accepted / 699 rejected; Complementary теперь блокируют shadowcolor0 и blockEntityId, а не specular. Оба committed JSON обновлены; exact-commit CI сравнение ещё предстоит.
- **Новый runtime blocker:** усиленный entity fixture в `a8f9c2a` даёт 0 active pixels (artifact 12169066320, run 39214054781), runtime validation errors=0. Одновременное чтение specular/albedo ещё не подтверждено, поэтому первый зелёный fixture недостаточен. Commit `e1e830d` добавляет descriptor trace также для entity pipelines с minecraft location и отдельные red/blue failure colors; порог/проверка не ослаблены. Требуется установить причину и исправить до integration/release.
- Скачанный source artifact 12168777659 от `c4e7406` содержит все 75 records, полностью совпадающие с локальным JSON. Это не runtime доказательство.
- Причина нулевого entity signal установлена по login positions: первый игрок `(7.5,-59,-2.5)` видел fixed cow, второй `(8.5,-59,9.5)` смотрел в другую сторону от неё. Это fixture placement bug, а не доказанная ошибка bindings. Cow теперь ставится относительно фактической позиции игрока вдоль camera yaw=45; усиленный predicate и failure colors сохранены. Нужен повторный GPU run.

## Предыдущий подтверждённый срез: water color inputs — 2026-10-19

- Runtime code HEAD `ef6fb4413d4ec8831e8b3c92fae2c9db6467bd46`. Push/PR Build success: 39210665983 / 39210672564. Скачанные CI XML подтверждают **106 tests, 0 failures/errors/skipped**; 25 example preprocesses / 22 direct SPIR-V compilations. Скачанный CI mod JAR повторно прошёл `scripts/verify_jar.py`.
- Push/PR Graphics success: 39210665863 / 39210672561. Все три fixtures проходят. MRT 222784/0 magenta, water-depth 222757/0, water-color-inputs 222757/0; runtime validation errors **0 у всех**. Exact-commit active/disabled water-color кадры скачаны и просмотрены.
- Water `colortex1..15` / legacy gdepth/gnormal/composite/gaux1..4 реализованы для максимум 6 distinct images в slots 6..11. Aliases дедуплицируются, bindings идут непрерывно после shadow/noise/depth. Read-only inputs выделяются/очищаются существующим lifecycle; reduced-size read-only target проверен. Current buffer side берётся после deferred; barriers на graphics command buffer вне render pass; dynamic viewport/scissor сохранены. Cleanup очищает только owned table entries по identity.
- Новый fixture доказывает opaque MRT -> read, deferred ping-pong -> read и initial half-resolution clear -> read. Неиспользуемый noisetex header также включён: runtime/GLSL detection согласованы. Квадратичный scan на preprocessing blank lines исправлен после jstack diagnosis; regression на 60000 строк проходит. Полный локальный audit после fix занял 2m 2s.
- Source push/PR success: 39210665829 / 39210672681. **Все 75 локальных записей совпали как JSON-данные со скачанным exact-commit CI report**: 5178 sources, 18 accepted / 699 rejected, visualValidation=false у всех. Обновлены оба committed reports. Complementary water теперь упирается в specular, BSL water в shadowtex1/shadowFade. Sildur water требует чтения colortex4, одновременно attached к draw: пока явный feedback blocker; другие geometry bridges ещё не имеют этих color inputs.
- Teardown НЕ чистый. В данном точном push run: MRT **10031**, water-depth **10049**, water-color-inputs **10370** diagnostics. Значения варьируются между runs; не приписывать всё Veyra или VulkanMod без ownership trace. Runtime-green не означает отсутствие leaks.
- Native Computer Use повторно недоступен (`native pipe ... os error 2`). Physical GPU/UI, popular-pack visual compatibility и длительный lifecycle stress не подтверждены. Backend work может продолжаться; вся цель не blocked и не complete.
- `main` остаётся e37f188/0.1.1, PR #2 draft. Alpha.1 release/tag по-прежнему на старом 2b5acb0; эти fixes доступны только в feature/CI development artifact. Стабильный релиз не объявлен готовым.
- Следующие приоритеты: PBR specular atlas/material inputs, отдельная opaque shadowtex1, pack-specific uniforms; определить корректную Iris/Vulkan семантику water same-attachment feedback и расширить color inputs на нужные entity/hand bridges. Затем real-pack end-to-end, физический GPU, ownership/stress проверки, stable integration/release.

Ниже сохранена история предыдущих этапов.
## История: water depth fix — 2026-10-19

- Последний runtime code commit: `51c5875949259444b465409828ad19cbd6557006`. Build push `39208570869` и PR `39208577331` — success. 96 JUnit tests, 0 failures/errors/skips; shaderc 23 example preprocesses / 20 direct SPIR-V compilations; JAR verifier проходит.
- Graphics push `39208570838` и PR `39208577387` — success, оба matrix cases. MRT: 222983 active / 0 disabled magenta pixels, runtime validation errors 0. Water opaque-depth: 222757 / 0, runtime validation errors 0. Скачанные кадры просмотрены; water signal занимает поверхность, а не случайные пиксели.
- `gbuffers_water` теперь читает существующий separate `depthtex1` snapshot (nearest/clamp); в opaque/shadow terrain этот sampler не разрешён. Ownership учитывает post и terrain, resize/world/reset используют прежний depth view fence retirement. Исправлены реальные runtime defects: optional descriptor gaps и main-depth borrowed view, ошибочно подменявшийся цветовым output depth-copy pass.
- Полный source audit: 75 configurations / 5178 sources / 18 accepted / 699 rejected. Все 75 локальных записей совпали как JSON-данные со скачанным CI source audit `39208129938` (`0815d6e`). Обновлены оба committed JSON reports; старые отчёты также содержали уже устранённые ранее `shadowtex0` и dimension uniform ошибки. Complementary water теперь останавливается на `gaux2` (colortex5), а не depthtex1.
- Teardown остаётся НЕ чистым: MRT 10031, water 10050 diagnostics. По сравнению artifact’ов дополнительные 19 — VkPipeline; ownership/причина ещё не доказаны. Не объявлять исправленные runtime layouts доказательством отсутствия GPU leaks.
- `main` остаётся на готовой 0.1.1 (`e37f188`). PR #2 draft. Публичный alpha.1 tag — старый `2b5acb0`, новые water fixes в него не входят. Stable 0.2 и полный Iris parity не достигнуты; цель остаётся активной.
- Следующие backend задачи: water `gaux2`/colortex5 и colortex4 с корректными read/write feedback rules; отдельный opaque shadowtex1; PBR specular и pack-specific uniforms. Далее physical GPU/UI и lifecycle stress. Не публиковать stable только на основании этих двух synthetic fixtures.

Сведения ниже до раздела «Продолжение после alpha» сохраняют историю первоначального состояния, а не заменяют этот актуальный срез.
## Цель и целевая среда

Продолжить существующий Veyra, не переписывая его с нуля. Целевая связка зафиксирована в `gradle.properties`, `fabric.mod.json` и исходниках VulkanMod, на которые рассчитаны mixin’ы:

- Minecraft **1.21.11**;
- Fabric Loader **0.18.4+**, Fabric API **0.140.0+1.21.11**;
- VulkanMod **0.6.8**;
- Java **21+**;
- версия текущей разработки: `0.2.0+mc1.21.11`.

Veyra реализует подмножество Iris/OptiFine shaderpack API поверх VulkanMod. Iris в основном управляет OpenGL pipeline; его framebuffer/program objects нельзя переносить напрямую в Vulkan. Veyra использует Minecraft RenderPipeline и внутренние точки VulkanMod, создавая Vulkan-совместимый bridge.

## Что уже реализовано

### Загрузка наборов и настройки

- `src/main/java/dev/veyra/shader/ShaderPackRepository.java` обнаруживает ZIP-архивы и распакованные папки в `shaderpacks/`, включая ZIP с одной внешней папкой-обёрткой. Выбранный pack хранится в `veyra/selected-pack.txt`.
- `ShaderPack.java` строит ограниченный снимок shaderpack-файлов, находит программы/измерения, свойства и условия, разворачивает dimension overrides и shadow/render-target настройки. При ошибке чтения/компиляции предыдущий активный pipeline сохраняется; refresh переоткрывает файлы с диска.
- `ShaderIncludes.java` рекурсивно раскрывает quoted/angle includes и ограничивает разрешение путей внутри shaderpack. `GlslPreprocessor.java` использует JCPP для define/условной GLSL обработки; filesystem-доступ препроцессора выключен.
- `ShaderPackOptions.java`, `ShaderOptionStore.java`, `AtomicFiles.java` реализуют поддерживаемое подмножество опций `shaders.properties`: define/constant options, допустимые значения, профили и их наследование, program toggles, sliders, вложенные screens, labels и columns. Overrides сохраняются отдельно на pack без изменения архива; запись атомарная, некорректные/устаревшие сохранённые значения игнорируются.
- `ShaderPackScreen.java` и `ShaderOptionsScreen.java` предоставляют внутриигровой экран F6: выбор, отключение, refresh, диагностика неподдерживаемых наборов, настройки, профили, применение и сброс. Применение перекомпилирует pack до сохранения выбора; ошибка замены оставляет прежний активный pack.
- При смене измерения/мира сбрасываются временные матрицы и depth history. Pipeline/source caches ограничены загрузкой pack; изменение настроек без изменения значений не требует перекомпиляции.

### GLSL и поддержанные программы

`VeyraRenderer.java` — основной renderer/компилятор и координатор lifecycle (крупный класс, не заменять рефакторингом без необходимости). Он адаптирует источники к pipeline Minecraft/VulkanMod, компилирует через shader manager VulkanMod и планирует pass’ы на границах world rendering.

- Post-фазы: `prepare` после shadow pass, `deferred` перед translucent terrain, `composite`/`final` после world render. Нумерованные pass’ы идут по номеру. Post vertex stage поддерживается ограниченно: full-screen triangle и несколько встроенных vertex символов/только `texcoord` varyings; произвольные vertex attributes/uniforms не поддержаны.
- Семейства geometry bridges: terrain (solid/cutout/water), entity/armor, hand, block entity models/items/custom geometry, sign glyph text, particles, weather, lightning, basic/leash, lines, glint, sky, world-border, damaged-block overlay, beacon beam, clouds. Программы имеют ограниченные Iris-style fallback-цепочки; неподходящий Minecraft pipeline пропускается с диагностикой.
- `TerrainShaderAdapter`, `EntityShaderAdapter`, `CloudShaderAdapter`, `ColorDistanceShaderAdapter`, `TextureColorDistanceShaderAdapter`, `DamagedBlockShaderAdapter`, `BeaconBeamShaderAdapter`, `IrisFogUniformAdapter`, `ShaderPackUniformAdapter` адаптируют доступные vertex inputs, varyings, uniforms, samplers и GLSL spellings. `FragmentOutputs.java` читает `layout(location)`, `outColorN`, `DRAWBUFFERS`/`RENDERTARGETS`; `GeometryBlendSettings.java` разбирает глобальный и per-target blend state.
- Реализованы ограниченные GLSL-совместимости: `gl_FragColor`/`gl_FragData`, старые `texture2D`/`texture`/`textureSize`, `tex` alias блока/terrain atlas, entity `varying vec4 color`, fog/alpha inputs, очистка только **неиспользуемых** sampler declarations перед адаптацией. Активные неизвестные samplers/uniforms приводят к явному отказу программы.
- Активные `compute`, `geometry`, `tessellation` и `begin` shader stages не исполняются. `ShaderPack` делает preflight и возвращает понятную ошибку вместо молчаливого пропуска обязательной возможности.

### Vulkan backend и ресурсы

- `VeyraMrtFramebuffer.java`, `VeyraGeometryTargets.java`, `VeyraDeviceFeatures.java` и mixin’ы вокруг `GraphicsPipeline`/render pass реализуют **одновременный MRT только для terrain geometry**: fragment output location сопоставляется физическому colortex target, доступны per-target blending, проверка `independentBlend` и максимального числа color attachments. Сохраняются viewport/scissor и draw state VulkanMod.
- `colortex0` — текущий scene color; `colortex1..15` — on-demand ping-pong color targets. Поддерживаются лишь документированные в README форматы (R8, RGBA8, R16F, RGBA16F, R32F) и ограничения возможностей устройства. Terrain geometry targets должны иметь размер сцены. Post outputs сейчас выполняются отдельными fullscreen draws по одному output; это не настоящий одновременный post MRT с image-store/atomic semantics.
- `VeyraDepthTextureSnapshot.java` делает Vulkan depth snapshots с явными clear/layout transitions. В post доступны `depthtex0`/`DepthSampler` (главный depth attachment), `depthtex1` (opaque/cutout snapshot до translucent terrain), `depthtex2` (до hand). Это **не означает**, что любой geometry pass может сэмплировать их: на текущем HEAD `gbuffers_water` поддерживает `depthtex1`, но opaque/shadow geometry ещё ограничены.
- `VeyraDepthTextureView.java` создаёт depth-only sampled view для depth-stencil изображения. Код переводит только корректный image/aspect в shader-read и восстанавливает состояние до повторного использования attachment.
- `VeyraNoiseTexture.java` генерирует Vulkan-backed Iris `noisetex`; размер по умолчанию 256, `noiseTextureResolution` ограничен 1..4096, sampler linear/repeat. Подключён к поддержанным terrain, shadow, entity/hand/block/sign и post программам.
- `shadowtex0` подключается к поддержанным terrain/entity/block/hand/text программам и post-пассам, если pack предоставляет shadow pass; сравнение depth программно адаптируется для поддержанных вызовов. Есть shadow variants solid/cutout/translucent/entities/block и свойства дистанции. **Второй shadow map `shadowtex1` и `shadowcolor` отсутствуют.**
- `VeyraPipelineResources`, `VeyraPipelinePassCache`, `VeyraShaderCache` и mixin’ы откладывают уничтожение созданных pipeline/view до очереди frame cleanup VulkanMod и удаляют устаревшие source/pipeline-cache entries Veyra. `SwapChainMixin` сохраняет native usage flags; sampled image barriers привязаны к основной graphics frame graph.
- `EyeBrightnessSmoother`, `ShaderPackIVec2Uniform`, `VeyraUniformsMixin` и общий snapshot передают доступные built-in Iris uniforms. Среди них: frame/world/weather/player/status-effect значения, matrix/camera позиции (включая integer/fractional/previous), `moonPhase`, raw/smoothed `eyeBrightness`, `bedrockLevel`, `cloudHeight`, `endFlashIntensity`, hand depth и fog. `eyeBrightnessHalflife` читается из GLSL; wetness использует Iris-подобные 600-tick wet/200-tick dry half-lives, но pack-specific wetness settings пока не читаются.

### Важные точки интеграции

`src/main/resources/veyra.mixins.json` включает hooks/accessors в Minecraft и внутренние классы VulkanMod, в том числе `ShaderManagerMixin`, `PipelineManagerMixin`, `WorldRendererMixin`, `VkGpuDeviceMixin`, `GraphicsPipelineMixin`, `VkRenderPassMixin`, `VkCommandEncoderMixin`, `RendererGeometryTargetsMixin`, `SwapChainMixin`, `DeviceManagerMixin` и accessors. Они рассчитаны на закреплённую версию VulkanMod 0.6.8. `VeyraShadowRenderer.java` обслуживает shadow path. Отдельные `src/graphicsSmoke` mixins/tests — test-only source set, не мод для дистрибутива.

## Существенные ошибки, уже исправленные

Ниже — отобранные интеграционные ошибки, подтверждённые коммитами/историей. Это не полный bug-by-bug changelog.

| Проблема и причина | Исправление |
| --- | --- |
| F6 polling пропускал короткие нажатия; первое KeyboardHandler-событие в выбранной связке также не срабатывало. Экран сначала пытался сделать второй blur в том же кадре. | В ранней версии hotkey переведён на tick edge-detection, меню рисует собственный фон. F6 открывал pack selector в Modrinth. |
| У Minecraft 1.21.11 отсутствовал использованный ранним прототипом `post/blit.vsh`; фактический quad vertex shader — `core/screenquad`. VulkanMod GLSL parser неверно интерпретировал `layout(location=...)`; неподходящий суффикс sampler мешал привязке post input. | Используется верный vertex shader; adapters нормализуют output layouts для соответствующего пути; вход связывается под именем `InSampler`. |
| Сначала post pass запускался до появления окончательного scene color; тест показывал вспомогательный красно-серый фон/ломал кадр. Map targets передавались как immutable `Map.of`, хотя frame graph мутирует map. В раннем пути освобождение transient render target приводило к native crash VulkanMod/NVIDIA. | Hook перенесён после world render перед GUI, graph получает mutable targets, текстура переведена на явно управляемый target. В переписке зафиксирован успешный grayscale pass в ранней версии; это не подтверждение совместимости сторонних pack’ов в текущем 0.2. |
| Прямое чтение depth attachment приводило к `VK_ERROR_DEVICE_LOST` или пустой/неверной карте: брался не тот texture, D24S8 view включал stencil, VulkanMod заново разрешал underlying image, дескриптор неизвестного sampler попадал в unit -1, wrapper терял tracked layout; барьер ждал только late depth tests. | Используется attachment из внутреннего main pass VulkanMod, depth-only image view и закреплённый sampler slot; image/layout state синхронизирован с native image, барьеры включают ранний и поздний fragment depth tests, переходы возвращаются в корректном render lifecycle. Для history применяется настоящий transfer clear. Последующий synthetic Lavapipe smoke проходит; visual correctness широких pack’ов ещё не доказана. |
| VulkanMod базово не поддерживал несколько color attachments; terrain MRT также требовал сохранить viewport/scissor, проверить `maxColorAttachments`/`independentBlend`, очистить новые targets и не маршрутизировать draw после ошибки. | Добавлены собственные borrowed multi-attachment framebuffer/render pass, pipeline state, draw routing, проверки возможностей, сохранение render state и тесты; устаревшие pipeline variants снимаются после frame fence. |
| Shader adapters отклоняли неиспользуемые samplers; `tex` и `textureSize`/shadow helper типы отличались от ожиданий VulkanMod; у terrain helper-функции `sampler2DShadow` оставался неподдерживаемый тип. | Неиспользуемые declarations удаляются по фактическим references; `tex` отображается на atlas; helper shadow sampler понижается к raw depth для реализованного сравнения; добавлены JUnit и shaderc regressions. Активные неподдерживаемые чтения всё ещё отклоняются. |
| В `ClientLevel` тестовых/переходных миров `endFlashState()` бывает `null`; uniform getter падал с NPE. | `endFlashIntensity` в отсутствие состояния равен Iris-совместимому `0.0`; добавлен регрессионный тест (`c2a87c7`). |
| Раннее восстановление настроек выбрасывало из списка все найденные pack’и, если выбранный pack содержал неверные свойства; сохранение выбора могло оставить частично записанные настройки. | Каталог discovery сохраняется независимо от ошибки restore; настройки/selected-pack пишутся атомарно с откатом предыдущих значений. |
| Linux CI изначально не мог исполнять `gradlew` без executable bit; ранние GitHub Actions использовали устаревшие версии actions/runner. | Исправлены права wrapper, обновлены actions до Node 24 и runner зафиксирован на Ubuntu 24.04. Gradle cache использует basic provider. |

## Что работает частично и ограничения

1. **Совместимость с Iris — подмножество, а не универсальный loader.** Source audit умеет обнаружить/препроцессировать больше stage-файлов, чем backend может скомпилировать или отрисовать. Успешное preprocessing не равно успешно загруженному pack.
2. **Настройки и переключение:** UI и persistence реализованы; compile-before-apply и сохранение старого pipeline предусмотрены. Современное GUI поведение на текущем jar не было подтверждено в последней сессии Computer Use.
3. **Геометрические vertex shaders:** geometry использует синтезированные Minecraft/VulkanMod vertex interfaces. Собственный `.vsh` для geometry/shadow, произвольные attributes/IDs/varyings не исполняются. Ограниченный fullscreen post `.vsh` поддерживает только соответствующие встроенные данные.
4. **Outputs:** настоящий MRT сделан для terrain geometry. Entity/hand/block/sign/sky/particles/weather/cloud/shadow bridges по-прежнему ограничены одним output. Post `DRAWBUFFERS`/`RENDERTARGETS` — последовательные отдельные draws, а не единый MRT.
5. **Textures/resources:** только документированные color/depth/noise/shadow aliases. Нет PBR `specular`, `shadowtex1`, `shadowcolor`, произвольных texture/image units, custom images/SSBOs и compute ресурса. Built-in `gaux` aliases не покрывают семантику всех legacy packs.
6. **Pass properties/state:** обработана часть options, profiles, dimension conditions, blend/clear/size/format settings; полная семантика Iris/OptiFine properties, feature flags и пользовательских expressions не реализована.
7. **Uniforms:** есть заметный набор Iris built-ins, но активные pack-specific uniforms (например `entityId`, `blockEntityId`, `shadowFade`, некоторые uniforms clouds) не поддержаны. Паковые значения wetness half-life не читаются.
8. **Device/runtime:** target formats и device capabilities ограничены. Resize, reload, долгие игровые сессии, переключение миров/измерений/pack’ов и выделение/освобождение GPU памяти не прошли полного стресс-теста. FPS-прирост не измерялся и не заявляется.

## Shaderpack audit — что реально подтверждено

`COMPATIBILITY.md`, `validation/shaderpack-inputs.json`, `validation/shaderpack-profile-matrix.json`, `validation/shaderpack-source-audit.json` и `validation/shaderpack-profile-audit.json` описывают pinned source audit, а не игровые прогоны. Текущие отчёты: **75 конфигураций** (12 default + 63 объявленных profile/dimension), **5 178 stage sources** обнаружены и обработаны; adapters приняли **18** и отклонили **699** geometry-fragment/configuration случаев. Во всех 75 report entries `visualValidation=false`.

| Пак и закреплённая версия | Source audit geometry result по матрице | Зафиксированные блокеры | Полный визуальный тест |
| --- | ---: | --- | --- |
| Complementary Reimagined r5.9.3 | 0 принято / 270 отклонено (27 конфигураций) | активный `specular`; в water geometry — `gaux2`/`colortex5`; cloud/spider-eye `blockEntityId`; `shadowcomp.csh` compute в части профилей | Нет |
| Complementary Unbound r5.9.3 | 0 / 270 (27) | те же группы ограничений, что и Reimagined | Нет |
| BSL 10.1.8 | 18 / 144 (18) | `shadowtex1`, `gaux2`, custom `entityId`, `blockEntityId`, `shadowFade`, cloud `texture`; точные ошибки различаются по профилю/измерению | Нет |
| Sildur’s Vibrant 2.02 Extreme | 0 / 15 (3) | активный `colortex4` в translucent geometry и cloud custom `texture` | Нет |

Отдельно проверены `noisetex`, BSL entity `varying vec4 color`, часть Iris uniforms и `sampler2DShadow` helper адаптации. Эти результаты говорят только о source adapter/compiler coverage. Они **не** подтверждают descriptor binding, правильные layout transitions, работу lighting/water/shadows/sky/transparency или корректное изображение. Источники pack’ов скачиваются CI по URL/SHA-512 и не входят в репозиторий из-за лицензий авторов.

## Сборки и проверки

### Проверки, завершившиеся успешно

- GitHub Build для текущего HEAD `e42f151`: [run 39184823848](https://github.com/glebKovshov/Veyra/actions/runs/39184823848) — **Success**, собран development artifact; workflow выполняет `build validateShaders` и `scripts/verify_jar.py`.
- GitHub PR Build для `e42f151`: [run 39184828650](https://github.com/glebKovshov/Veyra/actions/runs/39184828650) — **Success**. GitHub показал development JAR artifact около 403 KB; это Actions artifact с 14-дневным сроком хранения, не Release asset.
- Graphics smoke и pinned shader audit проходили на последнем code commit `c2a87c7`, после которого `c34e47d`/`e42f151` изменяли документацию/link. [Graphics run 39183912225](https://github.com/glebKovshov/Veyra/actions/runs/39183912225); [source audit run 39183912243](https://github.com/glebKovshov/Veyra/actions/runs/39183912243).
- Synthetic Lavapipe client fixture (Minecraft 1.21.11 + VulkanMod 0.6.8, Mesa Lavapipe, validation layer) прошёл terrain-MRT → composite readback: в активном frame **222 319 magenta пикселей**, baseline без shader pass — **0**, Vulkan validation errors **0 до начала shutdown**. Это тестовый fixture, не готовый shaderpack.
- При `vkDestroyDevice` тот же smoke оставляет **10 031** `VUID-vkDestroyDevice-device-05137` child-object diagnostics. Авторы/владельцы объектов пока не установлены; это не основание списывать их автоматически на Veyra или VulkanMod.
- GitHub PR page на дату проверки: [PR #2](https://github.com/glebKovshov/Veyra/pull/2) открыт как **Draft**, 57 commits в target `main`.

### Локальные результаты и оговорки

- В существующих `build/test-results/test/*.xml` агрегируется **91 JUnit test, 0 failures, 0 errors, 0 skipped** (10 suites; отчёты датированы 2026-10-19 01:07 UTC). XML не закрепляет source SHA; точный HEAD независимо подтверждают GitHub Build/PR Build для `e42f151`.
- История последней локальной проверки сообщает `test validateShaders build` на JDK 21: 21 пример shader source, 18 компиляций в Vulkan SPIR-V и 4 проверки conditional includes. Это не проверка descriptor/runtime поведений. Ранее проверялись source адаптеры и pipeline contract tests.
- `VALIDATION.md` содержит устаревшие цифры **74 tests / commit 340db0c**; не использовать их вместо текущих отчётов. Обновить его после следующего зафиксированного прогона.
- Локальный `build/libs` содержит `veyra-0.2.0+mc1.21.11.jar` и sources JAR. Их наличие не делает 0.2 опубликованным stable release.
- Ранняя версия `0.1.x` с простым grayscale final pass действительно запускалась в Modrinth и показывала эффект. Это историческая проверка базового fullscreen pass, не текущей версии 0.2 и не Complementary/BSL/Sildur’s.
- Последняя отдельная просьба проверить Modrinth на Windows не выполнена: Computer Use вернул `native pipe is unavailable: ... Не удается найти указанный файл`; `cua.getState()` показывал `apps: []`. Это ограничение среды управления приложениями, не диагностированная ошибка Veyra.

## Текущее состояние Git, PR и релизов

На момент проверки:

- `HEAD`: `e42f151` — `Link current development JAR`.
- Текущая ветка `feature/iris-compatibility-0.2` совпадает с `origin/feature/iris-compatibility-0.2` на `e42f151`.
- `main` и `origin/main`: `2eedd38` (`docs: add eye brightness shader example`). Feature branch имеет **57 commits ahead** относительно `main`; PR #2 не слит и остаётся draft. `e37f188` (релизный commit 0.1.1) не является предком `origin/main`, хотя опубликованный release существует.
- В начале этой сверки tracked-файлы были чистыми, но Git показывал **119 untracked entries**: локальные `.png` скриншоты/кадры, `.txt` VulkanMod dumps, `assets/` и `logs/`. Это уже существующие локальные артефакты, не включать их в `git add -A` и не удалять/перемещать без отдельной необходимости. После создания этой сводки `WORK_SUMMARY.md` тоже будет новым untracked файлом.
- GitHub Release [Veyra 0.1.1+mc1.21.11](https://github.com/glebKovshov/Veyra/releases) опубликован с tag `v0.1.1+mc1.21.11`, commit `e37f188`, mod JAR, sources/example assets и checksums. **Release 0.2.0 отсутствует.**
- `CHANGELOG.md`/`RELEASE_NOTES.md` описывают 0.2 как unreleased development и прямо запрещают заявлять полный Iris parity. README указывает на development workflow artifact.

### Значимые коммиты

- `c8be8c8` — первый commit в GitHub, README; проект стартовал из пустого репозитория.
- `e37f188` — опубликованный, CI-проверенный release 0.1.1 (предыдущая ветка стабилизации `4120c78`).
- `96d6c2e`, `35c3fde`, `bb08ede`, `3530144` — conditional preprocessing/options, pass scheduling/resource ownership, depth history/backend requirements, dimension conditions.
- `d594f58`, `34ad27a`, `1e6523f`, `b315fcc`, `a422737`, `b7ef2b0`, `00c2054` — terrain MRT, validation/state routing, readback/retirement, per-target blend, swapchain usage/layout synchronization.
- `d983391` — Vulkan-backed Iris `noisetex`.
- `ad25aac`, `e3ce1ea`, `b0bc7fe` — неиспользуемые samplers, `tex` alias, shadow sampler type lowering.
- `8cf7620`, `a6049e1`, `c2a87c7` — dimension uniforms; `shadowtex0` bridge; null-safe End flash uniform.
- `c34e47d` — compatibility/README/CHANGELOG evidence refresh.
- `e42f151` — актуальная ссылка на development JAR; текущий HEAD.

### CI pipeline

- `.github/workflows/build.yml`: каждый push/PR — Java 21, `build validateShaders`, проверка содержимого JAR, test reports и JAR artifact.
- `shaderpack-audit.yml`: pinned profile/source audit для feature branch и `workflow_dispatch`.
- `graphics-smoke.yml`: Lavapipe test mod/readback для feature branch и `workflow_dispatch`.
- `release.yml`: gated tag/`release/**` workflow проверяет версию, `build validateShaders packageExamplePacks`, JAR и checksums, затем публикует Release.
- На текущий момент source audit и graphics smoke проходят отдельными CI workflow, но **не являются шагами release workflow**; workflow аудита и графики не настроены на `pull_request`. Перед релизом надо явно решить, какие CI доказательства блокируют публикацию.

## Известные риски и неразрешённые вопросы

- 10 031 Vulkan teardown diagnostics в Lavapipe smoke остаются расследовать; нужно baseline-сравнение без Veyra и трассировка ownership до/после переключения паков. Не объявлять это доказанной утечкой Veyra без такого анализа, но и не объявлять GPU teardown чистым.
- Реальные популярные pack’и не проверены end-to-end. Нет подтверждения воды, теней, sky, fog, lighting, transparency, GUI/hand, dimension switching и долговременной стабильности.
- В истории были `VK_ERROR_DEVICE_LOST` на ранних версиях depth sampler; текущий синтетический smoke проходит, но это не заменяет физический GPU тест и стресс смены targets.
- VulkanMod 0.6.8 и Minecraft internals жёстко закреплены mixin’ами. Обновление Minecraft/VulkanMod без bytecode/API contract review может сломать старт клиента или native pipeline.
- Адаптеры намеренно отклоняют unsupported active inputs. Не маскировать ошибку удалением чтения или заглушками: shaderpack может собраться, но неверно отрисоваться.
- В корневых untracked Vulkan dumps/screenshots может быть полезная историческая диагностика, но у большинства нет закреплённого commit/run ID; считать их подсказками, не сертификатом последнего build.

## Приоритет следующих действий

1. **Сохранить рабочее дерево.** Не чистить 119 untracked entries; зафиксировать текущий HEAD/статус; добавить в отдельную документационную ветку только осознанно выбранные файлы.
2. **Добиться реального окна Modrinth/Computer Use.** Повторить загрузку свежего development JAR в профиль Fabric 1.21.11 + VulkanMod 0.6.8. Сейчас последняя попытка блокирована native pipe. Если доступ появится — начать с F6, обнаружения folder/ZIP, Configure/Apply/reset/disable, сохранения настроек и одного синтетического pack.
3. **Провести shaderpack проверки по одному паку.** Начать с BSL и выбранных профилей, затем Complementary Reimagined/Unbound и Sildur’s. Записывать pack hash/profile/GPU/действия, полный log, скриншоты и frame outputs; не считать source audit визуальным тестом.
4. **Устранить blocker’ы из pinned audit с технически правильной Vulkan семантикой:** PBR `specular`; отдельный непрозрачный `shadowtex1`; активные `depthtex1`/legacy `gaux` geometry uses; pack-specific entity/cloud uniforms; `colortex5`/`colortex4` в geometry. Пересчитать source audit и сохранить каждый устранённый отказ отдельным тестом.
5. **Расширять backend поэтапно:** arbitrary geometry vertex stages/inputs, geometry-family MRT, полноценные target/blend/clear/state properties, дополнительные samplers/images, compute и tessellation только после соответствующего Vulkan pipeline/dispatch/barrier/resource lifecycle.
6. **Проверить lifecycle:** resize, resource reload, dimension/world change, pack enable/disable/switch, повторные циклы и shutdown под validation layers; выяснить происхождение teardown objects и измерить GPU memory без неподтверждённых выводов.
7. **Обновить документацию и pipeline по фактическим проверкам.** Особенно синхронизировать `VALIDATION.md` (устаревшие 74 tests), compatibility limitations и статус current HEAD. Убедиться, что release-gating включает необходимый audit/graphics smoke либо честно документировать отдельные ручные gate’ы.
8. **Только после фактических end-to-end тестов** решить вопрос о ready-to-review PR, снять draft, слить в `main`, подготовить release notes/tag `v0.2.0+mc1.21.11`, прогнать release workflow и прикрепить JAR. Пока эти условия не выполнены — не публиковать и не заявлять Iris parity.

## Критически важные решения для продолжения

- Не переписывать `VeyraRenderer` и существующие bridges «с нуля»; сначала локализовать конкретную проблему и добавить regression fixture.
- Не переносить OpenGL Iris framebuffer/program logic напрямую. Сохранять адаптацию к VulkanMod pipeline, descriptor model, render pass/frame graph и image layout synchronization.
- Не отключать активную shaderpack-функцию ради зелёной сборки и не выдавать compile-only результат за визуальную поддержку. Неизвестные активные ресурсы должны завершаться диагностикой до draw.
- Не удалять depth-only view, explicit initial depth clear, graphics-ordered barriers, shared-resource ownership или frame-fence cleanup без эквивалентной гарантии корректного Vulkan lifetime.
- Сохранять capability checks (`independentBlend`, attachment count, image usage/format support) и исходное состояние VulkanMod pipeline/terrain optimization при отключении/ошибке.
- Оставлять graphics smoke test-only; не включать synthetic validation world/hooks в production JAR.
- Не изменять сторонние shaderpack ZIP’ы и не добавлять их в repo/release. Сохранять source URLs/checksums и соблюдение лицензий; проект MIT, JCPP сопровождается Apache-2.0 notice.
- Не приписывать 10 031 teardown diagnostics конкретному компоненту до доказательства. Не заявлять прирост FPS без измерений.
- Отделять три результата: unit/source compilation, Vulkan synthetic render fixture, third-party pack visual compatibility. Они проверяют разные уровни.

## Продолжение 2026-10-19 — текущая сессия

- Начальное состояние подтверждено `git fetch origin`: feature HEAD `e42f151`, tracked files чистые, локальные untracked артефакты сохранены. PR #2 остаётся draft; зарегистрирован в текущем T3 thread. GitHub API авторизован через существующий Git credential helper (секреты не выводились).
- Найден и исправлен недостаток include expansion: общий лимит 4096 раскрытий ограничивает разветвлённую рекурсию, а размер входа проверяется до копирования/masking. Сохранены guards, duplicate includes и conditional include behavior. Добавлен regression test.
- `test --rerun-tasks`: 92 tests, 0 failures/errors/skipped. `build validateShaders packageExamplePacks` успешен: 21 source preprocessed, 18 Vulkan SPIR-V compilations, conditional checks и shadow-sampler fixture пройдены.
- Полная Windows source-audit команда упёрлась в cmd.exe command-length limit. Добавлена передача JSON argument file в Gradle; Python runner выбирает gradlew.bat на Windows. Повторная проверка матрицы выполняется.
- Computer Use повторно инициализирован по skill; `sky.list_apps()` возвращает `Computer Use native pipe is unavailable: failed to connect native pipe: Не удается найти указанный файл. (os error 2)`. Поэтому новая физическая GPU/UI проверка не выполнена. Это не дефект Minecraft/Veyra.
- Stable release и слияние draft feature пока не подтверждены: остаются реальные shaderpack/backend blockers и непроверенный graphics lifecycle. Не считать эту запись выполнением полной цели.
- Подтверждено: origin/main является предком релизного e37f188; обе старые рабочие ветки уже являются предками feature HEAD. Начата отдельная сборка 0.1.1 перед fast-forward интеграцией готовой линии в main. Feature 0.2 не объявляется готовой.

- Full pinned source audit завершён: 75 configurations, 5178/5178 preprocessed, 18 accepted / 699 rejected; expansion-limit failures 0. Checksums всех четырёх inputs совпали.
- Готовая линия 0.1.1 после отдельного build/validateShaders/packageExamplePacks и проверки metadata/mixins интегрирована fast-forward в origin/main до e37f188. GitHub подтвердил PR #1 merged. Локальные артефакты сохранены.
- Для публикации текущего проверяемого подмножества выбрана отдельная версия 0.2.0-alpha.1+mc1.21.11 и prerelease, stable 0.2.0 не объявляется. Release workflow автоматически ставит prerelease и latest=false для semver с суффиксом до build metadata. Backend blockers остаются.

### Подтверждение alpha tag

- Commit/tag: `2b5acb0` / `v0.2.0-alpha.1+mc1.21.11`. Локальная alpha сборка и explicit JAR verifier успешны.
- Настоящий Python runner `scripts/run_shaderpack_source_audit.py` теперь успешно выполнен на Windows целиком: 12 default + 63 profile/dimension, 5178 stages, 18 accepted / 699 rejected. JSON arguments и gradlew.bat проверены фактическим запуском.
- Graphics prerequisite release run `39206709151` успешен на точном alpha tag. Скачанный artifact: 222926 active magenta pixels / 0 disabled; runtime validation errors 0; teardown diagnostics 10031. Это synthetic fixture, не visual certification популярных паков.
- Main `e37f188` также прошёл GitHub Build `39206618052`; PR #1 closed/merged. Feature PR #2 остаётся draft.

### Публикация завершена — 2026-10-19

- Реальный GitHub prerelease опубликован (`draft=false`, `prerelease=true`): https://github.com/glebKovshov/Veyra/releases/tag/v0.2.0-alpha.1%2Bmc1.21.11
- JAR: https://github.com/glebKovshov/Veyra/releases/download/v0.2.0-alpha.1%2Bmc1.21.11/veyra-0.2.0-alpha.1%2Bmc1.21.11.jar
- Tag `v0.2.0-alpha.1+mc1.21.11` закреплён на `2b5acb0ddc823443e42733ae712803d78982d986`. Release run https://github.com/glebKovshov/Veyra/actions/runs/39206709151 — success: оба reusable gate’а, build/tests/shaderc/JAR verification и publication завершены успешно.
- Все три опубликованных binary/source/example assets скачаны заново с публичных release URLs и сверены с SHA256SUMS.txt. Скачанный mod JAR повторно прошёл `scripts/verify_jar.py`. SHA-256 JAR: `e421f600972bd7d6e8ed509d7c4a0f134c440c7a4faefab2d75679230c7552a6` (430757 bytes).
- `main` и `origin/main` = `e37f188`, готовые stability/release 0.1.1 ветки интегрированы; 35 tests/0 failures и build/validateShaders подтверждены в отдельном worktree, GitHub main Build также success. Feature #2 остаётся draft, alpha tag не означает готовность stable 0.2.
- Новые implementation commits: `5b6a356` (include budget, Windows audit, CI gates), `2b5acb0` (alpha version/release). Последующие commits содержат только evidence/docs. Исходные untracked screenshots/dumps и GOAL.md сохранены, bulk add/clean/reset не применялись.
- Полная исходная цель остаётся незавершённой: нет stable 0.2 или Iris parity. Следующий приоритет — отдельный opaque shadowtex1/resource lifecycle и реальные pack-specific sampler/uniform blockers, затем физические GPU/UI/end-to-end проверки. Не снимать draft и не продвигать alpha в stable только на основании CPU/source/synthetic результатов.

## Продолжение после alpha — water geometry depthtex1 (2026-10-19)

Предыдущий goal turn классифицирован как progress: опубликован проверенный alpha, готовая линия 0.1.1 интегрирована в main. Полная стабильная цель остаётся активной.

- На старте tracked tree чистое, HEAD 55c93ce. Существующие реализации сохранены.
- Реализуется следующий backend blocker: `depthtex1` в `gbuffers_water`. Используется существующий snapshot, захватываемый перед translucent terrain; добавлены GLSL bridge, отдельный descriptor binding 7/native slot 5 и синхронизация обеих texture tables VulkanMod. Opaque/shadow geometry по-прежнему отклоняет этот sampler до определения корректной семантики.
- Ownership snapshot учитывает post и terrain consumers, чтобы rebuild post pipeline не освобождал используемый water depth view; world/reset/disable очищают table binding и используют существующее fence-retirement depth view.
- Новый оригинальный `examples/water-opaque-depth` проверяет depth за water fragment, исключая очищенный far-depth. Добавлен отдельный graphics CI matrix case с водой над opaque floor; прежний MRT fixture сохранён.
- Локально: 95 JUnit tests, 0 failures/errors/skips; build и JAR verifier проходят; shaderc: 23 example sources / 20 direct SPIR-V compilations. Новый GPU fixture ещё не подтверждён, source matrix предстоит пересчитать. Не объявлять water sampling проверенным в runtime до readback/validation.

### Первый GPU прогон и исправление descriptor layout

- Commit 1fd74ed: GitHub Build success; прежний MRT fixture прошёл (223059/0 magenta, runtime validation errors 0), новый water fixture отклонён с IndexOutOfBoundsException при `Pipeline.createDescriptorSetLayout`, до проверки изображения.
- Причина установлена по полному stack trace и pinned VulkanMod 0.6.8 source: layout buffer имеет размер числу descriptors, но индексируется binding number. Фиксированный optional binding 7 создавал разрыв при отсутствии shadow/noise. Исправлена непрерывная нумерация 5..N согласованно в terrain GLSL и PipelineConfig, включая уже существовавший noisetex-without-shadow gap. Native texture indices 3/4/5 не меняются.
- Добавлена regression матрица всех сочетаний shadow/noise для water depth binding. GPU fixture не ослаблялся; повторный прогон необходим.
- Water depth sampler явно использует nearest/clamp-to-edge через кэш SamplerManager VulkanMod, без отдельного unmanaged sampler. Локальная повторная build/test/shaderc проверка проходит.

### Второй GPU прогон: источник depth-copy

- `0815d6e` устранил descriptor-layout exception. Старый MRT fixture снова прошёл (222663/0 magenta), water fixture остался красным (1775/0 при неизменённом пороге 2000).
- Artifact run 39208129962 показал runtime Vulkan layout errors. В descriptor evidence depth-copy sampler `Sampler1Sampler` с native index 0 указывал на **собственный Color attachment snapshot**, а не borrowed main depth view. Это диагностированный binding defect существующего depth-copy пути; random magenta не считается подтверждением.
- `VkRenderPassMixin` расширен на основной borrowed depth sampler, с namespace guard для Veyra, явной native image table привязкой и layout ownership. Snapshot samplers сохраняют тот же путь. Повторный GPU test обязателен; threshold не уменьшался.
- CI теперь сохраняет validation error files и при неудачном readback result, до итоговых assertions.

## Продолжение — auxiliary water color inputs (2026-10-19)

Предыдущий goal turn: progress (depthtex1 water backend + два исправленных native defects подтверждены реальным Lavapipe readback). На старте tracked tree чистое, HEAD 728b254; основная стабильная цель остаётся активной.

- Добавляется `GeometryColorSamplers`: water `colortex1..15` и legacy gdepth/gnormal/composite/gaux1..4, dedup aliases, максимум 6 distinct images в свободных native slots 6..11. Scene colortex0 и read/attachment feedback отклоняются до draw: для них нужна отдельная snapshot/ping-pong модель, Vulkan feedback не имитируется.
- GLSL и PipelineConfig используют согласованные непрерывные binding numbers после optional shadow/noise/depth descriptors. Читаемые targets включены в существующее выделение/clear/format/size lifecycle, включая read-only targets без geometry/post writes.
- Water читает текущую ping-pong сторону после deferred. Переход attachment -> sampling записывается на graphics command buffer вне render pass; scene/MRT восстанавливается с сохранением viewport/scissor. Индексированные таблицы VulkanMod получают актуальный view; resize/reload не меняют shared resource ownership.
- Добавлен оригинальный `water-color-inputs` graphics fixture: opaque MRT colortex4/5, deferred смена стороны 5, modern/legacy aliases и half-resolution read-only colortex7 с заданной очисткой. Старые MRT и water-depth fixtures сохранены. GPU результат ещё ожидается; не считать локальную компиляцию runtime доказательством.
- Локально build/test/validateShaders проходят: 104 tests без failures/errors/skips; 25 source preprocesses / 22 direct SPIR-V compilations. JAR verifier проходит. Source audit и GPU matrix предстоят.

### Первый color-input GPU прогон

- `c4106f6`: Graphics push run 39209768717 прошёл все три fixtures: MRT 222857/0, water-depth 222757/0, water-color-inputs 222757/0 magenta; у всех runtime validation errors 0. Новый fixture реально проверил color4 из opaque MRT, color5 с актуальной deferred ping-pong стороны и инициализированный half-resolution color7.
- При review обнаружена дополнительная несовместимость определения noisetex: runtime учитывал unused declaration, а адаптер удалял её, что сдвигало следующие descriptor bindings. `usesNoiseSampler` теперь исключает unused declarations. Добавлены regression и такой неиспользуемый sampler в GPU fixture. Повторный точный GPU прогон обязателен.

### Ограничение regex при полном source audit

- Локальный audit process оставался живым (Java pid 14796, main CPU ~239s). JDK jstack показал RUNNABLE внутри `GeometryColorSamplers.read`, Pattern BmpCharPropertyGreedy/Caret на leading `^\s*`. Это доказанная квадратичная обработка длинных блоков пустых строк после preprocessing, не timeout-наблюдение и не внешний blocker.
- Начальный whitespace ограничен `[\t ]*`; такой же guard исправлен в terrain unsupported-uniform scan. Regression использует 60000 пустых строк и generous 5-second bound; проходит вместе с build/test/shaderc.
- Именно этот собственный Java audit остановлен после диагностики; прежние CI source audits c4106f6/bf5037f отменены, новый полный прогон запущен на исправленной версии. Незавершённый первый audit не считать результатом матрицы.

### Полный source audit и cleanup ownership

- Исправленный локальный runner завершил все 75 configurations / 5178 stage sources; 18 accepted / 699 rejected. Complementary water теперь отклоняется по specular, BSL water по shadowtex1 (Overworld/End) или shadowFade (Nether). Sildur water читает colortex4, одновременно присутствующий в attachments; остаётся явный feedback blocker, не работающий pack.
- Cleanup теперь хранит identity конкретных color view/image, которые Veyra положил в native/VRenderSystem slots, и очищает только совпадающие записи. Чужие/неиспользованные slots не сбрасываются. Images по-прежнему принадлежат существующим color targets и освобождаются прежним lifecycle.
- Native Computer Use перепроверен: тот же `native pipe is unavailable ... os error 2`; physical GPU/UI test не выполнен. Это не мешает продолжению backend задач и не является основанием объявлять всю цель blocked.

### Сравнение teardown с vanilla baseline (2026-10-19)

- Shutdown registry snapshot (run 39251324398) перед уничтожением allocator: в Veyra water fixture и в vanilla baseline одинаково 46 buffers / 667 images / 97 pipelines, очереди retirement пусты; ни одного объекта с именем Veyra не осталось. Undestroyed objects: 10050 (Veyra) против 10031 (baseline); основная масса — descriptor sets VulkanMod `animate_sprite_blit`/interpolate и неуничтоженные VulkanMod buffers/images. Вывод: teardown diagnostics принадлежат VulkanMod 0.6.8, не Veyra; разница в 19 объектов ещё не атрибутирована.
- Baseline run сам по себе завершился ложным failure: в smoke harness stage-0 проверка shader pipeline (tick 160) срабатывала раньше baseline ветки (tick 240). Исправлено исключением baseline из stage-0/1 веток; повторный CI прогон необходим.
- Повторный graphics run 39252310781 (1b8b8a3): все 11 Lavapipe fixtures success, включая vanilla-baseline (0/0 magenta). Teardown: baseline 10031 undestroyed objects, water-post-shadow-inputs 10034 (230400 magenta); registry снапшоты идентичны (46/667/97). Разница 3 объекта при отсутствии Veyra-named ресурсов — teardown diagnostics признаются pre-existing поведением VulkanMod 0.6.8, а не утечкой Veyra. Остаётся проверка повторных pack switch циклов.

## Продолжение работы — 2026-10-19

- Фактическая ветка `feature/iris-compatibility-0.2` находится на `d7036d9`, на девять коммитов впереди `main`; незакоммиченные пользовательские скриншоты, дампы, `assets/`, `GOAL.md` и этот файл сохранены. Уже опубликованная beta.1 относится к более раннему коммиту.
- Добавлен Iris `shadowFade`: берутся текущие углы солнца и луны из Minecraft environment attributes, применяется Iris-совместимая формула; одноимённое выражение из `shaders.properties` имеет приоритет. Добавлены unit/regression-проверки для terrain/post адаптеров, формулы и пользовательского override. Принудительный запуск `test --rerun-tasks` прошёл: 159 тестов, 0 ошибок.
- Исходный аудит после изменения: 75 конфигураций, 5,178 stage sources, 18 принятых и 699 отклонённых geometry fragment cases. BSL больше не блокируется по `shadowFade`; остаются per-draw entity IDs, cloud `texture` и недоступный source-only проверке зарегистрированный `BIOME_DESERT`. В двух Complementary остаются blockEntityId/velocity/cloud fogColor и выбранные compute-профили; в Sildur — colortex4/feedback/cloud texture. Это не визуальная совместимость.
- Обновлены версия кандидата на `0.2.0-beta.2+mc1.21.11`, changelog, release notes, README, compatibility и validation с сохранением исторических записей.
- Полная чистая сборка `clean test validateShaders build packageExamplePacks` прошла на JDK 21; 159 тестов без ошибок, shaderc обработал 39 примеров и скомпилировал 36, четыре conditional checks и entity-shadow/dataflow fixtures прошли. `scripts/verify_jar.py` подтвердил JAR `veyra-0.2.0-beta.2+mc1.21.11.jar` (current SHA-256 `6c46beb7245b1f087bbbc2a87b224a727fb3f47896c3482d6a40637dacc74175`; includes terrain/post shared `shadowFade` resolution).
- Первый graphics smoke на `c4e74c8` выявил, что terrain uniform resolver не возвращал `shadowFade`; общий resolver исправлен для terrain/post и smoke fixture уже проверяет оба пути. Все 159 тестов и чистая локальная сборка проходят. Повторные GitHub Build/source audit/graphics smoke нужны на новом fix commit; PR #3 пока не сливать. Пользовательские скриншоты/дампы/`assets/` остаются нетронутыми. Стабильная Iris-паритетная версия не подтверждена.

### Post custom uniform collision fix — 2026-10-19

- Свежий graphics smoke для `a271c1a` подтвердил terrain `shadowFade` provider, но выявил следующую конкретную ошибку: post uniform block добавлял custom `shadowFade` второй раз рядом со встроенным Iris полем; GLSL сообщал `duplicate member name: shadowFade`. Артефакт CI `12203347872` содержит исходный клиентский лог.
- Post uniforms теперь объединяются с сохранением порядка и без дубликатов через `ShaderPackUniformAdapter.mergeUniformNames`; custom expression сохраняет значение/приоритет через существующий resolver. Добавлен регрессионный тест, который разбирает реальное float override и проверяет один слот.
- После исправления `clean test validateShaders build packageExamplePacks` прошёл на JDK 21: **160 тестов, 0 failures/errors**, shaderc обработал 39 примеров и скомпилировал 36; 4 conditional checks, entity-shadow и unbound-output dataflow fixtures прошли.
- `scripts/verify_jar.py` подтвердил `veyra-0.2.0-beta.2+mc1.21.11.jar` (509,762 bytes), Minecraft 1.21.11 / VulkanMod 0.6.8; локальный SHA-256 `A6C01D5540117C4C47D01A88E99128FA53AE7DBFCEC1E07C3B6A1F62C1E322B1`.
- Исходный HEAD перед этим исправлением — `a271c1a`. Исправление и эта сводка фиксируются вместе; для нового commit требуются свежие GitHub Build, source audit и все graphics-smoke fixtures. PR #3 и beta.2 Release пока не готовы/не опубликованы. Пользовательские untracked screenshots, dumps, `assets/`, `GOAL.md` сохранены.

## Beta.2 опубликована — 2026-10-19

- PR #3 (`feature/iris-compatibility-0.2` → `main`) смержен merge commit `4be1bbaf7324b80b8aab09368e8c9e2b908e2eda`. На том же commit lightweight tag `v0.2.0-beta.2+mc1.21.11`; итоговый [GitHub Release](https://github.com/glebKovshov/Veyra/releases/tag/v0.2.0-beta.2%2Bmc1.21.11) опубликован как prerelease. Release workflow [39289684005](https://github.com/glebKovshov/Veyra/actions/runs/39289684005) завершился success.
- Главный asset: [veyra-0.2.0-beta.2+mc1.21.11.jar](https://github.com/glebKovshov/Veyra/releases/download/v0.2.0-beta.2%2Bmc1.21.11/veyra-0.2.0-beta.2%2Bmc1.21.11.jar), 509,744 bytes. JAR заново скачан, прошёл `scripts/verify_jar.py` (Minecraft 1.21.11, VulkanMod 0.6.8); SHA-256 `4238a919c89e40aa7435fc8a9129748f39a4a89785848dace5fdf1ce20199f73` совпал с опубликованным `SHA256SUMS.txt`.
- Final PR workflows на `d440f33`: Build [39288725708](https://github.com/glebKovshov/Veyra/actions/runs/39288725708), source audit [39288725787](https://github.com/glebKovshov/Veyra/actions/runs/39288725787), все 12 graphics fixtures [39288725971](https://github.com/glebKovshov/Veyra/actions/runs/39288725971) — success. Release run повторно прошёл те же source/graphics gates, сборку и verify. `custom-uniforms` readback: 222,521 active / 0 disabled magenta pixels; runtime validation gate passed.
- На release tree локально прошли `clean test validateShaders build packageExamplePacks` на JDK 21 и принудительный `test --rerun-tasks`: 160 тестов, 0 failures/errors; shaderc — 39 examples / 36 direct SPIR-V, плюс 4 conditional checks и entity-shadow/dataflow fixtures.
- Source matrix остаётся source-only: 75 случаев, 5,178 sources, 18 accepted / 699 rejected; `visualValidation=false` у всех. Complementary Reimagined/Unbound: 0/10 default geometry fragments и blockEntityId/velocity/cloud fogColor/selected compute blockers; BSL: 1/9 и per-draw entity IDs/cloud texture/source-only BIOME_DESERT; Sildur: 0/5, colortex4/feedback/cloud texture. Ни один реальный pack не подтверждён в игре.
- Это beta prerelease, не stable и не full Iris parity. Физический GPU, pack visuals, F6 UI, resize/reload/world/pack-switch stress и устранение Vulkan teardown diagnostics остаются непроверенными. Пользовательские скриншоты/дампы, `assets/`, `GOAL.md` сохранены и не включены в коммиты.

## Продолжение после beta.2 — Iris cloud fog uniforms — 2026-10-19

- Начальная сверка этой работы: `main`/`origin/main` совпадали на `b8aca8d`; beta.2 tag/release/workflow из предыдущей секции существуют. Пользовательские untracked скриншоты, дампы, `assets/`, `GOAL.md` и `scripts/analyze_vulkan_shutdown.py` сохранены. Реализация ведётся отдельно в `feature/cloud-fog-uniforms`.
- Добавлена поддержка Iris cloud `fogColor`, `fogStart`, `fogEnd`: поля `FogColor`, `FogEnvironmentalStart/End` дополняют одновременно cloud vertex/fragment UBO и `PipelineConfig`; значения приходят из VulkanMod uniform providers, а не подставляются константами. Проверка bytecode именно локального VulkanMod 0.6.8 jar подтвердила providers для этих fog-полей.
- Добавлен JUnit regression для имен/типов UBO-полей и макросов, cloud fog shaderc fixture, а пример `examples/gbuffers-clouds` теперь использует `fogColor`. Локально `test validateShaders` прошли: **161 тест**, 0 failures/errors/skips; shaderc препроцессировал 39 примеров / напрямую скомпилировал 36, включая новый `cloud-fog-uniforms`. `build packageExamplePacks` также прошли.
- Полная pinned source/profile/dimension матрица после изменения обработала 75 случаев / 5,178 stage sources: 18 принято / 699 отклонено, все `visualValidation=false`. Cloud-фрагменты больше не отклоняются именно по `fogColor`; Complementary пока отклоняется по активному `colortex0` в облаках, `blockEntityId`, terrain/water `velocity` и selected compute; BSL — `entityId`/`blockEntityId`, cloud `texture`, source-only `BIOME_DESERT`; Sildur — `colortex4`/attachment feedback/cloud `texture`. Обновлены `validation/shaderpack-source-audit.json`, `validation/shaderpack-profile-audit.json` и `COMPATIBILITY.md` по результатам этого прогона.
- До смены версии был собран и вручную проверен distributable `veyra-0.2.0-beta.2+mc1.21.11.jar`: 510,745 bytes, SHA-256 `75d967930aa1f82ec4a0b9aeafe819350b3d96c935c6e2d6837939fd3d3c4efa`; проверены mod id, Minecraft 1.21.11, VulkanMod 0.6.8, mixins, встроенный JCPP и отсутствие smoke-классов. Для локальной работы установлен в temp полный Temurin JDK 21.0.12.1; архив сверён с Adoptium API SHA-256 и workspace не затронут.
- Версия обновлена до `0.2.0-beta.3+mc1.21.11`; `test --rerun-tasks validateShaders build packageExamplePacks` после смены версии прошёл полностью (13 задач). XML: 161 тест, 0 failures/errors/skips. Beta.3 JAR: 510,745 bytes, SHA-256 `06647cc838563ca7b562b9b522741bbbabe78fba820ba15a2978407ae6675be3`; PowerShell проверил те же distributable-content инварианты, что `scripts/verify_jar.py` (в текущей Windows среде Python отсутствует). Добавлен свежий локальный срез в `VALIDATION.md`. PR/Release ещё не созданы; GitHub CI не запускался.
- Дальше: проверить diff и GitHub доступ, commit/push branch, открыть и немедленно связать PR с Build/source-audit/graphics gates, обновить CI evidence, merge в main, push tag `v0.2.0-beta.3+mc1.21.11`, подтвердить настоящий GitHub Release и скачать заново JAR/checksum.
- Следующая крупная функциональная задача остаётся per-draw `entityId`/`blockEntityId`: их нельзя передать правильным общим uniform в батчированные vertex buffers. Требуются расширенный entity vertex format/write path и Iris mappings `entity.properties`/`block.properties`; текущий адаптер по-прежнему честно отклоняет эти программы. Никакой визуальной проверки реального пакета и физического GPU в этой работе не было.


### PR #4 CI — 2026-10-19

- PR #4 (`feature/cloud-fog-uniforms` -> `main`, head `566e059c433bd64a14184a5d0e6dd66d394bff7d`) is linked to this T3 thread: https://github.com/glebKovshov/Veyra/pull/4.
- GitHub Build run [39294368264](https://github.com/glebKovshov/Veyra/actions/runs/39294368264) passed.
- GitHub Graphics smoke run [39294368250](https://github.com/glebKovshov/Veyra/actions/runs/39294368250) passed all 12 Lavapipe fixtures.
- GitHub Shaderpack source audit run [39294367864](https://github.com/glebKovshov/Veyra/actions/runs/39294367864) passed.
- The PR is ready to merge. The beta.3 GitHub Release and published JAR are still pending; they are not claimed as published here.


### Beta.3 merged and published — 2026-10-19

- PR #4 (`feature/cloud-fog-uniforms` -> `main`) merged as `b40ae406c1d84cb95bb958bbf90a89def54539d0`; its final head was `6d2cd34e040d639246b0d3ace358c6b0863d9cf8`. Final PR checks all passed: Build [39295185566](https://github.com/glebKovshov/Veyra/actions/runs/39295185566), pinned source audit [39295180398](https://github.com/glebKovshov/Veyra/actions/runs/39295180398), and 12-fixture Lavapipe graphics smoke [39295180473](https://github.com/glebKovshov/Veyra/actions/runs/39295180473).
- Lightweight tag `v0.2.0-beta.3+mc1.21.11` points to the merge commit. Release workflow [39295725182](https://github.com/glebKovshov/Veyra/actions/runs/39295725182) passed source audit, all 12 graphics fixtures, Gradle build/tests, and `scripts/verify_jar.py`; the tag Build [39295724917](https://github.com/glebKovshov/Veyra/actions/runs/39295724917) also passed.
- Published prerelease: https://github.com/glebKovshov/Veyra/releases/tag/v0.2.0-beta.3%2Bmc1.21.11. Main installable asset: https://github.com/glebKovshov/Veyra/releases/download/v0.2.0-beta.3%2Bmc1.21.11/veyra-0.2.0-beta.3%2Bmc1.21.11.jar (510,727 bytes, SHA-256 `5a397886d9781d1bdf10a22484a97f320614147abbbe57a3833d0e1a00b62552`). Downloaded JAR hash matches the published `SHA256SUMS.txt`; the artifact also opens as a valid ZIP/JAR. Release includes source JAR, example-packs ZIP, and checksum manifest.
- Local candidate checks after the version bump: Gradle `test --rerun-tasks validateShaders build packageExamplePacks` succeeded; 161 JUnit tests passed with zero failures/errors/skips, and shaderc processed 39 example sources (36 direct SPIR-V compilations) plus the cloud-fog fixture. The published Linux CI artifact is the verified release build; the local Windows candidate has a different byte size/hash.
- Confirmed scope: cloud `fogColor`, `fogStart`, and `fogEnd` resolve to live VulkanMod providers in the cloud UBO. Pinned source/profile audit remains 75 cases / 5,178 stages, 18 accepted and 699 rejected; all external-pack cases have `visualValidation=false`. Lavapipe fixtures verify the synthetic scenarios, not real shaderpack appearance on a physical GPU.
- Still incomplete against `GOAL.md`: real pack visual validation and several Iris interfaces remain unsupported. Next code target: per-vertex/per-draw `entityId` and `blockEntityId` via the entity vertex write path and Iris property mappings; current shader adapter deliberately rejects those active programs. Other known blockers include active cloud `colortex0`, cloud textures, velocity, selected compute, and render-target feedback. Do not describe beta.3 as stable or full Iris parity.


### Beta.4 entity and block entity identifiers — released (2026-10-19)

- Confirmed the active branch starts from beta.3 main and has no tracked local edits before this feature; existing untracked screenshots, logs, assets, and analysis script remain untouched.
- Added Iris entity.properties and block.properties readers, ordered ID maps, block state predicates and tag matching. The maps are activated and restored with the existing item ID map when shaderpacks change.
- Added per-submission entity/block state contexts, render type routing, cached lazy Vulkan pipeline variants, and entity uniform constants. Unmapped entity identifiers use Iris's -1 sentinel; terrain/cloud unbound uniform behavior remains zero.
- Added unit regressions for mapping precedence, namespaces, state predicates, tags, continuation lines, and mapped/unmapped shader constants. Local build and unit/shader validation are confirmed; GitHub runtime gates passed as recorded below, while real-pack visuals remain unverified.
- Local verification now passes on JDK 21: `test --rerun-tasks validateShaders build packageExamplePacks`; 166 JUnit tests passed with 0 failures/errors/skips. Shader validation processed 39 example sources, compiled 36 to Vulkan SPIR-V, and passed the entity-ID fixture plus the existing conditional-include, entity-shadow, cloud-fog, and dataflow checks.
- The beta.4 candidate JAR opens as a ZIP and contains mod metadata, both ID-map classes, the entity dispatcher mixin, and nested JCPP; smoke harness classes are absent. The local Windows candidate was 527,755 bytes with SHA-256 `deb6de3b242bd8ae71536c6b778fc3e9a0c731f22ba73a94f39b18ab74bde436`.
- PR #5 was opened and linked to this T3 thread, passed Build, the pinned source audit, and all 12 Lavapipe graphics fixtures, then merged to `main` as `f3ca6b0bae84efbde979b46b16767e7d175371c6`. The audit covered 12 default and 63 declared-profile/dimension cases; 18 geometry programs were accepted and 699 rejected. See `VALIDATION.md` and `COMPATIBILITY.md`.
- Branch audit: `feature/cloud-fog-uniforms` and `feature/iris-entity-identifiers` are integrated. `feature/iris-compatibility-0.2` has no commits unique from `main` and is 9 commits behind, so it has no remaining changes to merge.
- Tag `v0.2.0-beta.4+mc1.21.11` points to `2fce9f7ed764f9cd62ba7feaa0d931e9b3b6ae07`. Release workflow [39301135037](https://github.com/glebKovshov/Veyra/actions/runs/39301135037) passed source audit, all 12 graphics fixtures, `build validateShaders packageExamplePacks`, and `scripts/verify_jar.py`; tag Build [39301135146](https://github.com/glebKovshov/Veyra/actions/runs/39301135146) also passed.
- Published prerelease: https://github.com/glebKovshov/Veyra/releases/tag/v0.2.0-beta.4%2Bmc1.21.11. Downloaded JAR (527,737 bytes) SHA-256 `dd2230e13f64c1797477ab689dc3fdd44e4da9e82c9ab5abdadf7c283cc97a03` matches the release `SHA256SUMS.txt`. It opens as a ZIP and includes the new ID-map/mixin classes without smoke harness classes.
- The broad `GOAL.md` remains incomplete: third-party packs have not been visually verified on a physical GPU, and source blockers remain for cloud textures/`colortex0`, velocity, selected compute and render-target feedback. Continue with those interfaces and validate rendered pack output before any stable/full-parity claim.


### Beta.5 cloud vertex bridge — released (2026-10-19)

- Continued from the actual beta.4 `main` commit on a new `feature/cloud-vertex-shader` branch; tracked files were clean before editing, and all pre-existing untracked screenshots, logs, assets, and analysis files remain untouched.
- `ShaderPack.readCloudVertex()` now loads option-processed `gbuffers_clouds.vsh` and expands includes. `CloudShaderAdapter` adapts the supported `POSITION_COLOR` interface, mapping `gl_Vertex`, `gl_Color`, `gl_ModelViewProjectionMatrix` and `ftransform()`, with `color` and `vertexDistance` outputs. VulkanMod's default cloud color/distance are initialized before the pack's vertex body.
- Cloud vertex inputs/outputs that VulkanMod cannot provide, texture access, and custom vertex uniforms are rejected explicitly. This does not resolve active cloud `colortex0`, cloud textures/UVs, velocity, selected compute, or third-party-pack appearance.
- Added a cloud vertex example, ZIP/include-loading and adapter regressions, and a shaderc fixture compiling the adapted vertex shader to Vulkan SPIR-V.
- Full local beta.5 candidate verification passed on JDK 21: `test --rerun-tasks validateShaders build packageExamplePacks`; 169 JUnit tests, 0 failures/errors/skips; 39 example stages processed, 36 compiled directly to SPIR-V, plus the cloud vertex fixture. The distributable `veyra-0.2.0-beta.5+mc1.21.11.jar` is a valid ZIP with expected mod classes and no smoke harness; 531,553 bytes, SHA-256 `1ec25a62811d1e2808536439d0b41f4f47a23878c1398e9a0ba7eabea779eb61e`.
- PR #6 ([Add limited cloud vertex shader support](https://github.com/glebKovshov/Veyra/pull/6)) is linked to this T3 thread. Its final head `4a8218e43cb785e6e829c0a861392bcbe79b0f1c` passed Build [39305176030], pinned source audit [39305175917], and graphics smoke [39305175988]. The audit again covered 12 defaults and 63 declared-profile/dimension cases, accepting 18 and rejecting 699 geometry fragments; all 12 synthetic Lavapipe fixtures passed. No third-party pack was visually tested.
- PR #6 merged into `main` as `0a968a84a3f37873245174ab9e06055106c35802`. Tag `v0.2.0-beta.5+mc1.21.11` points to release commit `7e5fd09c42338cf5802e63b9519d3548b8456230`. Release workflow [39306047963](https://github.com/glebKovshov/Veyra/actions/runs/39306047963) passed pinned source audit, all 12 Lavapipe fixtures, `build test validateShaders packageExamplePacks`, and `scripts/verify_jar.py`; final-main Build [39305946938](https://github.com/glebKovshov/Veyra/actions/runs/39305946938) also passed.
- Published prerelease: https://github.com/glebKovshov/Veyra/releases/tag/v0.2.0-beta.5%2Bmc1.21.11. Its installable JAR is 531,535 bytes, SHA-256 `badc8f9c49449abab1a1a9d8bf6b02937e19196f86466696bbbbcce61661cee7`; the downloaded file matches `SHA256SUMS.txt`, opens as a ZIP, contains beta.5 metadata/classes, and excludes smoke harness classes. Release also includes sources, example packs, and checksums.
- The tracked working tree is clean after recording this release evidence; pre-existing untracked user files remain untouched. The broad `GOAL.md` is still incomplete: no popular third-party pack has been visually validated, and cloud textures/`colortex0`, velocity, selected compute, and render-target feedback remain blockers.
- Corrected the stale beta.3-era compatibility paragraph that said the beta.4 pinned audit was still pending. README, compatibility notes, changelog and release notes now describe the limited cloud vertex support and its restrictions.


### Продолжение: Iris uniform parity — beta.6 candidate (2026-10-19)

- Продолжил с фактического состояния: `main` на beta.5, `HEAD` — `d87b167` (`Add Iris velocity and match Iris custom-uniform semantics`) в `feature/iris-uniform-parity`; коммит также доступен как `origin/feature/iris-velocity-smooth`. `main` — предок текущего HEAD. Незакоммиченные изменения другой модели сохранены и проверены; пользовательские скриншоты, логи, дампы, `assets/`, `GOAL.md` и `scripts/analyze_vulkan_shutdown.py` не включались в работу.
- Незакоммиченная beta.6 реализация добавляет Iris common/dimension/player/celestial/hardcoded-custom uniforms для поддержанных terrain, entity-family и post блоков, pack-expression inputs, defaults для поддержанных non-opaque declarations, которые Iris не регистрирует, и Iris-compatible resolution `endFlashShadows`. Исходники Iris 1.21.11 (commit `0a1fcaa`) сверены для сглаживания, health values, fixed-time rules, celestial vectors и hardcoded uniforms.
- При сверке обнаружено, что новый `timeAngle` безусловно обнулял fixed-time dimensions, включая Nether/End, тогда как Iris сохраняет там время суток. Вычисление переведено на общий `frame.worldTime()` с Iris-правилами и добавлен regression-тест для fixed-time Nether/End.
- Локально прошла команда `test --rerun-tasks validateShaders build packageExamplePacks` на JDK 21: **179 тестов, 0 failures/errors/skips**; 39 shader examples, 36 прямых Vulkan SPIR-V компиляций, плюс существующие entity/cloud/conditional/dataflow fixtures. `python scripts/run_shaderpack_source_audit.py` прошёл: 12 default + 63 profile/dimension cases, 5,178 preprocessed stages, 18 accepted / 699 rejected; все 75 с `visualValidation=false`. Сгенерированные записи совпали с checked-in default/profile JSON.
- Кандидат `build/libs/veyra-0.2.0-beta.6+mc1.21.11.jar` прошёл `scripts/verify_jar.py`: 555,043 bytes, SHA-256 `8e2efffdba789d31714cb002424cde769a4a6af1c2286a764339379e2f574659`; metadata заявляет Minecraft 1.21.11 / VulkanMod 0.6.8, присутствуют обязательные nested JCPP и mixins, smoke classes отсутствуют.
- Обновлены beta.6 RELEASE_NOTES/README/CHANGELOG/COMPATIBILITY/VALIDATION. Compat audit не показывает прирост общего количества принимаемых программ: 18/699 сохраняется; blocker Complementary terrain/water сдвинулся с `heavyFog` на отсутствующий `lmCoord`. Облачные текстуры и активный `colortex0`, `currentRenderedItemId`, selected compute и feedback остаются blockers.
- Следующее: проверить diff, сохранить только файлы проекта в commit, push feature branch, открыть и связать PR, получить Build/source-audit/graphics-smoke CI, затем merge и опубликовать beta.6 с заново проверенным `.jar`/checksum. До CI/Release эта работа остаётся candidate. Популярные shaderpacks не проверены визуально; broad `GOAL.md` не завершён. После beta.6 продолжить с активного cloud scene-color `colortex0`.

## Beta.9 candidate follow-up — viewPos smoke fixture (2026-10-20)

- Reviewed PR #16 and its 14-job Lavapipe run. The new geometry-view-space shader had not failed its viewPos signal check: the shared smoke harness stopped earlier because it unconditionally requested geometry colortex1, while this fixture intentionally writes only to the presented colortex0 image.
- Updated GraphicsSmoke so the existing colortex1 lookup remains required for prior fixtures and is skipped only for geometry-view-space, which declares no auxiliary MRT target. Documented the geometry-view-space fixture output contract.
- JDK 21 rerun passed: 194 JUnit tests (0 failures/errors/skips), 42 example shaders processed, 39 direct Vulkan SPIR-V compilations, and build/packageExamplePacks succeeded. scripts/verify_jar.py passed for the explicit beta.9 candidate JAR (565,229 bytes; SHA-256 d8812d4c0388f59e56a8f67a34f04ccd5e8e5e4a54bd44eee0be17ed85f11309). Older beta.5–beta.8 distributables remained intact in build/libs.
- On PR #16 code head 0d12735832c1837935274a7e82d97b8af8c9bd38, Build run 39343008623, source audit run 39343008507, and all 14 Lavapipe fixtures run 39343008628 passed. The geometry-view-space readback measured 222,792 active magenta pixels and 0 with shaders disabled; runtime Vulkan validation errors were 0. The CI source artifact matched all 75 local records and all four pinned input SHA-512 values exactly: 90 accepted / 627 rejected, visualValidation=false in every case.
- Shutdown evidence still contains 10,031 VUID-vkDestroyDevice-device-05137 diagnostics, equal in count and category to the same-run vanilla baseline. Runtime diagnostics are zero; before allocator destruction both snapshots show 46 buffers / 667 images / 97 pipelines and no Veyra-named objects. This is not a clean-teardown claim.
- PR #16 is linked and merged as 3ac3f16b25fe16c9c5c78967f30c9cc1650f8c51. Beta.9 is published; the remaining GOAL.md work still includes physical-GPU and third-party-pack visual validation.

## Beta.9 release — 2026-10-20

- PR #16 merged to main as 3ac3f16b25fe16c9c5c78967f30c9cc1650f8c51. Lightweight tag v0.2.0-beta.9+mc1.21.11 points to that commit.
- Release workflow 39344144615 succeeded: pinned source audit, all 14 Lavapipe fixtures, build/tests, distributable verifier, and GitHub Release publication. Tag Build 39344144580 also passed.
- Published prerelease: https://github.com/glebKovshov/Veyra/releases/tag/v0.2.0-beta.9%2Bmc1.21.11
- Downloaded release JAR: 565,211 bytes, SHA-256 9c773e71622e21f0c5af3328849e84a1de02cf720ad62ceaadeeb45edfd73dcd. The digest matches SHA256SUMS.txt and scripts/verify_jar.py passes on the downloaded asset.
- The synthetic viewPos fixture passed at 222,792 active magenta pixels / 0 disabled, with zero runtime Vulkan validation errors. Process shutdown still emits 10,031 vkDestroyDevice diagnostics, matching the same-run vanilla baseline; this is not a clean-teardown claim.
- The release remains experimental. No physical-GPU third-party shaderpack visuals or full Iris parity are claimed; see GOAL.md for remaining scope.


### Beta.11 currentRenderedItemId candidate ? 2026-10-20

- Continued from beta.10 `main` at `6a2992ef4883f631efeb37e17485c48d38fa9f15` on `feature/current-rendered-item-id`; existing screenshots, logs, assets, `GOAL.md`, and analysis artifacts remain untracked and untouched.
- Added per-item `currentRenderedItemId` pipeline variants for entity, block-entity, hand, and shadow submissions. Minecraft's item render state retains the source `ItemStack`; the active pack's `item.properties` is resolved at layer submission time for every item, including block items. The context is cleared with `try/finally`. Iris hardcoded trim/cape IDs remain unsupported.
- Added an original Lavapipe fixture with diamond and stone item entities mapped to separate `item.properties` IDs and distinct magenta/green output signals. The graphics harness compiles locally; the actual client/readback fixture has not yet run in CI.
- Local JDK 21 command `test validateShaders build packageExamplePacks compileGraphicsSmokeJava --rerun-tasks -Pmod_version=0.2.0-beta.11+mc1.21.11` passed: 200 tests, zero failures/errors/skips; 44 example shader sources and 41 direct Vulkan SPIR-V compilations; graphics-smoke Java compilation and package generation passed.
- The pinned source audit passed for 4 packs / 12 defaults / 63 declared profiles: 5,178 preprocessed stages; 126 accepted and 591 rejected geometry fragments. All records remain `visualValidation=false`.
- Candidate `build/libs/veyra-0.2.0-beta.11+mc1.21.11.jar` passed `scripts/verify_jar.py`: 576,210 bytes, SHA-256 `0756B3F11BFFBF1C31300952B02DE33EF0E2E30CA79FA96E115DC37E18C86F73`.
- The feature is still uncommitted and unmerged; no PR, Lavapipe runtime result, or beta.11 release is claimed yet. Next: run the graphics matrix on GitHub, review its runtime evidence, merge the passing PR, then publish a version-matched release. The broad `GOAL.md` remains incomplete: physical-GPU real-pack visuals and other Iris blockers remain.
