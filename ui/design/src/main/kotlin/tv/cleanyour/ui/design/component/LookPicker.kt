package tv.cleanyour.ui.design.component

import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.ui.unit.Dp
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.selection.selectable
import androidx.compose.foundation.selection.selectableGroup
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.Immutable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import tv.cleanyour.ui.design.icon.CytIcons
import tv.cleanyour.ui.design.theme.CytTheme

// 0.4.0: one picture of the Look sheet's Picture row: a key, the label under it, and the
// thumbnail (the picture itself, or the plain colour the TV shows without one).
// look2: `sub`, a second line under the name ("Until 10 Nov", read by TalkBack too); `action`, a
// tile that opens something instead of choosing ("Choose a photo": a button with a plus, read as
// `actionDescription`, never selected).
@Immutable
data class PictureChoice(
    val key: String,
    val label: String,
    val image: ImageBitmap?,
    val color: Color = Color.Black,
    val sub: String? = null,
    val action: Boolean = false,
    val actionDescription: String? = null,
)

// The Picture row (Look sheet): small 16:9 thumbnails with their names in a row that scrolls
// sideways, one of them chosen (a check and a white rim). A tap only chooses; the sheet's button
// sends the look.
@Composable
fun PicturePicker(options: List<PictureChoice>, selectedKey: String?, onSelect: (PictureChoice) -> Unit, modifier: Modifier = Modifier,
    groupLabel: String? = null) {
    // Bloatless (mockup v4): a grid of four a row instead of a sideways strip. The first two rows show, with
    // "Show all" for the rest; the chosen picture is always in view.
    val c = CytTheme.colors
    val allState = androidx.compose.runtime.saveable.rememberSaveable { androidx.compose.runtime.mutableStateOf(false) }
    val all = allState.value
    val chosenAt = options.indexOfFirst { !it.action && it.key == selectedKey }
    val collapsed = !all && options.size > FIRST_PICTURES
    // Collapsed, the last tile of the second row is "Show all", so the grid keeps its shape and nothing sits under it.
    val showAll = PictureChoice(SHOW_ALL_KEY, androidx.compose.ui.res.stringResource(tv.cleanyour.ui.design.R.string.picker_show_all, options.size), null,
        action = true, actionDescription = androidx.compose.ui.res.stringResource(tv.cleanyour.ui.design.R.string.picker_show_all, options.size))
    // The chosen picture always shows: past the first seven, it takes the seventh place.
    val first = options.take(FIRST_PICTURES - 1).let { f -> if (chosenAt >= FIRST_PICTURES - 1) f.dropLast(1) + options[chosenAt] else f }
    val shown = if (collapsed) first + showAll else options
    val pick: (PictureChoice) -> Unit = { o -> if (o.key == SHOW_ALL_KEY) allState.value = true else onSelect(o) }
    BoxWithConstraints(modifier.fillMaxWidth()) {
        val tileWidth = (maxWidth - 8.dp * 3) / 4
        Column(
            Modifier.fillMaxWidth().selectableGroup()
                .then(if (groupLabel != null) Modifier.semantics { contentDescription = groupLabel } else Modifier),
            verticalArrangement = Arrangement.spacedBy(8.dp),
        ) {
            shown.chunked(4).forEach { row ->
                Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    for (o in row) PictureTile(o, !o.action && o.key == selectedKey, tileWidth, pick)
                }
            }
        }
    }
}

private const val FIRST_PICTURES = 8
private const val SHOW_ALL_KEY = "\u0000show-all"

@Composable
private fun PictureTile(o: PictureChoice, selected: Boolean, width: Dp, onSelect: (PictureChoice) -> Unit) {
    val c = CytTheme.colors
    val shape = CytTheme.shapes.small
    val tap = if (o.action) {
        Modifier.clickable(role = Role.Button) { onSelect(o) }.semantics(mergeDescendants = true) { o.actionDescription?.let { contentDescription = it } }
    } else {
        Modifier.selectable(selected, role = Role.RadioButton) { onSelect(o) }.semantics(mergeDescendants = true) {}
    }
    Column(Modifier.width(width).clip(shape).then(tap).padding(vertical = 2.dp), horizontalAlignment = Alignment.CenterHorizontally) {
        Box(
            Modifier.fillMaxWidth().aspectRatio(16f / 10f).clip(shape).background(if (o.action) c.surface3 else o.color)
                .border(if (selected) 2.dp else 1.dp, if (selected) c.accent else c.outline, shape),
            contentAlignment = Alignment.Center,
        ) {
            if (o.image != null) Image(o.image, null, Modifier.fillMaxSize(), contentScale = ContentScale.Crop)
            if (o.action) CytIcon(if (o.key == SHOW_ALL_KEY) CytIcons.ChevronDown else CytIcons.Plus, null, tint = c.textPrimary, size = 20.dp)
            if (selected) {
                Box(Modifier.size(20.dp).background(c.accent, CytTheme.shapes.pill), contentAlignment = Alignment.Center) {
                    CytIcon(CytIcons.Check, null, tint = c.onPrimary, size = 13.dp)
                }
            }
        }
        Text(
            o.label, Modifier.padding(top = 4.dp), color = if (selected) c.textPrimary else c.textSecondary,
            style = CytTheme.type.bodySmall, textAlign = TextAlign.Center, maxLines = 2, overflow = TextOverflow.Ellipsis,
        )
        if (o.sub != null) {
            Text(o.sub, color = c.textTertiary, style = CytTheme.type.bodySmall, textAlign = TextAlign.Center, maxLines = 2)
        }
    }
}
