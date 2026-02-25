package com.flopster101.siliconplayer.ui.screens

import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.StrokeJoin
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.graphics.vector.path
import androidx.compose.ui.unit.dp

internal object NetworkIcons {
    val FtpShareFolder: ImageVector by lazy {
        ImageVector.Builder(
            name = "ftp_share_folder",
            defaultWidth = 24.dp,
            defaultHeight = 24.dp,
            viewportWidth = 960f,
            viewportHeight = 960f
        ).apply {
            path(
                fill = SolidColor(Color.Black)
            ) {
                moveTo(160f, 800f)
                quadToRelative(-33f, 0f, -56.5f, -23.5f)
                reflectiveQuadTo(80f, 720f)
                verticalLineToRelative(-480f)
                quadToRelative(0f, -33f, 23.5f, -56.5f)
                reflectiveQuadTo(160f, 160f)
                horizontalLineToRelative(240f)
                lineToRelative(80f, 80f)
                horizontalLineToRelative(320f)
                quadToRelative(33f, 0f, 56.5f, 23.5f)
                reflectiveQuadTo(880f, 320f)
                horizontalLineTo(600f)
                quadToRelative(-66f, 0f, -113f, 47f)
                reflectiveQuadToRelative(-47f, 113f)
                quadToRelative(0f, 31f, 10.5f, 58f)
                reflectiveQuadToRelative(29.5f, 48f)
                verticalLineToRelative(148f)
                quadToRelative(-13f, 14f, -21.5f, 30.5f)
                reflectiveQuadTo(445f, 800f)
                horizontalLineTo(160f)
                close()
                moveToRelative(440f, 120f)
                quadToRelative(-33f, 0f, -56.5f, -23.5f)
                reflectiveQuadTo(520f, 840f)
                quadToRelative(0f, -23f, 11f, -41f)
                reflectiveQuadToRelative(29f, -29f)
                verticalLineToRelative(-221f)
                quadToRelative(-18f, -11f, -29f, -28.5f)
                reflectiveQuadTo(520f, 480f)
                quadToRelative(0f, -33f, 23.5f, -56.5f)
                reflectiveQuadTo(600f, 400f)
                quadToRelative(33f, 0f, 56.5f, 23.5f)
                reflectiveQuadTo(680f, 480f)
                quadToRelative(0f, 23f, -11f, 40.5f)
                reflectiveQuadTo(640f, 549f)
                verticalLineToRelative(115f)
                lineToRelative(160f, -53f)
                verticalLineToRelative(-62f)
                quadToRelative(-18f, -11f, -29f, -28.5f)
                reflectiveQuadTo(760f, 480f)
                quadToRelative(0f, -33f, 23.5f, -56.5f)
                reflectiveQuadTo(840f, 400f)
                quadToRelative(33f, 0f, 56.5f, 23.5f)
                reflectiveQuadTo(920f, 480f)
                quadToRelative(0f, 23f, -11f, 40.5f)
                reflectiveQuadTo(880f, 549f)
                verticalLineToRelative(119f)
                lineToRelative(-240f, 80f)
                verticalLineToRelative(22f)
                quadToRelative(18f, 11f, 29f, 29f)
                reflectiveQuadToRelative(11f, 41f)
                quadToRelative(0f, 33f, -23.5f, 56.5f)
                reflectiveQuadTo(600f, 920f)
                close()
            }
        }.build()
    }

    val SmbShare: ImageVector by lazy {
        ImageVector.Builder(
            name = "smb_share",
            defaultWidth = 24.dp,
            defaultHeight = 24.dp,
            viewportWidth = 960f,
            viewportHeight = 960f
        ).apply {
            path(
                fill = SolidColor(Color.Black)
            ) {
                moveTo(485f, 520f)
                horizontalLineToRelative(163f)
                quadToRelative(26f, 0f, 44f, -18f)
                reflectiveQuadToRelative(18f, -44f)
                quadToRelative(0f, -26f, -18f, -44.5f)
                reflectiveQuadTo(648f, 395f)
                horizontalLineToRelative(-2f)
                quadToRelative(-5f, -32f, -29f, -53.5f)
                reflectiveQuadTo(560f, 320f)
                quadToRelative(-26f, 0f, -47f, 13.5f)
                reflectiveQuadTo(481f, 370f)
                quadToRelative(-30f, 2f, -50.5f, 23.5f)
                reflectiveQuadTo(410f, 445f)
                quadToRelative(0f, 30f, 21.5f, 52.5f)
                reflectiveQuadTo(485f, 520f)
                close()
                moveTo(120f, 840f)
                quadToRelative(-33f, 0f, -56.5f, -23.5f)
                reflectiveQuadTo(40f, 760f)
                verticalLineToRelative(-520f)
                horizontalLineToRelative(80f)
                verticalLineToRelative(520f)
                horizontalLineToRelative(680f)
                verticalLineToRelative(80f)
                horizontalLineTo(120f)
                close()
                moveToRelative(160f, -160f)
                quadToRelative(-33f, 0f, -56.5f, -23.5f)
                reflectiveQuadTo(200f, 600f)
                verticalLineToRelative(-440f)
                quadToRelative(0f, -33f, 23.5f, -56.5f)
                reflectiveQuadTo(280f, 80f)
                horizontalLineToRelative(200f)
                lineToRelative(80f, 80f)
                horizontalLineToRelative(280f)
                quadToRelative(33f, 0f, 56.5f, 23.5f)
                reflectiveQuadTo(920f, 240f)
                verticalLineToRelative(360f)
                quadToRelative(0f, 33f, -23.5f, 56.5f)
                reflectiveQuadTo(840f, 680f)
                horizontalLineTo(280f)
                close()
            }
        }.build()
    }

    val WorldCode: ImageVector by lazy {
        ImageVector.Builder(
            name = "world_code",
            defaultWidth = 24.dp,
            defaultHeight = 24.dp,
            viewportWidth = 24f,
            viewportHeight = 24f
        ).apply {
            path(
                fill = SolidColor(Color.Transparent),
                stroke = SolidColor(Color.Black),
                strokeLineWidth = 2f,
                strokeLineCap = StrokeCap.Round,
                strokeLineJoin = StrokeJoin.Round
            ) {
                moveTo(20.942f, 13.02f)
                arcToRelative(9f, 9f, 0f, true, false, -9.47f, 7.964f)
            }
            path(
                fill = SolidColor(Color.Transparent),
                stroke = SolidColor(Color.Black),
                strokeLineWidth = 2f,
                strokeLineCap = StrokeCap.Round,
                strokeLineJoin = StrokeJoin.Round
            ) {
                moveTo(3.6f, 9f)
                horizontalLineToRelative(16.8f)
            }
            path(
                fill = SolidColor(Color.Transparent),
                stroke = SolidColor(Color.Black),
                strokeLineWidth = 2f,
                strokeLineCap = StrokeCap.Round,
                strokeLineJoin = StrokeJoin.Round
            ) {
                moveTo(3.6f, 15f)
                horizontalLineToRelative(9.9f)
            }
            path(
                fill = SolidColor(Color.Transparent),
                stroke = SolidColor(Color.Black),
                strokeLineWidth = 2f,
                strokeLineCap = StrokeCap.Round,
                strokeLineJoin = StrokeJoin.Round
            ) {
                moveTo(11.5f, 3f)
                arcToRelative(17f, 17f, 0f, false, false, 0f, 18f)
            }
            path(
                fill = SolidColor(Color.Transparent),
                stroke = SolidColor(Color.Black),
                strokeLineWidth = 2f,
                strokeLineCap = StrokeCap.Round,
                strokeLineJoin = StrokeJoin.Round
            ) {
                moveTo(12.5f, 3f)
                curveToRelative(2f, 3.206f, 2.837f, 6.913f, 2.508f, 10.537f)
            }
            path(
                fill = SolidColor(Color.Transparent),
                stroke = SolidColor(Color.Black),
                strokeLineWidth = 2f,
                strokeLineCap = StrokeCap.Round,
                strokeLineJoin = StrokeJoin.Round
            ) {
                moveTo(20f, 21f)
                lineToRelative(2f, -2f)
                lineToRelative(-2f, -2f)
            }
            path(
                fill = SolidColor(Color.Transparent),
                stroke = SolidColor(Color.Black),
                strokeLineWidth = 2f,
                strokeLineCap = StrokeCap.Round,
                strokeLineJoin = StrokeJoin.Round
            ) {
                moveTo(17f, 17f)
                lineToRelative(-2f, 2f)
                lineToRelative(2f, 2f)
            }
        }.build()
    }
}
