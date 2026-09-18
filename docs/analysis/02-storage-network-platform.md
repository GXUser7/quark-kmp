# Анализ проекта Quark (подготовка переноса на Kotlin Multiplatform + Compose for Desktop)

---

## 4. ХРАНИЛИЩЕ

### 4.1. Drift-схема ([drift_library_engine.dart](file:///E:/Projects/quark/lib/services/database/drift_library_engine.dart))
Файл базы данных создаётся в `getApplicationSupportDirectory()` по пути `quark.db` ([L241-L247](file:///E:/Projects/quark/lib/services/database/drift_library_engine.dart#L241-L247)).
Версия схемы: `schemaVersion = 1` ([L198](file:///E:/Projects/quark/lib/services/database/drift_library_engine.dart#L198)).  
Зарегистрированные таблицы в `@DriftDatabase`: `Playlists`, `PlaylistTracks`, `KnownTracks`, `CoverColors`, `ListenStats` ([L187-L189](file:///E:/Projects/quark/lib/services/database/drift_library_engine.dart#L187-L189)).

> [!NOTE]
> Таблица `StarredTrack` ([L333-L336](file:///E:/Projects/quark/lib/services/database/drift_library_engine.dart#L333-L336)) объявлена в коде, но **не** включена в список аннотации `@DriftDatabase`. В SQLDelight её можно либо опустить, либо включить осознанно.

#### Таблица `KnownTracks` (`known_tracks`) — [L259-L278](file:///E:/Projects/quark/lib/services/database/drift_library_engine.dart#L259-L278)
* **Колонки:**
  * `id`: `INTEGER PRIMARY KEY AUTOINCREMENT`
  * `path`: `TEXT NOT NULL UNIQUE` (путь к файлу трека)
  * `title`: `TEXT NOT NULL`
  * `artists`: `TEXT NOT NULL` (строка артистов, разделённых запятыми)
  * `album`: `TEXT NOT NULL`
  * `cover_url`: `TEXT NULLABLE`
  * `cover_path`: `TEXT NULLABLE`
  * `blur_cover_path`: `TEXT NULLABLE`
  * `md5`: `TEXT NULLABLE`
  * `source`: `TEXT NOT NULL DEFAULT 'local'`
  * `sourceid`: `TEXT NULLABLE`
  * `downloaded`: `INTEGER NOT NULL` (boolean)
* **Индексы:**
  * `idx_known_tracks_title` ON `known_tracks(title)` ([L259](file:///E:/Projects/quark/lib/services/database/drift_library_engine.dart#L259))
  * `idx_known_tracks_artists` ON `known_tracks(artists)` ([L260](file:///E:/Projects/quark/lib/services/database/drift_library_engine.dart#L260))
* **Вычисляемый хелпер:** в сгенерированном [library_engine.g.dart (L807-L808)](file:///E:/Projects/quark/lib/services/database/library_engine.g.dart#L807-L808) добавлен геттер `artistList`:
  ```dart
  artists.split(RegExp(r'[,&;/]')).map((a) => a.trim()).where((a) => a.isNotEmpty).toList();
  ```

#### Таблица `Playlists` (`playlists`) — [L280-L290](file:///E:/Projects/quark/lib/services/database/drift_library_engine.dart#L280-L290)
* **Колонки:**
  * `id`: `INTEGER PRIMARY KEY AUTOINCREMENT`
  * `title`: `TEXT NOT NULL`
  * `cover_url`: `TEXT NULLABLE`
  * `cover_path`: `TEXT NULLABLE`
  * `description`: `TEXT NULLABLE`
  * `blur_cover_path`: `TEXT NULLABLE`
  * `type`: `TEXT NOT NULL DEFAULT 'Playlist'` ('Playlist' или 'Album')

#### Таблица `PlaylistTracks` (`playlist_tracks`) — [L292-L303](file:///E:/Projects/quark/lib/services/database/drift_library_engine.dart#L292-L303)
* **Колонки:**
  * `track`: `INTEGER NOT NULL REFERENCES known_tracks(id)`
  * `playlist`: `INTEGER NOT NULL REFERENCES playlists(id)`
  * `position`: `INTEGER NOT NULL`
* **Первичный ключ:** `PRIMARY KEY (playlist, track)` ([L299](file:///E:/Projects/quark/lib/services/database/drift_library_engine.dart#L299))
* **Индексы:**
  * `idx_playlist_tracks_playlist_pos` ON `playlist_tracks(playlist, position)` ([L293-L295](file:///E:/Projects/quark/lib/services/database/drift_library_engine.dart#L293-L295))

#### Таблица `CoverColors` (`cover_colors`) — [L305-L315](file:///E:/Projects/quark/lib/services/database/drift_library_engine.dart#L305-L315)
* **Колонки:**
  * `md5`: `TEXT NOT NULL PRIMARY KEY`
  * `colors`: `TEXT NOT NULL` (JSON-сериализованный `List<int>` в формате ARGB32)
* **Индексы:**
  * `idx_cover_colors_md5` ON `cover_colors(md5)` ([L305](file:///E:/Projects/quark/lib/services/database/drift_library_engine.dart#L305))

#### Таблица `ListenStats` (`listen_stats`) — [L317-L329](file:///E:/Projects/quark/lib/services/database/drift_library_engine.dart#L317-L329)
* **Колонки:**
  * `track`: `INTEGER NOT NULL REFERENCES known_tracks(id)`
  * `time`: `INTEGER NOT NULL` (в Drift `dateTime()` сохраняется как UNIX epoch timestamp в секундах/мс или ISO8601)
  * `played_seconds`: `INTEGER NOT NULL`
  * `total_track_duration`: `INTEGER NOT NULL`
  * `progress_percent`: `INTEGER NOT NULL`
  * `is_skipped`: `INTEGER NOT NULL` (boolean)
* **Индексы:**
  * `idx_listen_stats_track` ON `listen_stats(track)` ([L317](file:///E:/Projects/quark/lib/services/database/drift_library_engine.dart#L317))
  * `idx_listen_stats_time` ON `listen_stats(time)` ([L318](file:///E:/Projects/quark/lib/services/database/drift_library_engine.dart#L318))

---

### 4.2. Все запросы из миксинов и AppDatabase

#### Миксин `PlaylistRepository` ([L12-L110](file:///E:/Projects/quark/lib/services/database/drift_library_engine.dart#L12-L110))
1. `deletePlaylist(playlistId)` ([L13-L21](file:///E:/Projects/quark/lib/services/database/drift_library_engine.dart#L13-L21)):
   ```sql
   DELETE FROM playlist_tracks WHERE playlist = :playlistId;
   DELETE FROM playlists WHERE id = :playlistId;
   ```
2. `renamePlaylist(playlistId, newTitle)` ([L23-L27](file:///E:/Projects/quark/lib/services/database/drift_library_engine.dart#L23-L27)):
   ```sql
   UPDATE playlists SET title = :newTitle WHERE id = :playlistId;
   ```
3. `changePlaylistCover(playlistId, path)` ([L29-L33](file:///E:/Projects/quark/lib/services/database/drift_library_engine.dart#L29-L33)):
   ```sql
   UPDATE playlists SET cover_path = :path WHERE id = :playlistId;
   ```
4. `createPlaylist(title)` ([L36-L41](file:///E:/Projects/quark/lib/services/database/drift_library_engine.dart#L36-L41)):
   ```sql
   INSERT INTO playlists (title) VALUES (:title); -- возвращает generated ID
   ```
5. `insertTrackIntoPlaylist(playlistID, trackID)` ([L43-L54](file:///E:/Projects/quark/lib/services/database/drift_library_engine.dart#L43-L54)):
   ```sql
   INSERT INTO playlist_tracks (playlist, track, position)
   VALUES (:playlistID, :trackID, (SELECT COALESCE(MAX(position), 0) + 1 FROM playlist_tracks WHERE playlist = :playlistID));
   ```
6. `removeTrackPositionPlaylist(playlistID, position)` ([L56-L61](file:///E:/Projects/quark/lib/services/database/drift_library_engine.dart#L56-L61)):
   ```sql
   DELETE FROM playlist_tracks WHERE playlist = :playlistID AND position = :position;
   ```
7. `getPlaylist(playlistId)` ([L63-L84](file:///E:/Projects/quark/lib/services/database/drift_library_engine.dart#L63-L84)):
   ```sql
   SELECT * FROM playlists WHERE id = :playlistId LIMIT 1;
   SELECT kt.* FROM known_tracks kt
   INNER JOIN playlist_tracks pt ON pt.track = kt.id
   WHERE pt.playlist = :playlistId
   ORDER BY pt.position ASC;
   ```
8. `getAllPlaylistsWithTracks()` ([L86-L109](file:///E:/Projects/quark/lib/services/database/drift_library_engine.dart#L86-L109)):
   ```sql
   SELECT p.*, kt.* FROM playlists p
   LEFT OUTER JOIN playlist_tracks pt ON pt.playlist = p.id
   LEFT OUTER JOIN known_tracks kt ON kt.id = pt.track;
   ```

#### Миксин `Covers` ([L112-L131](file:///E:/Projects/quark/lib/services/database/drift_library_engine.dart#L112-L131))
1. `saveColors(hash, colors)` ([L113-L119](file:///E:/Projects/quark/lib/services/database/drift_library_engine.dart#L113-L119)):
   ```sql
   INSERT OR REPLACE INTO cover_colors (md5, colors) VALUES (:hash, :colorsJson);
   ```
2. `getColors(hash)` ([L121-L130](file:///E:/Projects/quark/lib/services/database/drift_library_engine.dart#L121-L130)):
   ```sql
   SELECT colors FROM cover_colors WHERE md5 = :hash LIMIT 1;
   ```

#### Миксин `Tracks` ([L133-L182](file:///E:/Projects/quark/lib/services/database/drift_library_engine.dart#L133-L182))
1. `saveTracks(tracks, downloaded)` ([L135-L162](file:///E:/Projects/quark/lib/services/database/drift_library_engine.dart#L135-L162)):
   Фильтрует треки через локальный in-memory кэш `_knownPaths` ([L134](file:///E:/Projects/quark/lib/services/database/drift_library_engine.dart#L134)).
   ```sql
   INSERT OR IGNORE INTO known_tracks (path, title, artists, album, downloaded, cover_url)
   VALUES (:path, :title, :artists, :album, :downloaded, :coverUrl);
   ```
2. `saveSingleTrack(track, downloaded)` ([L164-L177](file:///E:/Projects/quark/lib/services/database/drift_library_engine.dart#L164-L177)):
   ```sql
   INSERT OR REPLACE INTO known_tracks (path, title, artists, album, downloaded, cover_url)
   VALUES (:path, :title, :artists, :album, :downloaded, :coverUrl)
   RETURNING id;
   ```
3. `getKnownTracks()` ([L179-L181](file:///E:/Projects/quark/lib/services/database/drift_library_engine.dart#L179-L181)):
   ```sql
   SELECT * FROM known_tracks;
   ```

#### Класс `AppDatabase` ([L190-L239](file:///E:/Projects/quark/lib/services/database/drift_library_engine.dart#L190-L239))
1. `saveStats(track, playedSeconds, totalTrackDuration, progressPercent, skipped)` ([L199-L228](file:///E:/Projects/quark/lib/services/database/drift_library_engine.dart#L199-L228)):
   Ищет трек: `SELECT id FROM known_tracks WHERE path = :path LIMIT 1`. Если отсутствует, выполняет `saveSingleTrack`.
   Затем вставка:
   ```sql
   INSERT INTO listen_stats (track, time, played_seconds, total_track_duration, progress_percent, is_skipped)
   VALUES (:trackId, :now, :playedSeconds, :totalTrackDuration, :progressPercent, :skipped);
   ```
2. `getStats()` ([L230-L232](file:///E:/Projects/quark/lib/services/database/drift_library_engine.dart#L230-L232)):
   ```sql
   SELECT * FROM listen_stats;
   ```
3. `init()` ([L234-L237](file:///E:/Projects/quark/lib/services/database/drift_library_engine.dart#L234-L237)):
   ```sql
   SELECT path FROM known_tracks;
   ```

---

### 4.3. Hive: настройки и кэш обложек

#### `hive_settings_engine.dart` ([L20-L130](file:///E:/Projects/quark/lib/services/database/hive_settings_engine.dart#L20-L130))
Box name: `'database'` ([L151](file:///E:/Projects/quark/lib/services/database/hive_settings_engine.dart#L151)).

| Enum ключ (`DatabaseKeys`) | Строковое значение в Hive | Dart/Kotlin тип |
|---|---|---|
| `volume` | `'volume'` | `Double` |
| `stateIndicatorState` | `'indicator_state'` | `Boolean` |
| `dynamicWindowColor` | `'dynamic_window_color'` | `Boolean` |
| `backgroundBlurRadius` | `'background_blur_radius'` | `Double` |
| `originalImageSizeCoverView` | `'original_image_size_cover_view'` | `Boolean` |
| `playerBackend` | `'player_backend'` | `String` ('standart' / 'media_kit') |
| `justAudioPrefetch` | `'just_audio_player_prefetch'` | `Boolean` |
| `changePlaylistWhileSelectCategory` | `'category_playlist_change'` | `Boolean` |
| `localApi` | `'local_api'` | `Boolean` |
| `playlistCategories` | `'playlist_categories'` | `Boolean` |
| `discordRPC` | `'discord_rpc'` | `Boolean` |
| `directoryObserver` | `'directory_observer'` | `Boolean` |
| `autoTrackCache` | `'auto_track_cache'` | `Boolean` |
| `recursiveFilesAdding` | `'recursive_files_adding'` | `Boolean` |
| `lastTrackPosition` | `'last_track_position'` | `Int` (секунды) |
| `playlistOpeningArea` | `'playlist_opening_area'` | `Boolean` |
| `yandexMusicPlaylists` | `'yandex_music_playlists'` | `List<Map<String, dynamic>>` |
| `windowManager` | `'window_manager_state'` | `Boolean` |
| `logListenedTracks` | `'log_listener_state'` | `Boolean` |
| `yandexMusicToken` | `'yandex_music_token'` | `String` |
| `yandexMusicNewestPlaylistInfo` | `'yandex_music_newest'` | `Boolean` |
| `yandexMusicEmail` | `'yandex_music_email'` | `String` |
| `yandexMusicLogin` | `'yandex_music_login'` | `String` |
| `yandexMusicDisplayName` | `'yandex_music_displayname'` | `String` |
| `yandexMusicFullName` | `'yandex_music_fullname'` | `String` |
| `yandexMusicTokenExpires` | `'yandex_music_token_expires'` | `Int` (epoch timestamp) |
| `yandexMusicUid` | `'yandex_music_uid'` | `Int` |
| `yandexMusicTrackQuality` | `'yandex_music_track_quality'` | `String` ('nq', 'hq', 'lossless') |
| `lastPlaylist` | `'last_playlist'` | `Map<String, dynamic>` (JSON сериализованного плейлиста) |
| `lastTrack` | `'last_track'` | `String` (filepath последнего трека) |
| `gradientMode` | `'gradient_mode'` | `Boolean` |
| `lastPlaylistState` | `'last_playlist_state'` | `Boolean` |
| `transitionSpeed` | `'transition_speed'` | `Double` |
| `yandexMusicSearch` | `'yandex_music_search'` | `Boolean` |
| `yandexMusicPreload` | `'yandex_music_preload'` | `Boolean` |

#### `hive_image_engine.dart` ([L1-L48](file:///E:/Projects/quark/lib/services/database/hive_image_engine.dart#L1-L48))
* Box name: `'images.db'` ([L17](file:///E:/Projects/quark/lib/services/database/hive_image_engine.dart#L17)).
* Ключ: `'images'` хранит словарь `Map<String, String>`: `filepath -> cachedImagePath` (связка пути к аудиофайлу и файла извлечённой обложки).
* Методы: `getImage(filepath)` ([L24](file:///E:/Projects/quark/lib/services/database/hive_image_engine.dart#L24)), `saveImage(filepath, imagepath)` ([L28](file:///E:/Projects/quark/lib/services/database/hive_image_engine.dart#L28)).

---

### 4.4. Агрегация статистики: ListenLogger и MusicAnalyticsEngine

1. **`ListenLogger`** ([listen_logger.dart](file:///E:/Projects/quark/lib/services/database/listen_logger.dart)):
   * Подписывается на события плеера: `trackChangeNotifier`, `durationNotifier`, `playedNotifier` ([L28-L30](file:///E:/Projects/quark/lib/services/database/listen_logger.dart#L28-L30)).
   * Алгоритм отслеживания: в `playedListener` ([L100-L114](file:///E:/Projects/quark/lib/services/database/listen_logger.dart#L100-L114)) вычисляется дельта `diff = currentSecond - lastCountedSecond`. Если `0 < diff <= seekThreshold` (порог 2 сек), то `totalPlayedSeconds += diff`. Перемотки (diff > 2 или < 0) не засчитываются в прослушанное время.
   * Фиксация прослушивания: в `trackListen()` ([L69-L94](file:///E:/Projects/quark/lib/services/database/listen_logger.dart#L69-L94)), если `totalPlayedSeconds > 10`, вызывается `saveListen`:
     * `progressPercent = min((totalPlayedSeconds / totalDuration * 100).round(), 100)`
     * `skipped = (changeReason != ChangeReason.completed)`
     * Пишется в таблицу `ListenStats` через `AppDatabase().saveStats()`.

2. **`MusicAnalyticsEngine`** ([listen_stats_enjoyer.dart](file:///E:/Projects/quark/lib/services/database/listen_stats_enjoyer.dart#L464-L958)):
   * **Порог завершения трека:** `_completionThreshold = 80`% ([L467](file:///E:/Projects/quark/lib/services/database/listen_stats_enjoyer.dart#L467)).
   * **Порог сессии:** `_sessionGapSeconds = 1800` сек (30 минут разрыва между треками) ([L465](file:///E:/Projects/quark/lib/services/database/listen_stats_enjoyer.dart#L465)).
   * **Агрегируемые метрики:**
     * **TrackStats** ([L490-L526](file:///E:/Projects/quark/lib/services/database/listen_stats_enjoyer.dart#L490-L526)): общее число прослушиваний, полных (progress >= 80%), пропусков (`isSkipped`), общее время в секундах, `completionRate`, `skipRate`, даты первого/последнего прослушивания, число уникальных дней и самая длинная серия дней подряд (`longestStreakDays`).
     * **ArtistStats** ([L539-L567](file:///E:/Projects/quark/lib/services/database/listen_stats_enjoyer.dart#L539-L567)): уникальные треки, топ-5 треков, распределение по альбомам `albumPlayCounts`, средний процент дослушиваний, `skipRate`.
     * **AlbumStats** ([L571-L634](file:///E:/Projects/quark/lib/services/database/listen_stats_enjoyer.dart#L571-L634)): последовательные прослушивания подряд `sequentialSessions` (когда треки идут из одного альбома run >= 2) и количество полных прослушиваний альбома `albumCompletionSessions` (доля прослушанных уникальных треков альбома за одну сессию).
     * **Временная активность:** `HourlyActivity` (24 часа, поиск `peakHour`), `DailyActivity`, `playsByDayOfWeek`, `playsByMonthYear`.
     * **Сессии:** группировка с разрывом > 30 минут, средняя длительность сессии, среднее число треков в сессии, топ-10 самых длинных сессий.
     * **Стрики:** текущий и максимальный непрерывный стрик дней прослушивания.
     * **Discovery & Loyalty:**
       * `DiscoveryEvent`: треки, прослушанные $\ge 3$ раз за первые 7 дней после первого знакомства ([L738-L749](file:///E:/Projects/quark/lib/services/database/listen_stats_enjoyer.dart#L738-L749)).
       * `quicklyAbandoned`: треки с ровно 1 прослушиванием за всё время ([L751-L757](file:///E:/Projects/quark/lib/services/database/listen_stats_enjoyer.dart#L751-L757)).
     * **Сплит по времени суток:** Morning (06:00–12:00), Afternoon (12:00–18:00), Evening (18:00–24:00), Night (00:00–06:00) ([L767-L773](file:///E:/Projects/quark/lib/services/database/listen_stats_enjoyer.dart#L767-L773)).
     * **Mood Periods:** понедельная классификация:
       * `skipRate > 0.4` $\to$ `"Restless / high skip"`
       * `compRate > 0.8` $\to$ `"Deep listening"`
       * `compRate > 0.6` $\to$ `"Engaged"`
       * иначе $\to$ `"Casual"` ([L803-L806](file:///E:/Projects/quark/lib/services/database/listen_stats_enjoyer.dart#L803-L806)).
     * **Тренды роста:** `recentVsHistoricPlayRatio` (соотношение активности за последние 30 дней к исторической средней за 30 дней).

---

### 4.5. `DatabaseStreamerService` ([database.dart](file:///E:/Projects/quark/lib/services/database/database.dart)) — все поля и их дефолты

Служба синхронизирует `ValueNotifier` с Hive при изменении ([L148-L200](file:///E:/Projects/quark/lib/services/database/database.dart#L148-L200)).

| Поле | Тип | Дефолтное значение | Строка объявления |
|---|---|---|---|
| `volume` | `Double` | `0.7` | [L23](file:///E:/Projects/quark/lib/services/database/database.dart#L23) |
| `stateIndicator` | `Boolean` | `true` | [L24](file:///E:/Projects/quark/lib/services/database/database.dart#L24) |
| `recursiveFilesAdding` | `Boolean` | `true` | [L25](file:///E:/Projects/quark/lib/services/database/database.dart#L25) |
| `playlistOpeningArea` | `Boolean` | `false` | [L26](file:///E:/Projects/quark/lib/services/database/database.dart#L26) |
| `yandexMusicToken` | `String` | `''` | [L27](file:///E:/Projects/quark/lib/services/database/database.dart#L27) |
| `transitionSpeed` | `Double` | `1.0` | [L28](file:///E:/Projects/quark/lib/services/database/database.dart#L28) |
| `yandexMusicSearch` | `Boolean` | `true` | [L29](file:///E:/Projects/quark/lib/services/database/database.dart#L29) |
| `yandexMusicPreload` | `Boolean` | `true` | [L30](file:///E:/Projects/quark/lib/services/database/database.dart#L30) |
| `yandexMusicQuality` | `String` | `'nq'` | [L31](file:///E:/Projects/quark/lib/services/database/database.dart#L31) |
| `lastTrack` | `String?` | `null` | [L32](file:///E:/Projects/quark/lib/services/database/database.dart#L32) |
| `lastPlaylist` | `Map?` | `null` | [L33](file:///E:/Projects/quark/lib/services/database/database.dart#L33) |
| `yandexMusicLogin` | `String` | `''` | [L34](file:///E:/Projects/quark/lib/services/database/database.dart#L34) |
| `yandexMusicFullName` | `String` | `''` | [L35](file:///E:/Projects/quark/lib/services/database/database.dart#L35) |
| `yandexMusicDisplayName` | `String` | `''` | [L36](file:///E:/Projects/quark/lib/services/database/database.dart#L36) |
| `yandexMusicUid` | `Int?` | `null` | [L37](file:///E:/Projects/quark/lib/services/database/database.dart#L37) |
| `yandexMusicEmail` | `String` | `''` | [L38](file:///E:/Projects/quark/lib/services/database/database.dart#L38) |
| `yandexMusicTokenExpires` | `Int` | `0` | [L39](file:///E:/Projects/quark/lib/services/database/database.dart#L39) |
| `gradientMode` | `Boolean` | `false` | [L41](file:///E:/Projects/quark/lib/services/database/database.dart#L41) |
| `lastPlaylistState` | `Boolean` | `false` | [L42](file:///E:/Projects/quark/lib/services/database/database.dart#L42) |
| `yandexMusicPlaylists` | `List?` | `null` | [L43](file:///E:/Projects/quark/lib/services/database/database.dart#L43) |
| `lastTrackPosition` | `Int` | `0` | [L44](file:///E:/Projects/quark/lib/services/database/database.dart#L44) |
| `windowManager` | `Boolean` | `false` | [L45](file:///E:/Projects/quark/lib/services/database/database.dart#L45) |
| `logListenedTracks` | `Boolean` | `false` | [L46](file:///E:/Projects/quark/lib/services/database/database.dart#L46) |
| `dynamicWindowColor` | `Boolean` | `true` | [L47](file:///E:/Projects/quark/lib/services/database/database.dart#L47) |
| `originalImageSizeForCoverView` | `Boolean` | `false` | [L48](file:///E:/Projects/quark/lib/services/database/database.dart#L48) |
| `playerBackend` | `String` | `'standart'` | [L49](file:///E:/Projects/quark/lib/services/database/database.dart#L49) |
| `justAudioPrefetch` | `Boolean` | `false` | [L50](file:///E:/Projects/quark/lib/services/database/database.dart#L50) |
| `changePlaylistWhileSelectCategory` | `Boolean` | `false` | [L51](file:///E:/Projects/quark/lib/services/database/database.dart#L51) |
| `localApi` | `Boolean` | `false` | [L52](file:///E:/Projects/quark/lib/services/database/database.dart#L52) |
| `categories` | `Boolean` | `true` | [L53](file:///E:/Projects/quark/lib/services/database/database.dart#L53) |
| `discordRPC` | `Boolean` | `false` | [L54](file:///E:/Projects/quark/lib/services/database/database.dart#L54) |
| `directoryObserver` | `Boolean` | `true` | [L55](file:///E:/Projects/quark/lib/services/database/database.dart#L55) |
| `autoTrackCache` | `Boolean` | `false` | [L56](file:///E:/Projects/quark/lib/services/database/database.dart#L56) |

---

## 5. СЕТЬ И ИНТЕГРАЦИИ

### 5.1. Яндекс.Музыка ([lib/overrided_libraries/yandex_music](file:///E:/Projects/quark/lib/overrided_libraries/yandex_music))

* **Базовый URL:** `https://api.music.yandex.net` ([lower_level.dart:15](file:///E:/Projects/quark/lib/overrided_libraries/yandex_music/lib/src/lower_level.dart#L15), [requests.dart:9](file:///E:/Projects/quark/lib/overrided_libraries/yandex_music/lib/src/requests/requests.dart#L9)).
* **Авторизация:** Заголовок `Authorization: OAuth <token>`.
* **Базовые заголовки клиента:**
  * Web: `User-Agent: YandexMusicAPI/1.0.0`, `x-yandex-music-client: YandexMusicWebNext/1.0.0`, `x-yandex-music-without-invocation-info: 1`, `x-yandex-music-multi-auth-user-id: <userId>`, `Origin: https://music.yandex.ru`, `Referer: https://music.yandex.ru/` ([lower_level.dart:273-282](file:///E:/Projects/quark/lib/overrided_libraries/yandex_music/lib/src/lower_level.dart#L273-L282)).
  * Mobile (для текстов): `X-Yandex-Music-Client: YandexMusicAndroid/24023621`, `User-Agent: Yandex-Music-API`, `Accept-Language: ru` ([lower_level.dart:301-306](file:///E:/Projects/quark/lib/overrided_libraries/yandex_music/lib/src/lower_level.dart#L301-L306)).

#### Криптографические подписи ([signs.dart](file:///E:/Projects/quark/lib/overrided_libraries/yandex_music/lib/src/signs/signs.dart))
1. `getFileInfoSign` ([L5-L21](file:///E:/Projects/quark/lib/overrided_libraries/yandex_music/lib/src/signs/signs.dart#L5-L21)):
   * Secret key: `"7tvSmFbyf5hJnIHhCimDDD"`
   * Данные для подписи: `"$timestamp$trackId$quality$codecsString$transport"` (например: `172600000047127losslessflacaache-aacmp3flac-mp4aac-mp4he-aac-mp4raw`).
   * Алгоритм: `HMAC-SHA256(key, signString)`. Результат — Base64-строка, у которой отсекается последний символ: `base64.substring(0, len - 1)`.
2. `getLyricsSign` ([L23-L33](file:///E:/Projects/quark/lib/overrided_libraries/yandex_music/lib/src/signs/signs.dart#L23-L33)):
   * Secret key: `"p93jhgh689SBReK6ghtw62"`
   * Timestamp: секунды `now.epochSeconds`.
   * Данные для подписи: `"$trackId$timestamp"`.
   * Алгоритм: `HMAC-SHA256(key, "$trackId$timestamp")`, результат в Base64.
   * Возвращает `{'timestamp': timestamp, 'signature': sign}`.
3. `getMp3Sign` ([L35-L43](file:///E:/Projects/quark/lib/overrided_libraries/yandex_music/lib/src/signs/signs.dart#L35-L43)):
   * Secret key: `"XGRlBW9FXlekgbPrRHuSiA"`
   * Извлекает из XML `<path>` и `<s>`.
   * Данные: `secretKey + path.substring(1) + s`.
   * Алгоритм: `MD5(signData)` в hex-виде.

#### Получение ссылки на трек ([lower_level.dart](file:///E:/Projects/quark/lib/overrided_libraries/yandex_music/lib/src/lower_level.dart))
* **V2 (Основной, HQ / Lossless / AAC / MP3):**
  `GET /get-file-info` ([L236-L291](file:///E:/Projects/quark/lib/overrided_libraries/yandex_music/lib/src/lower_level.dart#L236-L291)).
  Query: `ts`, `trackId`, `quality` (`lossless`, `hq`, `nq`), `codecs` (`flac,aac,he-aac,mp3,flac-mp4,aac-mp4,he-aac-mp4`), `transports=raw`, `sign=getFileInfoSign(...)`.
  Ответ JSON: `downloadInfo.urls` / `downloadInfo.url`. Прямой CDN-URL стриминга/скачивания.
* **V1 (Legacy MP3 320):**
  `GET /tracks/$trackID/download-info` ([L196-L206](file:///E:/Projects/quark/lib/overrided_libraries/yandex_music/lib/src/lower_level.dart#L196-L206)) $\to$ выбор ссылки с `bitrateInKbps == 320` (`downloadInfoUrl`).
  Запрос XML по `downloadInfoUrl` ([L219-L234](file:///E:/Projects/quark/lib/overrided_libraries/yandex_music/lib/src/lower_level.dart#L219-L234)) $\to$ парсинг `<host>`, `<path>`, `<ts>`, `<s>` $\to$ ссылка `https://$host/get-mp3/$sign/$ts$path`.

#### Тексты песен (Lyrics)
`GET /tracks/$trackId/lyrics` ([lower_level.dart:293-320](file:///E:/Projects/quark/lib/overrided_libraries/yandex_music/lib/src/lower_level.dart#L293-L320)).
Параметры: `format` (`TEXT_LRC` для синхронизированных или `TEXT_PLAIN`), `timeStamp`, `sign`.
Ответ JSON содержит `result.downloadUrl`. Файл скачивается по HTTP GET:
* Парсинг LRC ([yandex_music_singleton.dart:589-623](file:///E:/Projects/quark/lib/services/yandex_music/yandex_music_singleton.dart#L589-L623)): парсятся строки `[mm:ss.xx] words` в `Map<Duration, String>`.

#### Моя Волна (My Vibe / Rotor Wave)
Реализовано в [vibe.dart](file:///E:/Projects/quark/lib/overrided_libraries/yandex_music/lib/src/subclasses/objects/vibe.dart) и [lazy_wave.dart](file:///E:/Projects/quark/lib/overrided_libraries/yandex_music/lib/src/subclasses/objects/lazy_wave.dart):
* `GET /rotor/wave/settings` ([lower_level.dart:770](file:///E:/Projects/quark/lib/overrided_libraries/yandex_music/lib/src/lower_level.dart#L770)) — доступные пресеты волны (настроение, язык, характер).
* `POST /rotor/session/new` ([lower_level.dart:779](file:///E:/Projects/quark/lib/overrided_libraries/yandex_music/lib/src/lower_level.dart#L779)):
  Body JSON: `{"seeds": ["..."], "includeTracksInResponse": true, "includeWaveModel": true, "interactive": true}`.
  Возвращает `sessionId`, `batchId`, список треков `sequence`.
* `POST /rotor/session/$sessionId/tracks` ([lower_level.dart:797](file:///E:/Projects/quark/lib/overrided_libraries/yandex_music/lib/src/lower_level.dart#L797)):
  Body JSON: `{"queue": [queueIds], "feedbacks": [feedbackEvents]}`. Дозагрузка следующих треков.
* `POST /rotor/session/$sessionId/clone` ([lower_level.dart:811](file:///E:/Projects/quark/lib/overrided_libraries/yandex_music/lib/src/lower_level.dart#L811)).
* `POST /rotor/session/$sessionId/feedback/` ([lower_level.dart:832-925](file:///E:/Projects/quark/lib/overrided_libraries/yandex_music/lib/src/lower_level.dart#L832-L925)) с `from: "web-home-rup_main-radio-default"`:
  * `radioStarted`: событие старта волны.
  * `trackStarted`: `{"trackId": "$trackId:$albumId"}`, `batchId`.
  * `trackFinished`: `{"trackId": "$trackId:$albumId", "totalPlayedSeconds": ..., "trackLengthSeconds": ...}`.
  * `skip`: `{"trackId": "$trackId:$albumId", "totalPlayedSeconds": ...}`.

#### Все остальные эндпоинты `subclasses/objects/*`
* **Аккаунт:** `GET /account/status`, `GET /account/settings`.
* **Плейлисты:**
  * `GET /users/$userId/playlists/list` и `/kinds?addPlaylistWithLikes=true`
  * `GET /users/$userId/playlists/$kind` и `GET /playlist/$uuid`
  * `GET /users/$userId/playlists` (мульти-запрос по `kinds`, `mixed=true`)
  * `POST /users/$userId/playlists/create` (`title`, `visibility: 'public'|'private'`)
  * `POST /users/$userId/playlists/$kind/name` (переименование)
  * `POST /users/$userId/playlists/$kind/delete`
  * `POST /users/$userId/playlists/$kind/change-relative` (добавление, удаление, перемещение треков через JSON `diff` операциями `insert`/`delete`/`move` с ревизией `revision`)
  * `POST /users/$userId/playlists/$kind/visibility`
  * `POST /users/$userId/playlists/$kind/cover/upload` (multipart PNG) и `/cover/clear`
  * `GET /users/$userId/playlists/$kind/recommendations`
  * `POST /playlists/list` (получение информации по списку `playlistIds`)
* **Лайки и дизлайки:**
  * `GET /users/$userId/likes/tracks`, `POST /users/$userId/likes/tracks/add-multiple`, `POST /users/$userId/likes/tracks/remove`
  * `GET /users/$userId/dislikes/tracks`, `POST /users/$userId/dislikes/tracks/add`, `POST /users/$userId/dislikes/tracks/$trackId/remove`
* **Треки:**
  * `POST /tracks` (батч-запрос информации о треках по `trackIds`)
  * `GET /tracks/$trackID/full-info`
  * `GET /tracks/$trackID/similar`
  * `GET /tracks/$trackID/supplement`
* **Альбомы:**
  * `GET /albums/$albumId`, `GET /albums/$albumId/with-tracks?richTracks=true`, `POST /albums` (`album-ids`)
* **Артисты:**
  * `GET /artists/$artistId/brief-info`, `GET /artists/$artistId/tracks`, `GET /artists/$artistId/direct-albums`, `GET /artists/$artistId/playlists`, `GET /artists/$artistId/concerts`.
* **Поиск:**
  * `GET /search` (`text`, `page`, `type`, `nocorrect`)
  * `GET /search/instant/mixed` (`text`, `page`, `type`, `pageSize`, `withLikesCount`, `withBestResults`)
* **Главная (Landing):** `GET /landing3` (блоки: `personalplaylists,promotions,new-releases,new-playlists,mixes,chart,artists,albums,playlists,play_contexts,podcasts`), `GET /landing3/$block`.
* **UGC (Загрузка пользовательских треков):**
  * `POST /loader/upload-url?uid=$userId&playlist-id=$kind&path=$fileName` $\to$ ссылка на загрузку.
  * `POST $url` (multipart-загрузка аудиофайла) $\to$ `POST /ugc/tracks/$trackId/change` (установка тегов title/artist).

---

### 5.2. YouTube Music API ([ytmusic_services.dart](file:///E:/Projects/quark/lib/services/youtube_music/ytmusic_services.dart))
Бэкенд-прокси: `https://quarkaudio.ru/api/yt` (локально `http://localhost:8000/api/yt`) ([L8-L13](file:///E:/Projects/quark/lib/services/youtube_music/ytmusic_services.dart#L8-L13)).

1. **Поиск:**
   * `GET /api/yt/search?query=ytsearch{limit}:{query}&max_results={limit}` ([L24-L40](file:///E:/Projects/quark/lib/services/youtube_music/ytmusic_services.dart#L24-L40)).
   * Формат ответа: JSON массив объектов с полями `id`, `title`, `duration`, `uploader`, `thumbnail`, `thumbnails[]`.
2. **Получение ссылки на стрим трека:**
   * `GET /api/yt/song` ([L43-L60](file:///E:/Projects/quark/lib/services/youtube_music/ytmusic_services.dart#L43-L60)).
   * Body JSON: `{"video_id": "<id>", "format": "bestaudio[ext=m4a]/bestaudio[acodec!=opus]/bestaudio"}`.
   * Ответ: объект `Track` ([ytmusic.dart:98](file:///E:/Projects/quark/lib/services/youtube_music/ytmusic.dart#L98)), содержащий прямой стриминговый `streamUrl` (или `url`), список форматов `formats[]`, `duration`, `thumbnails[]`, метаданные `artist`, `album`.
3. **Авторизованный плейлист:**
   * `POST /api/yt/playlistAuth` ([L63-L90](file:///E:/Projects/quark/lib/services/youtube_music/ytmusic_services.dart#L63-L90)).
   * Multipart Form: `playlist_id`, `format`, `cookies` (содержимое cookie-файла).
4. **Авторизованные плейлисты пользователя:**
   * `POST /api/yt/playlists` ([L93-L131](file:///E:/Projects/quark/lib/services/youtube_music/ytmusic_services.dart#L93-L131)).
   * Multipart Form: `cookies` файл. Ответ: `YTMusicPlaylistsResponse`.

---

### 5.3. Распознавание аудио и MusicBrainz ([recognizer_api.dart](file:///E:/Projects/quark/lib/services/recognizer_api.dart))
1. **Quark Audio Recognizer:**
   * URL: `http://127.0.0.1:8000/api/quark/recognizer/recognize` ([L9-L33](file:///E:/Projects/quark/lib/services/recognizer_api.dart#L9-L33)).
   * Метод: `POST` (multipart `file: audio/mpeg`).
   * Ответ: `results[]` (распознанные треки).
2. **MusicBrainz API:**
   * Базовый URL: `https://musicbrainz.org` ([L48](file:///E:/Projects/quark/lib/services/recognizer_api.dart#L48)).
   * Поиск записи: `GET /ws/2/recording/{mbid}?inc=artists+releases+release-groups+url-rels+tags+ratings+genres+isrcs&fmt=json` ([L51-L67](file:///E:/Projects/quark/lib/services/recognizer_api.dart#L51-L67)).
   * Cover Art Archive: `https://coverartarchive.org/release/{releaseMbid}/front` ([L49-L71](file:///E:/Projects/quark/lib/services/recognizer_api.dart#L49-L71)).

---

### 5.4. Discord RPC ([discord_rpc.dart](file:///E:/Projects/quark/lib/services/discord_rpc.dart))
* Application ID: `"1520321415595954247"` ([L14](file:///E:/Projects/quark/lib/services/discord_rpc.dart#L14)).
* При смене трека устанавливается `RPCActivity` ([L91-L106](file:///E:/Projects/quark/lib/services/discord_rpc.dart#L91-L106)):
  * `details`: название трека
  * `state`: артисты через запятую
  * `activityType`: `listening`
  * `largeImage`: URL обложки трека, `largeText`: альбом
  * `smallImage`: URL обложки артиста (300x300), `smallText`: артист
  * `buttons`: `[{"label": "Join quark", "url": "https://quarkaudio.ru"}]`

---

### 5.5. Local API ([local_api.dart](file:///E:/Projects/quark/lib/services/local_api/local_api.dart))
* **Запуск сервера:** `HttpServer.bind(InternetAddress.anyIPv4, 0)` ([L105](file:///E:/Projects/quark/lib/services/local_api/local_api.dart#L105)) — случайный свободный порт.
* **Файл `api.port`:** порт записывается в текстовом виде в `<cacheDirectory>/api.port` ([L110-L120](file:///E:/Projects/quark/lib/services/local_api/local_api.dart#L110-L120)).
* **mDNS / Bonsoir:** имя `'quark'`, тип `'_quarkaudio._tcp'`, порт сервера, атрибуты `{'version': '0', 'path': '/api'}` ([L76-L90](file:///E:/Projects/quark/lib/services/local_api/local_api.dart#L76-L90)).
* **REST HTTP Endpoints:**
  * `GET /get-api-version` $\to$ `0`
  * `GET /get-volume` $\to$ `Double`
  * `GET /set-volume?value={0.0..1.0}` $\to$ `204 No Content`
  * `GET /get-repeat` $\to$ `Boolean`
  * `GET /set-repeat?value={true|false}` $\to$ `204`
  * `GET /get-shuffle` $\to$ `Boolean`
  * `GET /set-shuffle?value={true|false}` $\to$ `204`
  * `GET /get-position` $\to$ секунды `Int`
  * `GET /get-duration` $\to$ секунды `Int`
  * `GET /get-quick-parameters` $\to$ `{"repeat": bool, "shuffle": bool, "volume": double, "paused": bool}`
  * `GET /get-now-playing-track` $\to$ JSON трека (`title`, `artists`, `album`, `filepath`, `position`, `duration`, `paused`, `local`, `index`, `queue-index` и т.д.)
  * `GET /set-now-playing-track?value={index}` $\to$ `204`
  * `GET /get-now-playlist` $\to$ `{"length": n, "source": "...", "name": "...", "tracks": [...]}`
  * `GET /seek?value={seconds}` $\to$ `204`
  * `GET /seek-delta-seconds?value={seconds}` $\to$ `204`
  * `GET /play-next`, `GET /play-previous`, `GET /pause`, `GET /resume` $\to$ `204`
* **WebSocket подписки:**
  * `GET /subscribe?subscriptions={endpoints}` (через запятую) $\to$ HTTP 101 WebSocket Upgrade ([L136-L162](file:///E:/Projects/quark/lib/services/local_api/local_api.dart#L136-L162)).
  * Поддерживаемые события: `repeat`, `shuffle`, `now-playing-track`, `playlist`, `play-pause`, `duration`, `position`, `volume`.
  * Формат пуша подписчикам:
    ```json
    { "event": "<endpoint>-update", "new": <value> }
    ```

---

## 6. ПЛАТФОРМЕННОЕ

### 6.1. Аудио-контролы ОС (SMTC Windows и MPRIS Linux)
* **Windows ([smtc_windows](file:///E:/Projects/quark/pubspec.yaml#L20), [native_control.dart:69-130](file:///E:/Projects/quark/lib/services/native_controls/native_control.dart#L69-L130)):**
  * Использует `SMTCWindows` (через Rust FFI `flutter_rust_bridge`). Инициализирует System Media Transport Controls Windows 10/11: кнопки Play, Pause, Next, Prev, Stop, пересылает обложку и метаданные (Title, Artist, Album, Thumbnail).
  * **Что понадобится на JVM:** Windows Media SMTC API отсутствует в базовой Java. Потребуется нативная библиотека через JNA/FFM (Project Panama) или готовые обёртки SMTC для JVM (например, `jSystemMediaTransportControls` или написание JNI-модуля на C++/WinRT).
* **Linux ([audio_service_mpris](file:///E:/Projects/quark/pubspec.yaml#L27), [linux_audio_control.dart](file:///E:/Projects/quark/lib/services/native_controls/linux_audio_control.dart)):**
  * В [main.dart:53](file:///E:/Projects/quark/lib/main.dart#L53) вызывается `AudioServiceMpris.registerWith()`, связывающий Dart `audio_service` с интерфейсом D-Bus `org.mpris.MediaPlayer2.quark`. Экспортирует метаданные трека и обрабатывает Play, Pause, Stop, Seek, Next, Previous.
  * **Что понадобится на JVM:** Библиотека `dbus-java` (`hypfvieh/dbus-java`), реализующая D-Bus интерфейсы `org.mpris.MediaPlayer2` и `org.mpris.MediaPlayer2.Player`.

---

### 6.2. Динамический цвет окна Linux: `app/window_style`
* **Dart-сторона ([dynamic_window_color_linux.dart](file:///E:/Projects/quark/lib/services/dynamic_window_color_linux.dart)):**
  * Метод-канал: `MethodChannel('app/window_style')` ([L17](file:///E:/Projects/quark/lib/services/dynamic_window_color_linux.dart#L17)).
  * Вызовы:
    * `setHeaderColor`: передаёт цвета градиента `{r1, g1, b1, r2, g2, b2, r3, g3, b3, transition_speed}` ([L70-L82](file:///E:/Projects/quark/lib/services/dynamic_window_color_linux.dart#L70-L82)).
    * `setHeaderTitle`: передаёт `{"title": title}` ([L92](file:///E:/Projects/quark/lib/services/dynamic_window_color_linux.dart#L92)).
    * `setHeaderWidth`: передаёт высоту шапки `{"height": ...}` ([L101-L103](file:///E:/Projects/quark/lib/services/dynamic_window_color_linux.dart#L101-L103)).
* **Нативная часть в Linux ([linux/runner/my_application.cc:10-174](file:///E:/Projects/quark/linux/runner/my_application.cc#L10-L174)):**
  * Приложение перехватывает GNOME Shell на X11 (`gdk_x11_screen_get_window_manager_name`) ([L184-L194](file:///E:/Projects/quark/linux/runner/my_application.cc#L184-L194)) и заменяет стандартную рамку на `GtkHeaderBar` ([L200](file:///E:/Projects/quark/linux/runner/my_application.cc#L200)).
  * На канале `app/window_style` ([L367-L372](file:///E:/Projects/quark/linux/runner/my_application.cc#L367-L372)) в `changeColor()` формируется CSS-правило:
    ```css
    headerbar {
      background-image: linear-gradient(to right, rgb(r1, g1, b1), rgb(r2, g2, b2), rgb(r3, g3, b3));
      transition: all <transition_speed>s ease;
    }
    ```
    CSS динамически скармливается в `GtkCssProvider` ([L91](file:///E:/Projects/quark/linux/runner/my_application.cc#L91)).
* **Что понадобится на JVM:** В Compose for Desktop нет необходимости воевать с нативным GTK3 CSD. Окно делается `undecorated = true` (кастомный заголовок окна пишется целиком на Compose) либо используется системная рамка, а градиент анимируется через стандартный Compose `Brush.horizontalGradient` с `animateColorAsState`.

---

### 6.3. Нативные папки `windows/`, `linux/`, `macos/` — ревизия модификаций
* **`linux/`:**
  * Сильно модифицирован [my_application.cc](file:///E:/Projects/quark/linux/runner/my_application.cc): добавлены поля `GtkCssProvider`, `GtkHeaderBar`, регистрация `app/window_style`, генерация динамических GTK CSS-стилей для HeaderBar и кнопок окна.
* **`windows/`:**
  * [win32_window.cpp](file:///E:/Projects/quark/windows/runner/win32_window.cpp): модифицирован метод `UpdateTheme` — добавлен вызов Win32 API:
    ```cpp
    DwmSetWindowAttribute(window, DWMWA_USE_IMMERSIVE_DARK_MODE, &enable_dark_mode, sizeof(enable_dark_mode));
    ```
    для принудительного включения тёмной темы системного заголовка окна Win32.
  * [main.cpp](file:///E:/Projects/quark/windows/runner/main.cpp): изменён размер окна по умолчанию `Size(850, 720)` и заголовок `L"quark"`.
* **`macos/`:**
  * [MainFlutterWindow.swift:11-18](file:///E:/Projects/quark/macos/Runner/MainFlutterWindow.swift#L11-L18):
    ```swift
    self.titlebarAppearsTransparent = true
    self.titleVisibility = .hidden
    self.styleMask.insert(.fullSizeContentView)
    self.backgroundColor = NSColor.clear
    self.hasShadow = false
    ```
    Настроено полноэкранное прозрачное окно с контентом под строкой заголовка macOS.
* **Что понадобится на JVM:**
  * Windows: DWM атрибут `DWMWA_USE_IMMERSIVE_DARK_MODE` (20) выставляется через JNA вызовом `dwmapi.dll!DwmSetWindowAttribute`.
  * macOS: флаги `NSWindow` (`fullSizeContentView`, `titlebarAppearsTransparent`) применяются через JNA на указателе окна `AWT/Swing`.

---

### 6.4. Прочие платформенные зависимости

1. **`file_picker` ([pubspec.yaml:19](file:///E:/Projects/quark/pubspec.yaml#L19)):**
   * Используется для выбора треков, папок, CUE-файлов, cookies и директорий экспорта ([main.dart:10](file:///E:/Projects/quark/lib/main.dart#L10), [album.dart:5](file:///E:/Projects/quark/lib/widgets/media_cards/album.dart#L5), [playlist.dart:5](file:///E:/Projects/quark/lib/widgets/media_cards/playlist.dart#L5)).
   * **На JVM:** стандартный AWT `java.awt.FileDialog` (нативно на Windows/macOS) или легковесная кроссплатформенная библиотека `LWJGL/TinyFileDialogs`.
2. **`path_provider` ([files.dart:11-31](file:///E:/Projects/quark/lib/services/files.dart#L11-L31)):**
   * Использует 3 директории:
     * `getApplicationCacheDirectory()`: хранение `api.port`, кэш обложек, кэшированные FLAC-треки `cisum_xednay_krauq<id>.flac`.
     * `getApplicationSupportDirectory()`: база Drift `quark.db`, Hive settings.
     * `getApplicationDocumentsDirectory()`.
   * **На JVM:** прямая реализация резолвера путей:
     * Windows: `%LOCALAPPDATA%/quark` (cache) и `%APPDATA%/quark` (support).
     * Linux: `$XDG_CACHE_HOME/quark` (`~/.cache/quark`) и `$XDG_DATA_HOME/quark` (`~/.local/share/quark` или `~/.config/quark`).
     * macOS: `~/Library/Caches/quark` и `~/Library/Application Support/quark`.
3. **`desktop_drop` ([drag_drop.dart](file:///E:/Projects/quark/lib/widgets/drag_drop.dart)):**
   * Drag-and-drop файлов в плеер ([L49-L56](file:///E:/Projects/quark/lib/widgets/drag_drop.dart#L49-L56)).
   * **На JVM:** в Compose for Desktop / Swing реализуется нативно через `java.awt.dnd.DropTarget` и чтение `DataFlavor.javaFileListFlavor`.
4. **`flutter_inappwebview` ([yandex_login.dart:10](file:///E:/Projects/quark/lib/widgets/yandex_music_integration/yandex_login.dart#L10)):**
   * Используется для OAuth-авторизации Яндекса: открывает `https://oauth.yandex.ru/authorize?response_type=token&client_id=23cabbbdc6cd418abb4b39c32c41195d` ([L28-L30](file:///E:/Projects/quark/lib/widgets/yandex_music_integration/yandex_login.dart#L28-L30)) и в `onLoadStop` перехватывает фрагмент URL `#access_token=...&expires_in=...` ([L300-L315](file:///E:/Projects/quark/lib/widgets/yandex_music_integration/yandex_login.dart#L300-L315)).
   * На Linux WebView2 недоступен, поэтому предусмотрен fallback: открытие ссылки в системном браузере и ручной ввод токена ([L103-L250](file:///E:/Projects/quark/lib/widgets/yandex_music_integration/yandex_login.dart#L103-L250)).
   * **На JVM:**
     * Вариант 1 (рекомендуемый): локальный временный Ktor HTTP-сервер на `http://localhost:<port>/callback`, открытие системного браузера через `Desktop.getDesktop().browse(uri)` и перехват токена.
     * Вариант 2: встроенный браузер через JCEF (JetBrains Chromium Embedded Framework), однако он сильно утяжеляет размер дистрибутива.
     * Сохранение ручного ввода токена как запасного варианта.
5. **`flutter_discord_rpc` ([discord_rpc.dart](file:///E:/Projects/quark/lib/services/discord_rpc.dart)):**
   * Управление Presence в Discord через локальный Named Pipe / UNIX Domain Socket.
   * **На JVM:** библиотеки `Java-DiscordRPC` или чистый Kotlin Multiplatform клиент IPC через сокеты (`\\.\pipe\discord-ipc-0` на Windows и `/tmp/discord-ipc-0` на Unix).
