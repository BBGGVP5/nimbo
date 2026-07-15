package com.danila.nimbo.ui.components

import androidx.compose.foundation.layout.*
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp

@Composable
fun VpnStatus(
    ping: Int?,
    download: String,
    upload: String
) {

    Column(
        horizontalAlignment = Alignment.CenterHorizontally
    ) {

        ping?.let {
            Text("Ping: ${it} ms")
        }

        Spacer(Modifier.height(8.dp))

        Text("↓ $download")
        Text("↑ $upload")

    }

}
