package tv.cleanyour.ui.design.component

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.Box
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import tv.cleanyour.ui.design.theme.CytTheme

// Bloatless: the main screen's pieces (mockup v4). A tile of the 2 x 2 grid: icon, title, one line.
data class DashTileSpec(val icon: ImageVector, val title: String, val subtitle: String?, val onClick: () -> Unit)

@Composable
fun DashTile(spec: DashTileSpec, modifier: Modifier = Modifier) {
    val c = CytTheme.colors
    val shape = CytTheme.shapes.medium
    Column(
        modifier.clip(shape).background(c.surface1, shape).border(1.dp, c.outline, shape)
            .clickable(role = Role.Button, onClick = spec.onClick).heightIn(min = 96.dp).padding(14.dp),
    ) {
        CytIcon(spec.icon, null, tint = c.accent, size = 22.dp)
        Text(spec.title, Modifier.padding(top = 10.dp), style = CytTheme.type.bodyLarge, color = c.textPrimary, maxLines = 1, overflow = TextOverflow.Ellipsis)
        spec.subtitle?.let { Text(it, Modifier.padding(top = 2.dp), style = CytTheme.type.bodySmall, color = c.textTertiary, maxLines = 2, overflow = TextOverflow.Ellipsis) }
    }
}

// Two tiles a row; an odd last tile takes half the width.
@Composable
fun DashGrid(tiles: List<DashTileSpec>, modifier: Modifier = Modifier) {
    Column(modifier.fillMaxWidth(), verticalArrangement = Arrangement.spacedBy(10.dp)) {
        tiles.chunked(2).forEach { pair ->
            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                pair.forEach { DashTile(it, Modifier.weight(1f)) }
                if (pair.size == 1) Box(Modifier.weight(1f))
            }
        }
    }
}

// A row of small figures under the TV card: a big value over a label.
data class DashStat(val value: String, val label: String)

@Composable
fun DashStats(stats: List<DashStat>, modifier: Modifier = Modifier) {
    val c = CytTheme.colors
    Row(modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
        stats.forEach { s ->
            Column(Modifier.weight(1f).clip(CytTheme.shapes.small).background(c.bg).padding(vertical = 10.dp, horizontal = 8.dp)) {
                Text(s.value, style = CytTheme.type.titleMedium, color = c.textPrimary, maxLines = 1, overflow = TextOverflow.Ellipsis,
                    modifier = Modifier.fillMaxWidth(), textAlign = androidx.compose.ui.text.style.TextAlign.Center)
                Text(s.label, style = CytTheme.type.bodySmall, color = c.textTertiary, maxLines = 1, overflow = TextOverflow.Ellipsis,
                    modifier = Modifier.fillMaxWidth(), textAlign = androidx.compose.ui.text.style.TextAlign.Center)
            }
        }
    }
}

// A centred card with a big accent icon, a title and a line (Restore point's "Your TV is safe").
@Composable
fun HeroCard(icon: ImageVector, title: String, body: String?, modifier: Modifier = Modifier) {
    val c = CytTheme.colors
    val shape = CytTheme.shapes.medium
    Column(modifier.fillMaxWidth().clip(shape).background(c.surface1, shape).border(1.dp, c.outline, shape).padding(vertical = 20.dp, horizontal = 16.dp),
        horizontalAlignment = androidx.compose.ui.Alignment.CenterHorizontally) {
        CytIcon(icon, null, tint = c.accent, size = 36.dp)
        Text(title, Modifier.padding(top = 8.dp), style = CytTheme.type.bodyLarge, color = c.textPrimary, textAlign = androidx.compose.ui.text.style.TextAlign.Center)
        body?.let { Text(it, Modifier.padding(top = 4.dp), style = CytTheme.type.bodySmall, color = c.textTertiary, textAlign = androidx.compose.ui.text.style.TextAlign.Center) }
    }
}

// Filter chips that wrap to more lines (Switched off's groups): one selected, accent when on.
@OptIn(androidx.compose.foundation.layout.ExperimentalLayoutApi::class)
@Composable
fun FilterChips(options: List<Pair<String, String>>, selectedKey: String, onSelect: (String) -> Unit, modifier: Modifier = Modifier) {
    val c = CytTheme.colors
    androidx.compose.foundation.layout.FlowRow(modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(6.dp),
        verticalArrangement = Arrangement.spacedBy(0.dp)) {
        options.forEach { (key, label) ->
            val on = key == selectedKey
            // A 48 dp touch target around a smaller pill.
            Box(Modifier.heightIn(min = 48.dp).clickable(role = Role.Tab) { onSelect(key) }, contentAlignment = androidx.compose.ui.Alignment.Center) {
                Text(label, Modifier.clip(CytTheme.shapes.pill).background(if (on) c.accent else c.surface1, CytTheme.shapes.pill)
                    .border(1.dp, if (on) c.accent else c.outline, CytTheme.shapes.pill).padding(horizontal = 12.dp, vertical = 7.dp),
                    style = CytTheme.type.bodySmall, color = if (on) c.onPrimary else c.textSecondary, maxLines = 1)
            }
        }
    }
}
