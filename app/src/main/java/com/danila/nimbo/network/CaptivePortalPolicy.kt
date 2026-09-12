package com.danila.nimbo.network

object CaptivePortalPolicy {
    enum class Action { NONE, PAUSE_AND_SHOW_LOGIN, WAIT_FOR_VALIDATION, RECOVER_TUNNEL }

    data class State(
        val portalWasDetected: Boolean = false,
        val tunnelPausedForPortal: Boolean = false
    )

    data class Decision(val state: State, val action: Action)

    fun evaluate(state: State, network: NetworkContextSnapshot, vpnRequested: Boolean): Decision {
        if (!vpnRequested) return Decision(State(), Action.NONE)
        if (network.captivePortal) {
            if (state.tunnelPausedForPortal) {
                return Decision(state.copy(portalWasDetected = true), Action.WAIT_FOR_VALIDATION)
            }
            return Decision(
                State(portalWasDetected = true, tunnelPausedForPortal = true),
                Action.PAUSE_AND_SHOW_LOGIN
            )
        }
        if (state.portalWasDetected && !network.validated) {
            return Decision(state, Action.WAIT_FOR_VALIDATION)
        }
        if (state.tunnelPausedForPortal && network.validated) {
            return Decision(State(), Action.RECOVER_TUNNEL)
        }
        return Decision(state, Action.NONE)
    }
}
