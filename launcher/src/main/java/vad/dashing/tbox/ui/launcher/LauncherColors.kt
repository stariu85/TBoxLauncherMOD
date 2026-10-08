package vad.dashing.tbox.ui.launcher

import androidx.compose.material3.SwitchColors
import androidx.compose.material3.SwitchDefaults
import androidx.compose.runtime.Composable
import androidx.compose.ui.graphics.Color

/** Tesla reference palette. Left-panel colors follow [LauncherThemeState.darkTheme]. */
object LauncherColors {
    val AccentCyan = Color(0xFF0BE4FF)
    val AccentBlue = Color(0xFF3B82F6)

    // Left panel — light theme (default) / dark theme (optional, Tesla night style)
    private val LightPanelBg = Color(0xFFF4F5F7)
    private val LightPanelCard = Color(0xFFFFFFFF)
    private val LightTextPrimary = Color(0xFF111827)
    private val LightTextSecondary = Color(0xFF6B7280)
    private val LightGearActive = Color(0xFF111827)
    private val LightGearInactive = Color(0xFFD1D5DB)

    private val DarkPanelBg = Color(0xFF161D28)
    private val DarkPanelCard = Color(0xFF232D3B)
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

    // Right panel (dark)
    val CanvasDark = Color(0xFF0F1115)
    val SurfaceDark = Color(0xFF1A1D24)
    val CardDark = Color(0xFF232733)
    val CardDarkElevated = Color(0xFF2A2F3D)
    val TextPrimary = Color(0xFFF3F4F6)
    val TextSecondary = Color(0xFF9CA3AF)
    val TextMuted = Color(0xFF6B7280)
    val SettingsBackground = Color(0xFF11151C)
    val SettingsModelCenter = Color(0xFF303846)
    val SettingsModelEdge = Color(0xFF191E27)
    val SettingsBorder = Color(0xFF3A4352)

    // Bottom bar
    val BottomBarBg = Color(0xFF0A0C10)

    val DrawerScrim = Color(0xCC070A14)
    val DrawerSurface = Color(0xF01D1D1D)
}

@Composable
fun launcherSwitchColors(): SwitchColors = SwitchDefaults.colors(
    checkedThumbColor = LauncherColors.CanvasDark,
    checkedTrackColor = LauncherColors.AccentCyan,
    checkedBorderColor = LauncherColors.AccentCyan,
    checkedIconColor = LauncherColors.CanvasDark,
    uncheckedThumbColor = LauncherColors.TextMuted,
    uncheckedTrackColor = LauncherColors.SurfaceDark,
    uncheckedBorderColor = LauncherColors.TextMuted,
)
