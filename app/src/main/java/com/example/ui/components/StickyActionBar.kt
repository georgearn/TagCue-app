package com.example.ui.components

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import com.example.ui.theme.Dimens

/** Full-width bottom bar holding a screen's primary actions. */
@Composable
fun StickyActionBar(
    modifier: Modifier = Modifier,
    content: @Composable () -> Unit
) {
    Surface(
        modifier = modifier.fillMaxWidth(),
        color = MaterialTheme.colorScheme.surfaceContainer,
        shadowElevation = 8.dp
    ) {
        Box(
            modifier = Modifier.padding(
                horizontal = Dimens.ScreenPadding,
                vertical = Dimens.ActionBarVerticalPadding
            )
        ) {
            content()
        }
    }
}
