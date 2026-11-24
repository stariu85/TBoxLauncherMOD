package vad.dashing.tbox.ui.launcher

import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.togetherWith
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.Info
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.ColorFilter
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import vad.dashing.tbox.BuildConfig
import vad.dashing.tbox.CanDataViewModel
import vad.dashing.tbox.R
import vad.dashing.tbox.TboxViewModel
import vad.dashing.tbox.ui.rememberWrappedOnClick

/**
 * Data structure representing a tab subsection in the new settings UI.
 */
data class NewSettingsSubcategory(
    val id: String,
    val title: String,
    val description: String,
)

/**
 * Data structure representing a main category (left sidebar menu item).
 */
data class NewSettingsCategory(
    val id: String,
    val title: String,
    val iconRes: Int,
    val subcategories: List<NewSettingsSubcategory>,
)

private val NEW_SETTINGS_CATEGORIES = listOf(
    NewSettingsCategory(
        id = "launcher",
        title = "Лаунчер",
        iconRes = R.drawable.ic_tab_main_screen_settings,
        subcategories = listOf(
            NewSettingsSubcategory("home", "Главный экран", "Настройка элементов рабочего стола и скрытия приложений"),
            NewSettingsSubcategory("widgets", "Виджеты и панели", "Плавающие панели, виджеты и быстрый доступ"),
            NewSettingsSubcategory("dock", "Док-бар", "Нижняя панель, закреплённые приложения и сетка"),
            NewSettingsSubcategory("apps", "Приложения", "Список приложений, скрытые пакеты и автозапуск"),
        ),
    ),
    NewSettingsCategory(
        id = "vehicle",
        title = "Автомобиль",
        iconRes = R.drawable.ic_tab_car_settings,
        subcategories = listOf(
            NewSettingsSubcategory("body", "Кузов и двери", "Двери, багажник, зеркала и окна"),
            NewSettingsSubcategory("lights", "Свет", "Фары, дневные ходовые огни и подсветка"),
            NewSettingsSubcategory("wheels", "Колёса и шины", "Давление в шинах, индикация и предупреждения"),
            NewSettingsSubcategory("comfort", "Климат и комфорт", "Подогревы, вентиляция и память сидений"),
            NewSettingsSubcategory("adas", "Ассистенты (ADAS)", "Круиз-контроль, удержание в полосе и предупреждения"),
        ),
    ),
    NewSettingsCategory(
        id = "media",
        title = "Медиа и звук",
        iconRes = R.drawable.ic_media_volume_audio,
        subcategories = listOf(
            NewSettingsSubcategory("player", "Плеер", "Медиа-карточка, цвет обложки и прозрачность"),
            NewSettingsSubcategory("nav", "Навигация", "Яндекс.Навигатор, манёвры и спидометр"),
            NewSettingsSubcategory("cameras", "Камеры", "Круговой обзор 360, боковые камеры и запись"),
        ),
    ),
    NewSettingsCategory(
        id = "appearance",
        title = "Интерфейс",
        iconRes = R.drawable.ic_menu_themes,
        subcategories = listOf(
            NewSettingsSubcategory("theme", "Тема оформления", "Светлая, тёмная тема и автоматическое переключение"),
            NewSettingsSubcategory("wallpaper", "Обои", "Фоновые изображения рабочего стола"),
            NewSettingsSubcategory("scale", "Масштабирование", "Размеры элементов, шрифты и отступы"),
        ),
    ),
    NewSettingsCategory(
        id = "system",
        title = "Система",
        iconRes = R.drawable.ic_settings_sliders,
        subcategories = listOf(
            NewSettingsSubcategory("about", "О лаунчере", "Информация о версии, логирование и отладка"),
            NewSettingsSubcategory("tbmapi", "DCL / TBM API", "Интеграция TBM, порты и статус подключения"),
            NewSettingsSubcategory("updates", "Обновления", "Проверка обновлений лаунчера через OTA"),
            NewSettingsSubcategory("experimental", "Экспериментальные", "Оконные режимы, Dclwm и дополнительные опции"),
        ),
    ),
)

/**
 * New settings screen layout inside the desktop icon zone (right panel),
 * preserving the left ADAS panel, top header bar, and bottom dock bar.
 * Fonts are reduced by 30% from the previous 2x state for optimal balance.
 */
@Composable
fun LauncherNewSettingsScreen(
    @Suppress("UNUSED_PARAMETER") canViewModel: CanDataViewModel,
    @Suppress("UNUSED_PARAMETER") tboxViewModel: TboxViewModel,
    onClose: () -> Unit,
    modifier: Modifier = Modifier,
) {
    var selectedCategoryIndex by rememberSaveable { mutableIntStateOf(0) }
    var selectedTabIndex by rememberSaveable(selectedCategoryIndex) { mutableIntStateOf(0) }

    val currentCategory = NEW_SETTINGS_CATEGORIES.getOrElse(selectedCategoryIndex) { NEW_SETTINGS_CATEGORIES.first() }
    val currentSubcategories = currentCategory.subcategories
    val validTabIndex = selectedTabIndex.coerceIn(0, (currentSubcategories.size - 1).coerceAtLeast(0))

    Column(
        modifier = modifier
            .fillMaxSize()
            .background(LauncherColors.SettingsBackground)
            .padding(start = 12.dp, top = 8.dp, end = 12.dp, bottom = 8.dp),
    ) {
        // --- Header ---
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 4.dp, vertical = 2.dp),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(10.dp),
            ) {
                Text(
                    text = stringResource(R.string.action_settings_open),
                    color = LauncherColors.TextPrimary,
                    fontSize = 31.sp,
                    fontWeight = FontWeight.Bold,
                )
                Box(
                    modifier = Modifier
                        .clip(RoundedCornerShape(6.dp))
                        .background(LauncherColors.AccentCyan.copy(alpha = 0.2f))
                        .padding(horizontal = 8.dp, vertical = 3.dp),
                ) {
                    Text(
                        text = "NEW UI",
                        color = LauncherColors.AccentCyan,
                        fontSize = 15.sp,
                        fontWeight = FontWeight.Bold,
                    )
                }
                Text(
                    text = "v${BuildConfig.VERSION_NAME.substringBefore('-')}",
                    color = LauncherColors.TextMuted,
                    fontSize = 18.sp,
                )
            }

            IconButton(
                onClick = rememberWrappedOnClick(onClose),
                modifier = Modifier.size(44.dp),
            ) {
                Icon(
                    Icons.Filled.Close,
                    contentDescription = stringResource(R.string.action_close),
                    tint = LauncherColors.TextSecondary,
                    modifier = Modifier.size(28.dp),
                )
            }
        }

        // --- Main Workspace: Left Menu + Right Content ---
        Row(
            modifier = Modifier
                .fillMaxSize()
                .padding(top = 6.dp),
            horizontalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            // === LEFT: Category Menu ===
            Column(
                modifier = Modifier
                    .width(280.dp)
                    .fillMaxHeight()
                    .clip(RoundedCornerShape(16.dp))
                    .background(LauncherColors.SurfaceDark)
                    .verticalScroll(rememberScrollState())
                    .padding(8.dp),
                verticalArrangement = Arrangement.spacedBy(6.dp),
            ) {
                NEW_SETTINGS_CATEGORIES.forEachIndexed { index, category ->
                    val isSelected = index == selectedCategoryIndex
                    val wrappedSelect = rememberWrappedOnClick {
                        if (selectedCategoryIndex != index) {
                            selectedCategoryIndex = index
                            selectedTabIndex = 0
                        }
                    }

                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .heightIn(min = 58.dp)
                            .clip(RoundedCornerShape(12.dp))
                            .background(
                                if (isSelected) LauncherColors.AccentCyan.copy(alpha = 0.15f)
                                else Color.Transparent,
                            )
                            .clickable(onClick = wrappedSelect)
                            .padding(horizontal = 12.dp, vertical = 8.dp),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(12.dp),
                    ) {
                        // Left active indicator strip
                        Box(
                            modifier = Modifier
                                .width(4.dp)
                                .height(30.dp)
                                .clip(RoundedCornerShape(2.dp))
                                .background(
                                    if (isSelected) LauncherColors.AccentCyan
                                    else Color.Transparent,
                                ),
                        )

                        Image(
                            painter = painterResource(category.iconRes),
                            contentDescription = null,
                            colorFilter = ColorFilter.tint(
                                if (isSelected) LauncherColors.AccentCyan
                                else LauncherColors.TextSecondary,
                            ),
                            modifier = Modifier.size(30.dp),
                        )

                        Text(
                            text = category.title,
                            color = if (isSelected) LauncherColors.TextPrimary else LauncherColors.TextSecondary,
                            fontSize = 22.sp,
                            fontWeight = if (isSelected) FontWeight.SemiBold else FontWeight.Normal,
                        )
                    }
                }
            }

            // === RIGHT: Section Content with Top Subcategory Tabs ===
            Column(
                modifier = Modifier
                    .weight(1f)
                    .fillMaxHeight(),
            ) {
                // --- Top Subcategory Tabs ---
                if (currentSubcategories.isNotEmpty()) {
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .horizontalScroll(rememberScrollState())
                            .padding(bottom = 8.dp),
                        horizontalArrangement = Arrangement.spacedBy(8.dp),
                        verticalAlignment = Alignment.CenterVertically,
                    ) {
                        currentSubcategories.forEachIndexed { tabIndex, subcategory ->
                            val isTabSelected = tabIndex == validTabIndex
                            val wrappedTabClick = rememberWrappedOnClick {
                                selectedTabIndex = tabIndex
                            }

                            Box(
                                modifier = Modifier
                                    .clip(RoundedCornerShape(12.dp))
                                    .background(
                                        if (isTabSelected) LauncherColors.AccentCyan
                                        else LauncherColors.SurfaceDark,
                                    )
                                    .clickable(onClick = wrappedTabClick)
                                    .padding(horizontal = 16.dp, vertical = 10.dp),
                                contentAlignment = Alignment.Center,
                            ) {
                                Text(
                                    text = subcategory.title,
                                    color = if (isTabSelected) Color.Black else LauncherColors.TextSecondary,
                                    fontSize = 20.sp,
                                    fontWeight = if (isTabSelected) FontWeight.Bold else FontWeight.Medium,
                                )
                            }
                        }
                    }
                }

                // --- Subcategory Content Area ---
                AnimatedContent(
                    targetState = currentCategory.id to validTabIndex,
                    transitionSpec = { fadeIn() togetherWith fadeOut() },
                    label = "SettingsSubcategoryContent",
                    modifier = Modifier
                        .weight(1f)
                        .fillMaxWidth()
                        .clip(RoundedCornerShape(16.dp))
                        .background(LauncherColors.SurfaceDark)
                        .padding(16.dp),
                ) { (_, tabIdx) ->
                    val activeSub = currentSubcategories.getOrNull(tabIdx)
                    Column(
                        modifier = Modifier
                            .fillMaxSize()
                            .verticalScroll(rememberScrollState()),
                        verticalArrangement = Arrangement.spacedBy(14.dp),
                    ) {
                        // Breadcrumbs / title row
                        if (activeSub != null) {
                            Column(modifier = Modifier.fillMaxWidth()) {
                                Row(
                                    verticalAlignment = Alignment.CenterVertically,
                                    horizontalArrangement = Arrangement.spacedBy(6.dp),
                                ) {
                                    Text(
                                        text = currentCategory.title,
                                        color = LauncherColors.TextMuted,
                                        fontSize = 18.sp,
                                    )
                                    Text(
                                        text = "•",
                                        color = LauncherColors.TextMuted,
                                        fontSize = 18.sp,
                                    )
                                    Text(
                                        text = activeSub.title,
                                        color = LauncherColors.AccentCyan,
                                        fontSize = 18.sp,
                                        fontWeight = FontWeight.Medium,
                                    )
                                }
                                Text(
                                    text = activeSub.title,
                                    color = LauncherColors.TextPrimary,
                                    fontSize = 28.sp,
                                    fontWeight = FontWeight.Bold,
                                    modifier = Modifier.padding(top = 4.dp),
                                )
                                Text(
                                    text = activeSub.description,
                                    color = LauncherColors.TextSecondary,
                                    fontSize = 18.sp,
                                    modifier = Modifier.padding(top = 2.dp),
                                )
                            }
                        }

                        // --- Content template placeholder ---
                        NewSettingsSubcategoryPlaceholder(
                            category = currentCategory,
                            subcategory = activeSub,
                        )
                    }
                }
            }
        }
    }
}

/**
 * Placeholder template for subcategories with adjusted typography and controls.
 */
@Composable
private fun NewSettingsSubcategoryPlaceholder(
    category: NewSettingsCategory,
    subcategory: NewSettingsSubcategory?,
) {
    val context = LocalContext.current

    // Special case for Launcher -> Home: provide the live switch to toggle back if needed
    if ((category.id == "launcher") && (subcategory?.id == "home")) {
        val useNewSettingsViewRevision by LauncherAppConfigStore.useNewSettingsViewRevisionFlow
            .collectAsStateWithLifecycle()
        val isUseNewSettingsView = remember(context, useNewSettingsViewRevision) {
            LauncherAppConfigStore.useNewSettingsView(context)
        }

        Column(
            modifier = Modifier
                .fillMaxWidth()
                .clip(RoundedCornerShape(14.dp))
                .background(LauncherColors.CardDark)
                .padding(14.dp),
            verticalArrangement = Arrangement.spacedBy(6.dp),
        ) {
            NewSettingsToggleRow(
                label = stringResource(R.string.launcher_new_settings_view_title),
                active = isUseNewSettingsView,
                onClick = {
                    LauncherAppConfigStore.setUseNewSettingsView(context, !isUseNewSettingsView)
                },
            )
            Text(
                text = stringResource(R.string.launcher_new_settings_view_desc),
                color = LauncherColors.TextMuted,
                fontSize = 17.sp,
                modifier = Modifier.padding(horizontal = 12.dp),
            )
        }
    }

    // Info card stating that content is in template mode
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(14.dp))
            .background(LauncherColors.CardDark)
            .padding(14.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(14.dp),
    ) {
        Icon(
            imageVector = Icons.Filled.Info,
            contentDescription = null,
            tint = LauncherColors.AccentCyan,
            modifier = Modifier.size(36.dp),
        )
        Column(modifier = Modifier.weight(1f)) {
            Text(
                text = "Шаблон подраздела: ${subcategory?.title ?: ""}",
                color = LauncherColors.TextPrimary,
                fontSize = 21.sp,
                fontWeight = FontWeight.SemiBold,
            )
            Text(
                text = "Информацию из старых настроек пока не переносим. Шаблон подготовлен для добавления параметров.",
                color = LauncherColors.TextMuted,
                fontSize = 17.sp,
                modifier = Modifier.padding(top = 2.dp),
            )
        }
    }

    // Sample placeholder rows simulating future controls
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(14.dp))
            .background(LauncherColors.CardDark)
            .padding(8.dp),
        verticalArrangement = Arrangement.spacedBy(4.dp),
    ) {
        var sampleToggle1 by remember { mutableStateOf(value = false) }
        var sampleToggle2 by remember { mutableStateOf(value = true) }

        NewSettingsToggleRow(
            label = "Пример параметра 1",
            active = sampleToggle1,
            onClick = { sampleToggle1 = !sampleToggle1 },
        )
        NewSettingsToggleRow(
            label = "Пример параметра 2",
            active = sampleToggle2,
            onClick = { sampleToggle2 = !sampleToggle2 },
        )
        NewSettingsValueRow(
            label = "Выбор режима работы",
            value = "Автоматический",
            onClick = {},
        )
    }
}

/**
 * Toggle row with balanced typography (22sp) and scaled switch pill for automotive HU.
 */
@Composable
private fun NewSettingsToggleRow(
    label: String,
    active: Boolean,
    onClick: (() -> Unit)? = null,
    enabled: Boolean = onClick != null,
) {
    val wrappedOnClick = onClick?.let { rememberWrappedOnClick(it) }
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .heightIn(min = 60.dp)
            .clip(RoundedCornerShape(12.dp))
            .then(
                if (enabled && wrappedOnClick != null) {
                    Modifier.clickable(onClick = wrappedOnClick)
                } else {
                    Modifier
                },
            )
            .padding(horizontal = 12.dp, vertical = 8.dp),
        horizontalArrangement = Arrangement.SpaceBetween,
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Text(
            text = label,
            color = if (enabled) LauncherColors.TextPrimary else LauncherColors.TextMuted,
            fontSize = 22.sp,
            fontWeight = FontWeight.Medium,
        )
        Box(
            modifier = Modifier
                .width(56.dp)
                .height(32.dp)
                .clip(CircleShape)
                .background(
                    if (active) LauncherColors.AccentCyan
                    else LauncherColors.TextMuted.copy(alpha = 0.45f),
                ),
            contentAlignment = if (active) Alignment.CenterEnd else Alignment.CenterStart,
        ) {
            Box(
                modifier = Modifier
                    .padding(4.dp)
                    .size(24.dp)
                    .clip(CircleShape)
                    .background(if (enabled) Color.White else LauncherColors.TextSecondary),
            )
        }
    }
}

/**
 * Value display/picker row with balanced typography (22sp).
 */
@Composable
private fun NewSettingsValueRow(
    label: String,
    value: String,
    onClick: (() -> Unit)? = null,
) {
    val wrappedOnClick = onClick?.let { rememberWrappedOnClick(it) }
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .heightIn(min = 60.dp)
            .clip(RoundedCornerShape(12.dp))
            .then(
                if (wrappedOnClick != null) {
                    Modifier.clickable(onClick = wrappedOnClick)
                } else {
                    Modifier
                },
            )
            .padding(horizontal = 12.dp, vertical = 8.dp),
        horizontalArrangement = Arrangement.SpaceBetween,
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Text(
            text = label,
            color = LauncherColors.TextPrimary,
            fontSize = 22.sp,
            fontWeight = FontWeight.Medium,
        )
        Text(
            text = value,
            color = LauncherColors.TextSecondary,
            fontSize = 22.sp,
            fontWeight = FontWeight.SemiBold,
        )
    }
}
