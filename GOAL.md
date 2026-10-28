# Цель: полноценная поддержка Iris-совместимых шейдерпаков в VulkanMod

## Рабочий контекст и продолжение существующей разработки

Ты работаешь в **существующем GitHub-репозитории Veyra**. Значительная часть функциональности уже разработана: **не создавай проект с нуля** и не переписывай рабочую архитектуру без доказанной необходимости.

**До изменения кода:**
1. Найди и прочитай `WORK_SUMMARY.md` в корне репозитория, если он существует. Это передача контекста из предыдущего большого чата с уже выполненной работой. Если файла нет, не предполагай, что работа не велась: восстанови фактическое состояние через git, файлы и тесты.
2. Проверь текущую ветку, `git status`, локальные изменения, последние коммиты, открытые ветки, конфигурацию сборки и историю релизов. Не теряй незакоммиченные изменения. Не делай принудительных сбросов (`git reset --hard`, `git clean -fd`) без явного согласования.
3. Сопоставь заявления в summary с реальным кодом, коммитами и тестами. Сохрани реализованное ранее и продолжи с незавершённых задач.
4. Веди актуальный `WORK_SUMMARY.md` по мере работы: что сделано, подтверждено тестами, что сломано, какая следующая конкретная задача. Не заменяй выполнение работ только отчётом.

## Главная цель

Довести существующий Minecraft-мод Veyra до **стабильного релиза**, позволяющего пользователям VulkanMod устанавливать, запускать и настраивать Iris/OptiFine-совместимые шейдерпаки с максимально достижимым функциональным паритетом Iris Shaders на поддерживаемой версии Minecraft.

**Конечный результат:** готовый GitHub Release с проверенным `.jar`, инструкцией установки, документированной совместимостью и честным описанием ограничений. Не объявляй неподтверждённые функции работающими и не считай задачу выполненной без фактической публикации релиза.

## Исходные проекты

- Iris Shaders — https://github.com/IrisShaders/Iris
- VulkanMod — https://github.com/xCollateral/VulkanMod
- Veyra — текущий открытый репозиторий проекта (определи точный `origin` через `git remote -v`)
- Vitrail Shaders — https://github.com/avpbynf/vitrail-shaders
- Beryl — https://modrinth.com/mod/beryl

Изучи архитектуру соответствующих версий: загрузку/парсинг shaderpacks, препроцессор и GLSL, uniform и макросы, render passes, framebuffers, управление GPU-ресурсами и настройки в Iris; графический backend, lifecycle ресурсов и Vulkan API VulkanMod; существующую реализацию Veyra.

### Использование Vitrail и Beryl как референсов

- Изучи Vitrail как архитектурный и кодовый референс для переноса OptiFine/Iris shaderpack stages в Vulkan: отбор активных compute-программ после настроек измерения и профиля, преобразование/компиляция при загрузке, Vulkan compute pipeline, storage images, descriptor binding, dispatch-порядок и memory barriers. У Vitrail другой backend и другая версия Minecraft; переносить можно только проверенные идеи, адаптированные к VulkanMod 0.6.8 и существующей архитектуре Veyra.
- Лицензия Vitrail — LGPL-3.0-only, а его `NOTICE` перечисляет сторонние заимствования из Iris. Предпочитай самостоятельную реализацию по изученной архитектуре. Если берёшь исходный код или существенные фрагменты, сначала проверь совместимость лицензий Veyra и всех затронутых файлов, сохрани авторские notices и выполни требования LGPL; фиксируй происхождение в `NOTICE`/`THIRD_PARTY_NOTICES.md`.
- Beryl используй только как публичный продуктовый и функциональный референс. Его Modrinth-страница указывает лицензию ARR и встроенный shaderpack; не копируй и не декомпилируй его код, бинарные ресурсы или шейдеры без отдельного разрешения. Не считай его встроенный pipeline доказательством поддержки внешних Iris/OptiFine shaderpacks.
- Не добавляй Vitrail или Beryl как runtime-зависимости Veyra. Их функциональность, результаты тестов и совместимость не переносятся автоматически на Veyra.

### Приоритетная незавершённая задача

PR #84 закрыт без слияния; его реализация сохранена в beta.31 batch PR #85. Продолжай в существующих worktree `veyra-beta31-release-batch` и `veyra-beta31-compute-sizing`. Корневой checkout `feature/block-entity-hand-vertex` с локальными пользовательскими изменениями не переключай и не очищай.

По текущей серверной проверке `origin/main` — `f57e12503b896fd7f59e6cc2e8e7de964e525e1f`; PR #85 открыт draft и идёт из `release-prep/beta31-release-batch` на `main` с head `adf3a90f5564ecc7c9d83c8df4b69cae64483658`. PR #86 открыт draft поверх #85 с head `0826cdc3514a549dcd6d0a31d1e61387e2d18617`; он добавляет Iris `workGroupsRender` sizing и `indirect.<pass>` dispatch для shadow compute. Оба PR mergeable по последней GitHub metadata, T3 watchers активны. Между ранее проверенным кодовым head `ea4b9e2` и `0826cdc` — четыре документационных файла.

На точном #86 head `0826cdc` локальный JDK 21 gate прошёл: 512 тестов без failures/errors/skips, 97 example shaders, 113 прямых Vulkan SPIR-V compilations, graphics-smoke compilation, build и example-pack packaging. Pinned source audit прошёл на 4 паках, 12 defaults и 63 profile/dimension случаях: 864 geometry fragments приняты, 18 compute stages скомпилированы; audit остаётся source-only. Локальный JAR `veyra-0.2.0-beta.31+mc1.21.11.jar` прошёл verifier: 991 744 bytes, SHA-256 `D88455432687495533EF38D77F92EB3666CDF51486664F84320F1E486076BDD8`.

Обязательны свежие hosted Build, source audit и полный Lavapipe Graphics для точных PR heads #85 (`adf3a90`) и #86 (`0826cdc`). Локальный RTX smoke не заменяет Lavapipe validation. Не сливай PR и не публикуй релиз до выполнения последовательности ниже и сверки reviews.

Ветка реализует shadow terrain → `shadowcomp` → terrain, shared custom images, shadow MRT `shadowcolor0..1`, entity shadow vertex stages и runtime `bufferObject.N` SSBO. SSBO path прошёл RTX 3070 synthetic smoke: **230,400 активных / 0 после отключения** пикселей; entity shadow и composite descriptors использовали один storage-buffer handle. Это подтверждает конкретный fixture, но не полную визуальную совместимость с shaderpack.

На прежнем PR #85 head `4e017be0f03b70a7c29f176a55bc250aca6c028b` Lavapipe Graphics завершился падением пяти fixtures с `VUID-RuntimeSpirv-NonWritable-06341`: validation считал `vertexPipelineStoresAndAtomics` выключенным, хотя старый код подставлял его через `pEnabledFeatures`. В коммите `adf3a90` core features переведены на `VkPhysicalDeviceFeatures2` в начале `pNext`, `pEnabledFeatures` оставлен null, исходная цепочка VulkanMod сохранена, оба временно изменённых указателя восстанавливаются после `vkCreateDevice`. Bytecode VulkanMod 0.6.8 подтверждает используемый call shape. Добавлены регрессионные тесты на сохранение core features и Vulkan 1.1/dynamic-rendering цепочки.

На исправлении прошёл локальный JDK 21 gate: **503 теста, 0 failures/errors/skips**, 97 example shaders / 113 Vulkan SPIR-V компиляций, graphics-smoke compilation, build и example-pack packaging. Pinned source audit принял 864 geometry fragments и скомпилировал 18 активных compute stages; локальный beta.31 candidate прошёл `scripts/verify_jar.py` (982,808 байт; SHA-256 `6A646805E08D1BEAF31AEDD552AC4ED2204505C41E4EECF62FD965ACCFE35ACE`). Ранее проведённые RTX 3070 fixtures и Complementary/BSL lifecycle runs подтверждают только перечисленные синтетические пути и выбранные профили. На Windows нет Vulkan validation layer; свежий hosted Lavapipe CI обязателен.

Для #85 и #86 остаётся обязательным свежий exact-head Build, source audit и полный Lavapipe Graphics gate; локальная RTX-машина не имеет validation layer и не может закрыть старый `VUID-RuntimeSpirv-NonWritable-06341`. PR #84 закрыт без слияния, его код сохранён в #85. Последняя проверка PR обсуждений/reviews была пустой; T3 watchers активны. Не опрашивай hosted CI вручную, пока watcher наблюдает за ветками.

Следующие этапы:
1. Дождаться exact-head Build, shaderpack source audit и полного Graphics smoke для #85 на `adf3a90`. Если gate упадёт, открыть job log, исправить причину в существующей release-ветке и дождаться новой проверки.
2. После зелёных проверок и сверки reviews слить #85 в `main`. Затем переключить base PR #86 на актуальный `main`, дождаться его собственных exact-head gates, после их успеха слить #86. Не подменять требования одного слоя результатами другого.
3. После слияния стека синхронизировать `README.md`, `CHANGELOG.md`, `RELEASE_NOTES.md`, `COMPATIBILITY.md` и `WORK_SUMMARY.md` с финальными проверенными результатами и ограничениями. Затем собрать versioned beta.31 из итогового `main` и повторить полный релизный прогон. Запустить release workflow предусмотренным ref `release/0.2.0-beta.31+mc1.21.11`; workflow сам создаёт tag/release после прохождения audit, graphics, build, tests и JAR verification.
4. Скачать опубликованные assets, сверить SHA-256 `.jar` с `SHA256SUMS.txt` и повторно выполнить `scripts/verify_jar.py` для скачанного файла. Сохранить точные URL, commit, размер и checksum в `WORK_SUMMARY.md`.
5. Beta.31 с честной матрицей совместимости — промежуточный релиз, а не доказательство полной Iris parity. После публикации продолжить незакрытые shaderpack/runtime требования и добиться фактической стабильности перед объявлением стабильного релиза.

## Функциональные требования

### Шейдерпаки

- Поиск в `shaderpacks`, поддержка ZIP-архивов и распакованных директорий.
- Максимально широкая совместимость с распространёнными Iris/OptiFine shaderpacks для поддерживаемой версии Minecraft.
- Включение, выключение, смена шейдерпаков без перезапуска, когда технически возможно.
- Понятные ошибки компиляции без падения Minecraft, корректное освобождение и восстановление GPU-ресурсов при смене набора.
- Сохранение выбранного shaderpack и пользовательских опций.

### Vulkan render pipeline

- Преобразование и компиляция GLSL в SPIR-V с учётом semantics shaderpack.
- Uniforms, shaderpack-макросы и согласованная передача параметров.
- Vertex/fragment stages и, когда требуется и поддерживается GPU, geometry/tessellation/compute stages.
- Shadow rendering, shadow maps, deferred rendering и composite passes.
- Framebuffers, attachments, render targets, depth buffers/depth textures и Multiple Render Targets.
- Textures, samplers, descriptor sets, корректное управление ресурсами.
- Blend modes, depth testing, прозрачность, render state transitions, image layouts и GPU/CPU synchronization.
- Shaderpack-зависимые эффекты освещения, тумана, неба, облаков, погоды, воды и др.
- Корректный результат в игре, а не только успешная компиляция: без чёрных экранов, повреждённых текстур, неверных render passes и грубых артефактов.

### Интерфейс

Интегрированное меню Minecraft без конфликтов с VulkanMod:
- список и выбор shaderpacks;
- включение/отключение, переключение;
- чтение и настройка определённых авторами shaderpack параметров;
- профили качества, если предусмотрены;
- сброс и сохранение параметров между запусками;
- ясные сообщения об ошибках.

## Аудит и исправление существующей реализации

Найди и исправь **первопричины** незавершённых классов, заглушек/TODO, неверных Vulkan API calls, утечек RAM/VRAM/GPU-объектов, ошибок синхронизации и lifecycle, неправильного GLSL/SPIR-V, проблем загрузки, несовместимостей версий, вылетов и проблем производительности.

Не отключай сложные возможности лишь ради успешной сборки, не добавляй фиктивные реализации или тестовые заглушки вместо реальной работы. Приоритизируй критические сбои и сохраняй исправные компоненты. Следуй лицензиям Iris, VulkanMod и других зависимостей.

## Производительность и устойчивость

- Минимальный overhead Vulkan и отсутствие систематических GPU memory leaks.
- Кэширование компиляций и pipeline objects без ненужной перекомпиляции.
- Корректный resize, resource reload, смена миров/измерений/шейдерпаков.
- Проверка доступности GPU capabilities и корректная обработка неподдерживаемых функций.
- Отсутствие конфликтов с VulkanMod; измеряй FPS до заявлений об улучшении производительности.

## Матрица совместимости и тестирование

Проверь, насколько позволяют средства и лицензии:
- Complementary Reimagined;
- Complementary Unbound;
- BSL Shaders;
- Sildur's Vibrant Shaders;
- другие shaderpacks с отличающимися возможностями Iris.

Минимальные сценарии проверки:
1. Запуск Minecraft с VulkanMod + Veyra.
2. Меню шейдеров, обнаружение `.zip` и папок.
3. Выбор, включение, отключение и сохранение параметров.
4. Загрузка мира и визуальная проверка воды, теней, неба, освещения, прозрачности.
5. Смена shaderpacks, миров, измерений, resize и resource reload.
6. Отсутствие критических ошибок Vulkan validation, падений и утечек.
7. Gradle build, тесты и проверка собранного `.jar`.

Если нет GPU-окружения, делай доступные unit/integration/build/static проверки, но **не называй визуальную совместимость проверенной**. Веди матрицу с категориями: проверено реально / проверено автоматически / не проверено / известно неработающее.

## Сборка, интеграция веток и GitHub Release

После выполнения и проверки изменений:
1. Зафиксируй точные версии Minecraft, mod loader, Java, VulkanMod и требуемые зависимости.
2. Собери проект через Gradle; исправь ошибки компиляции, запусти доступные тесты.
3. Проверь состав и применимость итогового `.jar`.
4. Настрой GitHub Actions для повторяемой сборки.
5. Обнови README: установка, зависимости, использование и ограничения.
6. Подготовь CHANGELOG и таблицу проверенных shaderpacks.
7. Сохрани всю сделанную работу в понятных коммитах. **Слей готовые рабочие ветки** в целевую ветку репозитория с учётом правил защиты веток, без потери существующих изменений; разреши конфликты и повтори тесты после слияния.
8. Создай версионный Git tag, опубликуй **настоящий GitHub Release**, прикрепи проверенный `.jar`.
9. Используй уже доступную авторизацию GitHub. Не коммить секреты, токены, закрытые ключи или содержимое `.env`.
10. Если отсутствуют доступ, тестовое GPU-окружение или разрешение на публикацию, честно укажи блокер и выполни всё возможное без фиктивного релиза.

## Процесс работы агента

**Анализ → аудит и приоритизация → непосредственные изменения кода → сборка → тестирование → исправление → интеграция веток → выпуск релиза.**

Работай автономно и последовательно. Не останавливайся после плана. Решай задачи небольшими проверяемыми изменениями и обновляй `WORK_SUMMARY.md` после важных этапов, чтобы следующая сессия могла продолжить работу без потери контекста. Если один этап не выполним, документируй блокер и продолжай остальные независимые доступные этапы.

## Критерии готовности

- Проект успешно собирается.
- Shaderpack pipeline действительно интегрирован с VulkanMod.
- Работают обнаружение, загрузка, выбор и настройка шейдеров.
- Исправлены найденные критические ошибки.
- Выполнены доступные проверки стабильности и честно зафиксированы непроверенные сценарии.
- Совместимость и ограничения описаны без преувеличений.
- Рабочие ветки интегрированы, а проверенный `.jar` реально опубликован в GitHub Releases.

## Финальный отчёт

Предоставь:
- URL опубликованного GitHub Release и URL `.jar`;
- версии Minecraft / mod loader / VulkanMod;
- реализованные функции и исправленные дефекты;
- результаты Gradle, тестов и визуальной проверки;
- таблицу шейдерпаков с точным статусом;
- ограничения, оставшиеся проблемы и блокеры;
- коммиты, объединённые ветки и итоговый Git tag.

**Приоритет:** не прототип и не одна только аналитика, а реальный максимально полный и стабильный релиз на основе существующего кода Veyra. Не заявляй паритет с Iris или успешность выпуска без фактического подтверждения.

### Фактическое состояние реализации — 2026-10-21

Общий ресурс custom images подключён к обычной загрузке shaderpack: запись из shadow terrain → `shadowcomp` с фиксированными workGroups → чтение 3D-изображения в terrain. Синтетическая проверка на RTX 3070 прошла: 222 642 контрольных пикселя при включении и 0 после отключения. Сборка, 364 теста и проверка состава JAR прошли.

Следующий этап большой партии: shadow MRT, потребители custom images вне terrain/shadow, недостающие требования реальных паков, проверка смены измерения/пакета и освобождения ресурсов. Затем необходимы GPU-проверки реальных shaderpack и полный релизный прогон. PR #84 остаётся draft; новая версия не опубликована. Этот статус не сокращает исходную цель и не означает готовности стабильного релиза.

Результат полного feature-on аудита на том этапе: 75 случаев, 720 принятых и 144 отклонённых fragment-программы, все 18 compute stages компилируются. Включение custom images активировало ранее не проверявшиеся ветви Complementary. Приоритетные блокеры: sampler `tex` в shadow vertex и `floodfill_sampler`/`voxel_sampler` в basic/entity/block/hand/cloud/spidereyes. Этот снимок исторический; актуальные результаты смотри в `WORK_SUMMARY.md` и проверке PR #84 ниже. Во всех строках аудита `visualValidation=false`.
### Продолжение этапа custom images — 2026-10-21

Поддержка общей 3D image подключена к облачному проходу через существующий `Pipeline.Builder`: shadow terrain записывает voxel, `shadowcomp` обрабатывает общий image, clouds читают его и выдают диагностический сигнал. На RTX 3070 GPU-фикстура прошла: **52 440 active / 0 disabled**. Это исторический этап; позже добавлены другие custom-image потребители, shadow MRT и lifecycle интеграция.

Feature-on аудит того этапа: **738 accepted / 126 rejected** fragment-программ, 18 из 18 активных compute stages скомпилированы, 57 из 75 случаев без capability error. Результаты не являются текущими; см. актуальную запись ниже и `WORK_SUMMARY.md`.

### Актуальное состояние PR #84 — 2026-10-22

Исправления опубликованы в `feature/shadowcomp-compute`, commit `b598c4a`. PR #84 открыт в `main` и остаётся draft. Предыдущий точный head `f3bdcf1` прошёл Build и source audit, но Graphics run `39741773724` завершился шестью падениями: пять custom-image/entity fixture показали неподключённый core feature для vertex storage writes; `storage-buffer-shadow` показал прямую shaderc компиляцию SSBO на binding 0 вместо reserved binding 1024+. Эти ошибки исправлены локально и прошли 481 тест, source validation, пакетирование и RTX 3070 smoke; JAR прошёл локальный verifier, но не опубликован.

Для `b598c4a` Build run `39746059167` и source audit run `39746059527` завершились успешно. Graphics run `39746059275` остаётся в процессе из-за незавершённой matrix job; шесть jobs уже упали: пять с `VUID-RuntimeSpirv-NonWritable-06341`, одна — из-за stale binding 16 assertion. В feature worktree прошёл повторный локальный полный gate: 481 тест без ошибок, shader validation, build, packaging и graphics-smoke compile; beta.30-metadata candidate прошёл `verify_jar.py`. PR привязан к рабочему потоку и снова поставлен на автоматическое наблюдение. Следующий шаг — проверить новые gates и исправить возможные остаточные ошибки; после этого продолжить реальную проверку shaderpack и подготовку пакетного beta-релиза. До зелёных gates merge/release не выполняются; опубликованным остаётся beta.30. Корневая пользовательская ветка и её незакоммиченный исходный код не переключались.

### PR #84 после коммита a2dcbe8 — 2026-10-22

На ветку отправлен 2dcbe8. Локальный полный JDK 21 gate прошёл: 481 тест, shader validation, build, упаковка example packs и graphics-smoke compilation; локальный beta.30-metadata JAR прошёл verifier (949 400 bytes, SHA-256 F91D0AD5C4E0B0C90721AF894649591D8EC2227F3617CA3CE6AF7973FFC53905).
Свежие Build/source-audit выполняются, Graphics smoke ожидает запуска. Неразрешённый Lavapipe NonWritable-06341 остаётся блокером; PR draft, merge/release не выполнялись.

### Актуальное состояние PR #84 после f3fbb01 — 2026-10-22

Продолжена существующая ветка feature/shadowcomp-compute, проект не пересоздавался; текущий remote head PR #84 — f3fbb01390266d10d8c83af59adf1b87f2a0dd6c. На этом коммите Build run 39751853555 завершился успешно; source audit 39751853570 выполняется; Graphics smoke 39751853578 ожидает запуска. Дублирующий push-triggered Build 39751847066 также успешен. PR остаётся draft, T3-наблюдение активно.

Последний локальный JDK 21 gate прошёл: 481 тест без ошибок, 96 example shaders проверены, 112 Vulkan SPIR-V компиляций, build, packaging и graphics-smoke compilation. Новый candidate меняет передачу core features на копию VkDeviceCreateInfo с pEnabledFeatures и сохранённой VulkanMod pNext-цепочкой. Предыдущий exact-head Lavapipe запуск на a2dcbe8 показал четыре custom-image ошибки VUID-RuntimeSpirv-NonWritable-06341 и отдельный временный сбой разрешения Maven/Fabric зависимостей. Текущий fix ещё не подтверждён свежим Graphics CI. Не сливать и не публиковать release до зелёных exact-head gates и проверки оставшегося release batch.

### Продолжение после f3fbb01 — 2026-10-22

Проверены exact-head результаты f3fbb01: Build и source audit прошли; Graphics завершился 26/30 успешными fixtures. Четыре custom-image fixtures всё ещё падают с VUID-RuntimeSpirv-NonWritable-06341, хотя перед `vkCreateDevice` запрошенная feature отмечена как true. Для следующего запуска добавлена телеметрия результата native-вызова и созданного VkDevice; commit `1cde394` отправлен в тот же PR #84. Продолжай с этой ветки, используй новый exact-head graphics run, сопоставь native handle с handle из validation log и доведи feature activation до подтверждённого исправления. PR остаётся draft; merge, release и стабильный статус запрещены до прохождения всех gates, реальной проверки shaderpacks и итогового пакетного релиза.

### Актуальный следующий этап — 2026-10-22

Другой агент отправил коммит `1cde394` в draft PR #84. Продолжение сохраняет существующую ветку и реализацию. Исправлена отдельно обнаруженная ошибка компиляции `Sampler2` в entity shadow vertex shader; точечный тест и RTX 3070 smoke подтвердили исправление. Полный локальный JDK 21 gate прошёл (**482 теста**, 96 shader sources, 112 direct SPIR-V compilations), свежий pinned audit прошёл (75 строк, 864 geometry programs приняты, 18 compute stages скомпилированы), локальный JAR verifier прошёл.

Основной blocker остаётся открытым: Lavapipe CI сообщал `VUID-RuntimeSpirv-NonWritable-06341` для `vertexPipelineStoresAndAtomics`, хотя перед `vkCreateDevice` запрошено `true`. Добавленная post-call телеметрия ожидает свежего exact-head CI. PR #84 остаётся draft и наблюдаемым; до зелёного Vulkan CI и завершения оставшихся compatibility/runtime проверок не сливать ветку и не публиковать beta.30-метаданные как новый релиз. Целевая цель остаётся прежней: настоящий GitHub Release с новым версионированным, проверенным `.jar`.

### Обновление после push `a151219` — 2026-10-22

PR #84 открыт и остаётся draft. Новый commit `a151219298727112fb05571d77fb5643d5a5d9d5` исправляет `Sampler2` redefinition; локальные RTX 3070 smoke, полный JDK 21 gate и pinned audit прошли. На exact SHA Build `39756701229` и source audit `39756701453` выполняются, Graphics smoke `39756701219` в очереди. T3 watcher повторно включается после остановки. Блокирующий Lavapipe VUID `VUID-RuntimeSpirv-NonWritable-06341` ещё требует точного CI подтверждения; merge и релиз не выполнены.

### Продолжение после `c52cd3d` — 2026-10-22

Продолжена та же feature-ветка в существующем worktree: `feature/shadowcomp-compute` и PR #84, новый commit `c52cd3d` уже отправлен в origin. Корневой checkout `feature/block-entity-hand-vertex` с пользовательскими изменениями сохранён. Добавлена защита от кратковременного смешения scene framebuffer 10×10 с 800×450 geometry targets при смене измерения; добавлены 3 regression tests.

На RTX 3070 прошёл реальный Complementary Reimagined `COMPLEMENTARY` lifecycle smoke: resize, Nether, возврат, выключение/повторное включение и reload; `result.json` содержит все шесть lifecycle flags `true`, 360 000 non-black pixels, 54 color buckets, SSBO 810 549 248 bytes и пустой `error`. Полный локальный JDK 21 gate прошёл: 485 тестов, 96 shader examples, 112 Vulkan SPIR-V компиляций, build и packaging. Свежий pinned source audit принял 864 geometry fragments и скомпилировал все 18 активных compute stages. Beta.30-metadata JAR локально проверен, но не опубликован.

После push заново включён T3 watcher PR #84; результат exact-head CI ещё не обработан. Пока CI не подтвердит Vulkan/Lavapipe gates, не сливать PR и не выпускать новый jar. Следующий шаг — обработать watcher-событие, устранить любой остаточный CI blocker; после зелёных проверок слить feature-ветку и сделать единый release-prep batch с версией, README, compatibility matrix, CHANGELOG и проверенным GitHub Release. Stable parity для непроверенных shaderpacks не заявлять.

### Следующий локальный этап — Complementary Unbound и shader-pack textures — 2026-10-22

Продолжена та же ветка и PR #84. Поддержаны scoped `texture.<stage>.<sampler>` mappings, в том числе Complementary Unbound `texture.gbuffers.gaux4`; pack PNG загружаются безопасным shader asset reader с фильтрами из `.mcmeta`. Исправлена причина локального dimension/reload crash: если geometry pipeline запрашивает `shadowcolor0` до записи готовой shadow map, используется нейтральная texture, а зависящие от shadow post-проходы пропускаются до следующего корректного shadow pass.

На RTX 3070 прошёл Complementary Unbound r5.9.3 профиль `COMPLEMENTARY`: 360 000 non-black pixels / 54 color buckets; resize, Nether, возврат, disable, re-enable и resource reload подтверждены lifecycle flags; SSBO 810 549 248 bytes; прежняя ошибка `shadowcolor0` не повторилась. JDK 21 gate: 488 тестов без ошибок, 96 shader examples, 112 Vulkan SPIR-V, build и packaging успешны. Pinned source audit: 4 пака, 12 defaults, 63 profiles, 864 geometry fragments приняты, 18 compute stages скомпилированы; это source-only evidence, не visual certification.

Локальный beta.30-metadata JAR проверен (967 060 bytes, SHA-256 `187fab2313a758e25a74677c6b3069f1294a83b166f4bd42d05dd7de29199b7b`), но не опубликован и не подходит как новый release asset. Следующий шаг — отправить полный batch в PR #84 и дождаться exact-head Build/source-audit/Lavapipe gates через T3 watcher. Предыдущий Lavapipe `VUID-RuntimeSpirv-NonWritable-06341` не считать закрытым без зелёного нового CI. Merge/release ещё не выполнены; затем продолжить единый крупный release-prep batch.
### Отправка batch в PR #84 — `dcb31b0` — 2026-10-22

Полный scoped-texture/shadow-transition batch закоммичен как `dcb31b06560d68b4037152c2302c67543e1d4c08` и отправлен в `feature/shadowcomp-compute` / PR #84. Корневой checkout и его пользовательские изменения сохранены. Локальный JDK 21 gate, pinned source audit и Complementary Unbound `COMPLEMENTARY` lifecycle smoke прошли; эти результаты записаны в `WORK_SUMMARY.md` и `COMPATIBILITY.md` feature worktree.

PR остаётся draft. Теперь ожидаются exact-head Build, shaderpack source audit и Lavapipe graphics checks; предыдущий `VUID-RuntimeSpirv-NonWritable-06341` остаётся незакрытым до свежего зелёного CI. Новый beta/version/CHANGELOG/JAR/GitHub Release batch пока не публиковать. T3 PR watcher нужно включить для этого exact head.

### Продолжение release-prep — 2026-10-22

Проверил локальную и удалённую `feature/shadowcomp-compute`: обе указывают на `dcb31b06560d68b4037152c2302c67543e1d4c08`. PR #84 связан с текущим тредом и снова наблюдается T3 watcher. Hosted checks не запрашивались вручную.

Для крупного beta.31 batch создан отдельный локальный `release-prep/beta31` worktree от head PR. Там подготовлен version bump `0.2.0-beta.31+mc1.21.11` и draft документации; version/docs batch зафиксирован локально как `68967c9`, без push. Пользовательская корневая копия и feature head не менялись. Release workflow требует успешных source-audit и graphics-smoke jobs, а затем build/tests, shader validation, example-pack packaging и distributable verification до создания GitHub Release. Prep-ветка пока локальная, beta.31 JAR не собран и release не опубликован.

Следующий этап: дождаться T3-события для exact-head checks; при зелёных результатах слить PR #84, перенести release-prep на актуальный `main`, собрать и проверить versioned JAR, опубликовать beta.31 и сверить скачанный asset с checksum. Если Lavapipe gate снова покажет VUID, сначала исправить его на той же feature ветке.

### Versioned beta.31 gate — 2026-10-22

На отдельном `release-prep/beta31` commit `68967c9` локальный JDK 21 gate прошёл: 488 тестов без ошибок/пропусков, 96 example shaders, 112 Vulkan SPIR-V compilations, build, graphics-smoke compilation и example-pack packaging. Beta.31 JAR (`967 060 bytes`, SHA-256 `6322796300a54d0fcb91a898f593daf5cf7584e30cdf53b487b4b2d429f51d9e`) прошёл `scripts/verify_jar.py`. Это не доказывает удалённые PR gates и не является опубликованным asset. PR #84 остаётся watched; после зелёного exact-head CI нужны merge, финальная versioned release workflow, скачивание опубликованного JAR и сверка его checksum.

### Продолжение после push `de8eeac` — 2026-10-22

Remote `feature/shadowcomp-compute` подтверждён на `de8eeac98744505a9ccb5f3129d118fa05c2ca4f`; PR #84 открыт, draft, mergeable и T3 watcher наблюдает за новым head. Этот commit временно передаёт `pEnabledFeatures` через оригинальный `VkDeviceCreateInfo`, оставляет `pNext`-цепочку и восстанавливает исходный указатель после `vkCreateDevice`. Локальная JDK 21 проверка прошла, но Lavapipe `VUID-RuntimeSpirv-NonWritable-06341` считается открытым до exact-head Graphics результата. `release-prep/beta31-integrated` пока основан на `b7e8c62`. Дальше: дождаться watcher, при зелёных required gates слить PR и выполнить крупный beta.31 release workflow на итоговом `main`; при красном Graphics — исправить Vulkan feature activation в этой же ветке. Ничего не публиковать до прохождения gate и сверки финального `.jar`.

### Сводный beta.31 candidate — 2026-10-22

Ветка `release-prep/beta31-release-batch` объединяет подготовку beta.31, текущий PR head `de8eeac` и entity-texture commit `b7ebd44`; она запушена как draft stacked PR #85 поверх PR #84. Текущий head #85 — `91570ff`; после docs-only push полный JDK 21 gate, pinned audit и JAR verifier повторно прошли на этом exact local tree: 502 теста, 97 shader examples, 113 Vulkan SPIR-V compilations, 864 geometry fragments и 18 compute stages приняты, artifact 982,523 bytes с SHA-256 `653C3B4E3AC0667A11D43006FCBC39F3299937044A6565AE8F2E4AA8A8AA6728`. RTX 3070 entity-texture fixture дал 18,293 активных и 0 отключённых пикселей, sampler `marker` привязан к pack PNG на binding 6; custom-image compute fixture дал 222,994 активных magenta-пикселя. GPU smoke без validation layer не закрывает Lavapipe VUID. PR #84 и #85 наблюдаются watcher; далее дождаться exact-head Graphics результата, затем при успешных gates слить стек и выпустить beta.31 из финального `main` с проверкой скачанного `.jar`.
