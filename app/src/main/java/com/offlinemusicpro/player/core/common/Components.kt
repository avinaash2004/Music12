package com.offlinemusicpro.player.core.common

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.RowScope
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.rounded.ArrowBack
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Slider
import androidx.compose.material3.SliderDefaults
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import com.offlinemusicpro.player.OfflineMusicApp
import com.offlinemusicpro.player.core.theme.TextPrimary
import com.offlinemusicpro.player.core.theme.TextSecondary
import com.offlinemusicpro.player.di.AppContainer

@Composable
fun rememberContainer(): AppContainer {
    val context = LocalContext.current
    return remember { (context.applicationContext as OfflineMusicApp).container }
}

@Composable
fun ScreenHeader(
    title: String,
    onBack: () -> Unit,
    modifier: Modifier = Modifier,
    actions: @Composable RowScope.() -> Unit = {}
) {
    Row(
        modifier = modifier.fillMaxWidth().padding(horizontal = 4.dp, vertical = 8.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        IconButton(onClick = onBack) {
            Icon(Icons.AutoMirrored.Rounded.ArrowBack, contentDescription = "Back", tint = TextPrimary)
        }
        Text(
            title,
            style = MaterialTheme.typography.titleLarge,
            color = TextPrimary,
            modifier = Modifier.weight(1f)
        )
        actions()
    }
}

@Composable
fun SectionTitle(text: String, modifier: Modifier = Modifier) {
    Text(
        text.uppercase(),
        style = MaterialTheme.typography.labelMedium,
        color = MaterialTheme.colorScheme.secondary,
        modifier = modifier.padding(start = 20.dp, top = 20.dp, bottom = 6.dp)
    )
}

@Composable
fun LabeledSlider(
    label: String,
    valueText: String,
    value: Float,
    range: ClosedFloatingPointRange<Float>,
    onChange: (Float) -> Unit,
    modifier: Modifier = Modifier,
    enabled: Boolean = true,
    steps: Int = 0,
    onFinished: () -> Unit = {}
) {
    Column(modifier.padding(horizontal = 20.dp, vertical = 4.dp)) {
        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
            Text(label, style = MaterialTheme.typography.bodyMedium, color = TextPrimary)
            Text(valueText, style = MaterialTheme.typography.bodyMedium, color = TextSecondary)
        }
        Slider(
            value = value.coerceIn(range.start, range.endInclusive),
            onValueChange = onChange,
            onValueChangeFinished = onFinished,
            valueRange = range,
            steps = steps,
            enabled = enabled,
            colors = SliderDefaults.colors(
                thumbColor = MaterialTheme.colorScheme.primary,
                activeTrackColor = MaterialTheme.colorScheme.primary,
                inactiveTrackColor = TextSecondary.copy(alpha = 0.3f)
            )
        )
    }
}
