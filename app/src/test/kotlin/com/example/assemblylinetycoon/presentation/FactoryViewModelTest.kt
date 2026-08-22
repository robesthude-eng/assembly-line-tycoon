package com.example.assemblylinetycoon.presentation

import com.example.assemblylinetycoon.core.utils.TimeProvider
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

    @Test // ещё не реализованные действия честно сообщают об этом
    fun placementAndUpgradeReportNotImplemented() = runTest(dispatcher) {
        val viewModel = createViewModel(factoryState)
        val effects = mutableListOf<FactoryEffect>()
        // UnconfinedTestDispatcher — чтобы подписка на SharedFlow состоялась
        // немедленно: у эффектов нет буфера воспроизведения, и всё, что
        // отправлено до появления подписчика, теряется.
        backgroundScope.launch(UnconfinedTestDispatcher(testScheduler)) {
            viewModel.effects.collect { effects += it }
        }

        viewModel.onIntent(FactoryIntent.PlaceMachine(GridPosition(1, 1), MachineType.SMELTER))
        viewModel.onIntent(FactoryIntent.UpgradeMachine(smelter.id))
        advanceUntilIdle()

        assertEquals(2, effects.size)
        assertTrue(effects.all { it is FactoryEffect.NotImplementedYet })
    }

    @Test // ViewModel не трогает симуляцию: никаких команд движку не уходит
    fun viewModelDoesNotSimulate() = runTest(dispatcher) {
        val viewModel = createViewModel(factoryState)
        advanceUntilIdle()

        viewModel.onIntent(FactoryIntent.SelectCell(smelter.position))
        viewModel.onIntent(FactoryIntent.PlaceMachine(GridPosition(1, 1), MachineType.PRESS))
        viewModel.onIntent(FactoryIntent.UpgradeMachine(smelter.id))
        advanceUntilIdle()

        // Состояние игры меняет только движок. Пока команд постройки нет,
        // экран обязан оставить симуляцию нетронутой.
        assertTrue(engine.commands.isEmpty())
        assertEquals(factoryState.machines, engine.state.value.machines)
        assertEquals(factoryState.coins, engine.state.value.coins)
    }
}
