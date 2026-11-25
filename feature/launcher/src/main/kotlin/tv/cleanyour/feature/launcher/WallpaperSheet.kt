package tv.cleanyour.feature.launcher

import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import tv.cleanyour.core.spec.PlacesSettings
import tv.cleanyour.ui.design.component.ChoiceList
import tv.cleanyour.ui.design.component.ChoiceOption
import tv.cleanyour.ui.design.component.CytBottomSheet
import tv.cleanyour.ui.design.component.ListGroup
import tv.cleanyour.ui.design.component.Overline
import tv.cleanyour.ui.design.component.PrimaryButton
import tv.cleanyour.ui.design.component.SheetHeader
import tv.cleanyour.ui.design.component.SwitchRow

// Bloatless: the changing wallpaper sheet (places of the week, optional). Nothing reaches the TV until Save.
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun WallpaperSheet(current: PlacesSettings, onSave: (PlacesSettings) -> Unit, onDismiss: () -> Unit) {
    CytBottomSheet(onDismiss) { WallpaperContent(current, onSave) }
}

@Composable
internal fun ColumnScope.WallpaperContent(current: PlacesSettings, onSave: (PlacesSettings) -> Unit) {
    var on by remember { mutableStateOf(current.on) }
    var minutes by remember { mutableStateOf(current.minutes) }
    SheetHeader(stringResource(R.string.places_title), subtitle = stringResource(R.string.places_subtitle))
    ListGroup(Modifier.padding(top = 16.dp)) {
        SwitchRow(stringResource(R.string.places_switch), on, { on = it }, subtitle = stringResource(R.string.places_switch_note))
    }
    if (on) {
        Overline(stringResource(R.string.places_every), Modifier.padding(top = 20.dp, bottom = 8.dp, start = 4.dp))
        ChoiceList(PlacesSettings.CHOICES.map { ChoiceOption(it.toString(), intervalName(it), icon = null) }, minutes.toString(),
            { minutes = it.key.toInt() })
    }
    PrimaryButton(stringResource(R.string.places_save), { onSave(if (on) PlacesSettings(true, minutes) else PlacesSettings.OFF) },
        Modifier.padding(top = 20.dp))
}

@Composable
internal fun intervalName(minutes: Int): String = when (minutes) {
    15 -> stringResource(R.string.places_15m)
    60 -> stringResource(R.string.places_1h)
    360 -> stringResource(R.string.places_6h)
    1440 -> stringResource(R.string.places_1d)
    else -> "$minutes min"
}
