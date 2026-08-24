package com.opencode.multilensipcam

import kotlin.math.ceil
import kotlin.math.max
import kotlin.math.roundToInt

class Nv21PixelOverlayRenderer {
    private var cachedLayout: OverlayLayout? = null
    private var cachedLayoutKey: OverlayLayoutKey? = null

    fun draw(
        nv21: ByteArray,
        width: Int,
        height: Int,
        timestamp: String,
        status: VideoOverlayStatus
    ) {
        if (width <= 0 || height <= 0) return
        val batteryPercentText = status.batteryPercent?.coerceIn(0, 100)?.let { "$it%" } ?: "--%"
        val chargingText = if (status.charging) " ~" else ""
        val text = "$timestamp $batteryPercentText$chargingText"
        val glyphCount = text.length
        val textCells = glyphCount * GLYPH_ADVANCE_CELLS
        val totalCells = textCells + BATTERY_ADVANCE_CELLS
        if (totalCells <= 0) return

        val cellSize = (status.targetWidthPx / totalCells).coerceIn(MIN_CELL_SIZE_PX, MAX_CELL_SIZE_PX)
        val margin = max(8f, cellSize)
        val normalizedCellSize = (cellSize * 10f).roundToInt() / 10f
        val key = OverlayLayoutKey(
            timestamp = timestamp,
            batteryPercent = status.batteryPercent?.coerceIn(0, 100),
            charging = status.charging,
            cellSizePx = normalizedCellSize
        )
        val layout = getOrBuildLayout(key, timestamp, batteryPercentText, chargingText, cellSize)
        drawLayout(nv21, width, height, margin.roundToInt(), margin.roundToInt(), layout)
    }

    private fun getOrBuildLayout(
        key: OverlayLayoutKey,
        timestamp: String,
        batteryPercentText: String,
        chargingText: String,
        cellSize: Float
    ): OverlayLayout {
        val existing = cachedLayout
        if (existing != null && cachedLayoutKey == key) {
            return existing
        }

        var x = 0f
        val glyphs = ArrayList<GlyphLayoutEntry>(timestamp.length + batteryPercentText.length + chargingText.length + 8)
        for (char in timestamp) {
            x += appendGlyphLayoutEntry(glyphs, char, x, cellSize)
        }
        x += GLYPH_ADVANCE_CELLS * cellSize
        val batteryPercent = key.batteryPercent
        val battery = BatteryLayoutEntry(
            left = x.roundToInt(),
            top = 0,
            width = (BATTERY_WIDTH_CELLS * cellSize).roundToInt().coerceAtLeast(1),
            height = (GLYPH_HEIGHT * cellSize).roundToInt().coerceAtLeast(1),
            cellSizePx = cellSize.roundToInt().coerceAtLeast(1),
            fillBars = when {
                batteryPercent == null || batteryPercent <= 0 -> 0
                else -> ceil(batteryPercent / 25f).toInt().coerceIn(1, 4)
            }
        )
        x += BATTERY_ADVANCE_CELLS * cellSize
        for (char in " $batteryPercentText$chargingText") {
            x += appendGlyphLayoutEntry(glyphs, char, x, cellSize)
        }
        return OverlayLayout(glyphs = glyphs, battery = battery).also {
            cachedLayout = it
            cachedLayoutKey = key
        }
    }

    private fun appendGlyphLayoutEntry(
        entries: MutableList<GlyphLayoutEntry>,
        rawChar: Char,
        left: Float,
        cellSize: Float
    ): Float {
        val char = if (rawChar in 'a'..'z') rawChar.uppercaseChar() else rawChar
        if (char == ' ') return GLYPH_ADVANCE_CELLS * cellSize
        val glyph = GLYPHS[char] ?: GLYPHS['?'] ?: EMPTY_GLYPH
        entries.add(
            GlyphLayoutEntry(
                glyph = glyph,
                left = left.roundToInt(),
                top = 0,
                width = (GLYPH_WIDTH * cellSize).roundToInt().coerceAtLeast(1),
                height = (GLYPH_HEIGHT * cellSize).roundToInt().coerceAtLeast(1),
                cellSizePx = cellSize.roundToInt().coerceAtLeast(1)
            )
        )
        return GLYPH_ADVANCE_CELLS * cellSize
    }

    private fun drawLayout(
        nv21: ByteArray,
        width: Int,
        height: Int,
        originX: Int,
        originY: Int,
        layout: OverlayLayout
    ) {
        for (glyph in layout.glyphs) {
            val drawX = originX + glyph.left
            val drawY = originY + glyph.top
            val brightBackground = isBrightRegion(nv21, width, height, drawX, drawY, glyph.width, glyph.height)
            drawGlyphNv21(nv21, width, height, glyph, drawX, drawY, brightBackground)
        }
        val battery = layout.battery
        val batteryX = originX + battery.left
        val batteryY = originY + battery.top
        val brightBackground = isBrightRegion(nv21, width, height, batteryX, batteryY, battery.width, battery.height)
        drawBatteryNv21(nv21, width, height, battery, batteryX, batteryY, brightBackground)
    }

    private fun drawGlyphNv21(
        nv21: ByteArray,
        width: Int,
        height: Int,
        glyph: GlyphLayoutEntry,
        x: Int,
        y: Int,
        brightBackground: Boolean
    ) {
        val fg = if (brightBackground) LUMA_BLACK else LUMA_WHITE
        val shadow = if (brightBackground) LUMA_WHITE_SHADOW else LUMA_BLACK_SHADOW
        val shadowOffset = max(1, (glyph.cellSizePx * 33) / 100)
        drawGlyphPixels(nv21, width, height, glyph.glyph, x + shadowOffset, y + shadowOffset, glyph.cellSizePx, shadow)
        drawGlyphPixels(nv21, width, height, glyph.glyph, x, y, glyph.cellSizePx, fg)
    }

    private fun drawBatteryNv21(
        nv21: ByteArray,
        width: Int,
        height: Int,
        battery: BatteryLayoutEntry,
        x: Int,
        y: Int,
        brightBackground: Boolean
    ) {
        val fg = if (brightBackground) LUMA_BLACK else LUMA_WHITE
        val shadow = if (brightBackground) LUMA_WHITE_SHADOW else LUMA_BLACK_SHADOW
        val shadowOffset = max(1, (battery.cellSizePx * 33) / 100)
        drawBatteryBody(nv21, width, height, x + shadowOffset, y + shadowOffset, battery.cellSizePx, shadow, battery.fillBars)
        drawBatteryBody(nv21, width, height, x, y, battery.cellSizePx, fg, battery.fillBars)
    }

    private fun drawGlyphPixels(
        nv21: ByteArray,
        width: Int,
        height: Int,
        glyph: IntArray,
        x: Int,
        y: Int,
        cellSize: Int,
        luma: Int
    ) {
        glyph.forEachIndexed { rowIndex, rowBits ->
            for (col in 0 until GLYPH_WIDTH) {
                val bit = (rowBits shr (GLYPH_WIDTH - 1 - col)) and 0x1
                if (bit == 0) continue
                drawCellRect(nv21, width, height, x + col * cellSize, y + rowIndex * cellSize, cellSize, cellSize, luma)
            }
        }
    }

    private fun drawBatteryBody(
        nv21: ByteArray,
        width: Int,
        height: Int,
        x: Int,
        y: Int,
        cellSize: Int,
        luma: Int,
        fillBars: Int
    ) {
        drawCellRect(nv21, width, height, x, y, BATTERY_BODY_WIDTH_CELLS * cellSize, cellSize, luma)
        drawCellRect(
            nv21,
            width,
            height,
            x,
            y + (GLYPH_HEIGHT - 1) * cellSize,
            BATTERY_BODY_WIDTH_CELLS * cellSize,
            cellSize,
            luma
        )
        drawCellRect(nv21, width, height, x, y, cellSize, GLYPH_HEIGHT * cellSize, luma)
        drawCellRect(
            nv21,
            width,
            height,
            x + (BATTERY_BODY_WIDTH_CELLS - 1) * cellSize,
            y,
            cellSize,
            GLYPH_HEIGHT * cellSize,
            luma
        )
        val terminalX = x + BATTERY_BODY_WIDTH_CELLS * cellSize
        drawCellRect(nv21, width, height, terminalX, y + 2 * cellSize, BATTERY_TERMINAL_WIDTH_CELLS * cellSize, 3 * cellSize, luma)
        val innerStartX = x + cellSize
        val innerY = y + cellSize
        val barGapCells = 1
        for (bar in 0 until fillBars) {
            val barX = innerStartX + bar * (1 + barGapCells) * cellSize
            drawCellRect(nv21, width, height, barX, innerY, cellSize, (GLYPH_HEIGHT - 2) * cellSize, luma)
        }
    }

    private fun drawCellRect(
        nv21: ByteArray,
        width: Int,
        height: Int,
        left: Int,
        top: Int,
        rectWidth: Int,
        rectHeight: Int,
        luma: Int
    ) {
        val l = left.coerceIn(0, width)
        val t = top.coerceIn(0, height)
        val r = (left + rectWidth).coerceIn(l, width)
        val b = (top + rectHeight).coerceIn(t, height)
        if (r <= l || b <= t) return

        for (row in t until b) {
            var yIndex = row * width + l
            for (col in l until r) {
                nv21[yIndex] = luma.toByte()
                yIndex += 1
            }
        }
        setUvNeutral(nv21, width, height, l, t, r, b)
    }

    private fun setUvNeutral(
        nv21: ByteArray,
        width: Int,
        height: Int,
        left: Int,
        top: Int,
        right: Int,
        bottom: Int
    ) {
        val ySize = width * height
        val uvTop = (top / 2) * 2
        val uvBottom = ((bottom + 1) / 2) * 2
        val uvLeft = (left / 2) * 2
        val uvRight = ((right + 1) / 2) * 2
        var row = uvTop
        while (row < uvBottom && row < height) {
            val uvRowStart = ySize + (row / 2) * width
            var col = uvLeft
            while (col < uvRight && col + 1 < width) {
                val uvIndex = uvRowStart + col
                if (uvIndex + 1 < nv21.size) {
                    nv21[uvIndex] = 128.toByte()
                    nv21[uvIndex + 1] = 128.toByte()
                }
                col += 2
            }
            row += 2
        }
    }

    private fun isBrightRegion(
        nv21: ByteArray,
        width: Int,
        height: Int,
        left: Int,
        top: Int,
        regionWidth: Int,
        regionHeight: Int
    ): Boolean {
        val l = left.coerceIn(0, width - 1)
        val t = top.coerceIn(0, height - 1)
        val r = (left + regionWidth).coerceIn(l + 1, width)
        val b = (top + regionHeight).coerceIn(t + 1, height)
        val stepX = max(1, (r - l) / 4)
        val stepY = max(1, (b - t) / 4)
        var sum = 0
        var count = 0
        var y = t
        while (y < b) {
            var x = l
            val rowBase = y * width
            while (x < r) {
                sum += nv21[rowBase + x].toInt() and 0xFF
                count += 1
                x += stepX
            }
            y += stepY
        }
        if (count == 0) return false
        return (sum.toDouble() / count.toDouble()) >= LUMA_BRIGHT_THRESHOLD
    }

    private companion object {
        private const val GLYPH_WIDTH = 5
        private const val GLYPH_HEIGHT = 7
        private const val GLYPH_ADVANCE_CELLS = 6
        private const val BATTERY_BODY_WIDTH_CELLS = 6
        private const val BATTERY_TERMINAL_WIDTH_CELLS = 1
        private const val BATTERY_WIDTH_CELLS = BATTERY_BODY_WIDTH_CELLS + BATTERY_TERMINAL_WIDTH_CELLS
        private const val BATTERY_ADVANCE_CELLS = BATTERY_WIDTH_CELLS + 2
        private const val MIN_CELL_SIZE_PX = 2f
        private const val MAX_CELL_SIZE_PX = 45f
        private const val LUMA_BRIGHT_THRESHOLD = 160.0
        private const val LUMA_WHITE = 240
        private const val LUMA_BLACK = 20
        private const val LUMA_WHITE_SHADOW = 170
        private const val LUMA_BLACK_SHADOW = 90
        private val EMPTY_GLYPH = intArrayOf(0, 0, 0, 0, 0, 0, 0)
        private val GLYPHS: Map<Char, IntArray> = mapOf(
            '0' to rows("01110", "10001", "10001", "10001", "10001", "10001", "01110"),
            '1' to rows("00100", "01100", "00100", "00100", "00100", "00100", "01110"),
            '2' to rows("01110", "10001", "00001", "00010", "00100", "01000", "11111"),
            '3' to rows("11110", "00001", "00001", "01110", "00001", "00001", "11110"),
            '4' to rows("00010", "00110", "01010", "10010", "11111", "00010", "00010"),
            '5' to rows("11111", "10000", "10000", "11110", "00001", "00001", "11110"),
            '6' to rows("01110", "10000", "10000", "11110", "10001", "10001", "01110"),
            '7' to rows("11111", "00001", "00010", "00100", "01000", "01000", "01000"),
            '8' to rows("01110", "10001", "10001", "01110", "10001", "10001", "01110"),
            '9' to rows("01110", "10001", "10001", "01111", "00001", "00001", "01110"),
            'A' to rows("01110", "10001", "10001", "11111", "10001", "10001", "10001"),
            'B' to rows("11110", "10001", "10001", "11110", "10001", "10001", "11110"),
            'C' to rows("01110", "10001", "10000", "10000", "10000", "10001", "01110"),
            'D' to rows("11110", "10001", "10001", "10001", "10001", "10001", "11110"),
            'E' to rows("11111", "10000", "10000", "11110", "10000", "10000", "11111"),
            'F' to rows("11111", "10000", "10000", "11110", "10000", "10000", "10000"),
            'G' to rows("01110", "10001", "10000", "10111", "10001", "10001", "01110"),
            'H' to rows("10001", "10001", "10001", "11111", "10001", "10001", "10001"),
            'I' to rows("01110", "00100", "00100", "00100", "00100", "00100", "01110"),
            'J' to rows("00001", "00001", "00001", "00001", "10001", "10001", "01110"),
            'K' to rows("10001", "10010", "10100", "11000", "10100", "10010", "10001"),
            'L' to rows("10000", "10000", "10000", "10000", "10000", "10000", "11111"),
            'M' to rows("10001", "11011", "10101", "10101", "10001", "10001", "10001"),
            'N' to rows("10001", "11001", "10101", "10011", "10001", "10001", "10001"),
            'O' to rows("01110", "10001", "10001", "10001", "10001", "10001", "01110"),
            'P' to rows("11110", "10001", "10001", "11110", "10000", "10000", "10000"),
            'Q' to rows("01110", "10001", "10001", "10001", "10101", "10010", "01101"),
            'R' to rows("11110", "10001", "10001", "11110", "10100", "10010", "10001"),
            'S' to rows("01111", "10000", "10000", "01110", "00001", "00001", "11110"),
            'T' to rows("11111", "00100", "00100", "00100", "00100", "00100", "00100"),
            'U' to rows("10001", "10001", "10001", "10001", "10001", "10001", "01110"),
            'V' to rows("10001", "10001", "10001", "10001", "10001", "01010", "00100"),
            'W' to rows("10001", "10001", "10001", "10101", "10101", "10101", "01010"),
            'X' to rows("10001", "10001", "01010", "00100", "01010", "10001", "10001"),
            'Y' to rows("10001", "10001", "01010", "00100", "00100", "00100", "00100"),
            'Z' to rows("11111", "00001", "00010", "00100", "01000", "10000", "11111"),
            '/' to rows("00001", "00010", "00100", "01000", "10000", "00000", "00000"),
            ':' to rows("00000", "00100", "00100", "00000", "00100", "00100", "00000"),
            '%' to rows("11001", "11010", "00100", "01000", "10110", "00110", "00000"),
            '~' to rows("00000", "00000", "01001", "10110", "00000", "00000", "00000"),
            '-' to rows("00000", "00000", "00000", "01110", "00000", "00000", "00000"),
            '?' to rows("01110", "10001", "00010", "00100", "00100", "00000", "00100")
        )

        private fun rows(
            row0: String,
            row1: String,
            row2: String,
            row3: String,
            row4: String,
            row5: String,
            row6: String
        ): IntArray {
            return intArrayOf(
                rowToBits(row0),
                rowToBits(row1),
                rowToBits(row2),
                rowToBits(row3),
                rowToBits(row4),
                rowToBits(row5),
                rowToBits(row6)
            )
        }

        private fun rowToBits(row: String): Int {
            var bits = 0
            for (char in row) {
                bits = bits shl 1
                if (char == '1') bits = bits or 1
            }
            return bits
        }
    }

    private data class OverlayLayoutKey(
        val timestamp: String,
        val batteryPercent: Int?,
        val charging: Boolean,
        val cellSizePx: Float
    )

    private data class GlyphLayoutEntry(
        val glyph: IntArray,
        val left: Int,
        val top: Int,
        val width: Int,
        val height: Int,
        val cellSizePx: Int
    )

    private data class BatteryLayoutEntry(
        val left: Int,
        val top: Int,
        val width: Int,
        val height: Int,
        val cellSizePx: Int,
        val fillBars: Int
    )

    private data class OverlayLayout(
        val glyphs: List<GlyphLayoutEntry>,
        val battery: BatteryLayoutEntry
    )
}
