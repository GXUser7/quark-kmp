# План полной миграции quark на Compose Desktop

Рабочий план переноса, разбитый на этапы с конкретными файлами. Никаких прототипов и
песочниц: каждый этап оставляет после себя код, который идёт в релиз. Порядок выбран так,
чтобы приложение оставалось запускаемым после каждого этапа, а самое рискованное
(аудио-движок) решалось первым, пока не на что его натягивать.

Контекст и разбор оригинала — в [MIGRATION.md](MIGRATION.md) и [analysis/](analysis/).

## Ключевые решения

**Аудио-движок — libmpv через JNA.** Flutter-версия в режиме `Just Audio MK` уже играет через
libmpv (`media_kit`), так что поведение кодеков, gapless и ресемплинга останется тем же, что
слышат текущие пользователи. mpv даёт `speed` без сдвига тона, честный gapless через
`--gapless-audio`, и сам ходит в сеть по http(s). Дистрибутив — `libmpv-2.dll` (~40 МБ) против
~150 МБ у полного VLC. `AudioEngine` остаётся интерфейсом: второй бэкенд на VLCJ можно
добавить позже, как в оригинале был переключатель трёх движков.

**Одна база вместо двух.** Hive уходит целиком: реляционное — в SQLDelight, настройки — в один
JSON-файл, пишущийся атомарно. Схема SQLDelight уже совпадает с Drift, поэтому существующий
`quark.db` подхватывается как есть.

**Один источник состояния.** `StateFlow` вместо 38 `ValueNotifier` и 180 `setState`. UI —
чистые Composable, принимающие иммутабельный стейт.

**Никаких четырёх копий плеера.** Один state holder, три раскладки, переключаемые по размеру окна.

---

## Этап 1. Инфраструктура

Фундамент, на который встаёт всё остальное.

| Задача | Файлы |
| --- | --- |
| Убрать песочницу из `app` | удалить `SilentEngine.kt`, `PlaygroundScreen.kt` |
| Модель настроек вместо `DatabaseStreamerService` | `core/settings/Settings.kt`, `data/settings/SettingsStore.kt` |
| Логирование | `core/log/Log.kt` + slf4j в `app` |
| Драйвер БД и фабрика | `data/db/Database.kt` (JDBC SQLite, создание схемы, открытие legacy-файла) |
| Репозитории поверх SQLDelight | `data/repository/{TrackRepository,PlaylistRepository,ListenStatsRepository,CoverColorRepository}.kt` |
| Тесты репозиториев на in-memory БД | `data/src/commonTest/...` |

## Этап 2. Аудио-движок

| Задача | Файлы |
| --- | --- |
| JNA-биндинг клиентского API mpv | `player/src/jvmMain/.../mpv/LibMpv.kt` |
| Обёртка: свойства, команды, цикл событий | `player/src/jvmMain/.../mpv/MpvHandle.kt` |
| Реализация `AudioEngine` | `player/src/jvmMain/.../MpvAudioEngine.kt` |
| Поиск/поставка `libmpv-2.dll` и `libmpv.so` | `player/src/jvmMain/.../mpv/MpvLibraryLoader.kt` |
| Резолвер источников (локальный файл, сеть, заголовки) | `player/.../TrackSourceResolver.kt` |
| Gapless через `loadfile append` | внутри `MpvAudioEngine` |

## Этап 3. Локальная библиотека

| Задача | Файлы |
| --- | --- |
| Чтение тегов (jaudiotagger), обложка из файла | `data/local/TagReader.kt` |
| Сканер папок, рекурсивный и нет | `data/local/LibraryScanner.kt` |
| Парсер CUE вместо `dart_cue` | `core/cue/CueSheet.kt` + тесты |
| Наблюдение за папками (`WatchService`) | `data/local/DirectoryObserver.kt` |
| Запись тегов (замена `tag_writer.dart`) | `data/local/TagWriter.kt` |

## Этап 4. Яндекс.Музыка

Протокол целиком выписан в [analysis/02](analysis/02-storage-network-platform.md) §5.1.

| Задача | Файлы |
| --- | --- |
| HTTP-клиент, заголовки, ошибки | `network/yandex/YandexClient.kt`, `YandexException.kt` |
| DTO и модели ответов | `network/yandex/dto/*.kt` |
| Аккаунт, плейлисты, треки, альбомы, артисты, поиск | `network/yandex/api/*.kt` |
| Ссылка на файл: v2 `get-file-info` и legacy XML | `network/yandex/api/DownloadInfo.kt` |
| «Моя волна»: сессия, дозагрузка, фидбек | `network/yandex/api/Rotor.kt` |
| Тексты, разбор LRC | `network/yandex/api/Lyrics.kt`, `core/lyrics/Lrc.kt` |
| Кэш ссылок с TTL (замена `TimedCache`) | `core/util/TimedCache.kt` |
| Вход: системный браузер + локальный перехват токена | `app/.../auth/YandexAuth.kt` |

## Этап 5. Остальные источники

| Задача | Файлы |
| --- | --- |
| YouTube Music через `quarkaudio.ru` | `network/ytmusic/YtMusicClient.kt` |
| MusicBrainz + Cover Art Archive | `network/musicbrainz/MusicBrainzClient.kt` |
| Распознавание трека | `network/recognizer/RecognizerClient.kt` |

## Этап 6. Обложки и цвет

| Задача | Файлы |
| --- | --- |
| Дисковый кэш обложек по md5 | `data/images/CoverCache.kt` |
| Блюр через Skia вместо изолята с `image` | `app/.../image/Blur.kt` |
| Извлечение акцентных цветов (порт `AccentColorService`) | `core/color/AccentPalette.kt` + тесты |
| Загрузка в Compose | `app/.../image/CoverImage.kt` |

## Этап 7. Системные интеграции

| Задача | Файлы |
| --- | --- |
| Windows SMTC через JNA/WinRT | `platform/src/jvmMain/.../windows/Smtc.kt` |
| Linux MPRIS2 через D-Bus | `platform/src/jvmMain/.../linux/Mpris.kt` |
| Медиа-клавиши и общий фасад | `platform/.../NativeControls.kt` |
| Discord RPC поверх named pipe / unix socket | `platform/.../discord/DiscordRpc.kt` |
| Локальное HTTP+WS API на Ktor Server | `network/localapi/LocalApiServer.kt` |
| Публикация в mDNS (jmdns) и файл `api.port` | `network/localapi/ServiceBroadcast.kt` |
| Drag-and-drop файлов в окно | `app/.../DragAndDrop.kt` |

Локальное API повторяет контракт v0 из `lib/services/local_api/README.md` — у него уже есть
внешние клиенты, ломать нельзя.

## Этап 8. Плеер

| Задача | Файлы |
| --- | --- |
| Тема, палитра, типографика | `app/.../theme/*.kt` |
| State holder поверх `PlayerController` и репозиториев | `app/.../player/PlayerViewModel.kt` |
| Полная раскладка | `app/.../player/FullPlayer.kt` |
| Компактная и мини-раскладки | `app/.../player/CompactPlayer.kt`, `MacroPlayer.kt` |
| Выбор раскладки по размеру окна | `app/.../player/PlayerScreen.kt` |
| Слайдеры позиции и громкости | `app/.../player/Sliders.kt` |
| Очередь с перетаскиванием | `app/.../player/QueuePanel.kt` |

## Этап 9. Экраны контента

| Задача | Файлы |
| --- | --- |
| Список и содержимое плейлиста | `app/.../library/PlaylistScreen.kt` |
| Альбом, артист | `app/.../library/AlbumScreen.kt`, `ArtistScreen.kt` |
| Поиск по локальному и Яндексу | `app/.../search/SearchScreen.kt` |
| Тексты песен с подсветкой строки | `app/.../lyrics/LyricsScreen.kt` |
| Настройки | `app/.../settings/SettingsScreen.kt` |
| Статистика прослушиваний | `app/.../stats/ListenStatsScreen.kt` |
| «Моя волна» и её визуализация | `app/.../vibe/VibeScreen.kt` |
| Порт `vibe.frag` на SkSL | `app/.../vibe/VibeShader.kt` |
| Порт `CometLoader` | `app/.../ui/CometLoader.kt` |

## Этап 10. Дистрибутив

| Задача | Файлы |
| --- | --- |
| Иконки, метаданные, MSI и deb через jpackage | `app/build.gradle.kts` |
| Упаковка нативных библиотек mpv | `app/build.gradle.kts`, `installer/` |
| Перенос данных из Flutter-установки | `data/migration/LegacyImport.kt` |
| Прогон на Windows и Linux | — |

---

## Порядок и зависимости

```
1 Инфраструктура
      |
      +-- 2 Аудио-движок ------+
      |                        |
      +-- 3 Локальная бибkа ---+--- 8 Плеер --- 9 Экраны --- 10 Дистрибутив
      |                        |
      +-- 4 Яндекс ------------+
      |     |                  |
      |     +-- 5 Источники ---+
      |                        |
      +-- 6 Обложки -----------+
      |                        |
      +-- 7 Системное ---------+
```

Этапы 2–7 после первого независимы друг от друга и могут идти в любом порядке; UI-этапы
требуют, чтобы под ними уже лежали движок, библиотека и обложки.
