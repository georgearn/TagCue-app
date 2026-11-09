package com.example.ui.theme

import androidx.compose.material3.Typography
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.sp

val AppFontFamily = FontFamily.SansSerif
val NumberFontFamily = FontFamily.Monospace

val Typography = Typography(
    titleLarge = TextStyle(
        fontFamily = AppFontFamily,
        fontWeight = FontWeight.SemiBold,
        fontSize = 20.sp,
        lineHeight = 28.sp
    ),
    titleMedium = TextStyle(
        fontFamily = AppFontFamily,
        fontWeight = FontWeight.SemiBold,
        fontSize = 16.sp,
        lineHeight = 24.sp
    ),
    bodyLarge = TextStyle(
        fontFamily = AppFontFamily,
        fontWeight = FontWeight.Normal,
        fontSize = 16.sp,
        lineHeight = 24.sp
    ),
    bodyMedium = TextStyle(
        fontFamily = AppFontFamily,
        fontWeight = FontWeight.Normal,
        fontSize = 14.sp,
        lineHeight = 20.sp
    ),
    bodySmall = TextStyle(
        fontFamily = AppFontFamily,
        fontWeight = FontWeight.Normal,
        fontSize = 12.sp,
        lineHeight = 16.sp
    ),
    titleSmall = TextStyle(
        fontFamily = AppFontFamily,
        fontWeight = FontWeight.SemiBold,
        fontSize = 14.sp,
        lineHeight = 20.sp
    ),
    labelMedium = TextStyle(
        fontFamily = AppFontFamily,
        fontWeight = FontWeight.SemiBold,
        fontSize = 12.sp,
        lineHeight = 16.sp
    ),
    labelLarge = TextStyle(
        fontFamily = AppFontFamily,
        fontWeight = FontWeight.SemiBold,
        fontSize = 14.sp,
        lineHeight = 20.sp
    ),
    labelSmall = TextStyle(
        fontFamily = AppFontFamily,
        fontWeight = FontWeight.SemiBold,
        fontSize = 12.sp,
        lineHeight = 16.sp
    )
)

/** Tighter scale for short screens (e.g. 9:16 DAPs, about 640dp tall). */
fun Typography.compact(): Typography = copy(
    titleLarge = titleLarge.copy(fontSize = 18.sp, lineHeight = 24.sp),
    titleMedium = titleMedium.copy(fontSize = 15.sp, lineHeight = 20.sp),
    titleSmall = titleSmall.copy(fontSize = 13.sp, lineHeight = 18.sp),
    bodyLarge = bodyLarge.copy(fontSize = 14.sp, lineHeight = 20.sp),
    bodyMedium = bodyMedium.copy(fontSize = 13.sp, lineHeight = 18.sp),
    bodySmall = bodySmall.copy(fontSize = 11.sp, lineHeight = 15.sp),
    labelLarge = labelLarge.copy(fontSize = 13.sp, lineHeight = 18.sp),
    labelMedium = labelMedium.copy(fontSize = 11.sp, lineHeight = 14.sp),
    labelSmall = labelSmall.copy(fontSize = 11.sp, lineHeight = 14.sp)
)

object StatTypography {
    val metricLarge = TextStyle(
        fontFamily = NumberFontFamily,
        fontWeight = FontWeight.SemiBold,
        fontSize = 20.sp,
        lineHeight = 24.sp
    )
    val metricMedium = TextStyle(
        fontFamily = NumberFontFamily,
        fontWeight = FontWeight.SemiBold,
        fontSize = 16.sp,
        lineHeight = 20.sp
    )
    val metricBody = TextStyle(
        fontFamily = NumberFontFamily,
        fontWeight = FontWeight.Normal,
        fontSize = 14.sp,
        lineHeight = 18.sp
    )
    val metricBadge = TextStyle(
        fontFamily = NumberFontFamily,
        fontWeight = FontWeight.Normal,
        fontSize = 12.sp,
        lineHeight = 14.sp
    )
}
