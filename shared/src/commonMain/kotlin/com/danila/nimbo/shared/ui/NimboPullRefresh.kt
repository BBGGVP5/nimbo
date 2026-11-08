package com.danila.nimbo.shared.ui

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxScope
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.pulltorefresh.PullToRefreshDefaults
import androidx.compose.material3.pulltorefresh.pullToRefresh
import androidx.compose.material3.pulltorefresh.rememberPullToRefreshState
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier

/** Nested scroll starts pulling only after the real scrollable content reaches its top. */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
internal fun NimboPullRefresh(
    refreshing: Boolean,
    enabled: Boolean,
    onRefresh: () -> Unit,
    modifier: Modifier = Modifier,
    content: @Composable BoxScope.() -> Unit
) {
    val pullState = rememberPullToRefreshState()
    Box(modifier.pullToRefresh(
        state = pullState,
        isRefreshing = refreshing,
        enabled = enabled && !refreshing,
        onRefresh = { if (enabled && !refreshing) onRefresh() }
    )) {
        content()
        PullToRefreshDefaults.Indicator(
            state = pullState,
            isRefreshing = refreshing,
            modifier = Modifier.align(Alignment.TopCenter),
            containerColor = NimboPalette.SurfaceStrong,
            color = NimboPalette.Accent
        )
    }
}
