import SwiftUI

/// Optional reassurance beneath real state/progress. Never drives an operation.
struct NimboOperationPhrase: View {
    enum Operation: Hashable {
        case connection, updateDownload, syncTransfer

        var phrases: [String] {
            switch self {
            case .connection:
                return ["Один момент…", "Ещё немного…", "Спасибо за ожидание…"]
            case .updateDownload:
                return ["Забираем новую версию…", "Наводим порядок…", "Ещё немного…"]
            case .syncTransfer:
                return ["Собираем настройки…", "Готовим передачу…", "Ещё немного…"]
            }
        }
    }

    let operation: Operation
    let isActive: Bool
    @Environment(\.accessibilityReduceMotion) private var reduceMotion
    @State private var index = 0

    private struct RotationKey: Hashable {
        let operation: Operation
        let active: Bool
        let reduceMotion: Bool
    }

    var body: some View {
        Group {
            if isActive {
                Text(operation.phrases[index % operation.phrases.count])
                    .nimboFont(13, relativeTo: .caption)
                    .foregroundStyle(NimboNative.secondary)
                    .fixedSize(horizontal: false, vertical: true)
                    .transaction { transaction in transaction.animation = nil }
            }
        }
        // SwiftUI cancels the task on disappearance or key change. This also
        // freezes the first phrase immediately when Reduce Motion is enabled.
        .task(id: RotationKey(operation: operation, active: isActive, reduceMotion: reduceMotion)) {
            index = 0
            guard isActive, !reduceMotion else { return }
            while !Task.isCancelled {
                do { try await Task.sleep(nanoseconds: 4_500_000_000) }
                catch { return }
                guard !Task.isCancelled else { return }
                index = (index + 1) % operation.phrases.count
            }
        }
    }
}
