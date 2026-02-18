package com.flopster101.siliconplayer.ui.screens

import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.PathFillType
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.StrokeJoin
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.graphics.vector.group
import androidx.compose.ui.graphics.vector.path
import androidx.compose.ui.unit.dp

internal object PlayerChipIcons {
    val HardDrive: ImageVector by lazy {
        ImageVector.Builder(
            name = "HardDrive",
            defaultWidth = 24.dp,
            defaultHeight = 24.dp,
            viewportWidth = 960f,
            viewportHeight = 960f
        ).apply {
            group(translationY = 960f) {
                path(
                    fill = SolidColor(Color.Black),
                    stroke = null,
                    strokeLineWidth = 0.0f,
                    strokeLineCap = StrokeCap.Butt,
                    strokeLineJoin = StrokeJoin.Miter,
                    strokeLineMiter = 4.0f,
                    pathFillType = PathFillType.NonZero
                ) {
                    moveTo(160f, -280f)
                    horizontalLineToRelative(640f)
                    verticalLineToRelative(-240f)
                    horizontalLineTo(160f)
                    verticalLineToRelative(240f)
                    close()
                    moveToRelative(562.5f, -77.5f)
                    quadTo(740f, -375f, 740f, -400f)
                    reflectiveQuadToRelative(-17.5f, -42.5f)
                    quadTo(705f, -460f, 680f, -460f)
                    reflectiveQuadToRelative(-42.5f, 17.5f)
                    quadTo(620f, -425f, 620f, -400f)
                    reflectiveQuadToRelative(17.5f, 42.5f)
                    quadTo(655f, -340f, 680f, -340f)
                    reflectiveQuadToRelative(42.5f, -17.5f)
                    close()
                    moveTo(880f, -600f)
                    horizontalLineTo(767f)
                    lineToRelative(-80f, -80f)
                    horizontalLineTo(273f)
                    lineToRelative(-80f, 80f)
                    horizontalLineTo(80f)
                    lineToRelative(137f, -137f)
                    quadToRelative(11f, -11f, 25.5f, -17f)
                    reflectiveQuadToRelative(30.5f, -6f)
                    horizontalLineToRelative(414f)
                    quadToRelative(16f, 0f, 30.5f, 6f)
                    reflectiveQuadToRelative(25.5f, 17f)
                    lineToRelative(137f, 137f)
                    close()
                    moveTo(160f, -200f)
                    quadToRelative(-33f, 0f, -56.5f, -23.5f)
                    reflectiveQuadTo(80f, -280f)
                    verticalLineToRelative(-320f)
                    horizontalLineToRelative(800f)
                    verticalLineToRelative(320f)
                    quadToRelative(0f, 33f, -23.5f, 56.5f)
                    reflectiveQuadTo(800f, -200f)
                    horizontalLineTo(160f)
                    close()
                }
            }
        }.build()
    }

    val ReadinessScore: ImageVector by lazy {
        ImageVector.Builder(
            name = "ReadinessScore",
            defaultWidth = 24.dp,
            defaultHeight = 24.dp,
            viewportWidth = 960f,
            viewportHeight = 960f
        ).apply {
            group(translationY = 960f) {
                path(
                    fill = SolidColor(Color.Black),
                    stroke = null,
                    strokeLineWidth = 0.0f,
                    strokeLineCap = StrokeCap.Butt,
                    strokeLineJoin = StrokeJoin.Miter,
                    strokeLineMiter = 4.0f,
                    pathFillType = PathFillType.NonZero
                ) {
                    moveTo(295f, -119f)
                    quadToRelative(-36f, -1f, -68.5f, -18.5f)
                    reflectiveQuadTo(165f, -189f)
                    quadToRelative(-40f, -48f, -62.5f, -114.5f)
                    reflectiveQuadTo(80f, -440f)
                    quadToRelative(0f, -83f, 31.5f, -156f)
                    reflectiveQuadTo(197f, -723f)
                    quadToRelative(54f, -54f, 127f, -85.5f)
                    reflectiveQuadTo(480f, -840f)
                    quadToRelative(83f, 0f, 156f, 32f)
                    reflectiveQuadToRelative(127f, 87f)
                    quadToRelative(54f, 55f, 85.5f, 129f)
                    reflectiveQuadTo(880f, -433f)
                    quadToRelative(0f, 77f, -25f, 144f)
                    reflectiveQuadToRelative(-71f, 113f)
                    quadToRelative(-28f, 28f, -59f, 42.5f)
                    reflectiveQuadTo(662f, -119f)
                    quadToRelative(-18f, 0f, -36f, -4.5f)
                    reflectiveQuadTo(590f, -137f)
                    lineToRelative(-56f, -28f)
                    quadToRelative(-12f, -6f, -25.5f, -9f)
                    reflectiveQuadToRelative(-28.5f, -3f)
                    quadToRelative(-15f, 0f, -28.5f, 3f)
                    reflectiveQuadToRelative(-25.5f, 9f)
                    lineToRelative(-56f, 28f)
                    quadToRelative(-19f, 10f, -37.5f, 14.5f)
                    reflectiveQuadTo(295f, -119f)
                    close()
                    moveToRelative(2f, -80f)
                    quadToRelative(9f, 0f, 18.5f, -2f)
                    reflectiveQuadToRelative(18.5f, -7f)
                    lineToRelative(56f, -28f)
                    quadToRelative(21f, -11f, 43.5f, -16f)
                    reflectiveQuadToRelative(45.5f, -5f)
                    quadToRelative(23f, 0f, 46f, 5f)
                    reflectiveQuadToRelative(44f, 16f)
                    lineToRelative(57f, 28f)
                    quadToRelative(9f, 5f, 18f, 7f)
                    reflectiveQuadToRelative(18f, 2f)
                    quadToRelative(19f, 0f, 36f, -10f)
                    reflectiveQuadToRelative(34f, -30f)
                    quadToRelative(32f, -38f, 50f, -91f)
                    reflectiveQuadToRelative(18f, -109f)
                    quadToRelative(0f, -134f, -93f, -227.5f)
                    reflectiveQuadTo(480f, -760f)
                    quadToRelative(-134f, 0f, -227f, 94f)
                    reflectiveQuadToRelative(-93f, 228f)
                    quadToRelative(0f, 57f, 18.5f, 111f)
                    reflectiveQuadToRelative(51.5f, 91f)
                    quadToRelative(17f, 20f, 33f, 28.5f)
                    reflectiveQuadToRelative(34f, 8.5f)
                    close()
                    moveToRelative(183f, -281f)
                    close()
                    moveToRelative(56.5f, 96.5f)
                    quadTo(560f, -407f, 560f, -440f)
                    quadToRelative(0f, -8f, -1.5f, -16f)
                    reflectiveQuadToRelative(-4.5f, -16f)
                    lineToRelative(50f, -67f)
                    quadToRelative(10f, 13f, 17.5f, 27.5f)
                    reflectiveQuadTo(634f, -480f)
                    horizontalLineToRelative(82f)
                    quadToRelative(-15f, -88f, -81.5f, -144f)
                    reflectiveQuadTo(480f, -680f)
                    quadToRelative(-88f, 0f, -155f, 56.5f)
                    reflectiveQuadTo(244f, -480f)
                    horizontalLineToRelative(82f)
                    quadToRelative(14f, -54f, 57f, -87f)
                    reflectiveQuadToRelative(97f, -33f)
                    quadToRelative(17f, 0f, 32f, 3f)
                    reflectiveQuadToRelative(29f, 9f)
                    lineToRelative(-51f, 69f)
                    quadToRelative(-2f, 0f, -5f, -0.5f)
                    reflectiveQuadToRelative(-5f, -0.5f)
                    quadToRelative(-33f, 0f, -56.5f, 23.5f)
                    reflectiveQuadTo(400f, -440f)
                    quadToRelative(0f, 33f, 23.5f, 56.5f)
                    reflectiveQuadTo(480f, -360f)
                    quadToRelative(33f, 0f, 56.5f, -23.5f)
                    close()
                }
            }
        }.build()
    }
}
