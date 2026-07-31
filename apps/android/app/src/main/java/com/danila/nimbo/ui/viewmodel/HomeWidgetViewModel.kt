package com.danila.nimbo.ui.viewmodel

import android.app.Application
import android.util.Log
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.danila.nimbo.model.HomeWidget
import com.danila.nimbo.model.WidgetRegistry
import com.danila.nimbo.model.WidgetType
import com.danila.nimbo.utils.PreferencesManager
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch

class HomeWidgetViewModel(application: Application) : AndroidViewModel(application) {

    private val preferencesManager = PreferencesManager(application)

    private val _activeWidgets = MutableStateFlow<List<HomeWidget>>(emptyList())
    val activeWidgets: StateFlow<List<HomeWidget>> = _activeWidgets.asStateFlow()

    private val _hiddenWidgets = MutableStateFlow<List<HomeWidget>>(emptyList())
    val hiddenWidgets: StateFlow<List<HomeWidget>> = _hiddenWidgets.asStateFlow()

    private val _isEditMode = MutableStateFlow(false)
    val isEditMode: StateFlow<Boolean> = _isEditMode.asStateFlow()

    private val _hasUnsavedChanges = MutableStateFlow(false)
    val hasUnsavedChanges: StateFlow<Boolean> = _hasUnsavedChanges.asStateFlow()

    private var originalWidgets: List<HomeWidget> = emptyList()

    init {
        loadWidgets()
    }

    fun loadWidgets() {
        val raw = preferencesManager.loadHomeWidgets()

        // Миграция старых виджетов
        val loadedWidgets = WidgetRegistry.migrateWidgets(raw).toMutableList()

        val hiddenIds = preferencesManager.loadHiddenWidgets()
        val cleanHiddenIds = hiddenIds.filter { it != "speed_test" }

        val hiddenList = cleanHiddenIds.mapNotNull { id ->
            val config = WidgetRegistry.availableWidgets.find { w ->
                generateWidgetId(w.type) == id
            }
            config?.let {
                HomeWidget(id = id, type = it.type, isVisible = false, position = 0)
            }
        }.toMutableList()

        fun addIfMissing(type: WidgetType) {
            if (loadedWidgets.none { it.type == type }) {

                val position = when (type) {
                    WidgetType.VPN_CONNECT_HINT -> {
                        val vpnIndex = loadedWidgets.indexOfFirst { it.type == WidgetType.VPN_BUTTON }
                        if (vpnIndex != -1) vpnIndex + 1 else loadedWidgets.size
                    }
                    WidgetType.SPEED_STATS -> {
                        val selectorIndex = loadedWidgets.indexOfFirst { it.type == WidgetType.SERVER_SELECTOR }
                        if (selectorIndex != -1) selectorIndex + 1 else loadedWidgets.size
                    }
                    else -> loadedWidgets.size
                }

                loadedWidgets.add(
                    position,
                    HomeWidget(
                        id = generateWidgetId(type),
                        type = type,
                        isVisible = true,
                        position = position
                    )
                )
            }
        }

// ✅ гарантируем что кнопки есть
        addIfMissing(WidgetType.SERVER_ACTIONS)
        addIfMissing(WidgetType.VPN_CONNECT_HINT)

        // ─────────────────────────────────────────

        // 🔥 ПЕРЕСЧИТЫВАЕМ позиции после вставок
        val finalWidgets = loadedWidgets.mapIndexed { index, w ->
            w.copy(position = index)
        }
        // Сохраняем если изменилось
        if (raw != finalWidgets) {
            viewModelScope.launch {
                preferencesManager.saveHomeWidgets(finalWidgets)
            }
        }

        _activeWidgets.value = finalWidgets
        _hiddenWidgets.value = hiddenList
        originalWidgets = finalWidgets
        _hasUnsavedChanges.value = false

        Log.d("HomeWidgetViewModel", "Loaded ${loadedWidgets.size} active, ${hiddenList.size} hidden")
    }

    fun toggleEditMode() {
        _isEditMode.value = !_isEditMode.value
        if (_isEditMode.value) {
            originalWidgets = _activeWidgets.value
        } else {
            if (!_hasUnsavedChanges.value) {
                _activeWidgets.value = originalWidgets
            }
        }
    }

    fun cancelChanges() {
        _activeWidgets.value = originalWidgets
        _isEditMode.value = false
        _hasUnsavedChanges.value = false
    }

    fun saveChanges() {
        viewModelScope.launch {
            preferencesManager.saveHomeWidgets(_activeWidgets.value)
            val hiddenIds = _hiddenWidgets.value.map { it.id }
            preferencesManager.saveHiddenWidgets(hiddenIds)
            _hasUnsavedChanges.value = false
            _isEditMode.value = false
            originalWidgets = _activeWidgets.value
            Log.d("HomeWidgetViewModel", "Saved")
        }
    }

    fun moveWidget(fromIndex: Int, toIndex: Int) {
        val widgets = _activeWidgets.value.toMutableList()

        if (fromIndex !in widgets.indices) return

        val widget = widgets.removeAt(fromIndex)

        val safeIndex = toIndex.coerceIn(0, widgets.size)

        widgets.add(safeIndex, widget)

        _activeWidgets.value = widgets.mapIndexed { index, w ->
            w.copy(position = index)
        }

        _hasUnsavedChanges.value = true
    }


    fun hideWidget(widgetId: String) {
        val widgetIndex = _activeWidgets.value.indexOfFirst { it.id == widgetId }
        if (widgetIndex == -1) return

        val widget = _activeWidgets.value[widgetIndex]
        val config = WidgetRegistry.availableWidgets.find { it.type == widget.type }
        if (config?.isSystem == true) {
            Log.w("HomeWidgetViewModel", "Cannot hide system widget: ${widget.type}")
            return
        }

        val activeList = _activeWidgets.value.toMutableList()
        activeList.removeAt(widgetIndex)
        _activeWidgets.value = activeList.mapIndexed { index, w -> w.copy(position = index) }

        // Сохраняем оригинальную позицию для восстановления
        val hiddenList = _hiddenWidgets.value.toMutableList()
        if (hiddenList.none { it.id == widgetId }) {
            hiddenList.add(widget.copy(isVisible = false, position = widgetIndex))
            _hiddenWidgets.value = hiddenList
        }
        _hasUnsavedChanges.value = true
    }

    fun showWidget(widgetId: String) {
        val widgetIndex = _hiddenWidgets.value.indexOfFirst { it.id == widgetId }
        if (widgetIndex == -1) return

        val widget = _hiddenWidgets.value[widgetIndex]
        val hiddenList = _hiddenWidgets.value.toMutableList()
        hiddenList.removeAt(widgetIndex)
        _hiddenWidgets.value = hiddenList

        val activeList = _activeWidgets.value.toMutableList()
        
        // Восстанавливаем на сохранённую позицию или в конец
        val restorePosition = widget.position.coerceIn(0, activeList.size)
        activeList.add(restorePosition, widget.copy(isVisible = true))
        _activeWidgets.value = activeList.mapIndexed { index, w -> w.copy(position = index) }
        _hasUnsavedChanges.value = true
    }

    fun addWidget(type: WidgetType) {
        val widgetId = generateWidgetId(type)
        if (_activeWidgets.value.any { it.id == widgetId }) {
            Log.w("HomeWidgetViewModel", "Widget already exists: $widgetId")
            return
        }

        // Проверяем есть ли скрытый виджет этого типа
        val hiddenWidget = _hiddenWidgets.value.find { it.type == type }
        val position = hiddenWidget?.position ?: _activeWidgets.value.size
        
        val hiddenList = _hiddenWidgets.value.toMutableList()
        hiddenList.removeAll { it.id == widgetId }
        _hiddenWidgets.value = hiddenList

        val activeList = _activeWidgets.value.toMutableList()
        activeList.add(position.coerceIn(0, activeList.size), HomeWidget(id = widgetId, type = type, isVisible = true, position = position))
        _activeWidgets.value = activeList.mapIndexed { index, w -> w.copy(position = index) }
        _hasUnsavedChanges.value = true
    }

    fun getWidgetConfig(type: WidgetType) = WidgetRegistry.getConfigForType(type)

    private fun generateWidgetId(type: WidgetType): String = type.name.lowercase()

    fun getWidgetById(widgetId: String): HomeWidget? =
        _activeWidgets.value.find { it.id == widgetId }
            ?: _hiddenWidgets.value.find { it.id == widgetId }
}

