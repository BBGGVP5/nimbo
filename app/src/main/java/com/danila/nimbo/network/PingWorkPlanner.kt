package com.danila.nimbo.network

internal data class PingWorkCandidate(
    val resultKey: String,
    val target: Pair<String, Int>?,
    val serverProtocol: String? = null,
    val network: String? = null
)

internal data class PingWorkItem(
    val resultKey: String,
    val host: String,
    val port: Int,
    val serverProtocol: String?,
    val network: String?
)

internal data class PingWorkPlan(
    val items: List<PingWorkItem>,
    val unresolvedKeys: Set<String>
)

/** Creates one measurement per node; endpoint equality never implies result equality. */
internal object PingWorkPlanner {
    fun build(candidates: List<PingWorkCandidate>): PingWorkPlan {
        val items = ArrayList<PingWorkItem>(candidates.size)
        val unresolved = LinkedHashSet<String>()

        candidates.forEach { candidate ->
            val target = candidate.target
            if (target == null) {
                unresolved += candidate.resultKey
            } else {
                items += PingWorkItem(
                    resultKey = candidate.resultKey,
                    host = target.first,
                    port = target.second,
                    serverProtocol = candidate.serverProtocol,
                    network = candidate.network
                )
            }
        }

        return PingWorkPlan(items = items, unresolvedKeys = unresolved)
    }
}
