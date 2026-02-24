package com.flopster101.siliconplayer.ui.screens

import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.graphics.vector.path
import androidx.compose.ui.unit.dp

internal object NetworkIcons {
    val FolderData: ImageVector by lazy {
        ImageVector.Builder(
            name = "folder_data",
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
}
