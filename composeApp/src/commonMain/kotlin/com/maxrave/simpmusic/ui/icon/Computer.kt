package com.maxrave.simpmusic.ui.icon

import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.PathFillType
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.StrokeJoin
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.graphics.vector.path
import androidx.compose.ui.unit.dp

@Suppress("CheckReturnValue")
val SimpIcons.Computer: ImageVector
    get() {
        if (_Computer != null) {
            return _Computer!!
        }
        _Computer =
            ImageVector.Builder(
                name = "Computer",
                defaultWidth = 24.dp,
                defaultHeight = 24.dp,
                viewportWidth = 24f,
                viewportHeight = 24f,
            ).apply {
                path(
                    fill = SolidColor(Color.Black),
                    fillAlpha = 1f,
                    stroke = null,
                    strokeAlpha = 1f,
                    strokeLineWidth = 1f,
                    strokeLineCap = StrokeCap.Butt,
                    strokeLineJoin = StrokeJoin.Bevel,
                    strokeLineMiter = 1f,
                    pathFillType = PathFillType.Companion.NonZero,
                ) {
                    moveTo(20f, 18f)
                    curveToRelative(1.1f, 0f, 1.99f, -0.9f, 1.99f, -2f)
                    lineTo(22f, 6f)
                    curveToRelative(0f, -1.1f, -0.9f, -2f, -2f, -2f)
                    horizontalLineTo(4f)
                    curveToRelative(-1.1f, 0f, -2f, 0.9f, -2f, 2f)
                    verticalLineToRelative(10f)
                    curveToRelative(0f, 1.1f, 0.9f, 2f, 2f, 2f)
                    horizontalLineTo(0f)
                    verticalLineToRelative(2f)
                    horizontalLineToRelative(24f)
                    verticalLineToRelative(-2f)
                    horizontalLineToRelative(-4f)
                    close()
                    moveTo(4f, 6f)
                    horizontalLineToRelative(16f)
                    verticalLineToRelative(10f)
                    horizontalLineTo(4f)
                    verticalLineTo(6f)
                    close()
                }
            }.build()
        return _Computer!!
    }

private var _Computer: ImageVector? = null
