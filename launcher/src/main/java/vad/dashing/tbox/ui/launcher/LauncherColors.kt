package vad.dashing.tbox.ui.launcher

import androidx.compose.material3.SwitchColors
import androidx.compose.material3.SwitchDefaults
import androidx.compose.runtime.Composable
import androidx.compose.ui.graphics.Color

/** Tesla reference palette. Left-panel colors follow [LauncherThemeState.darkTheme]. */
object LauncherColors {
    private val DarkAccentCyan = Color(0xFF0BE4FF)
    private val LightAccentBlue = Color(0xFF2563EB)

    val AccentCyan: Color get() = if (LauncherThemeState.darkTheme) DarkAccentCyan else LightAccentBlue
    val AccentBlue = Color(0xFF3B82F6)

    // Left panel — light theme (default) / dark theme (optional, Tesla night style)
    private val LightPanelBg = Color(0xFFF4F5F7)
    private val LightPanelCard = Color(0xD9FFFFFF)
    private val LightTextPrimary = Color(0xFF4B5563)
    private val LightTextSecondary = Color(0xFF4B5563)
    private val LightGearActive = Color(0xFF4B5563)
    private val LightGearInactive = Color(0xFFD1D5DB)

    private val DarkPanelBg = Color(0xFF161D28)
    private val DarkPanelCard = Color(0xCC232D3B)
    private val DarkTextPrimary = Color(0xFFF3F4F6)
    private val DarkTextSecondary = Color(0xFF9AA4B2)
    private val DarkGearActive = Color(0xFFF3F4F6)
    private val DarkGearInactive = Color(0xFF5B6675)

    val LeftPanelBg: Color get() = if (LauncherThemeState.darkTheme) DarkPanelBg else LightPanelBg
    val LeftPanelCard: Color get() = if (LauncherThemeState.darkTheme) DarkPanelCard else LightPanelCard
    val LeftTextPrimary: Color get() = if (LauncherThemeState.darkTheme) DarkTextPrimary else LightTextPrimary
    val LeftTextSecondary: Color get() = if (LauncherThemeState.darkTheme) DarkTextSecondary else LightTextSecondary
    val GearActive: Color get() = if (LauncherThemeState.darkTheme) DarkGearActive else LightGearActive
    val GearInactive: Color get() = if (LauncherThemeState.darkTheme) DarkGearInactive else LightGearInactive

    val WarningAmber = Color(0xFFD97706)
    val WarningRed = Color(0xFFDC2626)

    // Right panel & popups
    private val LightSurfaceDark = Color(0xFFFFFFFF)
    private val LightCardDark = Color(0xFFF4F5F7) // Matches ADAS zone background
    private val LightCardDarkElevated = Color(0xFFE5E7EB) // Matches top/bottom bar
    private val LightDrawerSurface = Color(0xF2F4F5F7)

    val CanvasDark = Color(0xFF0F1115)
    val SurfaceDark: Color get() = if (LauncherThemeState.darkTheme) Color(0xFF1A1D24) else LightSurfaceDark
    val CardDark: Color get() = if (LauncherThemeState.darkTheme) Color(0xFF232733) else LightCardDark
    val CardDarkElevated: Color get() = if (LauncherThemeState.darkTheme) Color(0xFF2A2F3D) else LightCardDarkElevated

    private val LightTextPrimaryColor = Color(0xFF1F2937)
    private val LightTextSecondaryColor = Color(0xFF4B5563)
    private val DarkTextPrimaryColor = Color(0xFFF3F4F6)
    private val DarkTextSecondaryColor = Color(0xFF9CA3AF)

    val TextPrimary: Color get() = if (LauncherThemeState.darkTheme) DarkTextPrimaryColor else LightTextPrimaryColor
    val TextSecondary: Color get() = if (LauncherThemeState.darkTheme) DarkTextSecondaryColor else LightTextSecondaryColor
    val TextMuted: Color get() = if (LauncherThemeState.darkTheme) Color(0xFF6B7280) else Color(0xFF4B5563)

    val SettingsBackground: Color get() = if (LauncherThemeState.darkTheme) Color(0xFF11151C) else LightCardDark
    val SettingsModelCenter = Color(0xFF303846)
    val SettingsModelEdge = Color(0xFF191E27)
    val SettingsBorder: Color get() = if (LauncherThemeState.darkTheme) Color(0xFF3A4352) else Color(0xFFCBD5E1)

    // Top & Bottom bars
    private val LightTopBottomBarBg = Color(0xFFE5E7EB)
    private val DarkTopBottomBarBg = Color(0xFF0A0C10)

    val TopBarBg: Color get() = if (LauncherThemeState.darkTheme) DarkTopBottomBarBg else LightTopBottomBarBg
    val BottomBarBg: Color get() = if (LauncherThemeState.darkTheme) DarkTopBottomBarBg else LightTopBottomBarBg

    val DrawerScrim = Color(0xCC070A14)
    val DrawerSurface: Color get() = if (LauncherThemeState.darkTheme) Color(0xF01D1D1D) else LightDrawerSurface
}

@Composable
fun launcherSwitchColors(): SwitchColors = SwitchDefaults.colors(
    checkedThumbColor = if (LauncherThemeState.darkTheme) LauncherColors.CanvasDark else Color.White,
    checkedTrackColor = LauncherColors.AccentCyan,
    checkedBorderColor = LauncherColors.AccentCyan,
    checkedIconColor = if (LauncherThemeState.darkTheme) LauncherColors.CanvasDark else Color.White,
    uncheckedThumbColor = LauncherColors.TextMuted,
    uncheckedTrackColor = LauncherColors.SurfaceDark,
    uncheckedBorderColor = LauncherColors.TextMuted,
)
