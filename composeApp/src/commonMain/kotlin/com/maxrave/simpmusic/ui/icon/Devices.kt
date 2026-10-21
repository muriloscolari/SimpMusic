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
val SimpIcons.Devices: ImageVector
    get() {
        if (_Devices != null) {
            return _Devices!!
        }
        _Devices =
            ImageVector.Builder(
                name = "Devices",
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
                    moveTo(4f, 6f)
                    horizontalLineToRelative(18f)
                    verticalLineTo(4f)
                    horizontalLineTo(4f)
                    curveToRelative(-1.1f, 0f, -2f, 0.9f, -2f, 2f)
                    verticalLineToRelative(11f)
                    horizontalLineTo(0f)
                    verticalLineToRelative(3f)
                    horizontalLineToRelative(14f)
                    verticalLineToRelative(-3f)
                    horizontalLineTo(4f)
                    verticalLineTo(6f)
                    close()
                    moveTo(23f, 8f)
                    horizontalLineToRelative(-6f)
                    curveToRelative(-0.55f, 0f, -1f, 0.45f, -1f, 1f)
                    verticalLineToRelative(10f)
                    curveToRelative(0f, 0.55f, 0.45f, 1f, 1f, 1f)
                    horizontalLineToRelative(6f)
                    curveToRelative(0.55f, 0f, 1f, -0.45f, 1f, -1f)
                    verticalLineTo(9f)
                    curveToRelative(0f, -0.55f, -0.45f, -1f, -1f, -1f)
                    close()
                    moveTo(22f, 17f)
                    horizontalLineToRelative(-4f)
                    verticalLineToRelative(-7f)
                    horizontalLineToRelative(4f)
                    verticalLineToRelative(7f)
                    close()
                }
            }.build()
        return _Devices!!
    }

private var _Devices: ImageVector? = null
