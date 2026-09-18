# Комплексный технический анализ архитектуры, доменных моделей и аудио-движка Quark

---

## 1. АРХИТЕКТУРА

### 1.1. Порядок инициализации в [`lib/main.dart`](file:///E:/Projects/quark/lib/main.dart)

Точка входа [`main()`](file:///E:/Projects/quark/lib/main.dart#L36-L62) выполняет строго синхронно-асинхронную цепочку предстартовой загрузки:

1. [`WidgetsFlutterBinding.ensureInitialized()`](file:///E:/Projects/quark/lib/main.dart#L37) — привязка биндингов Flutter.
2. [`await ApplicationDirectories().init()`](file:///E:/Projects/quark/lib/main.dart#L38) — инициализация путей к директориям кэша, саппорта и документов через `path_provider` ([`services/files.dart#L21-L26`](file:///E:/Projects/quark/lib/services/files.dart#L21-L26)).
3. **Блок `try/catch` со скрытым подавлением ошибок** ([`lib/main.dart#L39-L45`](file:///E:/Projects/quark/lib/main.dart#L39-L45)):
   - `Hive.init(directory.path)` — регистрация пути к Hive.
   - [`await Database.init()`](file:///E:/Projects/quark/lib/main.dart#L43) — открытие Hive-бокса `'database'` ([`hive_settings_engine.dart#L151`](file:///E:/Projects/quark/lib/services/database/hive_settings_engine.dart#L151)) и запуск [`DatabaseSaver().init()`](file:///E:/Projects/quark/lib/services/database/hive_settings_engine.dart#L168).
   - [`await DatabaseStreamerService().init()`](file:///E:/Projects/quark/lib/main.dart#L44) — вычитывание всех ключей из Hive в 34 `ValueNotifier`, привязка сохраняющих листенеров (`_attachSavers`) и подписка на громкость плеера ([`database.dart#L16-L20`](file:///E:/Projects/quark/lib/services/database/database.dart#L16-L20)).
   - *Внимание:* Любое исключение здесь глушится пустым блоком `catch (e) {}` на строке 45, что при сбое диска оставляет базу в невалидном состоянии.
4. [`AccentColorService().init()`](file:///E:/Projects/quark/lib/main.dart#L46) — инициализация цветовой схемы.
5. [`Player.player.init(...)`](file:///E:/Projects/quark/lib/main.dart#L47-L51) — конфигурация бэкенда (`PlayerBackend.audioPlayers` либо `PlayerBackend.justAudioMediaKit`) на основе сохраненного значения `DatabaseStreamerService().playerBackend.value`.
6. [`NativeControl().init()`](file:///E:/Projects/quark/lib/main.dart#L52) — системные контролы: на Windows инициализирует Rust FFI через `RustLib.init()` и `SMTCWindows` ([`native_control.dart#L71-L88`](file:///E:/Projects/quark/lib/services/native_controls/native_control.dart#L71-L88)), подписывается на смену трека и статус воспроизведения.
7. [`AudioServiceMpris.registerWith()`](file:///E:/Projects/quark/lib/main.dart#L53) — регистрация протокола MPRIS для Linux.
8. [`DynamicWindowColor.init()`](file:///E:/Projects/quark/lib/main.dart#L54) — интеграция динамического цвета заголовка окна Linux.
9. [`LocalApi().init()`](file:///E:/Projects/quark/lib/main.dart#L55) — запуск локального HTTP/WebSocket API для удаленного управления Quark.
10. [`ListenLogger().init()`](file:///E:/Projects/quark/lib/main.dart#L56) — регистрация листенеров на `trackChangeNotifier`, `durationNotifier`, `playedNotifier` для трекинга прослушиваний.
11. [`AppDatabase().init()`](file:///E:/Projects/quark/lib/main.dart#L57) — инициализация реляционной SQLite БД на Drift ([`drift_library_engine.dart#L234-L237`](file:///E:/Projects/quark/lib/services/database/drift_library_engine.dart#L234-L237)), предзагрузка хэшей путей известных треков `_knownPaths`.
12. [`DiscordRpcManager().init()`](file:///E:/Projects/quark/lib/main.dart#L59) — опциональный запуск Discord Rich Presence, если включен флаг в `DatabaseStreamerService`.
13. [`runApp(const Quark())`](file:///E:/Projects/quark/lib/main.dart#L61) — запуск дерева виджетов.

---

### 1.2. Навигация и отображение страниц

В проекте **отсутствует декларативный роутинг** (нет Navigator 2.0 / GoRouter). Навигация разделена на два антипаттерна:

1. **Модальные оверлеи через булевы флаги внутри `MainPage`** ([`lib/main.dart#L93-L102`](file:///E:/Projects/quark/lib/main.dart#L93-L102)):
   - `loginView` (авторизация Яндекс.Музыки) -> [`lib/main.dart#L642`](file:///E:/Projects/quark/lib/main.dart#L642).
   - `playlistView` (список плейлистов Яндекса) -> [`lib/main.dart#L673`](file:///E:/Projects/quark/lib/main.dart#L673).
   - `dragAndDropView` (импорт cookie YT Music) -> [`lib/main.dart#L736`](file:///E:/Projects/quark/lib/main.dart#L736).
   - `settingsView` (экран настроек) -> [`lib/main.dart#L777`](file:///E:/Projects/quark/lib/main.dart#L777).
   - `isFileLoading` (индикатор CometLoader) -> [`lib/main.dart#L806`](file:///E:/Projects/quark/lib/main.dart#L806).
   Все оверлеи рендерятся в [`Stack`](file:///E:/Projects/quark/lib/main.dart#L604) через цепочку `AnimatedSwitcher`.
2. **Императивный переход к экрану плеера**:
   - В функциях `playlistRoute` ([`lib/main.dart#L238-L244`](file:///E:/Projects/quark/lib/main.dart#L238-L244)) и `_onYTMusicPlaylistSelected` ([`lib/main.dart#L324-L327`](file:///E:/Projects/quark/lib/main.dart#L324-L327)) вызывается:
     ```dart
     Navigator.push(context, CupertinoPageRoute(settings: RouteSettings(name: "/player"), builder: (context) => PlaylistPage()));
     ```
3. **Адаптивный роутер внутри [`PlaylistPage`](file:///E:/Projects/quark/lib/playlist_page_router.dart#L51-L68)**:
   Вместо адаптивных layout-компонентов используется жесткая подмена корневого виджета через `AnimatedSwitcher` на основе `MediaQuery`:
   - `Platform.isAndroid && orientation == portrait` -> [`AndroidWidget()`](file:///E:/Projects/quark/lib/widgets/players_widgets/android_player.dart).
   - `size.height < 80` -> [`MacroPlayer()`](file:///E:/Projects/quark/lib/widgets/players_widgets/macro_player.dart) (микрополоска управления).
   - `size.height <= 300` -> [`MiniPlayerWidget()`](file:///E:/Projects/quark/lib/widgets/players_widgets/mini_player.dart) (компактная панель).
   - Иначе -> [`MainPlayer()`](file:///E:/Projects/quark/lib/widgets/players_widgets/main_player.dart) (полноразмерный десктопный плеер).

---

### 1.3. Слои и реальное управление состоянием

Проект номинально разделен на каталоги:
- `lib/objects/` — доменные сущности (`track.dart`, `playlist.dart`, `cover.dart`).
- `lib/services/` — инфраструктура (аудио-бэкенд, работа с БД, интеграции API, кэш картинок).
- `lib/widgets/` — UI-компоненты.

**Истинное состояние стейт-менеджмента:**
- Зависимость `provider: ^6.1.5+1` в [`pubspec.yaml#L40`](file:///E:/Projects/quark/pubspec.yaml#L40) является **мертвой** — во всем проекте `package:provider` ни разу не импортируется.
- Состояние построено исключительно на **глобальных синглтонах с `ValueNotifier<T>` и ручных подписках через `addListener`/`setState`**.
- Внутри виджетов (например, [`_MainPlayerState`](file:///E:/Projects/quark/lib/widgets/players_widgets/main_player.dart#L46-L130), [`_MainPageState`](file:///E:/Projects/quark/lib/main.dart#L92-L108)) объявлены десятки локальных булевых флагов. В `initState` вручную навешиваются слушатели на синглтоны (например, [`playerListeners()`](file:///E:/Projects/quark/lib/widgets/players_widgets/main_player.dart#L374-L405), [`subscribeDatabase()`](file:///E:/Projects/quark/lib/widgets/players_widgets/main_player.dart#L355-L361)), вызывающие `setState()`, а в `dispose` они вручную отписываются.
- Из-за ручной отписки и разрозненного состояния регулярны утечки памяти и вызовы `setState()` на размонтированных виджетах.

---

### 1.4. Потоки данных: Player <-> Database <-> UI

```
┌─────────────────────────────────────────────────────────────┐
│                    UI Layer (Widgets)                       │
│  - MainPlayer / MiniPlayer / PlaylistWidget / MainPage      │
└──────────────▲───────────────────────────────▲──────────────┘
               │ ValueNotifier.addListener     │ setState / bind
               ▼                               ▼
┌──────────────────────────────┐  ┌───────────────────────────┐
│     Player (Singleton)       │  │  DatabaseStreamerService  │
│ - trackNotifier              │  │  - 34 ValueNotifiers      │
│ - playedNotifier             │  │  (volume, tokens, etc.)   │
│ - playlistNotifier           │  └─────────────┬─────────────┘
│ - queueNotifier              │                │ Database.put()
└──────────────┬───────────────┘                ▼
               │                          ┌───────────┐
               │                          │ Hive Box  │
               │                          └───────────┘
               ▼
┌──────────────────────────────┐
│  DatabaseSaver / Logger      │
│  - saveTracks() / saveStats()│
└──────────────┬───────────────┘
               ▼
┌──────────────────────────────┐
│    AppDatabase (Drift)       │
│  - SQLite (quark.db)         │
└──────────────────────────────┘
```

1. **Player -> Database**:
   - [`DatabaseSaver`](file:///E:/Projects/quark/lib/services/database/database.dart#L209-L259) слушает `Player.player.trackChangeNotifier` и `playlistNotifier`. При смене трека записывает путь в `DatabaseStreamerService().lastTrack`, сериализует текущий плейлист в Hive (`lastPlaylist`), и сбрасывает треки в SQLite через [`AppDatabase().saveTracks(...)`](file:///E:/Projects/quark/lib/services/database/database.dart#L257).
   - [`_LastTrackPositionSaver`](file:///E:/Projects/quark/lib/services/database/database.dart#L261-L310) слушает `playedNotifier`, фильтрует тики (порог 2 сек) и каждые 15 сек сохраняет позицию воспроизведения в Hive.
   - [`ListenLogger`](file:///E:/Projects/quark/lib/services/database/listen_logger.dart#L15-L94) слушает `trackChangeNotifier`: если трек играл более 10 секунд (`tps > 10`), формирует [`PlayerTrackStat`](file:///E:/Projects/quark/lib/services/database/listen_logger.dart#L85-L91) и делает insert в таблицу `listenStats` SQLite через `AppDatabase().saveStats`.
2. **Database -> Player**:
   - `DatabaseStreamerService._attachListeners()` ([`database.dart#L202-L206`](file:///E:/Projects/quark/lib/services/database/database.dart#L202-L206)) и `_databaseBootStrap()` ([`main.dart#L401-L410`](file:///E:/Projects/quark/lib/main.dart#L401-L410)) синхронизируют сохраненные значения громкости, токенов и последнего трека обратно в `Player.player`.

---

### 1.5. God-Objects, скрытые глобальные синглтоны и циклические зависимости

#### Реестр скрытых синглтонов:
1. [`Player.player`](file:///E:/Projects/quark/lib/services/player/player.dart#L460) — **God-Object** (управление движком, списки, две очереди, три режима шаффла, 10 `ValueNotifier`, вызовы UI-уведомлений).
2. [`_PlayerEngine._instance`](file:///E:/Projects/quark/lib/services/player/player.dart#L84) — приватный синглтон-мост к низкоуровневым плеерам.
3. [`DatabaseStreamerService._instance`](file:///E:/Projects/quark/lib/services/database/database.dart#L10) — **God-Object** настроек (хранит 34 глобальных `ValueNotifier`).
4. [`DatabaseSaver._instance`](file:///E:/Projects/quark/lib/services/database/database.dart#L210) и [`_LastTrackPositionSaver._instance`](file:///E:/Projects/quark/lib/services/database/database.dart#L263) — неявные фоновые демоны-наблюдатели.
5. [`Database`](file:///E:/Projects/quark/lib/services/database/hive_settings_engine.dart#L132) — статический класс со статическим `Box? _box`.
6. [`AppDatabase._instance`](file:///E:/Projects/quark/lib/services/database/drift_library_engine.dart#L194) — синглтон Drift SQLite.
7. [`YandexMusicSingleton`](file:///E:/Projects/quark/lib/services/yandex_music/yandex_music_singleton.dart#L22) — **God-Object** со статическими полями (12 статических `Map`-кэшей, статический инстанс API, 3 статических `ValueNotifier`).
8. [`NetConductor._singleton`](file:///E:/Projects/quark/lib/services/player/net_player.dart#L16) — фоновый кэшер аудиотреков на базе `pool`.
9. [`ListenLogger._instance`](file:///E:/Projects/quark/lib/services/database/listen_logger.dart#L16) — сборщик аналитики прослушиваний.
10. [`ApplicationDirectories._instance`](file:///E:/Projects/quark/lib/services/files.dart#L14) — глобальный поставщик путей к ФС.
11. [`NativeControl._instance`](file:///E:/Projects/quark/lib/services/native_controls/native_control.dart#L16) — системные уведомления и медиа-кнопки.
12. [`YTMusicAPI._instance`](file:///E:/Projects/quark/lib/services/youtube_music/ytmusic_services.dart#L11) — клиент к внешнему прокси YouTube.

#### Критические циклические зависимости:
1. **[`services/player/player.dart`](file:///E:/Projects/quark/lib/services/player/player.dart#L11) <---> [`services/database/database.dart`](file:///E:/Projects/quark/lib/services/database/database.dart#L6)**:
   - `player.dart` импортирует `database.dart` и обращается к `DatabaseStreamerService().playerBackend.value` ([`player.dart#L511`](file:///E:/Projects/quark/lib/services/player/player.dart#L511)).
   - `database.dart` импортирует `player.dart` и в `_attachListeners`, `DatabaseSaver` подписывается на `Player.player.*` ([`database.dart#L203-L205`](file:///E:/Projects/quark/lib/services/database/database.dart#L203-L205), [`database.dart#L230-L231`](file:///E:/Projects/quark/lib/services/database/database.dart#L230-L231)).
2. **[`objects/track.dart`](file:///E:/Projects/quark/lib/objects/track.dart#L9) <---> [`widgets/media_cards/album.dart`](file:///E:/Projects/quark/lib/widgets/media_cards/album.dart#L11)** (Грубое нарушение слоев):
   - Доменная сущность `PlayerTrack` импортирует UI-виджет `album.dart`, так как метод `Future<AlbumInfor?> getAlbumInfo()` ([`track.dart#L118`](file:///E:/Projects/quark/lib/objects/track.dart#L118)) возвращает тип `AlbumInfor`, объявленный в UI-слое ([`album.dart#L44`](file:///E:/Projects/quark/lib/widgets/media_cards/album.dart#L44)).
   - Виджет `album.dart` импортирует `track.dart`.
3. **[`objects/track.dart`](file:///E:/Projects/quark/lib/objects/track.dart#L4-L8) ---> [`services/yandex_music/yandex_music_singleton.dart`](file:///E:/Projects/quark/lib/services/yandex_music/yandex_music_singleton.dart#L11)**:
   - Доменная модель знает о синглтонах API и базе: `YandexMusicTrack` вызывает `YandexMusicSingleton.instance.tracks.getDownloadLink(...)` ([`track.dart#L327`](file:///E:/Projects/quark/lib/objects/track.dart#L327)), а `YTMusicTrack` вызывает `YTMusicAPI().getTrack(...)` ([`track.dart#L540`](file:///E:/Projects/quark/lib/objects/track.dart#L540)).
   - В свою очередь `yandex_music_singleton.dart` импортирует `objects/track.dart`.
4. **[`services/yandex_music/yandex_music_singleton.dart`](file:///E:/Projects/quark/lib/services/yandex_music/yandex_music_singleton.dart#L15-L16) <---> [`services/player/net_player.dart`](file:///E:/Projects/quark/lib/services/player/net_player.dart#L7)**:
   - `yandex_music_singleton.dart` в `init()` вызывает `NetConductor().init(...)` ([`yandex_music_singleton.dart#L57`](file:///E:/Projects/quark/lib/services/yandex_music/yandex_music_singleton.dart#L57)), а `net_player.dart` напрямую работает с `YandexMusicTrack` и вызывает методы загрузки треков.

---

## 2. ДОМЕННЫЕ МОДЕЛИ

### 2.1. Иерархия треков: [`lib/objects/track.dart`](file:///E:/Projects/quark/lib/objects/track.dart)

Базовый класс [`PlayerTrack`](file:///E:/Projects/quark/lib/objects/track.dart#L106-L152):
- **Поля:**
  - `String title` (название)
  - `List<String> artists` (список исполнителей)
  - `List<String> albums` (список альбомов)
  - `String filepath` (локальный путь к файлу или кэшированному треку)
  - `String cover` (строковый путь к обложке или URL, дефолт `'none'`)
  - `Uint8List coverByted` (сырые байты обложки из ID3-тегов, дефолт `Uint8List(0)`)
  - `CoverType coverType` (enum: `.url`, `.builtIn`, `.externalFile`, `.noCover`)
  - `bool canNavigateToArtist`, `bool canNavigateToAlbum` (флаги доступности переходов)
- **Контракт:**
  - `Future<dynamic> getArtistInfo(int artistNumber)`
  - `Future<AlbumInfor?> getAlbumInfo()`
  - `String getImage({CoverQuality quality})`
  - `Future<PlayableInfo> getPlayableInfo()`

#### Наследники:
1. [`LocalTrack`](file:///E:/Projects/quark/lib/objects/track.dart#L154-L209):
   - Локальный трек с диска. `getAlbumInfo()` и `getArtistInfo()` возвращают `null`.
   - `getPlayableInfo()` возвращает `PlayableInfo(type: .localFile, path: filepath)`.
2. [`YandexMusicTrack`](file:///E:/Projects/quark/lib/objects/track.dart#L211-L340):
   - Хранит SDK-объект `final Track track` из внешней библиотеки `yandex_music`.
   - Путь `filepath` формируется в локальный кэш через [`getTrackPath(track.id)`](file:///E:/Projects/quark/lib/objects/track.dart#L342-L344) (`.../audio_cache/yandex_music/cisum_xednay_krauq<id>.flac`).
   - `getPlayableInfo()`: проверяет наличие скачанного файла на диске через `File(filepath).exists()`. Если есть — отдает `PlaySourceType.localFile`. Если нет — запрашивает ссылку через [`_fetchAndCacheStreamUrl()`](file:///E:/Projects/quark/lib/objects/track.dart#L325-L339) с временем жизни 30 минут ([`TimedCache<String>`](file:///E:/Projects/quark/lib/objects/track.dart#L617-L625)).
   - `getImage()`: заменяет шаблон `%%` в `track.coverUri` на `100x100`, `300x300`, `1000x1000`, `orig` в зависимости от `CoverQuality`.
3. [`YTMusicTrack`](file:///E:/Projects/quark/lib/objects/track.dart#L384-L570):
   - Поля: `String videoId`, `String? channel`, `int? durationSeconds`, `String? streamUrl`, `Map<String, dynamic>? extraData`.
   - `getPlayableInfo()`: асинхронно стучится в `YTMusicAPI().getTrack(videoId)` и получает `streamUrl` ([`track.dart#L540-L546`](file:///E:/Projects/quark/lib/objects/track.dart#L540-L546)).
   - `getImage()`: регулярным выражением подменяет имя разрешения в ссылке Google Video (`mqdefault`, `hqdefault`, `sddefault`, `maxresdefault`).
4. [`ShowTrack`](file:///E:/Projects/quark/lib/objects/track.dart#L627-L685):
   - Поле `final String urlPath`. Проигрывает прямой URL-поток без интеграций.

---

### 2.2. Сериализация и десериализация

1. **Локальные треки** ([`track.dart#L346-L380`](file:///E:/Projects/quark/lib/objects/track.dart#L346-L380)):
   - `serializedLocalTrack`: сериализует `title`, `artists`, `albums`, `filepath`, `cover`, `coverType`. Поле `coverByted` всегда принудительно заменяется на фиктивный `Uint8List(0)` ([`track.dart#L361`](file:///E:/Projects/quark/lib/objects/track.dart#L361)) во избежание раздувания JSON.
   - `deserializedLocalTrack`: обратное восстановление полей.
2. **YouTube треки** ([`track.dart#L578-L615`](file:///E:/Projects/quark/lib/objects/track.dart#L578-L615)):
   - `serializedYTMusicTrack` / `deserializedYTMusicTrack` — сохраняют `type: 'ytmusic'`, `videoId`, `channel`, `streamUrl`, `extraData`.
3. **Плейлисты** ([`lib/objects/playlist.dart#L38-L114`](file:///E:/Projects/quark/lib/objects/playlist.dart#L38-L114)):
   - [`serializePlaylist`](file:///E:/Projects/quark/lib/objects/playlist.dart#L38-L63):
     ```dart
     for (final element in playlist.tracks) {
       if (element is YandexMusicTrack) {
         tracks.add({'source': 'yandex_music', 'data': element.track.raw});
       } else if (element is LocalTrack) {
         tracks.add({'source': 'local', 'data': serializedLocalTrack(element)});
       }
     }
     ```
     **Критический архитектурный баг:** Треки классов `YTMusicTrack` и `ShowTrack` **полностью игнорируются** и молча отбрасываются при сохранении плейлиста.
   - [`deserializePlaylist`](file:///E:/Projects/quark/lib/objects/playlist.dart#L86-L114):
     Парсит рекурсивный `Map` через `_deepConvertMap`. Восстанавливает только `'local'` и `'yandex_music'`. В блоке сопоставления источника плейлиста:
     ```dart
     PlaylistSource source = switch (playlist['source']) {
       'yandex_music' => PlaylistSource.yandexMusic,
       'local' => PlaylistSource.local,
       'spotify' => PlaylistSource.spotify,
       _ => PlaylistSource.local, // YouTube отсутствует, откатывается в local!
     };
     ```

---

### 2.3. Инварианты, равенство и хэши (`==` / `hashCode`)

**Критическая проблема доменной модели:**
- Ни класс `PlayerTrack`, ни его подклассы (`LocalTrack`, `YandexMusicTrack`, `YTMusicTrack`, `ShowTrack`), ни класс `PlayerPlaylist` **не переопределяют `operator ==` и `hashCode`**.
- Сравнение треков во всем приложении происходит исключительно по **ссылочной идентичности (`identical`)**.
- **Последствия:**
  - Методы `getNew(...)` ([`track.dart#L177`](file:///E:/Projects/quark/lib/objects/track.dart#L177), [`track.dart#L270`](file:///E:/Projects/quark/lib/objects/track.dart#L270), [`track.dart#L499`](file:///E:/Projects/quark/lib/objects/track.dart#L499)), создающие клон трека, возвращают объект, который **не равен** оригиналу (`newTrack != oldTrack`).
  - В методах плеера [`queue.remove(nowPlayingTrack)`](file:///E:/Projects/quark/lib/services/player/player.dart#L545), [`queue.contains(nowPlayingTrack)`](file:///E:/Projects/quark/lib/services/player/player.dart#L564), [`playlist.indexWhere((t) => t == unQueuedLastTrack)`](file:///E:/Projects/quark/lib/services/player/player.dart#L571) поиск ломается, если трек был пересоздан (например, восстановлен из БД или преобразован из API).

---

### 2.4. Анализ [`lib/objects/cover.dart`](file:///E:/Projects/quark/lib/objects/cover.dart)

- Файл содержит абстракцию `CoverResolver`, реализации `YandexMusicCoverResolver`, `YoutubeCoverResolver`, `SpotifyCoverResolver`, классы `Cover` и `CoverResolvers`.
- В файле повторно объявляются `enum CoverType` ([`cover.dart#L77`](file:///E:/Projects/quark/lib/objects/cover.dart#L77)) и `enum CoverSource` ([`cover.dart#L94`](file:///E:/Projects/quark/lib/objects/cover.dart#L94)), дублируя аналогичные енумы из `track.dart` ([`track.dart#L23-L72`](file:///E:/Projects/quark/lib/objects/track.dart#L23-L72)).
- **Статус:** `lib/objects/cover.dart` является **неиспользуемым мертвым кодом**. Он не импортируется ни в одном исполняемом файле приложения.

---

### 2.5. Чистая логика vs привязка к Flutter-типам

| Компонент / Поле | Статус | Привязка к Flutter / Проблема |
| :--- | :--- | :--- |
| `PlayerTrack.title, artists, albums, filepath` | Чистая логика | Стандартные типы Dart (`String`, `List<String>`). Чистый перенос на KMP. |
| `PlayerTrack.coverByted` | Завязано на UI | `Uint8List` из `dart:typed_data`, используемый напрямую в `MemoryImage(...)` ([`mini_player.dart#L180`](file:///E:/Projects/quark/lib/widgets/players_widgets/mini_player.dart#L180)). В KMP должен быть `ByteArray`. |
| `PlayerTrack.getAlbumInfo()` | Нарушение слоев | Возвращает [`AlbumInfor`](file:///E:/Projects/quark/lib/widgets/media_cards/album.dart#L44), содержащий `BuildContext context`, `Color accentColor`, `VoidCallback onPlayAll`. Доменная модель жестко привязана к Flutter UI. |
| `AppDatabase.saveColors` / `getColors` | Завязано на Flutter | Таблица SQLite `coverColors` хранит цвета в ARGB32, но контракт mixin `Covers` принимает и возвращает `List<Color>` из `dart:ui` ([`drift_library_engine.dart#L113-L130`](file:///E:/Projects/quark/lib/services/database/drift_library_engine.dart#L113-L130)). |
| `TimedCache<T>` | Чистая логика | Полностью независимый дженерик-кэш на базе `DateTime`. |
| `Shuffles` (алгоритмы перемешивания) | Чистая логика | Чистые алгоритмические функции над списками. |

---

## 3. АУДИО-ДВИЖОК

### 3.1. Полный контракт `_PlayerEngine` и `Player` ([`lib/services/player/player.dart`](file:///E:/Projects/quark/lib/services/player/player.dart))

#### Класс `_PlayerEngine` (строки 81–381):
Низкоуровневый фасад над нативными библиотеками:
- **Методы управления:**
  - `Future<void> init(PlayerBackend playerBackend2)` — инициализация движка.
  - `Future<void> play(String filePath)` — воспроизведение локального файла.
  - `Future<void> playNet(String url)` — воспроизведение по URL-ссылке.
  - `Future<void> stop()`, `Future<void> resume()`, `Future<void> pause()`.
  - `Future<void> seek(Duration duration)`.
  - `Future<void> setVolume(double volume)`.
  - `Future<void> setSpeed(double speed)`.
  - `Future<void> setNextFile(String filepath)`, `Future<void> setNextNet(String uri)` — подготовка следующего трека.
  - `Future<void> getReadyFile(String filepath)`, `Future<void> getReadyNet(String url)` — предзагрузка метаданных/буфера без старта.
  - `Future<void> playNext()` — переключение на следующий подготовленный трек (только `just_audio`).
  - `Future<void> clearPlaylist()` — очистка внутренней очереди `just_audio`.
  - `Future<void> dispose()` — отмена всех `StreamSubscription` и вызов `dispose()` бэкендов.
- **Слушатели:**
  - `Future<void> setupListeners(void Function(void) onComplete, void Function(Duration) onDuration, void Function(Duration) onPlayed)` — подключение стримов позиции, длительности и завершения трека.

#### Класс `Player` (строки 456–922):
Высокоуровневый стейт-контроллер плеера:
- **Контракт нотификаторов (`ValueNotifier`):**
  - `trackNotifier: ValueNotifier<PlayerTrack>` — текущий активный трек.
  - `trackChangeNotifier: ValueNotifier<TrackChange>` — событие смены трека и причина (`completed` — естественное завершение, `external` — по действию пользователя).
  - `playedNotifier: ValueNotifier<Duration>` — текущая позиция воспроизведения.
  - `durationNotifier: ValueNotifier<Duration>` — полная длительность трека.
  - `playlistNotifier: ValueNotifier<List<PlayerTrack>>` — текущий список воспроизведения.
  - `queueNotifier: ValueNotifier<List<PlayerTrack>>` — пользовательская временная очередь.
  - `repeatModeNotifier: ValueNotifier<bool>` — режим повтора.
  - `shuffleModeNotifier: ValueNotifier<bool>` — режим перемешивания.
  - `volumeNotifier: ValueNotifier<double>` — уровень громкости (0.0 – 1.0).
  - `playingNotifier: ValueNotifier<bool>` — флаг воспроизведения (`true` — играет, `false` — пауза/стоп).
- **Методы API плеера:**
  - Воспроизведение: `playNext({bool? forceNext, bool? completed})`, `playPrevious()`, `playPause(bool play)`, `pause()`, `resume()`, `stop()`, `playCustom(PlayerTrack track)`, `playNetTrack(String link, PlayerTrack track)`.
  - Очередь: `playTemporaryQueue(List<PlayerTrack> tracks, {bool? startsNow, bool? first})`, `insertInQueue(PlayerTrack track)`, `insertListInQueue(...)`, `addEndQueue(...)`, `addListInEndQueue(...)`, `removeFromQueue(...)`, `clearQueue()`.
  - Управление списком: `updatePlaylist(...)`, `insertTrack(...)`, `moveTrack(...)`, `removeTrack(...)`, `addTracks(...)`.
  - Режимы: `shuffle(ShuffleMode? shuffleMode1)`, `unShuffle()`, `enableRepeat()`, `disableRepeat()`.
  - Параметры: `setVolume(double volume)`, `setSpeed(double sp)`, `seek(Duration seek)`.

---

### 3.2. Переключение бэкендов (`audioplayers` / `just_audio` / `just_audio_media_kit`)

Определено через перечисление [`PlayerBackend`](file:///E:/Projects/quark/lib/services/player/player.dart#L62-L71):
1. `PlayerBackend.audioPlayers ("Standart")`:
   - Использует пакет `audioplayers` ([`player.dart#L113`](file:///E:/Projects/quark/lib/services/player/player.dart#L113)).
   - На Windows работает через стандартный WinRT/MediaFoundation плагин.
   - Gapless не поддерживается аппаратно: имитируется вручную через слушатель `audioPlayersPlayer!.onPlayerComplete` и подгрузку сохраненного `_Next` объекта ([`player.dart#L236-L243`](file:///E:/Projects/quark/lib/services/player/player.dart#L236-L243)).
2. `PlayerBackend.justAudio ("Just Audio")`:
   - Использует `just_audio` с кастомным User-Agent Android YouTube ([`player.dart#L101-L104`](file:///E:/Projects/quark/lib/services/player/player.dart#L101-L104)) для обхода блокировок сетевых стримов.
3. `PlayerBackend.justAudioMediaKit ("Just Audio MK")`:
   - Вызывает `JustAudioMediaKit.ensureInitialized()` и включает питч-шифтинг `JustAudioMediaKit.pitch = true` ([`player.dart#L106-L107`](file:///E:/Projects/quark/lib/services/player/player.dart#L106-L107)).
   - Проксирует вызовы `just_audio` в нативный плеер на базе **libmpv** (`media_kit_libs_windows_audio`), обеспечивая нативную поддержку кодеков FLAC, ALAC, Opus и плавный gapless.
- **Особенность стримов в `just_audio`:**
  Завершение трека детектируется не через `playerStateStream`, а через смену индекса в плейлисте движка `currentIndexStream`:
  ```dart
  justAudioPlayer!.currentIndexStream.where((index) => index != null).distinct().listen((index) async {
    final sequenceLength = justAudioPlayer!.sequence.length;
    if (index! > 0 && sequenceLength > 1) {
      await justAudioPlayer!.removeAudioSourceAt(0);
      onComplete(null);
    }
  });
  ```

---

### 3.3. Логика очереди (`queue`, `queue2`, `unQueuedLastTrack`)

В плеере сосуществуют три поля управления очередью:
1. `List<PlayerTrack> queue` ([`player.dart#L501`](file:///E:/Projects/quark/lib/services/player/player.dart#L501)) — активная приоритетная очередь пользователя.
2. `PlayerTrack? unQueuedLastTrack` ([`player.dart#L502`](file:///E:/Projects/quark/lib/services/player/player.dart#L502)) — указатель возврата. Когда очередь пуста и в нее добавляется первый трек (`_createQueue()`, строка 780), плеер запоминает текущий играющий трек: `unQueuedLastTrack = nowPlayingTrack;`.
3. `List<PlayerTrack> queue2 = []` ([`player.dart#L503`](file:///E:/Projects/quark/lib/services/player/player.dart#L503)) — **мертвый код**. Объявлен, но нигде в кодовой базе не читается и не изменяется.

#### Алгоритм выбора следующего трека `_getNext()` ([`player.dart#L562-L603`](file:///E:/Projects/quark/lib/services/player/player.dart#L562-L603)):
1. Если `queue.isNotEmpty`:
   - Если текущий трек есть в `queue`, берется следующий элемент очереди.
   - Если достигнут конец очереди: плеер ищет `unQueuedLastTrack` в основном `playlist`. Если найден — возвращается к следующему за ним треку из основного плейлиста (или к нему же при `isRepeat`).
   - Если текущего трека нет в очереди — берется `queue.first`.
2. Если `queue.isEmpty`:
   - Если остался `unQueuedLastTrack`, он извлекается, обнуляется и воспроизводится.
   - Иначе: ищется индекс текущего трека в основном `playlist`. При `isRepeat == true` возвращается текущий индекс; иначе `index + 1` (по кругу при достижении конца списка).

#### Поведение `playCustom(track)` ([`player.dart#L877-L900`](file:///E:/Projects/quark/lib/services/player/player.dart#L877-L900)):
- Если выбранный трек находится внутри очереди `queue`, все элементы очереди от текущего до выбранного отсекаются (`queue.removeRange(...)`).
- Если выбранный трек из `playlist`, он записывается как точка возврата: `unQueuedLastTrack = track;`.

---

### 3.4. Shuffle (три режима) и Repeat

Режимы перемешивания инкапсулированы в классе [`Shuffles`](file:///E:/Projects/quark/lib/services/player/player.dart#L924-L952):
1. [`ShuffleMode.full`](file:///E:/Projects/quark/lib/services/player/player.dart#L15):
   - `Shuffles.full(list)`: клонирует список через `[...list]` и вызывает стандартный `list.shuffle()`.
2. [`ShuffleMode.afterCurrent`](file:///E:/Projects/quark/lib/services/player/player.dart#L18):
   - `Shuffles.afterCurrent(list, afterValue)`: находит текущий трек. Список разделяется на две части: `fixedPart` (от начала до текущего трека включительно остается неизменной) и `queue` (все треки после текущего перемешиваются).
3. [`ShuffleMode.nowOnTop`](file:///E:/Projects/quark/lib/services/player/player.dart#L21):
   - `Shuffles.nowOnTop(list, topValue)`: удаляет текущий трек из копии списка, перемешивает оставшиеся элементы и вставляет текущий трек на нулевую позицию (`list.insert(0, topValue)`).

**Поведение [`unShuffle()`](file:///E:/Projects/quark/lib/services/player/player.dart#L867-L875):**
- Очищает очередь `queue.clear()`.
- Сбрасывает флаг `isShuffle = false`.
- Восстанавливает исходный список из сохраненной копии: `playlist = unShuffledPlaylist`.

**Режим [`Repeat`](file:///E:/Projects/quark/lib/services/player/player.dart#L799-L812):**
- Управляется булевым флагом `isRepeat`. Зацикливает один и тот же трек (в `_getNext()` возвращается тот же индекс). Режима повтора всего плейлиста без повтора отдельного трека нет — плейлист зациклен всегда по умолчанию через оператор `%` / сброс индекса на 0.

---

### 3.5. Gapless через `setNext*`, prefetch, seek/speed, сетевые URL и кэширование

1. **Gapless-механизм ([`player.dart#L626-L634`](file:///E:/Projects/quark/lib/services/player/player.dart#L626-L634)):**
   - После старта трека плеер вызывает `_sendNext()`, который вычисляет `_getNext()`, асинхронно получает его `PlayableInfo` и отправляет в движок:
     - Для локальных файлов -> `_PlayerEngine().setNextFile(filepath)`
     - Для сетевых потоков -> `_PlayerEngine().setNextNet(uri)`
   - В бэкенде `just_audio` / `media_kit` очищается хвост источников (`removeAudioSourceRange(1, length)`) и добавляется новый источник (`addAudioSource`), формируя непрерывный gapless-пайплайн.
2. **Фоновое кэширование через [`NetConductor`](file:///E:/Projects/quark/lib/services/player/net_player.dart#L12-L173):**
   - Слушает `trackChangeNotifier`. При смене трека, если включен флаг `DatabaseStreamerService().autoTrackCache.value`, запускает предзагрузку.
   - Формирует окно вокруг текущего трека (индексы `-1`, `0`, `1` в плейлисте).
   - Фильтрует незакэшированные треки (`getUncached`).
   - Скачивает аудиопотоки через пул потоков `Pool(8)` ([`net_player.dart#L97`](file:///E:/Projects/quark/lib/services/player/net_player.dart#L97)).
   - Для треков Яндекс.Музыки скачивание идет через `instance.tracks.download(track.id, quality: downloadQuality)` с 3 попытками ретрая и экспоненциальной задержкой (`1 << attempt`). Запись байтов на диск выполняется в фоновом Dart Isolate через `compute(writeFile, ...)`.
3. **Seek и Speed:**
   - Прямой проброс через `seek(Duration)` и `setSpeed(double)`.
   - В `_afterFn()` ([`player.dart#L536-L542`](file:///E:/Projects/quark/lib/services/player/player.dart#L536-L542)) зашит хак под Linux: из-за бага ALSA/GStreamer при смене источника сбрасывалась громкость до 100%, поэтому принудительно вызывается `setVolume(...)` и `setSpeed(...)`.

---

### 3.6. Фичи, не имеющие прямого аналога вне Flutter (KMP / Compose Desktop)

При миграции на Kotlin Multiplatform (Compose for Desktop JVM) следующие компоненты потребуют архитектурной замены:

1. **Аудио-движки Flutter (`just_audio`, `audioplayers`, `just_audio_media_kit`):**
   - *Аналог в KMP Desktop:* Использование **libmpv** напрямую через JNA / Panama FFI (библиотеки вроде `kmp-media` или прямая обертка над C-API `mpv_command_string`), либо **VLCJ (libvlc)**. Прямого аналога dart-пакета `just_audio` на Desktop JVM нет.
2. **Нативные системные контролы (`smtc_windows`, `audio_service_mpris`):**
   - *Windows SMTC:* В проекте используется связка Rust FFI (`smtc_windows`). В Kotlin Desktop потребуется вызов Windows Runtime C++/WinRT API через JNI (библиотеки Java-SMTC или самописный DLL-мост).
   - *Linux MPRIS:* В проекте используется Flutter-плагин. В JVM реализуется через D-Bus протокол по шине `org.mpris.MediaPlayer2` с помощью библиотеки `dbus-java`.
3. **Кэш и обработка изображений ([`services/cached_images.dart`](file:///E:/Projects/quark/lib/services/cached_images.dart)):**
   - Проект использует связку Dart `Isolate.run` + CPU-библиотеку `image` (ресайз до 150px, `gaussianBlur` радиусом 25) + провайдеры Flutter `ImageProvider` (`CachedImageProvider`, `MemoryBytesImageProvider`).
   - *В Compose Desktop:* Заменяется на аппаратный шейдер размытия Compose GraphicsLayer / Skia RenderEffect (`org.jetbrains.skia.ImageFilter.makeBlur`), а загрузка и кэширование картинок — на **Coil 3 (Compose Multiplatform)**.
4. **Базы данных (Hive + Drift SQLite):**
   - *Hive:* Заменяется на **Multiplatform Settings** или **Jetpack DataStore Preferences**.
   - *Drift SQLite:* Заменяется на **SQLDelight** или **Room KMP**.
5. **Реактивное состояние (`ValueNotifier`):**
   - 34 `ValueNotifier` из `DatabaseStreamerService` и нотификаторы `Player` должны быть заменены на единый доменный стейт: `StateFlow<PlayerState>` / `MutableStateFlow` с подпиской в Compose через `collectAsStateWithLifecycle()`.
6. **Доступ к тегам файлов (`audio_metadata_reader`, `dart_cue`):**
   - *Аналог в KMP:* JVM-библиотека **JAudiotagger** либо нативная обертка над **TagLib** (через JNA).
