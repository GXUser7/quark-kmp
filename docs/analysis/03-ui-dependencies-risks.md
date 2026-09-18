# Отчёт по архитектурному анализу Quark (Flutter/Dart -> Kotlin Multiplatform + Compose Desktop)

---

## 7. UI: Экраны и виджеты в `lib/widgets/`

### 7.1. Иерархия экранов и переключение между ними

Архитектура экранов в проекте разделена на два уровня: **Dashboard (главное меню)** в [`lib/main.dart`](file:///E:/Projects/quark/lib/main.dart) и **Плеер (`PlaylistPage`)** в [`lib/playlist_page_router.dart`](file:///E:/Projects/quark/lib/playlist_page_router.dart), переключаемые через `Navigator.push`.

#### 1. Главный экран ([`MainPage`](file:///E:/Projects/quark/lib/main.dart#L85-L838))
В корневом виджете [`_MainPageState`](file:///E:/Projects/quark/lib/main.dart#L92) навигация построена **не через стандартный роутинг Flutter**, а через многослойный `Stack` с набором независимых булевых флагов состояния:
* `loginView` ([строки 642–669](file:///E:/Projects/quark/lib/main.dart#L642-L669)) — оверлей авторизации Яндекс.Музыки ([`YandexLogin`](file:///E:/Projects/quark/lib/widgets/yandex_music_integration/yandex_login.dart#L12)).
* `playlistView` ([строки 671–704](file:///E:/Projects/quark/lib/main.dart#L671-L704)) — оверлей библиотеки и плейлистов пользователя ([`YandexPlaylists`](file:///E:/Projects/quark/lib/widgets/yandex_music_integration/yandex_playlists_widget.dart#L8)).
* `dragAndDropView` ([строки 735–775](file:///E:/Projects/quark/lib/main.dart#L735-L775)) — оверлей импорта cookie YouTube Music через Drag & Drop ([`GlassDropZone`](file:///E:/Projects/quark/lib/widgets/drag_drop.dart#L6) / [`YTMusicPlaylists`](file:///E:/Projects/quark/lib/widgets/ytmusic_playlist_widget.dart#L11)).
* `settingsView` ([строки 777–804](file:///E:/Projects/quark/lib/main.dart#L777-L804)) — модальное окно настроек ([`Settings`](file:///E:/Projects/quark/lib/widgets/settings.dart#L18)).
* `isFileLoading` ([строки 806–818](file:///E:/Projects/quark/lib/main.dart#L806-L818)) — блокирующий оверлей сканирования папок с кастомным шейдерным лоадером [`CometLoader`](file:///E:/Projects/quark/lib/widgets/comets.dart#L73).
* `mdnsView` ([строки 705–734](file:///E:/Projects/quark/lib/main.dart#L705-L734), закомментирован) — поиск устройств в локальной сети (`QuarkControlDevices`).

Переключение между этими модальными слоями происходит через `AnimatedSwitcher(duration: Duration(milliseconds: 300))` с эффектом появления поверх подложки `Container(color: Colors.black.withAlpha(25))`.

При выборе трека или плейлиста вызывается метод `playlistRoute(PlayerPlaylist playlist)` ([строки 211–245](file:///E:/Projects/quark/lib/main.dart#L211-L245)), который обновляет состояние синглтона [`Player.player`](file:///E:/Projects/quark/lib/services/player/player.dart#L91) и делает push экрана плеера:
```dart
Navigator.push(context, CupertinoPageRoute(settings: RouteSettings(name: "/player"), builder: (context) => PlaylistPage()));
```

#### 2. Адаптивный роутер плеера ([`PlaylistPage`](file:///E:/Projects/quark/lib/playlist_page_router.dart#L17-L69))
В [`_PlaylistPageState.build`](file:///E:/Projects/quark/lib/playlist_page_router.dart#L50-L68) на базе `MediaQuery.of(context).size` динамически выбирается один из четырёх плееров:
```dart
final bool isCompactState = size.height <= 300;
final bool isAndroid = Platform.isAndroid && query.orientation == Orientation.portrait;

return AnimatedSwitcher(
  duration: Duration(milliseconds: 500),
  child: isAndroid
      ? AndroidWidget()
      : isCompactState
          ? size.height < 80 ? MacroPlayer() : MiniPlayerWidget()
          : MainPlayer(),
);
```

---

### 7.2. Разбор иерархии виджетов плееров

#### 1. [`MainPlayer`](file:///E:/Projects/quark/lib/widgets/players_widgets/main_player.dart#L39) (1627 строк)
Полноразмерный десктопный плеер:
* **Фон:** [`CoverBlurredImage`](file:///E:/Projects/quark/lib/services/cached_images.dart#L835) с размытой обложкой текущего трека и оверлеем затемнения `Colors.black.withOpacity(0.5)`. Переход между обложками — `AnimatedSwitcher` с `FadeTransition` ([строка 526](file:///E:/Projects/quark/lib/widgets/players_widgets/main_player.dart#L526)).
* **Центральный блок:** Анимированный сдвиг влево на `playerPadding = 400.0` при открытии боковой панели плейлиста ([строка 514](file:///E:/Projects/quark/lib/widgets/players_widgets/main_player.dart#L514)).
  * Обложка: [`CoverImage`](file:///E:/Projects/quark/lib/services/cached_images.dart#L735) (270x270 px) с поддержкой полноэкранного зума по клику (`toggleCover()`).
  * Метаданные: название трека, кликабельный альбом (открывает [`AlbumInforWidget`](file:///E:/Projects/quark/lib/widgets/media_cards/album.dart#L174)), кликабельный исполнитель (открывает диалог выбора артиста [`WarningMessage`](file:///E:/Projects/quark/lib/widgets/players_widgets/main_player.dart#L1440) и страницу [`ArtistInfoWidget`](file:///E:/Projects/quark/lib/widgets/yandex_music_integration/yandex_widgets.dart#L352)).
  * Позиционирование: [`ProgressWidget`](file:///E:/Projects/quark/lib/widgets/players_widgets/slider_widget.dart#L8) с колесом мыши (`Listener` с `onPointerSignal` для перемотки на ±10 сек, [строки 708–726](file:///E:/Projects/quark/lib/widgets/players_widgets/main_player.dart#L708-L726)).
  * Громкость: [`VolumeWidget`](file:///E:/Projects/quark/lib/widgets/players_widgets/slider_widget.dart#L527) (также реагирует на скролл колесом мыши на ±2.5%, [строки 850–866](file:///E:/Projects/quark/lib/widgets/players_widgets/main_player.dart#L850-L866)).
  * Панель действий: кнопки плейлиста, shuffle, like, upload, repeat и раскрывающаяся капсула [`AnimatedExpand`](file:///E:/Projects/quark/lib/overrided_libraries/animated_expand/lib/animated_expand.dart#L57) (регулировка скорости 0.75x–1.2x, добавление папки через [`FilePicker`](file:///E:/Projects/quark/lib/widgets/players_widgets/main_player.dart#L1117), открытие [`Settings`](file:///E:/Projects/quark/lib/widgets/settings.dart#L18), кнопка выхода).
* **Боковая шторка (Drawer):** `Positioned(left: 0)` с анимацией `SlideTransition(playlistOffsetAnimation)` ([строка 1219](file:///E:/Projects/quark/lib/widgets/players_widgets/main_player.dart#L1219)). Отображает один из 3-х виджетов:
  1. [`PlaylistOverlay`](file:///E:/Projects/quark/lib/widgets/playlist/playlist_widget.dart#L68) — список треков.
  2. [`LyricsView`](file:///E:/Projects/quark/lib/widgets/yandex_music_integration/lyrics_playlist_extension.dart#L24) — синхронизированный текст песни.
  3. [`AutomatedAdditionalInformationWidget`](file:///E:/Projects/quark/lib/widgets/yandex_music_integration/additional_information_playlist_extension.dart#L10) — техническая информация о треке.
* **Кастомный рендер-элемент:** [`MarkItemWidget`](file:///E:/Projects/quark/lib/widgets/players_widgets/main_player.dart#L1375) реализует нестандартный `createElement() => MarkElement(this)`, наследуясь напрямую от [`ComponentElement`](file:///E:/Projects/quark/lib/widgets/players_widgets/main_player.dart#L1384) с ручным вызовом `markNeedsBuild()` при наведении мыши.
* **Триггер открытия мыши:** `MouseRegion` шириной 50 px у левого края экрана ([строки 1195–1215](file:///E:/Projects/quark/lib/widgets/players_widgets/main_player.dart#L1195-L1215)), открывающий плейлист при движении курсора к левой границе.

#### 2. [`MacroPlayer`](file:///E:/Projects/quark/lib/widgets/players_widgets/macro_player.dart#L12) (731 строка)
Ультракомпактный микроплеер фиксированной высоты (45–55 px) для мини-окон или нижних плашек.
* Разделяется по ширине экрана:
  * `width > 460`: [`MacroPlayer3`](file:///E:/Projects/quark/lib/widgets/players_widgets/macro_player.dart#L43) — обложка, бегущая строка с названием, кнопки Previous/Play/Next, слайдер прогресса, лайк, громкость.
  * `width <= 460`: [`MacroPlayer2`](file:///E:/Projects/quark/lib/widgets/players_widgets/macro_player.dart#L396) — только обложка и базовые кнопки управления.
* Регистрирует слушатели напрямую в синглтонах [`Player.player`](file:///E:/Projects/quark/lib/services/player/player.dart#L91) и [`YandexMusicSingleton`](file:///E:/Projects/quark/lib/services/yandex_music/yandex_music_singleton.dart#L18).

#### 3. [`MiniPlayerWidget`](file:///E:/Projects/quark/lib/widgets/players_widgets/mini_player.dart#L18) (502 строки)
Горизонтальный плеер средней компактности (высота 80–300 px):
* Компактный двухколоночный layout: слева квадратная обложка 100x100 px, справа название, кнопки управления, слайдер позиции и слайдер громкости.
* Внутренний класс состояния ошибочно продублирован с именем `_PlaylistPage1State` (рудимент копипаста из `main_player.dart`).

#### 4. [`AndroidWidget`](file:///E:/Projects/quark/lib/widgets/players_widgets/android_player.dart#L32) (1235 строк)
Мобильный вертикальный layout:
* Содержит обложки в виде карусели [`CarouselSlider`](file:///E:/Projects/quark/lib/widgets/players_widgets/android_player.dart#L551).
* При свайпе страницы карусели вызывается переключение трека `buttonCarouselController.jumpToPage(...)` ([строка 321](file:///E:/Projects/quark/lib/widgets/players_widgets/android_player.dart#L321)).
* Стилизация шрифтов через `GoogleFonts.lexend(...)`.
* Шторка плейлиста выезжает снизу, а не сбоку.

#### 5. [`slider_widget.dart`](file:///E:/Projects/quark/lib/widgets/players_widgets/slider_widget.dart#L1) (660 строк)
* [`ProgressWidget`](file:///E:/Projects/quark/lib/widgets/players_widgets/slider_widget.dart#L8): Интерактивный ползунок на базе пакета `interactive_slider` с таймингами `0:00 / 3:45`. Поддерживает расчёт процентов и обновление позиции через `Player.player.seek`.
* [`ProgressWidgetNative`](file:///E:/Projects/quark/lib/widgets/players_widgets/slider_widget.dart#L202): Альтернатива на базе стандартного `Slider`. Использует кастомный [`CustomEqualHeightSliderTrackShape`](file:///E:/Projects/quark/lib/widgets/players_widgets/slider_widget.dart#L437), наследующий [`SliderTrackShape`](file:///E:/Projects/quark/lib/widgets/players_widgets/slider_widget.dart#L437) и вручную рисующий скруглённые `RRect` активной и неактивной дорожек на `PaintingContext.canvas` ([строки 503–524](file:///E:/Projects/quark/lib/widgets/players_widgets/slider_widget.dart#L503-L524)).
* [`VolumeWidget`](file:///E:/Projects/quark/lib/widgets/players_widgets/slider_widget.dart#L527) и [`VolumeWidgetNative`](file:///E:/Projects/quark/lib/widgets/players_widgets/slider_widget.dart#L580): Регулировка громкости от 0.0 до 1.0 с иконками `Icons.volume_down` / `volume_up`.

#### 6. [`playlist/playlist_widget.dart`](file:///E:/Projects/quark/lib/widgets/playlist/playlist_widget.dart#L1) (1178 строк)
Боковая панель треклиста ([`PlaylistOverlay`](file:///E:/Projects/quark/lib/widgets/playlist/playlist_widget.dart#L68)):
* Реализует смахивание для закрытия через `GestureDetector(onHorizontalDragEnd: ...)` ([строка 437](file:///E:/Projects/quark/lib/widgets/playlist/playlist_widget.dart#L437)).
* Поиск: текстовое поле с debounce 500 мс (`_searchDebounceTimer`), при вводе фильтрует текущий плейлист, а при включённой настройке `yandexMusicSearch` отправляет сетевой запрос к API Яндекс.Музыки с `CancelToken` ([строки 212–270](file:///E:/Projects/quark/lib/widgets/playlist/playlist_widget.dart#L212-L270)).
* Быстрая фильтрация по альбомам и исполнителям через горизонтальные плашки `_buildCategoryButtons()` ([строки 373–391](file:///E:/Projects/quark/lib/widgets/playlist/playlist_widget.dart#L373-L391)).
* Рендеринг списка: кастомная линеаризация списка `cachedFlatList2` ([строки 407–430](file:///E:/Projects/quark/lib/widgets/playlist/playlist_widget.dart#L407-L430)), совмещающая элементы плейлиста (`_TrackItem`), разделитель очереди (`_QueueDividerItem`) и треки ручной очереди воспроизведения (`_QueuedTrackItem`).
* Список обёрнут в `CustomScrollView` + `SliverReorderableList` с перетаскиванием (drag-and-drop сортировка треков через `moveTrack`, [строки 547–561](file:///E:/Projects/quark/lib/widgets/playlist/playlist_widget.dart#L547-L561)).
* Автоскролл к играющему треку: `scrollToCurrentTrack()` с вычислением фиксированного оффсета (высота элемента 61 px, [строка 307](file:///E:/Projects/quark/lib/widgets/playlist/playlist_widget.dart#L307)).

#### 7. Семейство медиа-карточек ([`lib/widgets/media_cards/`](file:///E:/Projects/quark/lib/widgets/media_cards/))
Модульный UI для сущностей аудио-библиотеки:
* [`tools.dart`](file:///E:/Projects/quark/lib/widgets/media_cards/tools.dart): Обобщенные модели интерфейса: [`WidgetInteraction`](file:///E:/Projects/quark/lib/widgets/media_cards/tools.dart#L4) (иконка, callback, размер, цвет), [`WidgetSubInfo`](file:///E:/Projects/quark/lib/widgets/media_cards/tools.dart#L29), [`MediaCover`](file:///E:/Projects/quark/lib/widgets/media_cards/tools.dart#L53) (источники `url`, `file`, `none`), диалог подтверждения [`WarningMessage`](file:///E:/Projects/quark/lib/widgets/media_cards/tools.dart#L73) и подзаголовок [`SubHeaderWidget`](file:///E:/Projects/quark/lib/widgets/media_cards/tools.dart#L177).
* [`track.dart`](file:///E:/Projects/quark/lib/widgets/media_cards/track.dart): Модель [`TrackInfor`](file:///E:/Projects/quark/lib/widgets/media_cards/track.dart#L14) и карточки треков с контекстными действиями.
* [`album.dart`](file:///E:/Projects/quark/lib/widgets/media_cards/album.dart): Полноэкранный просмотр альбома [`AlbumInforWidget`](file:///E:/Projects/quark/lib/widgets/media_cards/album.dart#L174): заголовок с размытым градиентом акцентного цвета альбома, список дисков [`AlbumDiscInfor`](file:///E:/Projects/quark/lib/widgets/media_cards/album.dart#L28), группировка треков, экспорт, шаринг, переход к артисту.
* [`artist.dart`](file:///E:/Projects/quark/lib/widgets/media_cards/artist.dart): Страница артиста с популярными треками, дискографией, концертами и похожими исполнителями.
* [`playlist.dart`](file:///E:/Projects/quark/lib/widgets/media_cards/playlist.dart): Экран плейлиста [`PlaylistInfoWidget`](file:///E:/Projects/quark/lib/widgets/media_cards/playlist.dart#L104) с редактированием метаданных, сменой обложки и треклистом.

#### 8. Настройки ([`Settings`](file:///E:/Projects/quark/lib/widgets/settings.dart#L18))
Модальный диалог в стиле Glassmorphism с `BackdropFilter(filter: ImageFilter.blur(sigmaX: 75, sigmaY: 75))` ([строка 42](file:///E:/Projects/quark/lib/widgets/settings.dart#L42)). Содержит секции:
* `_LocalSettings`: выбор аудио-бэкенда (`Standart` vs `Just Audio MK`), скорость анимаций, директории сканирования.
* `_YandexMusicSettings`: токен, предзагрузка, битрейт/качество (`nq` / `hq`), поиск.
* `_AdvancedSettings`: переключатели Discord RPC, SMTC (Windows), MPRIS (Linux), порт и статус Local API HTTP/WS сервера.
* `_DebugSettings`: пасхалка, открывается после 5 кликов по заголовку `Preferences` ([строка 84](file:///E:/Projects/quark/lib/widgets/settings.dart#L84)).

#### 9. Статистика прослушиваний ([`ListenStatsWidget`](file:///E:/Projects/quark/lib/widgets/listen_stats/listen_stats.dart#L24))
Экран статистики времени прослушивания и чартов треков на базе SQLite БД ([`ListenLogger`](file:///E:/Projects/quark/lib/services/database/listen_logger.dart#L8)). В нижней части закреплён `MacroPlayer` ([строка 66](file:///E:/Projects/quark/lib/widgets/listen_stats/listen_stats.dart#L66)).

#### 10. Модули интеграции Яндекс.Музыки ([`lib/widgets/yandex_music_integration/`](file:///E:/Projects/quark/lib/widgets/yandex_music_integration/))
* [`yandex_login.dart`](file:///E:/Projects/quark/lib/widgets/yandex_music_integration/yandex_login.dart): Проверяет наличие WebView2 через `WebViewEnvironment.getAvailableVersion()` ([строка 81](file:///E:/Projects/quark/lib/widgets/yandex_music_integration/yandex_login.dart#L81)). При наличии поднимает `InAppWebView` для перехвата OAuth-токена из hash URL (`#access_token=...`), а на Linux или при отсутствии WebView2 показывает кнопку открытия браузера и текстовое поле ручного ввода токена с валидацией.
* [`yandex_playlists_widget.dart`](file:///E:/Projects/quark/lib/widgets/yandex_music_integration/yandex_playlists_widget.dart): Сетка плейлистов пользователя и альбома «Мне нравится».
* [`yandex_widgets.dart`](file:///E:/Projects/quark/lib/widgets/yandex_music_integration/yandex_widgets.dart) (2233 строки): Монолит с карточками артистов, релизов, концертов и кнопками. Включает `OpenContainer` ([строка 506](file:///E:/Projects/quark/lib/widgets/yandex_music_integration/yandex_widgets.dart#L506)) из пакета `animations` для модального раскрытия карточек.
* [`lyrics_playlist_extension.dart`](file:///E:/Projects/quark/lib/widgets/yandex_music_integration/lyrics_playlist_extension.dart): Построчный просмотр текстов песен с подсветкой текущей строки по таймингам.
* [`my_wave_playlist_extension.dart`](file:///E:/Projects/quark/lib/widgets/yandex_music_integration/my_wave_playlist_extension.dart): Меню переключения настроек потока «Моя волна».

---

### 7.3. Специализированный рендеринг, шейдеры и эффекты

#### 1. Шейдер `vibe.frag` и аниматор `vibe_animation.dart`
Файл [`shaders/vibe.frag`](file:///E:/Projects/quark/shaders/vibe.frag) (208 строк) реализует процедурную органическую визуализацию «Моей волны» на базе 3D-шума Симплекса (`snoise3`, [строка 25](file:///E:/Projects/quark/shaders/vibe.frag#L25)) и треугольного шума (`triNoise3D`, [строка 86](file:///E:/Projects/quark/shaders/vibe.frag#L86)).

* **Передаваемые Uniform-переменные:**
  Передаются последовательным заполнением массива float через `shader.setFloat(idx++, v)` в [`_VibePainter.paint`](file:///E:/Projects/quark/lib/widgets/yandex_music_integration/vibe_animation.dart#L23-L46):
  1. `uScreenSize` (`vec2`, floats 0..1): ширина и высота холста (`size.width`, `size.height`).
  2. `uTime` (`float`, float 2): текущее время анимации в секундах.
  3. `uScale` (`float`, float 3): масштаб эффекта (по умолчанию 0.38).
  4. `uBgBrightness` (`float`, float 4): яркость фона (в коде передаётся 0.0).
  5. `uColor0` .. `uColor5` (`vec3` x 6, floats 5..22): RGB-компоненты 6-ти цветов, определяющих градиенты 3-х вращающихся световых сгустков (blobs).
  6. `uRotation0` .. `uRotation2` (`vec3` x 3, floats 23..31): 3 случайных трехмерных вектора `(x, y, z)` для угловых скоростей и осей вращения шума.
* **Откуда берутся цвета:**
  Вспомогательный класс [`PaletteAnimator`](file:///E:/Projects/quark/lib/widgets/yandex_music_integration/vibe_animation.dart#L233) принимает начальный оттенок `baseHue` (по умолчанию 200). Он формирует 6 объектов [`ColorLerp`](file:///E:/Projects/quark/lib/widgets/yandex_music_integration/vibe_animation.dart#L207) (`bottomStart/End`, `middleStart/End`, `topStart/End`), преобразуя HSL в RGB через формулу `_hslToRgb` ([строка 298](file:///E:/Projects/quark/lib/widgets/yandex_music_integration/vibe_animation.dart#L298)). Каждый цветовой канал интерполируется отдельным скалярным интерполятором [`ScalarLerp`](file:///E:/Projects/quark/lib/widgets/yandex_music_integration/vibe_animation.dart#L182) с длительностью перехода 3000 мс.
* **Как анимируется:**
  В [`_VibeWidgetState`](file:///E:/Projects/quark/lib/widgets/yandex_music_integration/vibe_animation.dart#L74) запущен бесконечный `AnimationController(vsync: this, duration: Duration(days: 1))..repeat()`. На каждый тик вычисляется дельта времени `dtMs`, обновляется интерполяция цветов `_palette.tick(dtMs)`, инкрементируется фаза `_time = (_time + speed * dtMs / 1000.0) % 86400.0`, и вызывается `setState()`, перерисовывающий холст `CustomPaint` через `Canvas.drawRect` с кастомным шейдером.

#### 2. Кэш обложек, блюр и MD5 ([`cached_images.dart`](file:///E:/Projects/quark/lib/services/cached_images.dart#L1))
* **Кэширование на диск:** Класс [`ImageCacheService`](file:///E:/Projects/quark/lib/services/cached_images.dart#L178) сохраняет файлы в папку `<ApplicationCacheDirectory>/cached_images/`. Имя каждого файла на диске — это MD5-хэш (`md5.convert(utf8.encode(uri)).toString()`, [строка 293](file:///E:/Projects/quark/lib/services/cached_images.dart#L293)). Загрузка по сети идет через `Dio().get<Uint8List>(uri)`.
* **Вшитые обложки:** Если трек локальный (`CoverType.builtIn`), метаданные читаются через `readMetadata(File(path), getImage: true)` из `audio_metadata_reader`, и массив байтов обложки записывается на диск под MD5-хэшем пути трека ([строки 201–212](file:///E:/Projects/quark/lib/services/cached_images.dart#L201-L212)).
* **Генерация Blur:** Выполняется в сервисе [`ImageBlurService`](file:///E:/Projects/quark/lib/services/cached_images.dart#L20). Имя размытого файла имеет постфикс `"${key}_b95"`. Если размытая копия отсутствует, она вычисляется **в отдельном фоновом изоляте** `Isolate.run` ([строка 159](file:///E:/Projects/quark/lib/services/cached_images.dart#L159)), чтобы не блокировать UI:
  1. Декодирование картинки через `img.decodeImage`.
  2. Ресайз до ширины 150 px через `img.copyResize(image, width: 150)`.
  3. Размытие по Гауссу через `img.gaussianBlur(newResizedImage, radius: 25)`.
  4. Сжатие в JPEG с качеством 70 (`img.encodeJpg(blurred, quality: 70)`).
* **Кастомные ImageProvider:** Для интеграции с движком Flutter написаны наследники `ImageProvider`: [`CachedImageProvider`](file:///E:/Projects/quark/lib/services/cached_images.dart#L540), [`CachedBlurredImageProvider`](file:///E:/Projects/quark/lib/services/cached_images.dart#L599) и [`MemoryBytesImageProvider`](file:///E:/Projects/quark/lib/services/cached_images.dart#L663).

#### 3. Палитра и извлечение акцентных цветов (`palette_generator`)
В `pubspec.yaml` объявлен пакет `palette_generator: ^0.3.3+7`, **но в коде проекта он нигде не используется**.
Вместо него автор реализовал собственный легковесный алгоритм извлечения акцентного цвета в [`AccentColorService`](file:///E:/Projects/quark/lib/services/dynamic_window_color_linux.dart#L170):
1. Метод `_getColors(Uint8List imageBytes)` декодирует сырые RGBA-пиксели через движок `ui.instantiateImageCodec`.
2. Пропускает верхние 15% пикселей (`skipRows = height * 0.15`).
3. Делит изображение по ширине на 3 равные вертикальные зоны (`zoneWidth = width / 3`).
4. В каждой зоне усредняет компоненты R, G, B, A, получая 3 базовых цвета ([строки 206–230](file:///E:/Projects/quark/lib/services/dynamic_window_color_linux.dart#L206-L230)).
5. Результат кэшируется в Hive-боксе `accent_colors` под MD5-хэшем URL обложки.

#### 4. Эффект летящих комет ([`comets.dart`](file:///E:/Projects/quark/lib/widgets/comets.dart#L1))
Виджет загрузки [`CometLoader`](file:///E:/Projects/quark/lib/widgets/comets.dart#L73) генерирует 40 частиц-комет (`Comet`), рендеринг которых выполняется через [`CometPainter`](file:///E:/Projects/quark/lib/widgets/comets.dart#L35):
* На каждом кадре очищается холст `canvas.drawRect(..., Paint()..color = Colors.black.withOpacity(0.5))`.
* Каждая комета рисуется линией `canvas.drawLine` с градиентным шейдером `LinearGradient(transparent -> white with opacity)` и закругленным наконечником `StrokeCap.round` ([строки 48–66](file:///E:/Projects/quark/lib/widgets/comets.dart#L48-L66)).
* Позиция кометы инкрементируется `c.x += c.speed * dt`. При вылете за правый край (`c.x - c.length > width`) комета пересоздаётся у левого края (`startX: -c.length`) со случайной скоростью (100–500 px/s), длиной (40–160 px), прозрачностью (0.4–1.0) и толщиной (0.5–2.0 px).

#### 5. Текст и карусели: Marquee, AutoSizeText, CarouselSlider
* **`marquee: ^2.3.0`**: Объявлен в `pubspec.yaml`, но **не импортирован ни в одном файле `lib/`**. Длинные заголовки обрезаются стандартным `TextOverflow.ellipsis`.
* **`auto_size_text: ^3.0.0`**: Применяется в [`listen_stats.dart`](file:///E:/Projects/quark/lib/widgets/listen_stats/listen_stats.dart#L150) (строки 150, 278) и [`media_cards/album.dart`](file:///E:/Projects/quark/lib/widgets/media_cards/album.dart#L607) (строки 607, 823) для адаптивного уменьшения шрифта длинных названий альбомов и статистики без переноса на новую строку.
* **`carousel_slider: ^5.1.2`**: Применяется исключительно в мобильном плеере [`android_player.dart`](file:///E:/Projects/quark/lib/widgets/players_widgets/android_player.dart#L22) (строки 105, 321, 551) для создания горизонтальной свайп-карусели обложек треков с программным управлением через `buttonCarouselController`.

---

## 8. Зависимости: Аудит 55 библиотек из `pubspec.yaml` и их замена на JVM / KMP

Ниже представлен полный построчный разбор всех зависимостей, объявленных в [`pubspec.yaml`](file:///E:/Projects/quark/pubspec.yaml).

| № | Зависимость в `pubspec.yaml` | Где и зачем используется в Quark | Чем заменить на JVM / KMP (Compose Desktop) |
|---|---|---|---|
| 1 | `flutter` (sdk) | Ядро UI-фреймворка, рендеринг, виджеты. | **Compose Multiplatform (Desktop)** (`org.jetbrains.compose.*`). |
| 2 | `dio: ^5.9.0` | HTTP-клиент: скачивание обложек в [`cached_images.dart:8`](file:///E:/Projects/quark/lib/services/cached_images.dart#L8), запросы в [`yandex_music`](file:///E:/Projects/quark/lib/overrided_libraries/yandex_music/lib/yandex_music.dart#L27), распознавание в [`recognizer_api.dart:1`](file:///E:/Projects/quark/lib/services/recognizer_api.dart#L1). | **Ktor Client** (`io.ktor:ktor-client-core`, `ktor-client-cio`, `ktor-client-content-negotiation`). |
| 3 | `hive: ^2.2.3` | NoSQL хранилище ключей: настройки в [`hive_settings_engine.dart`](file:///E:/Projects/quark/lib/services/database/hive_settings_engine.dart#L1), кэш цветов в [`dynamic_window_color_linux.dart:7`](file:///E:/Projects/quark/lib/services/dynamic_window_color_linux.dart#L7). | **AndroidX DataStore Preferences (KMP)** или **Multiplatform-Settings** (`com.russhwolf:multiplatform-settings`). |
| 4 | `path: ^1.9.1` | Манипуляция путями к файлам в [`files.dart:8`](file:///E:/Projects/quark/lib/services/files.dart#L8), [`cached_images.dart:18`](file:///E:/Projects/quark/lib/services/cached_images.dart#L18). | **Kotlinx-io** (`kotlinx.io.files.Path`) или стандартный Java NIO `java.nio.file.Path`. |
| 5 | `logging: ^1.3.0` | Логирование подсистем проекта (22 файла: `Logger('Name')`). | **Napier** (`io.github.aakira:napier`) или **Kermit** (`co.touchlab:kermit`) для KMP, либо SLF4J / Logback на JVM. |
| 6 | `animations: ^2.2.0` | Material motion: переход `OpenContainer` в [`yandex_widgets.dart:21, 506`](file:///E:/Projects/quark/lib/widgets/yandex_music_integration/yandex_widgets.dart#L506). | Встроенные анимации Compose: `AnimatedContent`, `SharedTransitionLayout` / `sharedElement` (Compose 1.7+). |
| 7 | `url_launcher: ^6.3.2` | Открытие внешних ссылок в браузере: переход на GitHub ([`main.dart:21`](file:///E:/Projects/quark/lib/main.dart#L21)), авторизация ([`yandex_login.dart:7`](file:///E:/Projects/quark/lib/widgets/yandex_music_integration/yandex_login.dart#L7)). | `java.awt.Desktop.getDesktop().browse(URI)` (JVM) или библиотека **Kmp-File / uri-handler** (`androidx.compose.ui.platform.LocalUriHandler`). |
| 8 | `file_picker: ^11.0.2` | Выбор папок с музыкой на диске ([`main.dart:10`](file:///E:/Projects/quark/lib/main.dart#L10), [`main_player.dart:16`](file:///E:/Projects/quark/lib/widgets/players_widgets/main_player.dart#L16)). | **LWJGL / Nativefiledialog** или `javax.swing.JFileChooser` / AWT `FileDialog`, либо библиотека **Peekaboo / FileKit** (`io.github.vinceglb:filekit-compose`). |
| 9 | `smtc_windows: ^1.1.0` | Системный контроллер медиа Windows (SMTC) в [`native_control.dart:9`](file:///E:/Projects/quark/lib/services/native_controls/native_control.dart#L9). | Прямой вызов Windows WinRT SMTC через **JNA / Project Panama** или готовая Java-библиотека **JSystemMediaControls**. |
| 10 | `yandex_music: ^1.2.2` | Клиент API Яндекс.Музыки (переопределен локально в `./lib/overrided_libraries/yandex_music`). | Готового качественного KMP-клиента нет. **Писать руками** на базе Ktor Client + `kotlinx.serialization` (перенести логику из Dart-библиотеки). |
| 11 | `path_provider: ^2.1.5` | Получение путей к кэшу и AppData в [`files.dart:6`](file:///E:/Projects/quark/lib/services/files.dart#L6), [`drift_library_engine.dart:7`](file:///E:/Projects/quark/lib/services/database/drift_library_engine.dart#L7). | Ручное определение: `System.getProperty("user.home")`, `System.getenv("APPDATA")` (Windows), `~/.cache` (Linux). Либо KMP-библиотека **okio** / **directories-jvm**. |
| 12 | `audio_service: ^0.18.18` | Фоновый аудио-сервис для Linux в [`linux_audio_control.dart:1`](file:///E:/Projects/quark/lib/services/native_controls/linux_audio_control.dart#L1). | Не требуется в архитектуре Desktop. Для интеграции с ОС используются отдельные нативные мосты (MPRIS / SMTC). |
| 13 | `cupertino_icons: ^1.0.8` | Иконки стиля iOS. В коде `lib/` прямых импортов нет (дефолтный шаблон Flutter). | Заменить на векторные Compose Icons (`androidx.compose.material.icons`). |
| 14 | `animated_expand: ^1.0.2` | Выпадающая панель кнопок в плеере ([`main_player.dart:18`](file:///E:/Projects/quark/lib/widgets/players_widgets/main_player.dart#L18), переопределен локально). | Нативный модификатор Compose: `Modifier.animateContentSize()` + `AnimatedVisibility`. |
| 15 | `interactive_slider: ^0.5.1` | Кастомный слайдер перемотки и громкости ([`slider_widget.dart:3`](file:///E:/Projects/quark/lib/widgets/players_widgets/slider_widget.dart#L3), [`main_player.dart:19`](file:///E:/Projects/quark/lib/widgets/players_widgets/main_player.dart#L19)). | Compose `Slider` с кастомным `SliderDefaults.Track` и `thumb`, либо свой компонент на `Modifier.pointerInput`. |
| 16 | `audio_service_mpris: ^0.2.0` | Поддержка стандарта MPRIS (D-Bus) для управления плеером на Linux ([`main.dart:12`](file:///E:/Projects/quark/lib/main.dart#L12)). | **dbus-java** (`com.github.hypfvieh:dbus-java`) с реализацией интерфейса `org.mpris.MediaPlayer2.Player`. |
| 17 | `audio_metadata_reader: ^1.4.2` | Чтение ID3/Vorbis/FLAC тегов и обложек из аудиофайлов ([`files.dart:9`](file:///E:/Projects/quark/lib/services/files.dart#L9), [`cached_images.dart:7`](file:///E:/Projects/quark/lib/services/cached_images.dart#L7)). | **Jaudiotagger** (`org.jaudiotagger:jaudiotagger`) — золотой стандарт теггинга на JVM. |
| 18 | `material_symbols_icons: ^4.2892.0` | Иконки Google Material Symbols ([`main_player.dart:17`](file:///E:/Projects/quark/lib/widgets/players_widgets/main_player.dart#L17), [`cached_images.dart:14`](file:///E:/Projects/quark/lib/services/cached_images.dart#L14)). | Использовать SVG-ресурсы через Compose Resources (`compose.components.resources`) или шрифт Material Symbols. |
| 19 | `audioplayers: ^6.5.1` | Базовый аудио-движок («Standart») в [`player.dart:9, 113`](file:///E:/Projects/quark/lib/services/player/player.dart#L9). | Не переносить. Заменяется единым движком на базе **libmpv** или **VLCJ**. |
| 20 | `just_audio: ^0.10.5` | Продвинутый аудио-движок в [`player.dart:10, 88`](file:///E:/Projects/quark/lib/services/player/player.dart#L10). | См. п. 19: на JVM заменить на **libmpv (через JNA/Panama)** или **JavaFX MediaPlayer**. |
| 21 | `just_audio_media_kit: ^2.1.0` | Мост just_audio к libmpv в [`player.dart:5, 106`](file:///E:/Projects/quark/lib/services/player/player.dart#L5). | Прямой биндинг к **libmpv** на C/C++ через Java 22 FFM (Foreign Function & Memory API) / JNA. |
| 22 | `just_audio_windows: ^0.2.2` | Плагин Windows для just_audio (десктопная сборка). | Не требуется (актуально только для Flutter). |
| 23 | `media_kit: ^1.2.6` | Ядро интеграции libmpv под капотом `just_audio_media_kit`. | Заменяется нативным подключением динамической библиотеки `mpv-2.dll` / `libmpv.so`. |
| 24 | `media_kit_video: ^2.0.1` | Видео-пакет media_kit (подтянут транзитивно). | Не нужен для аудиоплеера. |
| 25 | `media_kit_libs_video: ^1.0.7` | Нативные библиотеки mpv для видео. | Не нужен для аудиоплеера. |
| 26 | `media_kit_libs_windows_audio: ^1.0.9` | Скомпилированные Windows dll mpv для аудио. | Поставка `mpv-2.dll` в дистрибутиве Compose Desktop (через Conveyor или jpackage). |
| 27 | `provider: ^6.1.5+1` | State Management. В коде `lib/` не используется (рудимент, в проекте всюду `ValueNotifier`). | Compose State (`mutableStateOf`, `StateFlow`, `SharedFlow`) + ViewModel (`androidx.lifecycle:lifecycle-viewmodel-compose`). |
| 28 | `crypto: ^3.0.7` | Вычисление MD5 для ключей кэша ([`cached_images.dart:10, 293`](file:///E:/Projects/quark/lib/services/cached_images.dart#L10)). | `java.security.MessageDigest.getInstance("MD5")` (JVM) или **Kotlinx-io / KMP-Crypto**. |
| 29 | `intl: ^0.20.2` | Форматирование дат релизов в [`album.dart:9`](file:///E:/Projects/quark/lib/widgets/media_cards/album.dart#L9) и [`listen_stats.dart:7`](file:///E:/Projects/quark/lib/widgets/listen_stats/listen_stats.dart#L7). | **Kotlinx-datetime** (`org.jetbrains.kotlinx:kotlinx-datetime`) или Java `java.time.format.DateTimeFormatter`. |
| 30 | `google_fonts: ^8.0.2` | Шрифт Lexend в мобильном плеере ([`android_player.dart:14, 716`](file:///E:/Projects/quark/lib/widgets/players_widgets/android_player.dart#L14)). | Локальные TTF/WOFF2 файлы шрифтов в `resources/font/` через Compose `Font()`. |
| 31 | `async: ^2.13.0` | `CancelableOperation` для отмены сетевой буферизации треков ([`net_player.dart:4, 28`](file:///E:/Projects/quark/lib/services/player/net_player.dart#L4)). | **Kotlin Coroutines** (`Job.cancel()`, `cancelAndJoin()`). |
| 32 | `image: ^4.8.0` | Обработка изображений: ресайз и Gaussian blur обложек в изоляте ([`cached_images.dart:15, 171`](file:///E:/Projects/quark/lib/services/cached_images.dart#L15)). | Нативное размытие через **Skia ImageFilter (`org.jetbrains.skia.ImageFilter.makeBlur`)** в Compose Desktop или Java `BufferedImage` / `Thumbnailator`. |
| 33 | `auto_size_text: ^3.0.0` | Адаптивный размер текста ([`album.dart:4, 607`](file:///E:/Projects/quark/lib/widgets/media_cards/album.dart#L4), [`listen_stats.dart:6, 150`](file:///E:/Projects/quark/lib/widgets/listen_stats/listen_stats.dart#L6)). | Кастомный Composable `AutoResizeText` с пересчетом `TextStyle.fontSize` через `Paragraph.didExceedMaxLines`. |
| 34 | `drift: ^2.32.1` | Реляционная SQLite база данных плейлистов и истории ([`drift_library_engine.dart:3`](file:///E:/Projects/quark/lib/services/database/drift_library_engine.dart#L3)). | **Room KMP** (`androidx.room:room-compiler`, `room-runtime`) или **SQLDelight** (`app.cash.sqldelight`). |
| 35 | `pool: ^1.5.2` | Ограничение параллельных загрузок треков (Concurrency Pool) в [`net_player.dart:2`](file:///E:/Projects/quark/lib/services/player/net_player.dart#L2). | Корутины с `kotlinx.coroutines.sync.Semaphore(permits)`. |
| 36 | `desktop_drop: ^0.7.1` | Drag and Drop файлов в окно приложения ([`drag_drop.dart:3`](file:///E:/Projects/quark/lib/widgets/drag_drop.dart#L3)). | Compose Desktop drag & drop через AWT: `java.awt.dnd.DropTarget` на окне `ComposeWindow`. |
| 37 | `cross_file: ^0.3.5+2` | Абстракция файлов `XFile` для drop zone ([`drag_drop.dart:4`](file:///E:/Projects/quark/lib/widgets/drag_drop.dart#L4), [`main.dart:4`](file:///E:/Projects/quark/lib/main.dart#L4)). | `java.io.File` / `java.nio.file.Path`. |
| 38 | `carousel_slider: ^5.1.2` | Карусель обложек треков в мобильном плеере ([`android_player.dart:22, 551`](file:///E:/Projects/quark/lib/widgets/players_widgets/android_player.dart#L22)). | Compose `HorizontalPager` из `androidx.compose.foundation.pager`. |
| 39 | `multicast_dns: ^0.3.3+1` | Поиск плееров в LAN по протоколу mDNS ([`local_api.dart:8`](file:///E:/Projects/quark/lib/services/local_api/local_api.dart#L8), [`main.dart:893`](file:///E:/Projects/quark/lib/main.dart#L893)). | **JmDNS** (`org.jmdns:jmdns`) для Service Discovery на JVM. |
| 40 | `musicbrainz_api_client: ^0.2.3` | Распознавание треков и релизов по базе MusicBrainz ([`recognizer_api.dart:2, 39`](file:///E:/Projects/quark/lib/services/recognizer_api.dart#L2)). | **MusicBrainz API Client на Ktor** (готового KMP SDK нет, делать прямые REST-запросы к `musicbrainz.org/ws/2/`). |
| 41 | `flutter_discord_rpc: ^1.1.0` | Статус прослушивания в Discord ([`discord_rpc.dart:1, 18`](file:///E:/Projects/quark/lib/services/discord_rpc.dart#L1)). | **discord-rpc-java** (`com.github.MinnDevelopment:java-discord-rpc`) или IPC по именованным пайпам на корутинах. |
| 42 | `bonsoir: ^7.1.4` | mDNS Broadcast сервиса Quark в локальную сеть ([`local_api.dart:5, 79`](file:///E:/Projects/quark/lib/services/local_api/local_api.dart#L5)). | **JmDNS** (`org.jmdns:jmdns`) — регистрация и анонс mDNS-сервисов. |
| 43 | `nsd: ^5.0.1` | Network Service Discovery. Объявлен в `pubspec.yaml`, но не импортирован (рудимент тестов). | Не нужен (см. JmDNS в п. 39, 42). |
| 44 | `uuid: ^4.5.3` | Генератор UUID. В коде `lib/` импорт отсутствует (UUID генерируются на стороне бэкендов). | Стандартный `java.util.UUID.randomUUID().toString()`. |
| 45 | `web_socket_channel: ^3.0.3` | Объявлен в `pubspec.yaml`, в коде `local_api.dart` заменен стандартным `dart:io WebSocketTransformer`. | **Ktor WebSockets** (`io.ktor:ktor-server-websockets`). |
| 46 | `watcher: ^1.2.1` | Отслеживание изменений файлов в директориях ([`directory_observer.dart:2`](file:///E:/Projects/quark/lib/services/directory_observer.dart#L2)). | Стандартный Java NIO **`java.nio.file.WatchService`** или KMP-библиотека **DirectoryWatcher**. |
| 47 | `marquee: ^2.3.0` | Бегущая строка текста. Объявлен в `pubspec.yaml`, но **не используется нигде в проекте**. | При необходимости: кастомный Compose `basicMarquee()` (`Modifier.basicMarquee()`). |
| 48 | `dart_cue: ^0.1.1` | Парсинг CUE-файлов плейлистов. Импортирован в [`files.dart:3`](file:///E:/Projects/quark/lib/services/files.dart#L3), но фактически в логике не задействован. | Для полноценной поддержки CUE: Java-парсер **jcue** или самописный парсер на регулярных выражениях. |
| 49 | `palette_generator: ^0.3.3+7` | Экстракция палитры. Объявлен в `pubspec.yaml`, но **не используется** (заменен кодом в `AccentColorService`). | **KMPPalette** (`com.kmpalette:kmpalette-core`) или перенос логики усреднения зон из [`dynamic_window_color_linux.dart`](file:///E:/Projects/quark/lib/services/dynamic_window_color_linux.dart#L190). |
| 50 | `flutter_inappwebview: ^6.1.5` | WebView2 для авторизации в Яндекс.Музыке ([`yandex_login.dart:10`](file:///E:/Projects/quark/lib/widgets/yandex_music_integration/yandex_login.dart#L10)). | **KCEF (Kotlin CEF / Chromium Embedded Framework)** или авторизация через внешний системный браузер с локальным redirect-портом `localhost:port/callback`. |
| 51 | `flutter_rust_bridge: 2.11.1` (override) | Транзитивная зависимость плагина `smtc_windows`. | Не требуется при отказе от Flutter-плагина. |
| 52 | `flutter_test` (dev) | Фреймворк тестирования Flutter. | **Kotlin Test** (`kotlin.test`), **Compose UI Test** (`org.jetbrains.compose.ui:ui-test`). |
| 53 | `flutter_lints: ^6.0.0` (dev) | Статический анализатор Dart. | **Ktlint** и **Detekt** для Kotlin. |
| 54 | `build_runner: ^2.13.1` (dev) | Генератор кода для Drift и Hive. | **KSP (Kotlin Symbol Processing)** в связке с Room / SQLDelight. |
| 55 | `drift_dev: ^2.32.1` (dev) | Кодогенератор схем Drift. | **Room KSP Compiler** (`androidx.room:room-compiler`). |

---

## 9. Архитектурные риски и проблемные зоны при портировании

### 9.1. Ранжированный реестр рисков (по убыванию критичности и трудоёмкости)

```
[Критический] 1. Десктопный аудио-движок (libmpv/FFMPEG) и стриминг
     │
[Высокий]     2. API Яндекс.Музыки: кастомные протоколы (Шифрование, Моя волна)
     │
[Высокий]     3. Интеграция с ОС (Windows SMTC, Linux MPRIS, D-Bus)
     │
[Средний]     4. Портирование кастомного шейдера vibe.frag на SkSL (Skia)
     │
[Средний]     5. Авторизация Яндекс: WebView на Desktop
     │
[Умеренный]   6. Дублирование UI-кода плееров и распутывание состояния
```

#### 1. Десктопный кроссплатформенный аудио-движок (Критический риск)
* **Проблема в проекте:** В Flutter-версии автор безуспешно пытался подружить `audioplayers` и `just_audio_media_kit`. В [`player.dart`](file:///E:/Projects/quark/lib/services/player/player.dart#L62) существует переключатель бэкендов, потому что один падал на сетевых потоках Яндекс.Музыки, а второй имел проблемы с питчем и сборкой на Windows.
* **Специфика Desktop на KMP:** В стандартной библиотеке Java/Kotlin нет встроенного движка, способного без задержек воспроизводить HTTPS live-stream аудио (HLS, MP3, AAC, FLAC), поддерживать gapless playback, эквалайзер и смену скорости без изменения высоты тона.
* **Решение:** Использование связки **libmpv** через Java 22 FFM API (Foreign Function & Memory) или JNA (аналог `media_kit`). Потребуется сборка и поставка бинарных библиотек (`mpv-2.dll` под Windows x64, `libmpv.so` под Linux x64) вместе с установщиком приложения.

#### 2. Собственный клиент Яндекс.Музыки и радио «Моя Волна» (Высокий риск)
* **Проблема:** Вся интеграция держится на локальном форке [`lib/overrided_libraries/yandex_music`](file:///E:/Projects/quark/lib/overrided_libraries/yandex_music) (127 файлов исходников). В нем вручную реализованы неофициальные API: роторные станции (`wave.dart`), получение прямых ссылок на загрузку с расшифровкой sign/md5 XML-хранилища (`signs.dart`), очередь треков и отправка отзывов прослушивания (feedback: `trackStarted`, `trackFinished`, `skip`).
* **Решение:** Потребуется полностью с нуля написать модуль на Ktor Client + `kotlinx.serialization`. Прямой порт затруднен тем, что логика смешана с архитектурой Dio.

#### 3. Нативная интеграция управления медиа (Windows SMTC и Linux MPRIS) (Высокий риск)
* **Проблема:** Десктопный плеер обязан управляться медиа-кнопками клавиатуры и отображать оверлей громкости ОС. В Dart это делали плагины `smtc_windows` (через Rust-библиотеку) и `audio_service_mpris` (через C-биндинги).
* **Решение:**
  * Windows: реализация COM/WinRT-интерфейсов `ISystemMediaTransportControls` через JNA / Panama.
  * Linux: реализация протокола D-Bus `org.mpris.MediaPlayer2` через `dbus-java`.

#### 4. Портирование GLSL-шейдера визуализатора на SkSL (Средний риск)
* **Проблема:** Шейдер [`shaders/vibe.frag`](file:///E:/Projects/quark/shaders/vibe.frag) написан на Flutter GLSL с использованием `<flutter/runtime_effect.glsl>` и вызова `FlutterFragCoord()`.
* **Решение:** Compose Desktop работает поверх Skiko (Skia). Шейдер нужно адаптировать под SkSL (Skia Shading Language) и компилировать через `org.jetbrains.skia.RuntimeEffect.makeForShader()`. Входная точка преобразуется из `void main()` в `float4 main(float2 fragCoord)`, а передача uniform-массивов (`vec3[6]` и `vec3[3]`) должна быть развернута в плоский буфер `Data.makeFromBytes`.

#### 5. Авторизация Яндекс.Музыки через WebView на Desktop (Средний риск)
* **Проблема:** Официальный вход требует парсинга редиректа OAuth. Во Flutter использовался плагин `flutter_inappwebview` на базе Microsoft Edge WebView2.
* **Решение:** Внедрение тяжелого KCEF (Chromium) нежелательно (добавит 100+ МБ к размеру плеера). Оптимально: открытие системного браузера с `redirect_uri=https://music.yandex.ru/` и инструкцией ручной вставки токена (как уже реализовано в качестве fallback в [`yandex_login.dart`](file:///E:/Projects/quark/lib/widgets/yandex_music_integration/yandex_login.dart#L103-L170)).

---

### 9.2. Зоны жесткого спагетти и дублирования (Полная переписка, а не портирование)

Код следующих файлов **категорически нельзя портировать построчно**:

#### 1. Монолитные классы плееров: [`main_player.dart`](file:///E:/Projects/quark/lib/widgets/players_widgets/main_player.dart) (1627 строк), [`android_player.dart`](file:///E:/Projects/quark/lib/widgets/players_widgets/android_player.dart) (1235 строк) и [`mini_player.dart`](file:///E:/Projects/quark/lib/widgets/players_widgets/mini_player.dart) (502 строки)
* **Почему переписывать:**
  В этих классах состояние плеера, логика подписки на синглтоны, вычисление процентов перемотки, форматирование строк `0:00`, запросы к базе данных, отправка лайков и построение тяжелого UI сдублированы методом Copy-Paste. Даже имена классов состояний скопированы (`_PlaylistPage1State` фигурирует в трёх не связанных файлах).
* **Архитектурный рефакторинг в Compose:**
  Вынести 100% бизнес-логики в единый `PlayerViewModel` / `PlayerStateHolder` с реактивными потоками (`StateFlow<PlayerUiState>`). Компоненты UI должны стать легковесными функциями (`DesktopPlayerLayout`, `CompactPlayerLayout`, `MiniBarPlayerLayout`), принимающими иммутабельный стейт.

#### 2. Монолит [`yandex_widgets.dart`](file:///E:/Projects/quark/lib/widgets/yandex_music_integration/yandex_widgets.dart) (2233 строки)
* **Почему переписывать:**
  Файл является свалкой из 14 разнородных классов: от карточек релизов до экрана артиста на 900 строк, дублирующего [`media_cards/artist.dart`](file:///E:/Projects/quark/lib/widgets/media_cards/artist.dart). Внутри перемешаны сетевые вызовы к API, ручная сборка ссылок на обложки с заменой `%%` на `300x300` и UI-разметка.
* **Рефакторинг:**
  Разбить на доменные фичи: `feature:artist`, `feature:album`, `feature:playlist` с переиспользуемыми компонентами карточек.

#### 3. Сервис состояния [`database.dart`](file:///E:/Projects/quark/lib/services/database/database.dart) (311 строк)
* **Почему переписывать:**
  Класс [`DatabaseStreamerService`](file:///E:/Projects/quark/lib/services/database/database.dart#L8) содержит 38 отдельных `ValueNotifier`-полей ([строки 23–56](file:///E:/Projects/quark/lib/services/database/database.dart#L23-L56)). На каждое поле вручную навешивается `addListener`, внутри которого примитивное значение сохраняется в Hive.
* **Рефакторинг:**
  Заменить на типизированную модель `AppSettings` (Data Class), сохраняемую атомарно через **DataStore Preferences** или **kotlinx.serialization** в единый JSON-конфиг.

#### 4. Архитектурный гибрид баз данных: Hive + Drift
* **Почему переписывать:**
  В проекте одновременно живут две БД: NoSQL **Hive** (для настроек и кэша цветов) и SQL **Drift** (для треков и плейлистов). При этом при открытии плейлиста в [`main.dart`](file:///E:/Projects/quark/lib/main.dart#L344) происходит ручная сериализация/десериализация огромного словаря `Map<String, dynamic>` через `deserializePlaylist()`.
* **Рефакторинг:**
  Полный переход на единую базу **Room KMP** (или **SQLDelight**) для реляционных данных + **DataStore** для настроек.

---

### 9.3. Оценка трудоёмкости по крупным блокам (человеко-дни / недели)

Расчет приводится для одного опытного разработчика уровня Senior Kotlin / KMP:

| Крупный блок разработки | Что входит в блок | Трудоёмкость (дни) | Трудоёмкость (недели) |
|---|---|---|---|
| **1. Audio Engine & Core Player** | Интеграция `libmpv` (Panama/JNA), стейт-машина воспроизведения, очередь треков, gapless playback, буферизация сетевых потоков, тайминги. | 10–12 дней | ~2.5 недели |
| **2. Доменный слой & Yandex Music API** | Ktor-клиент, авторизация, парсинг треков/плейлистов/альбомов, алгоритм генерации ссылок на аудио, стриминг «Моя Волна», отправка фидбека. | 8–10 дней | ~2 недели |
| **3. База данных и хранилище** | Схема Room/SQLDelight, миграции, DAO для треков/плейлистов/истории, DataStore Preferences для настроек, кэш обложек на диске. | 4–5 дней | ~1 неделя |
| **4. Системные интеграции Desktop** | Windows SMTC (WinRT через JNA), Linux MPRIS (D-Bus), Discord RPC, Local HTTP/WebSocket API на Ktor Embedded Server. | 6–8 дней | ~1.5 недели |
| **5. Skia-шейдеры и кастомный рендеринг** | Портирование `vibe.frag` на SkSL (Skiko), Canvas-рендерер `CometLoader`, экстракция цветов обложки через Skia. | 3–4 дня | ~1 неделя |
| **6. Архитектура UI и экраны плееров** | Реализация `PlayerViewModel`, `MainPlayerLayout`, `MiniPlayerLayout`, адаптивная трансформация при ресайзе окна, слайдеры прогресса и громкости. | 7–9 дней | ~1.5–2 недели |
| **7. Экраны контента и библиотеки** | Боковая панель плейлиста, Reorderable-списки с DnD, страница альбома, артиста, тексты песен (`LyricsView`), диалог настроек в Glassmorphism. | 8–10 дней | ~2 недели |
| **8. Стабилизация и дистрибуция** | Тестирование на Windows и Linux, сборка нативных инсталляторов (`msi`, `deb`/`rpm`) через Conveyor / jpackage, профилирование памяти JVM. | 5–6 дней | ~1 неделя |
| **ИТОГО** | **Полный перенос Quark на Kotlin Multiplatform / Desktop** | **51–64 дня** | **~10–13 недель (2.5–3 месяца)** |
