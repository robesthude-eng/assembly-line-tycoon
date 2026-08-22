# Конвейер: Завод Деталей (Assembly Line Tycoon)

Idle/Tycoon-симулятор автоматизации производства для Android.
Целевой магазин — **RuStore**.

Этот репозиторий содержит **фундамент проекта**: конфигурацию сборки, слои,
контракты и каркасы классов. Игровая логика (машины, конвейеры, экономика)
намеренно не реализована — см. [ARCHITECTURE.md](ARCHITECTURE.md) и раздел
«Дальнейшие этапы».

## Стек

| Что | Чем |
|---|---|
| Язык | Kotlin 2.0.21 |
| UI | Jetpack Compose (BOM 2024.12.01), Material 3 |
| Отрисовка завода | Compose `Canvas` поверх Compose UI |
| Архитектура | MVVM + MVI, однонаправленный поток данных |
| Асинхронность | Kotlin Coroutines 1.9.0 |
| Навигация | Navigation Compose 2.8.5 |
| Хранение | DataStore (типизированный + Preferences) |
| Сериализация | kotlinx.serialization 1.7.3 |
| Реклама | Yandex Mobile Ads 8.1.0 |
| Покупки | RuStore Billing 8.0.0 |
| Сборка | AGP 8.7.3, Gradle 8.9, JDK 17 |
| SDK | compileSdk 35, targetSdk 35, minSdk 26 |

`minSdk 26` выбран осознанно: Android 8.0 даёт adaptive-иконки и покрывает
подавляющее большинство аудитории RuStore. RuStore Billing требует 24+,
Yandex Ads — 21+, так что ограничение задаёт не SDK, а иконки и API-набор.

## Структура пакетов

```
com.example.assemblylinetycoon
├── core            # константы, утилиты, расширения, диспетчеры, время
├── data            # DataStore, репозитории, мапперы, конфиг сериализации
├── domain          # модели, контракты репозиториев, use case'ы, игровой движок
├── presentation    # Compose UI, тема, Canvas-рендерер, ViewModel'и, MVI-состояния
├── monetization    # обёртки Yandex Ads и RuStore Billing
└── app             # Application, MainActivity, навигация, граф зависимостей
```

Ключевое свойство: **в `domain` нет ни одного импорта `android.*`**.
Проверяется одной командой:

```bash
grep -rn "^import android" app/src/main/kotlin/com/example/assemblylinetycoon/domain/ && echo "НАРУШЕНИЕ" || echo "OK"
```

## Что уже готово

**Domain — модели и баланс (этап 2 готов)**
* `GameState` — единственный источник правды: баланс, поле, машины, слоты, «Ускорение», версия схемы
* Сетка: `FactoryGrid`, `Cell`, `CellType`, `GridPosition`, `Direction` — плоский список ячеек, предмет хранится с долей пройденного пути (`itemProgress`), значение 1.0 = противодавление
* Предметы: `Item` (ключ, название, категория, ярус, цена, размер стопки, подсказки для отрисовки), `ItemCategory` (RAW / PROCESSED / COMPONENT / FINAL), `ItemId` — 13 предметов со стабильными строковыми ключами
* Рецепты: `Recipe` — входы как `Map<String, Int>`, проверка не зависит от порядка; `canCraftFrom`, `missingInputs`, `consumeFrom`
* Машины: `MachineType` (SPAWNER, SMELTER, PRESS, WIRE_DRAWER, ASSEMBLER, QUALITY_GATE, EXPORTER), `MachineStatus` (IDLE → CRAFTING → OUTPUT_EJECT), `Machine`
* Каталоги: `ItemCatalog`, `RecipeCatalog`, `MachineCatalog`, `SlotCatalog` — баланс в коде, не в сохранении
* Контракты: `GameRepository`, `FactoryRepository`, `RecipeRepository`, `SettingsRepository`, `AdsRepository`, `BillingRepository`
* Формулы: `MathUtility` — `upgradeCost`, `bulkUpgradeCost`, `affordableLevels`, `craftDuration`, `offlineEarnings`, `addCoins` с защитой от переполнения `Long`
* `GameSettings`, `OfflineProgress`, `AdPlacement`, `AdResult`, `BillingProduct`, `PurchaseResult`
* Контракты: `GameRepository`, `SettingsRepository`, `AdsRepository`, `BillingRepository`
* Use case'ы: наблюдение/загрузка/сохранение состояния, настройки, офлайн-прогресс, показ рекламы
* `GameEngine` + `GameLoop` — каркас тикера 50 мс на корутинах с чистой функцией `reduce`

**Domain — симуляция завода (этап 3 готов)**
* `FactorySimulation` — чистая функция `step(state, deltaMillis)`: за такт сначала работают машины, затем едут ленты
* Ленты: движение строго по направлению клетки без поиска пути, один предмет на клетку, затор ждёт с прогрессом 1.0
* Машины: IDLE → CRAFTING → OUTPUT_EJECT; карьер производит сырьё, переработка списывает входы рецепта, экспортёр превращает изделие в монеты
* Смена фазы не съедает такт — результат не зависит от частоты кадров; дельта обрезается сверху, симуляция детерминирована
* `MovingItem` — едущий предмет (откуда, куда, доля пути, тип, количество); `ProductionStats` — произведено / заработано / отыграно
* `OfflineProgressCalculator` — доход за закрытую игру от сохранённой отметки времени, потолок 2 часа
* Движок не зависит от Android: весь завод крутится в юнит-тестах, CI это проверяет

**Data**
* `DataStoreProvider` — два независимых стора: снапшот игры и настройки
* `GameStateSerializer` — JSON + обработка повреждённого файла
* `SerializationConfig` — единая настройка `Json` с обратной совместимостью сохранений
* Реализации репозиториев и мапперы

**Presentation — экран завода (этап 4 готов)**
* `MviViewModel<S, I, E>` — базовый класс: `StateFlow` состояния + `SharedFlow` эффектов
* `FactoryUiState` / `FactoryIntent` / `FactoryEffect` — состояние экрана, намерения игрока, разовые эффекты
* `FactoryUiStateMapper` — чистая проекция `GameState` в состояние экрана, тестируется без Android
* `FactoryViewModel` — только подписка на движок и маршрутизация намерений, никакой симуляции
* `FactoryCanvas` — отрисовка поля, лент со стрелками, машин с уровнем и полосой такта, едущих предметов
* `FactoryGeometry` — перевод пикселей в клетки и обратно, чистая арифметика без Compose
* `FactoryHud` — баланс, производство в минуту, плашка усилений
* `MachineDialog` с рабочей кнопкой улучшения и магазин оборудования в пустой ячейке, `MainScreen` с навигацией
* `FactoryBuilder` — постройка и улучшение: цены из `MachineCatalog`, отказ при нехватке денег
* Тема Material 3 (промышленная палитра), рабочие `@Preview` экрана, HUD и диалога

**Monetization**
* `AdsInitializer` / `YandexAdsInitializer` — инициализация после согласия 152-ФЗ
* `RewardedAdsManager`, `InterstitialAdsManager` + заглушки Yandex
* `BillingManager` / `RuStoreBillingManager` + `onNewIntent` для возврата из оплаты

**App**
* `AppContainer` — ручной граф зависимостей, `GameApplication`, `MainActivity`, `AppNavHost`

## Сборка

```bash
# требуется JDK 17 и Android SDK 35
echo "sdk.dir=/путь/к/android-sdk" > local.properties
./gradlew :app:assembleDebug
./gradlew :app:testDebugUnitTest
```

Состояние проверки: `assembleDebug` собирает APK (~21 МБ),
`testDebugUnitTest` — 79 тестов, 0 падений.

Релиз и версии — см. [RELEASE.md](RELEASE.md):
`./gradlew printVersion`, `./gradlew bumpPatch`, `./gradlew :app:bundleRelease`.

В Android Studio: `File → Open`, дождаться Gradle sync, запустить конфигурацию `app`.
Compose Preview открывается в `presentation/ui/screens/FactoryScreen.kt`.

## Дальнейшие этапы

| Этап | Содержание |
|---|---|
| ~~2. Домен~~ | ✅ Модели, каталоги, контракты и формулы прогрессии, 79 юнит-тестов |
| ~~3. Движок~~ | ✅ Симуляция завода: движение по лентам, буферы машин, заторы, экспорт, офлайн-доход с потолком 2 ч; 124 юнит-теста |
| ~~4. UI~~ | ✅ Экран завода: Canvas-рендерер, HUD, карточка машины, MVI-слой; 37 тестов, включая Compose-тесты на Robolectric |
| ~~4b. Постройка~~ | ✅ Постройка и улучшение машин: команды движку, цены из `MachineCatalog`, магазин в ячейке |
| 4c. Редактор | Прокладка конвейеров и снос (нужна цена ленты — в балансе её нет) |
| 5. Реклама | Подключение Yandex SDK в обёртки, экран согласия, кулдауны плейсментов |
| 6. Покупки | RuStore Billing: «Без рекламы», «Автоматический управляющий», паки валюты |
| ~~7. Релиз~~ | ✅ Подпись, R8, AAB, автоверсионирование, CI-пайплайны |

## Экономика

Валюта — `Long`, без чисел с плавающей точкой. Стоимость апгрейда растёт как
`1.15^level` (у ассемблера 1.18, у контроля качества 1.20), длительность
крафта падает как `0.95^level` с жёстким полом в 100 мс.

Производственная цепочка и маржа передела:

| Изделие | Ярус | Машина | Вход | Цена | Маржа |
|---|---|---|---|---|---|
| Железная руда | 0 | Карьер | — | 1 | — |
| Медная руда | 0 | Карьер | — | 2 | — |
| Пластик-сырец | 0 | Карьер | — | 3 | — |
| Кремний | 0 | Карьер | — | 8 | — |
| Слиток железа | 1 | Плавильня | 2× руда | 6 | +200% |
| Медный провод | 1 | Стан | 1× медь → 2 шт | 10 | +900% |
| Пластиковый корпус | 1 | Пресс | 3× пластик | 14 | +56% |
| Шестерня | 2 | Пресс | 3× слиток | 30 | +67% |
| Микрочип | 2 | Сборщик | 4× кремний, 2× провод | 90 | +73% |
| Электромотор | 3 | Сборщик | 2× шестерня, 4× провод | 180 | +80% |
| Умный контроллер | 3 | Сборщик | 2× чип, 2× корпус | 420 | +102% |
| Промышленный дрон | 4 | Сборщик | мотор, контроллер, 2× корпус | 900 | +43% |
| ИИ-робот | 5 | Контроль качества | дрон, 2× шестерня | 1 500 | +56% |

Такты — от 2 с у карьера до 25 с у контроля качества.
Слоты линий: 0, 100, 500, 2 500, 10 000, 50 000, 250 000, 1 000 000,
5 000 000, 25 000 000 монет.

Целостность баланса проверяется тестами, а не глазами: `CatalogIntegrityTest`
падает, если появился недостижимый предмет, цикл в цепочке, убыточный передел
или маржа ниже 10%.
