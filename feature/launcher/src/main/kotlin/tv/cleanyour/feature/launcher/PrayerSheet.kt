package tv.cleanyour.feature.launcher

import androidx.compose.foundation.layout.ColumnScope
import tv.cleanyour.ui.design.theme.Glow
import tv.cleanyour.ui.design.theme.CytTheme
import tv.cleanyour.ui.design.component.ScreenTitle
import tv.cleanyour.ui.design.component.ScreenScaffold
import tv.cleanyour.ui.design.component.Lead
import tv.cleanyour.ui.design.component.Gap
import tv.cleanyour.ui.design.component.CytTopBar
import tv.cleanyour.core.flows.PrayerTimesSource
import tv.cleanyour.core.flows.HomeScreenState
import tv.cleanyour.core.flows.HomeScreenIntent
import androidx.compose.ui.draw.clip
import androidx.compose.ui.Alignment
import androidx.compose.material3.Text
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.border
import androidx.compose.foundation.background
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
import tv.cleanyour.ui.design.component.ListRow
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
internal fun ColumnScope.PrayerContent(current: PrayerSettings, onSave: (PrayerSettings) -> Unit, page: Boolean = false) {
    // Keyed on the saved choice, so a page that opens before it is read shows it once it is.
    var on by remember(current) { mutableStateOf(current.on) }
    var state by remember(current) { mutableStateOf(PrayerKeys.zone(current.zone)?.state) }
    var zone by remember(current) { mutableStateOf(current.zone) }
    var azan by remember(current) { mutableStateOf(current.azan) }
    var volume by remember(current) { mutableStateOf(current.volume) }
    var prayers by remember(current) { mutableStateOf(current.prayers) }
    var picking by remember(current) { mutableStateOf(current.zone.isEmpty()) }

    if (!page) SheetHeader(stringResource(R.string.prayer_title), subtitle = stringResource(R.string.prayer_subtitle))
    // In the sheet the list scrolls inside it; on the page the page itself scrolls.
    Column(if (page) Modifier else Modifier.weight(1f, fill = false).verticalScroll(rememberScrollState())) {
        ListGroup(Modifier.padding(top = 16.dp)) {
            SwitchRow(stringResource(R.string.prayer_show), on, { on = it })
        }
        if (on) {
            // A chosen zone is one row ("Terengganu, TRG04: Dungun, Kemaman") until Change opens the lists again.
            val chosen = PrayerKeys.zone(zone)
            if (chosen != null && !picking) {
                Overline(stringResource(R.string.prayer_zone), Modifier.padding(top = 20.dp, bottom = 8.dp, start = 4.dp))
                ListGroup {
                    ListRow(chosen.state, subtitle = "${chosen.code} · ${chosen.places}", value = stringResource(R.string.prayer_change),
                        onClick = { picking = true })
                }
            } else {
                Overline(stringResource(R.string.prayer_state), Modifier.padding(top = 20.dp, bottom = 8.dp, start = 4.dp))
                ChoiceList(PrayerKeys.STATES.map { ChoiceOption(it, it, icon = null) }, state, { o ->
                    if (o.key != state) { state = o.key; zone = "" }
                })
                state?.let { st ->
                    Overline(stringResource(R.string.prayer_zone), Modifier.padding(top = 20.dp, bottom = 8.dp, start = 4.dp))
                    ChoiceList(PrayerKeys.ZONES.filter { it.state == st }.map { ChoiceOption(it.code, it.code, it.places, icon = null) }, zone.ifEmpty { null },
                        { zone = it.key; picking = false })
                }
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

// Bloatless (mockup v4): Prayer times as a page of its own: today's five times for the zone, the next one in
// the accent, then the same choices as the sheet. Save sends the look with them, as from the Home screen page.
@Composable
fun PrayerScreen(state: HomeScreenState, onIntent: (HomeScreenIntent) -> Unit, onBack: () -> Unit, modifier: Modifier = Modifier) {
    ScreenScaffold(Glow.Tv, modifier, topBar = { CytTopBar(onBack = onBack) }) {
        ScreenTitle(stringResource(R.string.prayer_title), Modifier.padding(top = 20.dp))
        Lead(stringResource(R.string.prayer_subtitle), Modifier.padding(top = 8.dp))
        if (state.prayer.on) TodayCard(state.prayer.zone, state.prayerToday, Modifier.padding(top = 16.dp))
        PrayerContent(state.prayer, { onIntent(HomeScreenIntent.SetPrayer(it)) }, page = true)
        Gap(24.dp)
    }
}

@Composable
private fun TodayCard(zone: String, times: List<String>?, modifier: Modifier = Modifier) {
    val c = CytTheme.colors
    val place = PrayerKeys.zone(zone)?.places?.substringBefore(',') ?: zone
    Column(modifier.fillMaxWidth().clip(CytTheme.shapes.medium).background(c.surface1).border(1.dp, c.outline, CytTheme.shapes.medium).padding(16.dp)) {
        Text(msg(R.string.prayer_today, "place" to place), style = CytTheme.type.bodySmall, color = c.textTertiary)
        if (times == null) {
            Text(stringResource(R.string.prayer_today_none), Modifier.padding(top = 8.dp), style = CytTheme.type.bodyMedium, color = c.textSecondary)
            return@Column
        }
        val now = java.time.LocalTime.now(java.time.ZoneId.of("Asia/Kuala_Lumpur"))
        val next = PrayerTimesSource.nextIndex(times, now.hour * 60 + now.minute)
        Row(Modifier.fillMaxWidth().padding(top = 10.dp), horizontalArrangement = Arrangement.SpaceBetween) {
            PrayerKeys.NAMES.forEachIndexed { i, name ->
                Column(horizontalAlignment = Alignment.CenterHorizontally) {
                    Text(name, style = CytTheme.type.bodySmall, color = if (i == next) c.accent else c.textTertiary)
                    Text(times[i], Modifier.padding(top = 2.dp), style = CytTheme.type.titleMedium, color = if (i == next) c.accent else c.textPrimary)
                }
            }
        }
    }
}
