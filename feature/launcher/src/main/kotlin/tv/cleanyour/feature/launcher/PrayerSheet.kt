package tv.cleanyour.feature.launcher

import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.foundation.layout.Column
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import tv.cleanyour.core.spec.PrayerKeys
import tv.cleanyour.core.spec.PrayerSettings
import tv.cleanyour.ui.design.component.ChoiceList
import tv.cleanyour.ui.design.component.ChoiceOption
import tv.cleanyour.ui.design.component.CytBottomSheet
import tv.cleanyour.ui.design.component.CytSlider
import tv.cleanyour.ui.design.component.ListDivider
import tv.cleanyour.ui.design.component.ListGroup
import tv.cleanyour.ui.design.component.Overline
import tv.cleanyour.ui.design.component.PrimaryButton
import tv.cleanyour.ui.design.component.SheetHeader
import tv.cleanyour.ui.design.component.SwitchRow

// Bloatless: the prayer times sheet. Nothing reaches the TV until "Save and send to the TV".
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun PrayerSheet(current: PrayerSettings, onSave: (PrayerSettings) -> Unit, onDismiss: () -> Unit) {
    CytBottomSheet(onDismiss) { PrayerContent(current, onSave) }
}

@Composable
internal fun ColumnScope.PrayerContent(current: PrayerSettings, onSave: (PrayerSettings) -> Unit) {
    var on by remember { mutableStateOf(current.on) }
    var state by remember { mutableStateOf(PrayerKeys.zone(current.zone)?.state) }
    var zone by remember { mutableStateOf(current.zone) }
    var azan by remember { mutableStateOf(current.azan) }
    var volume by remember { mutableStateOf(current.volume) }
    var prayers by remember { mutableStateOf(current.prayers) }

    SheetHeader(stringResource(R.string.prayer_title), subtitle = stringResource(R.string.prayer_subtitle))
    Column(Modifier.weight(1f, fill = false).verticalScroll(rememberScrollState())) {
        ListGroup(Modifier.padding(top = 16.dp)) {
            SwitchRow(stringResource(R.string.prayer_show), on, { on = it })
        }
        if (on) {
            Overline(stringResource(R.string.prayer_state), Modifier.padding(top = 20.dp, bottom = 8.dp, start = 4.dp))
            ChoiceList(PrayerKeys.STATES.map { ChoiceOption(it, it, icon = null) }, state, { o ->
                if (o.key != state) { state = o.key; zone = "" }
            })
            state?.let { st ->
                Overline(stringResource(R.string.prayer_zone), Modifier.padding(top = 20.dp, bottom = 8.dp, start = 4.dp))
                ChoiceList(PrayerKeys.ZONES.filter { it.state == st }.map { ChoiceOption(it.code, it.code, it.places, icon = null) }, zone.ifEmpty { null },
                    { zone = it.key })
            }
            ListGroup(Modifier.padding(top = 20.dp)) {
                SwitchRow(stringResource(R.string.prayer_azan), azan, { azan = it }, subtitle = stringResource(R.string.prayer_azan_note))
            }
            if (azan) {
                Overline(stringResource(R.string.prayer_volume), Modifier.padding(top = 20.dp, start = 4.dp))
                CytSlider(volume, { volume = it }, PrayerSettings.VOLUME, 1, stringResource(R.string.prayer_volume), "$volume")
                Overline(stringResource(R.string.prayer_which), Modifier.padding(top = 12.dp, bottom = 8.dp, start = 4.dp))
                ListGroup {
                    PrayerKeys.NAMES.forEachIndexed { i, name ->
                        if (i > 0) ListDivider()
                        SwitchRow(name, i in prayers, { c -> prayers = if (c) prayers + i else prayers - i })
                    }
                }
            }
        }
    }
    val ready = !on || zone.isNotEmpty()
    PrimaryButton(stringResource(if (ready) R.string.prayer_save else R.string.prayer_pick_zone), {
        onSave(if (on) PrayerSettings(zone, azan, volume, prayers) else PrayerSettings.OFF)
    }, Modifier.padding(top = 20.dp), enabled = ready)
}
