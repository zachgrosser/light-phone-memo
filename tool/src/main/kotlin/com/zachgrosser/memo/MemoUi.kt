package com.zachgrosser.memo

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.gestures.detectHorizontalDragGestures
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.material3.Text
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.keepScreenOn
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.unit.isSpecified
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import com.thelightphone.sdk.ui.LightBarButton
import com.thelightphone.sdk.ui.LightFullscreenModal
import com.thelightphone.sdk.ui.LightIcons
import com.thelightphone.sdk.ui.LightText
import com.thelightphone.sdk.ui.LightTextVariant
import com.thelightphone.sdk.ui.LightTheme
import com.thelightphone.sdk.ui.LightThemeController
import com.thelightphone.sdk.ui.LightThemeTokens
import com.thelightphone.sdk.ui.LightTopBar
import com.thelightphone.sdk.ui.LightTopBarCenter
import com.thelightphone.sdk.ui.designVerticalPxToSp
import com.thelightphone.sdk.ui.gridUnitsAsDp
import com.thelightphone.sdk.ui.lightClickable
import kotlin.math.floor
import kotlin.math.max

/**
 * Applies the user's LightOS theme (light or dark), fills the screen with its background, and
 * keeps the screen on while Memo is open, whatever the phone's screen timeout says.
 */
@Composable
fun MemoTheme(content: @Composable () -> Unit) {
    val colors by LightThemeController.colors.collectAsState()
    LightTheme(colors = colors) {
        Box(
            modifier = Modifier
                .fillMaxSize()
                .keepScreenOn()
                .background(LightThemeTokens.colors.background),
        ) {
            content()
        }
    }
}

fun backButton(onClick: () -> Unit) = LightBarButton.LightIcon(
    icon = LightIcons.BACK,
    onClick = onClick,
    contentDescription = "Back",
)

/** A top bar plus a centred message, with an optional action under it. */
@Composable
fun MessageScreen(
    title: String,
    message: String,
    onBack: (() -> Unit)?,
    action: String? = null,
    onAction: (() -> Unit)? = null,
) {
    val unit = 1f.gridUnitsAsDp()
    Column(modifier = Modifier.fillMaxSize()) {
        LightTopBar(
            leftButton = onBack?.let { backButton(it) },
            center = LightTopBarCenter.Text(title),
        )
        Column(
            modifier = Modifier
                .weight(1f)
                .fillMaxWidth()
                .padding(horizontal = unit),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = androidx.compose.foundation.layout.Arrangement.Center,
        ) {
            LightText(text = message, variant = LightTextVariant.Copy, align = TextAlign.Center)
            if (action != null && onAction != null) {
                Spacer(Modifier.height(unit * 1.5f))
                LightText(
                    text = action,
                    variant = LightTextVariant.Button,
                    underline = true,
                    modifier = Modifier.lightClickable(onClick = onAction),
                )
            }
        }
    }
}

/** One tappable line in a list: a title, an optional detail line under it, and [trailing] text. */
@Composable
fun ListRow(title: String, onClick: (() -> Unit)?, detail: String? = null, trailing: String? = null) {
    val unit = 1f.gridUnitsAsDp()
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .then(if (onClick != null) Modifier.lightClickable(onClick = onClick) else Modifier)
            .padding(top = unit * 0.5f, bottom = unit * 0.5f, end = unit),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Column(modifier = Modifier.weight(1f)) {
            LightText(text = title, variant = LightTextVariant.Copy, maxLines = 2, overflow = TextOverflow.Ellipsis)
            detail?.let {
                LightText(text = it, variant = LightTextVariant.Detail, lighten = true, maxLines = 3, overflow = TextOverflow.Ellipsis)
            }
        }
        if (trailing != null) {
            Spacer(Modifier.width(unit))
            LightText(text = trailing, variant = LightTextVariant.Detail, lighten = true)
        }
    }
}

/** A small heading above a group of rows. */
@Composable
fun ListHeading(text: String) {
    val unit = 1f.gridUnitsAsDp()
    LightText(
        text = text,
        variant = LightTextVariant.Detail,
        lighten = true,
        modifier = Modifier.padding(top = unit, bottom = unit * 0.25f),
    )
}

/** A paragraph of explanation inside a scrolling list. */
@Composable
fun Note(text: String, modifier: Modifier = Modifier) {
    LightText(
        text = text,
        variant = LightTextVariant.Paragraph,
        modifier = modifier.padding(end = 1f.gridUnitsAsDp(), bottom = 0.5f.gridUnitsAsDp()),
    )
}

/**
 * Times that count up or down, in Light's typeface (Akkurat, built into the phone) with
 * same-width digits, so "0:11" and "0:18" take the same space and the text doesn't shuffle
 * sideways every second. [style] is one of Light's styles, such as
 * `LightThemeTokens.typography.title` (the large, light numbers LightOS uses).
 */
@Composable
fun TimeText(text: String, style: TextStyle, modifier: Modifier = Modifier, lighten: Boolean = false) {
    val colors = LightThemeTokens.colors
    Text(
        text = text,
        modifier = modifier,
        color = if (lighten) colors.contentSecondary else colors.content,
        // Scaled to the screen the same way LightText scales Light's styles.
        style = style.copy(
            fontSize = style.fontSize.value.designVerticalPxToSp(),
            lineHeight = style.lineHeight.value.designVerticalPxToSp(),
            letterSpacing = if (style.letterSpacing.isSpecified) style.letterSpacing.value.designVerticalPxToSp() else style.letterSpacing,
            fontFeatureSettings = "tnum",
        ),
    )
}

/** Light's full-screen notice, drawn over the current screen: low storage, recording stopped. */
@Composable
fun FullScreenNotice(message: String, onClose: () -> Unit) {
    LightFullscreenModal(
        message = message,
        onClose = onClose,
        modifier = Modifier.background(LightThemeTokens.colors.background),
    )
}

/**
 * The recording drawn as lines, like Google Recorder: a row of rounded vertical bars, one per
 * [Waveform] sample, mirrored about the middle and scrolling past a fixed line in the centre.
 *
 * - While recording, [position] is [totalSamples], the end of what's recorded: new bars appear at the centre line and
 *   move left. Right of the line is empty, shown as a row of dots.
 * - While playing, bars left of the line (already heard) are full colour and the rest are
 *   dimmed. With [onScrub], dragging sideways moves through the recording.
 *
 * [position] is in samples and may be fractional, so the drawing scrolls smoothly.
 */
@Composable
fun WaveformView(
    levels: List<Int>,
    position: Float,
    modifier: Modifier = Modifier,
    totalSamples: Int = levels.size,
    /** Draw dots past [totalSamples] too: the empty future while recording. */
    dotsBeyondEnd: Boolean = false,
    onScrubStart: (() -> Unit)? = null,
    onScrub: ((Float) -> Unit)? = null,
    onScrubEnd: (() -> Unit)? = null,
) {
    val colors = LightThemeTokens.colors
    val barWidth = 0.24f.gridUnitsAsDp()
    val gap = 0.2f.gridUnitsAsDp()
    val centreLineWidth = 0.2f.gridUnitsAsDp()
    val currentPosition by rememberUpdatedState(position)
    val currentTotal by rememberUpdatedState(totalSamples)
    val dragModifier = if (onScrub == null) {
        Modifier
    } else {
        Modifier.pointerInput(Unit) {
            val step = (barWidth + gap).toPx()
            var dragPosition = 0f
            detectHorizontalDragGestures(
                onDragStart = {
                    dragPosition = currentPosition
                    onScrubStart?.invoke()
                },
                onDragEnd = { onScrubEnd?.invoke() },
                onDragCancel = { onScrubEnd?.invoke() },
            ) { change, dragAmount ->
                change.consume()
                // Dragging right pulls earlier audio towards the centre line, as in Recorder.
                dragPosition = (dragPosition - dragAmount / step).coerceIn(0f, currentTotal.toFloat())
                onScrub(dragPosition)
            }
        }
    }
    Canvas(modifier = modifier.then(dragModifier)) {
        val step = (barWidth + gap).toPx()
        val bar = barWidth.toPx()
        val centreX = size.width / 2f
        val centreY = size.height / 2f
        // The loudest bars fill most of the height; the centre line reaches just past them.
        val maxHalf = size.height * 0.36f
        val minHalf = bar / 2f
        val radius = CornerRadius(bar / 2f, bar / 2f)

        // The bars that fit on screen around the current position.
        val first = max(0, floor(position - centreX / step).toInt() - 1)
        val last = (position + centreX / step).toInt() + 1
        for (i in first..last) {
            val x = centreX + (i - position) * step
            if (x < -bar || x > size.width) continue
            val level = if (i < totalSamples) levels.getOrNull(i) else null
            if (level == null) {
                // Nothing drawn here (yet): a dot on the middle line.
                if (i < totalSamples || dotsBeyondEnd) {
                    drawCircle(colors.contentSecondary.copy(alpha = 0.5f), radius = bar / 3f, center = Offset(x + bar / 2f, centreY))
                }
                continue
            }
            val half = max(minHalf, maxHalf * level / 255f)
            val heard = i < position
            drawRoundRect(
                color = if (heard) colors.content else colors.contentSecondary.copy(alpha = 0.55f),
                topLeft = Offset(x, centreY - half),
                size = Size(bar, half * 2f),
                cornerRadius = radius,
            )
        }
        // The centre line: "now". Rounded ends, like the bars.
        val lineWidth = centreLineWidth.toPx()
        val lineHalf = maxHalf + bar * 2f
        drawRoundRect(
            color = colors.content,
            topLeft = Offset(centreX + bar / 2f - lineWidth / 2f, centreY - lineHalf),
            size = Size(lineWidth, lineHalf * 2f),
            cornerRadius = CornerRadius(lineWidth / 2f, lineWidth / 2f),
        )
    }
}
