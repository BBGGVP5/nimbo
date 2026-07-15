package com.danila.nimbo.vpn

/**
 * Pure state machine for connection recovery. Android callbacks are translated
 * into [RecoveryEvent] values by the service, keeping ordering rules testable.
 */
object VpnRecoveryPolicy {
    private const val BASE_RETRY_DELAY_MS = 1_000L
    private const val MAX_RETRY_DELAY_MS = 30_000L
    private const val MAX_RETRY_ATTEMPT = 6

    enum class Phase {
        DISCONNECTED,
        CONNECTING,
        CONNECTED,
        WAITING_FOR_NETWORK,
        PAUSED_BY_SCREEN
    }

    data class State(
        val desiredConnected: Boolean = false,
        val phase: Phase = Phase.DISCONNECTED,
        val screenPaused: Boolean = false,
        val networkAvailable: Boolean = true,
        val retryAttempt: Int = 0,
        val connectPending: Boolean = false
    )

    sealed interface Event {
        data class ManualConnect(val hasServer: Boolean) : Event
        data object ManualDisconnect : Event
        data class ScreenOff(val pauseEnabled: Boolean) : Event
        data class ScreenOn(val resumeEnabled: Boolean, val hasServer: Boolean) : Event
        data class NetworkChanged(
            val available: Boolean,
            val autoRecoveryEnabled: Boolean,
            val hasServer: Boolean
        ) : Event
        data class ConnectFailed(
            val retryable: Boolean,
            val autoRecoveryEnabled: Boolean,
            val hasServer: Boolean
        ) : Event
        data object ConnectSucceeded : Event
        data object RetryElapsed : Event
        data class StickyRestore(val screenInteractive: Boolean, val hasServer: Boolean) : Event
    }

    sealed interface Command {
        data object StartConnection : Command
        data object StopTunnelForScreen : Command
        data object StopTunnelForNetwork : Command
        data object StopService : Command
        data object CancelRetry : Command
        data class ScheduleRetry(val delayMs: Long) : Command
        data class Diagnostic(val message: String) : Command
    }

    data class Result(
        val state: State,
        val commands: List<Command> = emptyList()
    )

    fun reduce(state: State, event: Event): Result = when (event) {
        is Event.ManualConnect -> {
            if (!event.hasServer) {
                Result(
                    state.copy(
                        desiredConnected = false,
                        phase = Phase.DISCONNECTED,
                        connectPending = false
                    ),
                    listOf(Command.Diagnostic("No saved server is available"))
                )
            } else {
                Result(
                    state.copy(
                        desiredConnected = true,
                        phase = if (state.networkAvailable) Phase.CONNECTING else Phase.WAITING_FOR_NETWORK,
                        screenPaused = false,
                        retryAttempt = 0,
                        connectPending = state.networkAvailable
                    ),
                    buildList {
                        add(Command.CancelRetry)
                        if (state.networkAvailable) add(Command.StartConnection)
                    }
                )
            }
        }

        Event.ManualDisconnect -> Result(
            State(networkAvailable = state.networkAvailable),
            listOf(Command.CancelRetry, Command.StopService)
        )

        is Event.ScreenOff -> {
            val active = state.phase == Phase.CONNECTED ||
                state.phase == Phase.CONNECTING ||
                state.phase == Phase.WAITING_FOR_NETWORK
            if (!event.pauseEnabled || !state.desiredConnected || !active || state.screenPaused) {
                Result(state)
            } else {
                Result(
                    state.copy(
                        phase = Phase.PAUSED_BY_SCREEN,
                        screenPaused = true,
                        connectPending = false
                    ),
                    listOf(Command.CancelRetry, Command.StopTunnelForScreen)
                )
            }
        }

        is Event.ScreenOn -> {
            if (!state.screenPaused) {
                Result(state)
            } else if (!event.resumeEnabled) {
                Result(
                    State(networkAvailable = state.networkAvailable),
                    listOf(Command.CancelRetry, Command.StopService)
                )
            } else if (!event.hasServer) {
                Result(
                    State(networkAvailable = state.networkAvailable),
                    listOf(Command.CancelRetry, Command.Diagnostic("Cannot resume without a saved server"))
                )
            } else if (!state.networkAvailable) {
                Result(
                    state.copy(
                        phase = Phase.WAITING_FOR_NETWORK,
                        screenPaused = false,
                        connectPending = false
                    )
                )
            } else {
                Result(
                    state.copy(
                        phase = Phase.CONNECTING,
                        screenPaused = false,
                        retryAttempt = 0,
                        connectPending = true
                    ),
                    listOf(Command.CancelRetry, Command.StartConnection)
                )
            }
        }

        is Event.NetworkChanged -> {
            if (!event.available) {
                if (!state.desiredConnected || state.screenPaused) {
                    Result(state.copy(networkAvailable = false))
                } else if (!event.autoRecoveryEnabled) {
                    Result(
                        State(networkAvailable = false),
                        listOf(Command.CancelRetry, Command.StopService)
                    )
                } else {
                    Result(
                        state.copy(
                            phase = Phase.WAITING_FOR_NETWORK,
                            networkAvailable = false,
                            connectPending = false
                        ),
                        listOf(Command.CancelRetry, Command.StopTunnelForNetwork)
                    )
                }
            } else if (
                state.desiredConnected &&
                !state.screenPaused &&
                event.autoRecoveryEnabled &&
                event.hasServer &&
                !state.connectPending &&
                state.phase != Phase.CONNECTED
            ) {
                Result(
                    state.copy(
                        phase = Phase.CONNECTING,
                        networkAvailable = true,
                        connectPending = true
                    ),
                    listOf(Command.CancelRetry, Command.StartConnection)
                )
            } else {
                Result(state.copy(networkAvailable = true))
            }
        }

        is Event.ConnectFailed -> {
            val canRetry = state.desiredConnected &&
                !state.screenPaused &&
                state.networkAvailable &&
                event.retryable &&
                event.autoRecoveryEnabled &&
                event.hasServer
            if (!canRetry) {
                Result(
                    state.copy(
                        phase = if (state.screenPaused) Phase.PAUSED_BY_SCREEN else Phase.DISCONNECTED,
                        connectPending = false
                    )
                )
            } else {
                val nextAttempt = (state.retryAttempt + 1).coerceAtMost(MAX_RETRY_ATTEMPT)
                Result(
                    state.copy(
                        phase = Phase.WAITING_FOR_NETWORK,
                        retryAttempt = nextAttempt,
                        connectPending = false
                    ),
                    listOf(Command.ScheduleRetry(retryDelayMs(nextAttempt)))
                )
            }
        }

        Event.ConnectSucceeded -> Result(
            state.copy(
                desiredConnected = true,
                phase = Phase.CONNECTED,
                screenPaused = false,
                retryAttempt = 0,
                connectPending = false
            ),
            listOf(Command.CancelRetry)
        )

        Event.RetryElapsed -> {
            if (
                state.desiredConnected &&
                !state.screenPaused &&
                state.networkAvailable &&
                !state.connectPending &&
                state.phase != Phase.CONNECTED
            ) {
                Result(
                    state.copy(phase = Phase.CONNECTING, connectPending = true),
                    listOf(Command.StartConnection)
                )
            } else {
                Result(state)
            }
        }

        is Event.StickyRestore -> {
            when {
                !state.desiredConnected || !event.hasServer -> Result(state)
                state.screenPaused && !event.screenInteractive ->
                    Result(state.copy(phase = Phase.PAUSED_BY_SCREEN, connectPending = false))
                !state.networkAvailable ->
                    Result(state.copy(phase = Phase.WAITING_FOR_NETWORK, connectPending = false))
                state.connectPending || state.phase == Phase.CONNECTED -> Result(state)
                else -> Result(
                    state.copy(
                        phase = Phase.CONNECTING,
                        screenPaused = false,
                        connectPending = true
                    ),
                    listOf(Command.StartConnection)
                )
            }
        }
    }

    fun retryDelayMs(attempt: Int): Long {
        val normalized = attempt.coerceIn(1, MAX_RETRY_ATTEMPT)
        return (BASE_RETRY_DELAY_MS shl (normalized - 1)).coerceAtMost(MAX_RETRY_DELAY_MS)
    }
}
