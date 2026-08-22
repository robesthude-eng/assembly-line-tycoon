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

**Domain**
* `GameState` — единственный источник правды (баланс, отметка времени, маска технологий, версия схемы)
* `GameSettings`, `OfflineProgress`, `AdPlacement`, `AdResult`, `BillingProduct`, `PurchaseResult`
* Контракты: `GameRepository`, `SettingsRepository`, `AdsRepository`, `BillingRepository`
* Use case'ы: наблюдение/загрузка/сохранение состояния, настройки, офлайн-прогресс, показ рекламы
* `GameEngine` + `GameLoop` — каркас тикера 50 мс на корутинах с чистой функцией `reduce`

**Data**
* `DataStoreProvider` — два независимых стора: снапшот игры и настройки
* `GameStateSerializer` — JSON + обработка повреждённого файла
* `SerializationConfig` — единая настройка `Json` с обратной совместимостью сохранений
* Реализации репозиториев и мапперы

**Presentation**
* `MviViewModel<S, I, E>` — базовый класс: `StateFlow` состояния + `SharedFlow` эффектов
* `GameViewModel`, `GameUiState`, `GameIntent`, `GameEffect`
* Тема Material 3 (промышленная палитра), `FactoryCanvas` — рендерер только для чтения
* `FactoryScreen` с работающим `@Preview`

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

В Android Studio: `File → Open`, дождаться Gradle sync, запустить конфигурацию `app`.
Compose Preview открывается в `presentation/ui/screens/FactoryScreen.kt`.

## Дальнейшие этапы

| Этап | Содержание |
|---|---|
| 2. Домен | `Item`, `Recipe`, `Machine`, `GridPosition`, `Direction`; формулы в `MathUtils`: `Cost = BaseCost × 1.15^Level`, `Duration = max(100, Base × 0.95^Level)` |
| 3. Движок | Реализация `reduce`: движение предметов по ячейкам, буферы машин, backpressure, экспорт; офлайн-доход с потолком 2 ч |
| 4. UI | Отрисовка сетки и предметов на Canvas, HUD, магазин, дерево технологий |
| 5. Реклама | Подключение Yandex SDK в обёртки, экран согласия, кулдауны плейсментов |
| 6. Покупки | RuStore Billing: «Без рекламы», «Автоматический управляющий», паки валюты |
| 7. Релиз | Подпись, R8-профиль, AAB, чеклист модерации RuStore |

## Экономика (из ТЗ, для этапа 2)

Валюта — `Long`, без чисел с плавающей точкой.
Цепочка: руда → слиток → деталь → узел → готовое изделие.
Стоимость апгрейда растёт как `1.15^level`, длительность крафта падает как
`0.95^level` с жёстким полом в 100 мс.
