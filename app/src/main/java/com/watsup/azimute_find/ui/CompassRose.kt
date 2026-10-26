package com.watsup.azimute_find.ui

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.derivedStateOf
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.draw.drawWithCache
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.drawText
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.rememberTextMeasurer
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.watsup.azimute_find.Azimuth
import com.watsup.azimute_find.R
import com.watsup.azimute_find.ui.theme.azimuthColor
import kotlin.math.cos
import kotlin.math.roundToInt
import kotlin.math.sin

// Fixed light-on-dark colors: the rose sits on map imagery in both app themes.
private val RoseColor = Color.White
private val FaceColor = Color.Black.copy(alpha = 0.3f)
private val NorthColor = Color(0xFFFF5252)

/**
 * Compass rose drawn over the map center, replacing the crosshair: its center marks the map center.
 * The rose and the azimuth lines are one cached drawing rotated by `-heading` in a graphics layer,
 * so heading updates (~50 Hz) only change a layer transform: no recomposition, no re-drawing.
 */
@Composable
fun CompassRose(
    heading: () -> Float?,
    azimuths: List<Azimuth>,
    modifier: Modifier = Modifier,
    size: Dp = 220.dp,
) {
    val textMeasurer = rememberTextMeasurer()
    val lubberColor = MaterialTheme.colorScheme.primary

    Box(
        modifier.size(size).drawBehind {
            drawCircle(FaceColor)
            drawCircle(RoseColor.copy(alpha = 0.6f), style = Stroke(2f))
        },
    ) {
        Spacer(
            Modifier
                .matchParentSize()
                .graphicsLayer { rotationZ = -(heading() ?: 0f) }
                .drawWithCache {
                    val r = this.size.minDimension / 2f
                    val c = Offset(this.size.width / 2f, this.size.height / 2f)
                    val ticks = (0 until 360 step 10).map { deg ->
                        val major = deg % 30 == 0
                        Triple(
                            pointOnCircle(c, deg.toFloat(), r * (if (major) 0.84f else 0.9f)),
                            pointOnCircle(c, deg.toFloat(), r * 0.98f),
                            major,
                        )
                    }
                    val labels = listOf("N" to 0f, "E" to 90f, "S" to 180f, "W" to 270f).map { (label, deg) ->
                        val layout = textMeasurer.measure(
                            label,
                            TextStyle(
                                fontSize = 15.sp,
                                fontWeight = FontWeight.Bold,
                                color = if (label == "N") NorthColor else RoseColor,
                            ),
                        )
                        val p = pointOnCircle(c, deg, r * 0.68f)
                        layout to Offset(p.x - layout.size.width / 2f, p.y - layout.size.height / 2f)
                    }
                    val northArrow = Path().apply {
                        val tip = pointOnCircle(c, 0f, r * 0.5f)
                        moveTo(tip.x, tip.y)
                        lineTo(c.x - 7f, c.y - r * 0.3f)
                        lineTo(c.x + 7f, c.y - r * 0.3f)
                        close()
                    }
                    val azimuthLines = azimuths.map { az -> azimuthColor(az.slot) to pointOnCircle(c, az.degrees, r * 0.95f) }

                    onDrawBehind {
                        ticks.forEach { (start, end, major) ->
                            drawLine(
                                color = RoseColor.copy(alpha = if (major) 0.9f else 0.5f),
                                start = start,
                                end = end,
                                strokeWidth = if (major) 3f else 1.5f,
                            )
                        }
                        azimuthLines.forEach { (color, end) ->
                            drawLine(color, start = c, end = end, strokeWidth = 5f, cap = StrokeCap.Round)
                        }
                        drawPath(northArrow, NorthColor)
                        labels.forEach { (layout, topLeft) -> drawText(layout, topLeft = topLeft) }
                    }
                },
        )

        // Fixed marks: exact map center, and the lubber line (where the top of the phone points).
        Spacer(
            Modifier.matchParentSize().drawWithCache {
                val r = this.size.minDimension / 2f
                val c = Offset(this.size.width / 2f, this.size.height / 2f)
                val lubber = Path().apply {
                    moveTo(c.x, c.y - r - 2f)
                    lineTo(c.x - 10f, c.y - r + 16f)
                    lineTo(c.x + 10f, c.y - r + 16f)
                    close()
                }
                onDrawBehind {
                    drawCircle(Color.Black, radius = 6f)
                    drawCircle(RoseColor, radius = 4f)
                    drawPath(lubber, lubberColor)
                }
            },
        )
    }
}

@Composable
fun HeadingReadout(heading: () -> Float?, modifier: Modifier = Modifier) {
    // Recomposes only when the displayed whole degree changes, not on every sensor sample.
    val degrees by remember { derivedStateOf { heading()?.roundToInt()?.rem(360) } }
    Surface(
        modifier = modifier,
        shape = MaterialTheme.shapes.large,
        color = MaterialTheme.colorScheme.surfaceContainerHigh,
        tonalElevation = 3.dp,
    ) {
        Text(
            text = degrees?.let { stringResource(R.string.heading_true, it) } ?: "—",
            style = MaterialTheme.typography.titleMedium,
            modifier = Modifier.padding(horizontal = 14.dp, vertical = 8.dp),
        )
    }
}

/** Point at [deg] clockwise from up, at [radius] from [center]. */
private fun pointOnCircle(center: Offset, deg: Float, radius: Float): Offset {
    val a = Math.toRadians(deg.toDouble())
    return Offset(center.x + radius * sin(a).toFloat(), center.y - radius * cos(a).toFloat())
}
