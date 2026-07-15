package com.danila.nimbo.model

import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.Help
import androidx.compose.material.icons.filled.*
import androidx.compose.ui.graphics.vector.ImageVector
import kotlinx.serialization.Serializable

@Serializable
enum class WidgetType {
    VPN_BUTTON,
    VPN_CONNECT_HINT,    // Текст "Нажмите кнопку для подключения"
    VPN_STATUS,
    SERVER_SELECTOR,
    SERVER_ACTIONS,      // 4 кнопки в ряд (обновить, пинг, серверы, добавить)
    SUBSCRIPTION_INFO,
    IP_INFO,
    SPEED_DOWNLOAD,    // Скорость загрузки — отдельный виджет
    SPEED_UPLOAD,      // Скорость отдачи — отдельный виджет
    SPEED_TEST,        // Устарел — для миграции
    TRAFFIC_STATS,
    QUICK_ACTIONS,
    SERVER_REFRESH,
    SERVER_PING,
    SERVER_LIST,
    SERVER_ADD,
    COMBO_STATS,       // Дни + Трафик + Устройства
    SPEED_STATS,        // Загрузка + Отдача (красивый виджет)
    DEVICE_STATS,      // Лимит устройств (радикальный круг)
    EXPIRY_STATS       // Срок действия (радикальный круг)
}

@Serializable
data class HomeWidget(
    val id: String,
    val type: WidgetType,
    val isVisible: Boolean = true,
    val position: Int = 0
)

data class WidgetConfig(
    val type: WidgetType,
    val title: String,
    val description: String,
    val icon: ImageVector,
    val isSystem: Boolean = false
)

object WidgetRegistry {
    val availableWidgets = listOf(
        WidgetConfig(
            type = WidgetType.VPN_BUTTON,
            title = "Кнопка подключения",
            description = "Основная кнопка включения/выключения VPN",
            icon = Icons.Filled.PowerSettingsNew,
            isSystem = true
        ),
        WidgetConfig(
            type = WidgetType.VPN_CONNECT_HINT,
            title = "Подсказка подключения",
            description = "Текст 'Нажмите кнопку для подключения'",
            icon = Icons.Filled.Info,
            isSystem = false
        ),
        WidgetConfig(
            type = WidgetType.VPN_STATUS,
            title = "Статус подключения",
            description = "Таймер подключения и статус защиты",
            icon = Icons.Filled.Shield
        ),
        WidgetConfig(
            type = WidgetType.SERVER_SELECTOR,
            title = "Выбор сервера",
            description = "Выпадающий список для выбора сервера",
            icon = Icons.Filled.Dns
        ),
        WidgetConfig(
            type = WidgetType.SERVER_ACTIONS,
            title = "Действия с сервером",
            description = "4 кнопки: обновить, пинг, серверы, добавить",
            icon = Icons.Filled.Tune
        ),
        WidgetConfig(
            type = WidgetType.SUBSCRIPTION_INFO,
            title = "Информация о подписке",
            description = "Данные о тарифе, трафике и сроке действия",
            icon = Icons.Filled.Work
        ),
        WidgetConfig(
            type = WidgetType.SPEED_DOWNLOAD,
            title = "Скорость загрузки",
            description = "Текущая скорость входящего трафика",
            icon = Icons.Filled.ArrowDownward
        ),
        WidgetConfig(
            type = WidgetType.SPEED_UPLOAD,
            title = "Скорость отдачи",
            description = "Текущая скорость исходящего трафика",
            icon = Icons.Filled.ArrowUpward
        )
    )

    fun getConfigForType(type: WidgetType): WidgetConfig =
        availableWidgets.find { it.type == type } ?: WidgetConfig(
            type = type,
            title = "Виджет",
            description = "",
            icon = Icons.AutoMirrored.Filled.Help
        )

    /**
     * Миграция: если в сохранённых данных есть старый SPEED_TEST,
     * заменяем его на два отдельных виджета SPEED_DOWNLOAD + SPEED_UPLOAD
     */
    fun migrateWidgets(widgets: List<HomeWidget>): List<HomeWidget> {
        val result = mutableListOf<HomeWidget>()
        var hasDownload = false
        var hasUpload = false
        var hasCombo = false

        for (widget in widgets) {
            when (widget.type) {
                // Старый объединенный виджет — разделяем на два
                WidgetType.SPEED_TEST -> {
                    if (!hasDownload) {
                        hasDownload = true
                        result.add(HomeWidget(id = "speed_download", type = WidgetType.SPEED_DOWNLOAD, isVisible = true, position = widget.position))
                    }
                    if (!hasUpload) {
                        hasUpload = true
                        result.add(HomeWidget(id = "speed_upload", type = WidgetType.SPEED_UPLOAD, isVisible = true, position = widget.position + 1))
                    }
                }
                // Удаляем устаревшие круговые виджеты и заменяем их на общий блок статистики
                WidgetType.DEVICE_STATS, WidgetType.EXPIRY_STATS -> {
                    if (!hasCombo && result.none { it.type == WidgetType.COMBO_STATS }) {
                        hasCombo = true
                        result.add(
                            HomeWidget(
                                id = "combo_stats",
                                type = WidgetType.COMBO_STATS,
                                isVisible = true,
                                position = widget.position
                            )
                        )
                    }
                }
                WidgetType.IP_INFO -> {
                    // Полностью скрываем устаревший IP-виджет на главной.
                }
                WidgetType.COMBO_STATS -> {
                    hasCombo = true
                    result.add(widget)
                }
                WidgetType.SPEED_DOWNLOAD -> {
                    hasDownload = true
                    result.add(widget)
                }
                WidgetType.SPEED_UPLOAD -> {
                    hasUpload = true
                    result.add(widget)
                }
                else -> result.add(widget)
            }
        }

        // Переставляем позиции
        return result.mapIndexed { index, w -> w.copy(position = index) }
    }
}
