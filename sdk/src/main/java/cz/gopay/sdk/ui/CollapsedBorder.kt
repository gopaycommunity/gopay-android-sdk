package cz.gopay.sdk.ui

import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.RoundRect
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Outline
import androidx.compose.ui.graphics.Shape
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.unit.Density
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.LayoutDirection
import androidx.compose.ui.unit.dp

/**
 * Where a field sits inside a block of inputs collapsed by
 * [PaymentCardFormTheme.inputBorderCollapse].
 *
 * The position decides which of the field's edges it draws — a line shared with a neighbour is
 * drawn by one field only, so the block reads as a single box rather than a grid of boxes — and
 * which of its corners the border radius rounds, which is only ever a corner of the whole block.
 *
 * Edges are shared only where the fields actually touch, see [CollapsedBorderCell]. Where they do
 * not, the field draws a full frame; a focused or invalid cell always strokes its whole outline.
 */
internal enum class CollapsedBorderPosition {
    /** Full-width field on top of the block. */
    TOP,

    /** Leading field of the bottom row. */
    BOTTOM_START,

    /** Trailing field of the bottom row. */
    BOTTOM_END
}

/**
 * A field's place in a collapsed block together with which of its neighbours it actually touches.
 *
 * An edge is shared, and drawn once, only where nothing is rendered between the two fields.
 * [rowsTouch] says the card number sits flush on the row below: no gap, no label above the bottom
 * row, and no error or helper line under the card number. [bottomRowTouches] says the expiration
 * sits flush against the CVV. Where a gap separates two fields both draw a full frame instead, the
 * way the hosted form does, so a field is never left floating without a side.
 */
internal data class CollapsedBorderCell(
    val position: CollapsedBorderPosition,
    val rowsTouch: Boolean,
    val bottomRowTouches: Boolean
)

/**
 * Whether the two rows of a collapsed block meet, given whether the card number currently shows
 * an error or helper line. Any [PaymentCardFormTheme.groupSpacing], a visible label above the
 * bottom row or a slot reserved by [PaymentCardFormTheme.errorMinHeight] pushes them apart, as
 * does an error or helper text under the card number while it is shown. The iOS SDK resolves this
 * the same way.
 */
internal fun PaymentCardFormTheme.collapsedRowsTouch(topRowHasFooter: Boolean): Boolean =
    groupSpacing <= 0.dp && labelHidden && errorMinHeight <= 0.dp && !topRowHasFooter

/** Whether the expiration and the CVV of a collapsed block sit flush against each other. */
internal val PaymentCardFormTheme.collapsedBottomRowTouches: Boolean
    get() = groupSpacing <= 0.dp

/**
 * Edges and corners a collapsed field draws, resolved to physical sides for the draw phase.
 *
 * An edge that is not drawn is always one shared with a neighbour that owns it; an outer edge of
 * the block is always drawn. A corner is rounded where two outer edges meet.
 */
internal data class CollapsedBorderEdges(
    val drawTop: Boolean,
    val drawBottom: Boolean,
    val drawLeft: Boolean,
    val drawRight: Boolean,
    val roundTopLeft: Boolean,
    val roundTopRight: Boolean,
    val roundBottomRight: Boolean,
    val roundBottomLeft: Boolean
)

/** Resolves [cell] into physical edges, mirroring start and end in a right-to-left layout. */
internal fun collapsedBorderEdges(
    cell: CollapsedBorderCell,
    layoutDirection: LayoutDirection
): CollapsedBorderEdges {
    val rowsTouch = cell.rowsTouch
    val bottomRowTouches = cell.bottomRowTouches
    // Resolved in leading/trailing terms first, then mapped to physical sides.
    val outerTop: Boolean
    val outerBottom: Boolean
    val outerLeading: Boolean
    val outerTrailing: Boolean
    val drawTop: Boolean
    val drawLeading: Boolean
    when (cell.position) {
        // Owns the line it shares with the row below.
        CollapsedBorderPosition.TOP -> {
            outerTop = true
            outerBottom = !rowsTouch
            outerLeading = true
            outerTrailing = true
            drawTop = true
            drawLeading = true
        }

        // Owns the line it shares with the trailing field.
        CollapsedBorderPosition.BOTTOM_START -> {
            outerTop = !rowsTouch
            outerBottom = true
            outerLeading = true
            outerTrailing = !bottomRowTouches
            drawTop = !rowsTouch
            drawLeading = true
        }

        CollapsedBorderPosition.BOTTOM_END -> {
            outerTop = !rowsTouch
            outerBottom = true
            outerLeading = !bottomRowTouches
            outerTrailing = true
            drawTop = !rowsTouch
            drawLeading = !bottomRowTouches
        }
    }
    val drawTrailing = true
    val roundTopLeading = outerTop && outerLeading
    val roundTopTrailing = outerTop && outerTrailing
    val roundBottomTrailing = outerBottom && outerTrailing
    val roundBottomLeading = outerBottom && outerLeading

    val ltr = layoutDirection == LayoutDirection.Ltr
    return CollapsedBorderEdges(
        drawTop = drawTop,
        drawBottom = true,
        drawLeft = if (ltr) drawLeading else drawTrailing,
        drawRight = if (ltr) drawTrailing else drawLeading,
        roundTopLeft = if (ltr) roundTopLeading else roundTopTrailing,
        roundTopRight = if (ltr) roundTopTrailing else roundTopLeading,
        roundBottomRight = if (ltr) roundBottomTrailing else roundBottomLeading,
        roundBottomLeft = if (ltr) roundBottomLeading else roundBottomTrailing
    )
}

/**
 * The corner radius as drawn, capped at half the shorter side the way a browser caps an oversized
 * `border-radius`: a larger value would make the straight runs negative and turn the arcs inside
 * out. Background and border both go through here, so they agree for a pill radius.
 */
internal fun collapsedCornerRadius(radius: Float, size: Size): Float =
    radius.coerceIn(0f, minOf(size.width, size.height) / 2f)

/**
 * Background and clip shape of a collapsed field, rounding only the outer corners of the block.
 * The edges are already physical, so the shape ignores the layout direction.
 */
internal class CollapsedCellShape(
    private val edges: CollapsedBorderEdges,
    private val radius: Dp
) : Shape {
    override fun createOutline(size: Size, layoutDirection: LayoutDirection, density: Density): Outline {
        val corner = collapsedCornerRadius(with(density) { radius.toPx() }, size)
        fun radiusIf(round: Boolean) = if (round) CornerRadius(corner) else CornerRadius.Zero
        return Outline.Rounded(
            RoundRect(
                left = 0f,
                top = 0f,
                right = size.width,
                bottom = size.height,
                topLeftCornerRadius = radiusIf(edges.roundTopLeft),
                topRightCornerRadius = radiusIf(edges.roundTopRight),
                bottomRightCornerRadius = radiusIf(edges.roundBottomRight),
                bottomLeftCornerRadius = radiusIf(edges.roundBottomLeft)
            )
        )
    }
}

/** A straight run of the border, from [start] to [end], stroked at the border width. */
internal data class BorderLine(val start: Offset, val end: Offset)

/** A quarter-circle run of the border, described the way `drawArc` takes it. */
internal data class BorderArc(val topLeft: Offset, val diameter: Float, val startAngle: Float)

/** Everything a collapsed border draws, as plain geometry so it can be checked without a canvas. */
internal data class CollapsedBorderGeometry(val lines: List<BorderLine>, val arcs: List<BorderArc>)

/**
 * The lines and arcs of a collapsed field's border.
 *
 * Each drawn edge is a line half a stroke inside the field, joined to a rounded corner by an arc.
 * At a square corner two butt-ended lines would leave the corner pixel unpainted, so a line is
 * lengthened by half a stroke at every square end and the two runs overlap into a full corner.
 *
 * With [fullOutline] the field also draws the edges its neighbours own, for a focused or invalid
 * cell. Such a line is painted where the neighbour paints its own: half a stroke outside this
 * field, over the seam. The state colour then replaces the shared line instead of doubling it.
 */
internal fun collapsedBorderGeometry(
    size: Size,
    strokeWidth: Float,
    radius: Float,
    edges: CollapsedBorderEdges,
    fullOutline: Boolean
): CollapsedBorderGeometry {
    val half = strokeWidth / 2f
    val left = half
    val top = half
    val right = size.width - half
    val bottom = size.height - half
    val corner = collapsedCornerRadius(radius, size)
    val topLeft = if (edges.roundTopLeft) corner else 0f
    val topRight = if (edges.roundTopRight) corner else 0f
    val bottomRight = if (edges.roundBottomRight) corner else 0f
    val bottomLeft = if (edges.roundBottomLeft) corner else 0f
    // A square end reaches half a stroke further, into the corner the perpendicular line leaves.
    fun startOf(cornerRadius: Float, inset: Float) = if (cornerRadius > 0f) inset + cornerRadius else inset - half
    fun endOf(cornerRadius: Float, inset: Float) = if (cornerRadius > 0f) inset - cornerRadius else inset + half

    val lines = mutableListOf<BorderLine>()
    if (edges.drawTop || fullOutline) {
        val y = if (edges.drawTop) top else -half
        lines += BorderLine(Offset(startOf(topLeft, left), y), Offset(endOf(topRight, right), y))
    }
    if (edges.drawBottom || fullOutline) {
        val y = if (edges.drawBottom) bottom else size.height + half
        lines += BorderLine(Offset(startOf(bottomLeft, left), y), Offset(endOf(bottomRight, right), y))
    }
    if (edges.drawLeft || fullOutline) {
        val x = if (edges.drawLeft) left else -half
        lines += BorderLine(Offset(x, startOf(topLeft, top)), Offset(x, endOf(bottomLeft, bottom)))
    }
    if (edges.drawRight || fullOutline) {
        val x = if (edges.drawRight) right else size.width + half
        lines += BorderLine(Offset(x, startOf(topRight, top)), Offset(x, endOf(bottomRight, bottom)))
    }

    val arcs = mutableListOf<BorderArc>()
    if (topLeft > 0f) arcs += BorderArc(Offset(left, top), topLeft * 2, 180f)
    if (topRight > 0f) arcs += BorderArc(Offset(right - topRight * 2, top), topRight * 2, 270f)
    if (bottomRight > 0f) {
        arcs += BorderArc(Offset(right - bottomRight * 2, bottom - bottomRight * 2), bottomRight * 2, 0f)
    }
    if (bottomLeft > 0f) arcs += BorderArc(Offset(left, bottom - bottomLeft * 2), bottomLeft * 2, 90f)
    return CollapsedBorderGeometry(lines, arcs)
}

/**
 * Draws the border of a collapsed field, see [collapsedBorderGeometry]. Compose has no per-edge
 * border, so the outline is drawn rather than composed.
 */
internal fun DrawScope.drawCollapsedBorder(
    color: Color,
    strokeWidth: Float,
    radius: Float,
    edges: CollapsedBorderEdges,
    fullOutline: Boolean
) {
    if (strokeWidth <= 0f) return
    // Clamped to the cell, see [usableStrokeWidth].
    val width = usableStrokeWidth(size, strokeWidth)
    val geometry = collapsedBorderGeometry(size, width, radius, edges, fullOutline)
    val stroke = Stroke(width = width)
    geometry.lines.forEach { drawLine(color, it.start, it.end, width) }
    geometry.arcs.forEach {
        drawArc(color, it.startAngle, 90f, false, it.topLeft, Size(it.diameter, it.diameter), style = stroke)
    }
}
