package com.example.assemblylinetycoon.presentation

import com.example.assemblylinetycoon.core.utils.TimeProvider
import com.example.assemblylinetycoon.domain.engine.FactoryBuilder
import com.example.assemblylinetycoon.domain.engine.GameCommand
import com.example.assemblylinetycoon.domain.engine.GameEngine
import com.example.assemblylinetycoon.domain.model.Direction
import com.example.assemblylinetycoon.domain.model.FactoryGrid
import com.example.assemblylinetycoon.domain.model.GameSettings
import com.example.assemblylinetycoon.domain.model.GameState
import com.example.assemblylinetycoon.domain.model.GridPosition
import com.example.assemblylinetycoon.domain.model.ItemId
import com.example.assemblylinetycoon.domain.model.Machine
import com.example.assemblylinetycoon.domain.model.MachineStatus
import com.example.assemblylinetycoon.domain.model.MachineType
import com.example.assemblylinetycoon.domain.model.ProductionStats
import com.example.assemblylinetycoon.domain.repository.GameRepository
import com.example.assemblylinetycoon.domain.repository.SettingsRepository
import com.example.assemblylinetycoon.domain.usecase.LoadGameStateUseCase
import com.example.assemblylinetycoon.domain.usecase.ObserveSettingsUseCase
import com.example.assemblylinetycoon.domain.usecase.SaveGameStateUseCase
import com.example.assemblylinetycoon.presentation.state.FactoryDialog
import com.example.assemblylinetycoon.presentation.state.FactoryEffect
import com.example.assemblylinetycoon.presentation.state.FactoryIntent
import com.example.assemblylinetycoon.presentation.viewmodel.FactoryViewModel
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.launch
import kotlinx.coroutines.test.StandardTestDispatcher
import kotlinx.coroutines.test.UnconfinedTestDispatcher
import kotlinx.coroutines.test.advanceUntilIdle
import kotlinx.coroutines.test.resetMain
import kotlinx.coroutines.test.runTest
import kotlinx.coroutines.test.setMain
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test

/**
 * Поведение ViewModel экрана завода.
 *
 * Проверяется ровно то, за что он отвечает: проекция состояния и маршрутизация
 * намерений. Симуляция подменена фейком — если бы тест зависел от настоящего
 * движка, он проверял бы уже проверенное и падал бы от любой правки баланса.
 */
@OptIn(ExperimentalCoroutinesApi::class)
class FactoryViewModelTest {

    private val dispatcher = StandardTestDispatcher()

    private val smelter = Machine(
        id = 4,
        type = MachineType.SMELTER,
        position = GridPosition(2, 2),
        level = 1,
        recipeOutputId = ItemId.IRON_INGOT.key,
        status = MachineStatus.CRAFTING,
        elapsedMillis = 500L,
    )

    private val factoryState = GameState.EMPTY.copy(
        coins = 3_000L,
        isInitialized = true,
        grid = FactoryGrid.EMPTY
            .withMachine(smelter)
            .withBelt(GridPosition(3, 2), Direction.RIGHT),
        machines = mapOf(smelter.id to smelter),
        stats = ProductionStats(coinsEarned = 120L, simulatedMillis = 60_000L),
    )

    /** Движок-заглушка: запоминает вызовы, ничего не симулирует. */
    private class FakeEngine(initial: GameState) : GameEngine {
        private val _state = MutableStateFlow(initial)
        override val state: StateFlow<GameState> = _state.asStateFlow()

        var startedWith: GameState? = null
        var stopCount: Int = 0
        val commands = mutableListOf<GameCommand>()

        override fun start(initialState: GameState) {
            startedWith = initialState
            _state.value = initialState
        }

        override fun stop() {
            stopCount++
        }

        override fun dispatch(command: GameCommand) {
            commands += command
            // Фейк повторяет поведение настоящего движка для команд игрока:
            // без этого нельзя проверить, что экран показывает результат.
            _state.value = when (command) {
                is GameCommand.PlaceMachine ->
                    FactoryBuilder.place(_state.value, command.position, command.type)
                is GameCommand.UpgradeMachine ->
                    FactoryBuilder.upgrade(_state.value, command.machineId)
                else -> _state.value
            }
        }

        fun emit(state: GameState) {
            _state.value = state
        }
    }

    private class FakeGameRepository(private val saved: GameState) : GameRepository {
        var savedState: GameState? = null
        override fun observeGameState() = flowOf(saved)
        override suspend fun loadGameState(): GameState = saved
        override suspend fun saveGameState(state: GameState) {
            savedState = state
        }

        override suspend fun clear() = Unit
    }

    private class FakeSettingsRepository : SettingsRepository {
        override fun observeSettings() = flowOf(GameSettings())
        override suspend fun updateSettings(transform: (GameSettings) -> GameSettings) = Unit
    }

    private class FixedTime(private val value: Long) : TimeProvider {
        override fun nowMillis(): Long = value
        override fun elapsedRealtimeMillis(): Long = value
    }

    private lateinit var engine: FakeEngine
    private lateinit var repository: FakeGameRepository

    private fun createViewModel(initial: GameState = GameState.EMPTY): FactoryViewModel {
        engine = FakeEngine(initial)
        repository = FakeGameRepository(factoryState)
        return FactoryViewModel(
            gameEngine = engine,
            loadGameState = LoadGameStateUseCase(repository),
            saveGameState = SaveGameStateUseCase(repository),
            observeSettings = ObserveSettingsUseCase(FakeSettingsRepository()),
            timeProvider = FixedTime(0L),
        )
    }

    @Before
    fun setUp() {
        // viewModelScope живёт на главном диспетчере, которого в JVM-тесте нет.
        Dispatchers.setMain(dispatcher)
    }

    @After
    fun tearDown() {
        Dispatchers.resetMain()
    }

    @Test // состояние движка превращается в состояние экрана
    fun engineStateBecomesUiState() = runTest(dispatcher) {
        val viewModel = createViewModel()

        engine.emit(factoryState)
        advanceUntilIdle()

        val ui = viewModel.state.value
        assertTrue(!ui.isLoading)
        assertEquals(3_000L, ui.coins)
        assertEquals(2.0, ui.coinsPerSecond, 0.0001)   // 120 монет за 60 секунд
        assertEquals(1, ui.render.machines.size)
        assertEquals(factoryState.grid, ui.render.grid)
    }

    @Test // экран стал виден — симуляция запускается загруженным состоянием
    fun screenStartedLaunchesEngine() = runTest(dispatcher) {
        val viewModel = createViewModel()

        viewModel.onIntent(FactoryIntent.ScreenStarted)
        advanceUntilIdle()

        assertEquals(factoryState, engine.startedWith)
    }

    @Test // уход в фон останавливает тикер и сохраняет прогресс
    fun screenStoppedSavesProgress() = runTest(dispatcher) {
        val viewModel = createViewModel(factoryState)

        viewModel.onIntent(FactoryIntent.ScreenStopped)
        advanceUntilIdle()

        assertEquals(1, engine.stopCount)
        assertEquals(factoryState, repository.savedState)
    }

    @Test // касание машины выделяет клетку и открывает карточку
    fun selectingMachineOpensDialog() = runTest(dispatcher) {
        val viewModel = createViewModel(factoryState)
        advanceUntilIdle()

        viewModel.onIntent(FactoryIntent.SelectCell(smelter.position))
        advanceUntilIdle()

        val ui = viewModel.state.value
        assertEquals(smelter.position, ui.selectedCell)
        assertNotNull(ui.selectedMachine)
        val dialog = ui.dialog as FactoryDialog.MachineInfo
        assertEquals(smelter.id, dialog.machine.id)
        assertEquals(MachineType.SMELTER, dialog.machine.type)
    }

    @Test // касание пустой клетки открывает заготовку постройки
    fun selectingEmptyCellOpensPlacementDialog() = runTest(dispatcher) {
        val viewModel = createViewModel(factoryState)
        advanceUntilIdle()

        viewModel.onIntent(FactoryIntent.SelectCell(GridPosition(8, 8)))
        advanceUntilIdle()

        val dialog = viewModel.state.value.dialog as FactoryDialog.EmptyCell
        assertEquals(GridPosition(8, 8), dialog.position)
    }

    @Test // касание за пределами поля ничего не открывает
    fun tapOutsideGridChangesNothing() = runTest(dispatcher) {
        val viewModel = createViewModel(factoryState)
        advanceUntilIdle()

        viewModel.onIntent(FactoryIntent.SelectCell(GridPosition(50, 50)))
        advanceUntilIdle()

        assertEquals(FactoryDialog.None, viewModel.state.value.dialog)
    }

    @Test // карточку можно открыть по идентификатору машины
    fun openMachineDialogByIdSelectsItsCell() = runTest(dispatcher) {
        val viewModel = createViewModel(factoryState)
        advanceUntilIdle()

        viewModel.onIntent(FactoryIntent.OpenMachineDialog(smelter.id))
        advanceUntilIdle()

        assertEquals(smelter.position, viewModel.state.value.selectedCell)
        assertTrue(viewModel.state.value.dialog is FactoryDialog.MachineInfo)
    }

    @Test // закрытие диалога не сбрасывает выделение клетки
    fun closingDialogKeepsSelection() = runTest(dispatcher) {
        val viewModel = createViewModel(factoryState)
        advanceUntilIdle()
        viewModel.onIntent(FactoryIntent.SelectCell(smelter.position))
        advanceUntilIdle()

        viewModel.onIntent(FactoryIntent.CloseDialog)
        advanceUntilIdle()

        assertEquals(FactoryDialog.None, viewModel.state.value.dialog)
        assertEquals(smelter.position, viewModel.state.value.selectedCell)
    }

    @Test // диалог переживает тик симуляции и обновляется свежими данными
    fun dialogSurvivesSimulationTick() = runTest(dispatcher) {
        val viewModel = createViewModel(factoryState)
        advanceUntilIdle()
        viewModel.onIntent(FactoryIntent.SelectCell(smelter.position))
        advanceUntilIdle()

        engine.emit(
            factoryState.copy(
                coins = 4_000L,
                machines = mapOf(smelter.id to smelter.copy(elapsedMillis = 1_500L)),
            ),
        )
        advanceUntilIdle()

        val ui = viewModel.state.value
        assertTrue("Диалог не должен закрываться от тика", ui.dialog is FactoryDialog.MachineInfo)
        assertEquals(4_000L, ui.coins)
    }

    @Test // постройка уходит в движок командой и списывает деньги
    fun placingMachineSendsCommand() = runTest(dispatcher) {
        val viewModel = createViewModel(factoryState.copy(coins = 10_000L))
        advanceUntilIdle()
        val position = GridPosition(7, 7)
        val cost = FactoryBuilder.buildCost(engine.state.value, MachineType.PRESS)

        viewModel.onIntent(FactoryIntent.PlaceMachine(position, MachineType.PRESS))
        advanceUntilIdle()

        assertEquals(
            listOf(GameCommand.PlaceMachine(position, MachineType.PRESS)),
            engine.commands,
        )
        assertEquals(10_000L - cost, viewModel.state.value.coins)
        // Магазин закрывается сам: ячейка перестала быть пустой.
        assertEquals(FactoryDialog.None, viewModel.state.value.dialog)
    }

    @Test // без денег команда не уходит, игрок получает сообщение
    fun placingWithoutCoinsReportsAndSendsNothing() = runTest(dispatcher) {
        val viewModel = createViewModel(factoryState.copy(coins = 0L))
        val effects = mutableListOf<FactoryEffect>()
        backgroundScope.launch(UnconfinedTestDispatcher(testScheduler)) {
            viewModel.effects.collect { effects += it }
        }
        advanceUntilIdle()

        viewModel.onIntent(FactoryIntent.PlaceMachine(GridPosition(7, 7), MachineType.ASSEMBLER))
        advanceUntilIdle()

        assertTrue("Команда не должна уходить движку", engine.commands.isEmpty())
        assertEquals(1, effects.size)
        assertTrue(effects.single() is FactoryEffect.ShowMessage)
    }

    @Test // улучшение уходит в движок и поднимает уровень
    fun upgradingSendsCommand() = runTest(dispatcher) {
        val viewModel = createViewModel(factoryState.copy(coins = 1_000_000L))
        advanceUntilIdle()

        viewModel.onIntent(FactoryIntent.UpgradeMachine(smelter.id))
        advanceUntilIdle()

        assertEquals(listOf(GameCommand.UpgradeMachine(smelter.id)), engine.commands)
        assertEquals(smelter.level + 1, engine.state.value.machines.getValue(smelter.id).level)
    }

    @Test // улучшение без денег не уходит движку
    fun upgradingWithoutCoinsReportsAndSendsNothing() = runTest(dispatcher) {
        val viewModel = createViewModel(factoryState.copy(coins = 0L))
        advanceUntilIdle()

        viewModel.onIntent(FactoryIntent.UpgradeMachine(smelter.id))
        advanceUntilIdle()

        assertTrue(engine.commands.isEmpty())
    }

    @Test // магазин пустой ячейки показывает каталожные цены
    fun emptyCellDialogListsCatalogPrices() = runTest(dispatcher) {
        val viewModel = createViewModel(factoryState.copy(coins = 200L))
        advanceUntilIdle()

        viewModel.onIntent(FactoryIntent.SelectCell(GridPosition(8, 8)))
        advanceUntilIdle()

        val dialog = viewModel.state.value.dialog as FactoryDialog.EmptyCell
        assertEquals(MachineType.entries.size, dialog.options.size)
        val spawner = dialog.options.first { it.type == MachineType.SPAWNER }
        assertEquals(MachineType.SPAWNER.baseCost, spawner.cost)
        assertTrue("На карьер за 50 монет хватает 200", spawner.canAfford)
        assertTrue(
            "На сборщик за 800 монет 200 не хватает",
            !dialog.options.first { it.type == MachineType.ASSEMBLER }.canAfford,
        )
    }

    @Test // экран меняет состояние только командами, а не напрямую
    fun viewModelChangesStateOnlyThroughCommands() = runTest(dispatcher) {
        val viewModel = createViewModel(factoryState.copy(coins = 1_000_000L))
        advanceUntilIdle()
        val before = engine.state.value

        // Выделение и открытие диалога — дело экрана, симуляции они не касаются.
        viewModel.onIntent(FactoryIntent.SelectCell(smelter.position))
        viewModel.onIntent(FactoryIntent.OpenMachineDialog(smelter.id))
        viewModel.onIntent(FactoryIntent.CloseDialog)
        advanceUntilIdle()

        assertTrue("Просмотр не должен трогать движок", engine.commands.isEmpty())
        assertEquals(before, engine.state.value)
    }
}
