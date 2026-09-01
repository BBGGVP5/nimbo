import SwiftUI
import WidgetKit

/// Точка входа расширения.
///
/// Пункт управления держит элементы приложений начиная с iOS 18; на более
/// старых системах связка остаётся пустой, и система просто ничего не
/// предлагает — это лучше, чем отказ установки на старом устройстве.
@main
struct NimboControlWidgetBundle: WidgetBundle {
    var body: some Widget {
        if #available(iOS 18.0, *) {
            NimboControlWidget()
        }
    }
}
