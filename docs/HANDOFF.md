# Что сделано и что осталось

Документ для того, кто продолжит перенос. Читать после [MIGRATION.md](MIGRATION.md)
(что переносим и почему) и [PLAN.md](PLAN.md) (разбивка по этапам). Разбор оригинала —
в [analysis/](analysis/), там же протоколы всех интеграций со ссылками на файлы и строки.

Оригинал лежит рядом: `E:\Projects\quark`, коммит `bd61c35`. Он не трогается.

---

## 1. Как собрать и запустить

Тулчейн не зарегистрирован в системе — в PATH по умолчанию только JRE 8, и без этой
строки сборка падает сразу:

```bash
source /e/Projects/.tools/env.sh
```

Дальше обычно:

```bash
./gradlew build          # сборка и тесты
./gradlew :app:run       # запустить плеер
./gradlew :app:jvmTest   # среди прочего перерисует превью в app/build/preview
```

Две особенности окружения, обе уже учтены в репозитории:

* **Maven Central рвёт TLS-handshake именно с JDK** («Remote host terminated the
  handshake»), хотя curl на тот же URL отдаёт 200. Лечится `systemProp.https.protocols=TLSv1.2`
  в `gradle.properties`. Если сборка вдруг переедет в другую сеть — строку можно убрать.
* **libmpv весит 115 МБ и в git не лежит.** Таска `:app:fetchMpv` качает официальную сборку
  и `7zr.exe` (архив использует фильтр BCJ2, который не берут ни py7zr, ни commons-compress),
  распаковывает `libmpv-2.dll` в `app/resources/windows-x64/`. `run` и упаковка от неё зависят,
  так что свежий клон заводится сам. Контрольные суммы закреплены в `app/build.gradle.kts`.

---

## 2. Модули и правила

| Модуль | Что внутри | Зависит от |
| --- | --- | --- |
| `core` | Доменные модели, очередь, shuffle, настройки, LRC, палитра, `TimedCache`. Без IO. | — |
| `data` | SQLDelight, репозитории, чтение тегов, сканер, дисковый кеш обложек, хранилище настроек. | `core` |
| `network` | Ktor-клиенты. Сейчас только Яндекс. | `core` |
| `player` | `AudioEngine`, `PlayerController`, биндинг libmpv. | `core` |
| `platform` | `AppDirs`. Сюда же пойдут SMTC/MPRIS. | `core` |
| `app` | Compose-интерфейс, view-модели, склейка. | всё |

`core`, `data`, `network`, `player` держат код в `commonMain` и трогают платформу только
через `expect`/`actual` — добавление Android или iOS не потребует переносить файлы.
`platform` и `app` намеренно JVM-ные.

**Правила, которые стоит соблюдать дальше.**

* **Никакого Material.** `MaterialTheme`, `Button`, `Slider`, `Card` не используются:
  они тянут свою палитру, формы и ripple. Есть `QuarkTheme` с токенами и свои контролы
  в `app/ui/`. Иконки берутся из `material-icons-extended`, потому что оригинал сам
  использует `material_symbols_icons`, и это только векторные контуры.
* **Стекло — через `GlassSurface`**, три рецепта в `Glass.Panel` / `Glass.Card` /
  `Glass.Dialog`. Откуда взяты значения — в [decisions/02-visual-language.md](decisions/02-visual-language.md).
* **Экраны не знают про view-модели.** `PlayerScreen` принимает интерфейс `PlayerUi`,
  `LyricsScreen` — готовое состояние. Благодаря этому их рисует `UiPreviewTest` без движка
  и без базы. Новые экраны делать так же.
* **Состояние — один `StateFlow` на область.** Не плодить по нотификатору на поле, как было
  в оригинале (38 штук в одном сервисе).
* **Комментарии объясняют «почему», а не «что».** Если поведение повторяет оригинал или
  намеренно от него отходит — сказать об этом и сослаться на файл и строку в Dart.
* **Тесты там, где есть логика.** Сейчас 75. Чистые вещи (`core`) покрыты, сеть — через
  Ktor `MockEngine`, движок — против настоящего libmpv.

---

## 3. Что уже работает

**Воспроизведение.** libmpv через свой JNA-биндинг (`player/src/jvmMain/.../mpv/`).
Gapless настоящий: следующий трек отдаётся mpv через `loadfile … append`, пока текущий
играет, а переход распознаётся по росту `playlist-pos`. Поэтому **завершение трека и
переключение — разные пути**: на `EngineEvent.Completed` контроллер только догоняет
состояние и предзагружает следующий, ничего не открывая. 12 тестов, из них 4 против
настоящей библиотеки.

**Очередь и порядок.** `core/player/PlaybackQueue.kt` — порт `_getNext`/`_getPrevious`/
`playCustom` без IO, три режима shuffle, 20 тестов.

**Хранилище.** Схема SQLDelight совпадает с Drift колонка в колонку, существующий
`quark.db` открывается без миграции (`data/src/jvmMain/.../db/DatabaseFactory.kt`).
Репозитории треков, плейлистов и статистики.

**Настройки.** Одна модель `core/settings/Settings.kt`, атомарная запись в json,
битый файл откладывается в `.broken`.

**Яндекс.Музыка.** Аккаунт, плейлисты, треки, альбомы, артисты, поиск, подписанные
ссылки на файл, тексты, «Моя волна» (сессия, дозагрузка, фидбек). Подписи покрыты тестами
против эталонов, посчитанных независимо. Вход — через системный браузер и вставку адреса
или токена; почему не автоматом, написано в `network/yandex/YandexOAuth.kt`.

**Локальная библиотека.** Теги через jaudiotagger, обход папок с прогрессом.

**Интерфейс.** Стартовый экран, плеер в трёх раскладках по размеру окна, панель плейлиста
с поиском, тексты с подсветкой строки, настройки, визуализатор «Моей волны» на SkSL,
`CometLoader`. Обложки декодируются через Skia, из них же берутся акцентные цвета.

---

## 4. Что осталось

Порядок внутри разделов — по убыванию пользы.

### 4.1. Довести библиотеку и воспроизведение

| Задача | Куда | Откуда брать |
| --- | --- | --- |
| **Восстановление сессии при запуске.** `Settings.memory` (последний трек, позиция, плейлист) смоделирован, но никто его не пишет и не читает. | `app/QuarkApp.kt`, `app/player/PlayerViewModel.kt` | `database.dart:261-310` (`_LastTrackPositionSaver`, порог 2 сек, запись раз в 15 сек) |
| **Логирование прослушиваний.** `ListenStatsRepository` готов, но `controller.trackChanges` никуда не подписан. Писать, если играл больше 10 секунд. | новый `app/stats/ListenLogger.kt` | `services/database/listen_logger.dart` |
| **Сохранение плейлистов в БД.** Репозиторий есть, приложение им не пользуется — открытая папка живёт только в памяти. | `app/player/PlayerViewModel.kt` | — |
| **Кеширование стримов на диск.** `PlaybackSettings.cacheRemoteTracks` не действует. Нужен аналог `NetConductor`: окно −1/0/+1 вокруг текущего трека, пул на 8 загрузок, 3 попытки с экспоненциальной задержкой. | новый `data/net/TrackCacher.kt` | `services/player/net_player.dart` |
| **CUE.** Формат простой, парсер свой. | `core/cue/CueSheet.kt` + тесты | замена `dart_cue` |
| **Слежение за папками.** `java.nio.file.WatchService`. Включается `LibrarySettings.watchFolders`. | `data/local/DirectoryObserver.kt` | `services/directory_observer.dart` |
| **Запись тегов.** | `data/local/TagWriter.kt` | `services/audio_tags/tag_writer.dart` |
| **Цвета обложек в БД.** Таблица `cover_colors` в схеме есть, репозитория нет — палитра считается заново при каждом запуске. | `data/repository/CoverColorRepository.kt` | `drift_library_engine.dart:112-131` |

### 4.2. Остальные источники

| Задача | Куда | Протокол |
| --- | --- | --- |
| **YouTube Music** через `quarkaudio.ru`: поиск, ссылка на трек, плейлисты по cookie-файлу. | `network/ytmusic/YtMusicClient.kt` | [analysis/02](analysis/02-storage-network-platform.md) §5.2 |
| **MusicBrainz + Cover Art Archive.** | `network/musicbrainz/` | analysis/02 §5.3 |
| **Распознавание трека** (`/api/quark/recognizer/recognize`, multipart). | `network/recognizer/` | analysis/02 §5.3 |

`YtMusicTrack` в домене уже есть, и `QuarkSourceResolver` умеет отдать его `streamUrl` —
не хватает только клиента, который этот url добудет.

### 4.3. Системные интеграции

Ничего из этого пока нет; всё — в `platform`, кроме локального API.

| Задача | Как | Протокол |
| --- | --- | --- |
| **Discord RPC.** Самое простое из списка: JSON поверх named pipe `\\.\pipe\discord-ipc-0` на Windows и unix-сокета `/tmp/discord-ipc-0`. App id `1520321415595954247`. | `platform/.../discord/DiscordRpc.kt` | analysis/02 §5.4, поля активности перечислены |
| **Локальное API.** HTTP + WebSocket на Ktor Server, случайный порт, запись его в `AppDirs.portFile`. **Контракт v0 ломать нельзя** — у него есть внешние клиенты. | `network/localapi/LocalApiServer.kt` | `E:\Projects\quark\lib\services\local_api\README.md` целиком, плюс analysis/02 §5.5 |
| **mDNS.** jmdns, имя `quark`, тип `_quarkaudio._tcp`, атрибуты `{version: 0, path: /api}`. | `network/localapi/ServiceBroadcast.kt` | analysis/02 §5.5 |
| **MPRIS2 (Linux).** `dbus-java`, интерфейсы `org.mpris.MediaPlayer2` и `…MediaPlayer2.Player`. | `platform/.../linux/Mpris.kt` | analysis/02 §6.1 |
| **SMTC (Windows).** Самое тяжёлое: WinRT через JNA или Panama. В оригинале это делал Rust-мост. | `platform/.../windows/Smtc.kt` | analysis/02 §6.1 |
| **Drag-and-drop файлов в окно.** `java.awt.dnd.DropTarget`, `DataFlavor.javaFileListFlavor`. | `app/DragAndDrop.kt` | `widgets/drag_drop.dart` |

Фасад над SMTC/MPRIS стоит свести к одному интерфейсу (`platform/NativeControls.kt`),
который подписывается на `PlayerController.trackChanges` и `state`.

### 4.4. Экраны

| Задача | Замечания |
| --- | --- |
| **Очередь.** Панель есть, содержимое очереди в ней не показано и не переставляется мышью. | `state.queue` уже в `PlayerState` |
| **«Моя волна».** Клиент готов (`startWave`, `waveTracks`, `sendWaveFeedback`), визуализатор готов — нет экрана и подкачки треков в очередь по мере проигрывания. Фидбек (`trackStarted`, `trackFinished`, `skip`) слать обязательно, иначе волна начинает повторяться. | `network/yandex/YandexMusic.kt` |
| **Альбом и артист.** Данные в клиенте есть (`album`, `artist`, `artistTracks`, `artistAlbums`). | в оригинале — `widgets/media_cards/` |
| **Поиск по Яндексу.** Сейчас поиск фильтрует только открытый плейлист. Включается `YandexSettings.searchEnabled`. | `api.search` готов |
| **Статистика прослушиваний.** `ListenStatsRepository.topTracks` и `totalSeconds` готовы. | `widgets/listen_stats/` |
| **Увеличение обложки** по клику. | `main_player.dart`, `toggleCover` |
| **Хром окна.** Оригинал на Linux подменяет `GtkHeaderBar` и красит его градиентом из обложки, на Windows включает тёмный заголовок через `DwmSetWindowAttribute`. В Compose разумнее `undecorated = true` и свой заголовок; тёмная рамка на Windows — JNA-вызов `dwmapi.dll`. | analysis/02 §6.2–6.3 |

### 4.5. Дистрибутив

* `jpackage` уже настроен на MSI и deb, но нет иконок и метаданных.
* `fetchMpv` качает только Windows-сборку. Для Linux ожидается системный `libmpv.so.2`
  (загрузчик его найдёт), для macOS — либо brew, либо класть `libmpv.dylib` в
  `app/resources/macos-*`.
* **Перенос данных из Flutter-установки.** `AppDirs.legacySupport` и `legacyCache` уже
  указывают на старые каталоги, но ими никто не пользуется. `quark.db` открывается как есть,
  а вот настройки лежат в Hive — это собственный бинарный формат, и читать его придётся
  руками (кадры с CRC32) либо смириться с тем, что токен и последний плейлист пользователь
  введёт заново.

---

## 5. Грабли, на которые уже наступили

Это всё стоило времени; повторять не надо.

* **`mpv_observe_property` отдаёт текущее значение сразу после подписки.** Из-за этого
  движок считал себя играющим ещё до того, как кто-то подписался на события, и настоящий
  старт потом глушился дедупликацией. Вывод: не фильтровать повторы в `emitPlaying` —
  вниз по потоку `StateFlow`, он и так схлопывает.
* **`SharedFlow` без replay теряет событие, если подписчик не успел.** `onStart` не спасает:
  он выполняется **до** регистрации подписки. Нужен `onSubscription`. В `PlayerController`
  из-за этого появился `listening: CompletableDeferred`, который `start()` ждёт.
* **Очередь в оригинале ставит точку возврата даже когда очереди нет.** После клика по треку
  в плейлисте `_getNext()` возвращал тот же самый трек. С настоящим gapless это играло бы
  трек дважды; в порте точка возврата ставится только при открытой очереди.
* **SkSL — не GLSL.** `vec2/3/4` не существуют, цикл должен разворачиваться (счётчик `int`),
  вход — параметр `main`, выход — возврат `half4`. И главное: **ошибка в шейдере не ломает
  сборку**, `RuntimeEffect.makeForShader` просто вернёт null и визуализатор молча исчезнет.
  Поэтому есть `VibeShaderTest`.
* **Униформы шейдера лучше задавать по имени** через `RuntimeShaderBuilder`, а не паковать
  буфер: выравнивание `float3` легко перепутать, и проявится это только неправильными
  цветами.
* **`Modifier.blur` в Compose размывает сам компонент, а не фон под ним.** Backdrop-эффект
  здесь сделан выборкой известной картинки по координатам компонента — см. `app/ui/Glass.kt`.

Дефекты самого оригинала, которые сознательно не перенесены, перечислены
в [MIGRATION.md](MIGRATION.md) §8.

---

## 6. Быстрая карта кода

```
core/
  model/        Track (sealed), Playlist, Cover
  player/       PlaybackQueue, Shuffles, PlayerState
  settings/     Settings, SettingsStore
  lyrics/       LrcParser
  color/        AccentPalette
  util/         TimedCache
data/
  db/           DatabaseFactory  (+ схема в src/commonMain/sqldelight)
  repository/   TrackRepository, PlaylistRepository, ListenStatsRepository
  local/        TagReader, LibraryScanner
  images/       CoverCache
  settings/     JsonSettingsStore
network/
  yandex/       YandexClient, YandexMusic, YandexSignatures, YandexOAuth, dto/
player/
  AudioEngine, PlayerController
  mpv/          LibMpv (JNA), MpvHandle, MpvLibraryLoader
  MpvAudioEngine
platform/
  AppDirs
app/
  QuarkApp          склейка всего
  QuarkSourceResolver
  theme/            QuarkTheme, Glass, токены
  ui/               GlassSurface, CircleButton, ThinSlider, CometLoader
  image/            CoverLoader, CoverBlur
  player/           PlayerUi, PlayerViewModel, PlayerScreen
  yandex/           YandexSession, YandexViewModel, YandexScreen
  lyrics/           LyricsViewModel, LyricsScreen
  settings/         SettingsScreen
  vibe/             VibeShader (SkSL), VibeAnimation
```
