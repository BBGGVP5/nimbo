import SwiftUI

@MainActor
struct NimboLaunchView: View {
    @StateObject private var launch = NimboLaunchCoordinator()

    var body: some View {
        ZStack {
            // The child (including every State initializer) does not exist
            // until the gate's acknowledged preparation calls its lazy factory.
            if let attempt = launch.rootToken {
                NimboRootOwnerView(attempt: attempt, launch: launch)
                    .allowsHitTesting(launch.exposeRoot)
                    .accessibilityHidden(!launch.exposeRoot)
            }
            if !launch.exposeRoot { nativeSurface }
        }
        .overlay(alignment: .topTrailing) {
            if launch.exposeRoot && (launch.gate.diagnostics || launch.hasFailure) {
                VStack(alignment: .trailing, spacing: 8) {
                    if launch.gate.diagnostics && !launch.gate.completed {
                        Button("Интерфейс виден") { launch.confirmVisibleInterface() }
                            .disabled(!launch.canConfirmVisibleInterface)
                            .accessibilityIdentifier("nimbo.launch.confirm-visible")
                    }
                    Button("Диагностика запуска") { launch.stayNative() }
                }
                    .font(.system(.caption)).padding(10)
                    .background(Color(uiColor: .secondarySystemBackground), in: RoundedRectangle(cornerRadius: 12))
                    .padding()
            }
        }
    }

    private var nativeSurface: some View {
        ScrollView {
            VStack(alignment: .leading, spacing: 18) {
                Text("Nimbo").font(.system(.largeTitle, design: .rounded).bold())
                Text("Запуск приложения").font(.system(.title2).bold())
                Text(launch.record.version + " · " + launch.record.build + " · " + launch.record.revision)
                    .font(.system(.caption, design: .monospaced))
                Text(status).font(.system(.body))
                Text(launch.phase.rawValue).font(.system(.caption, design: .monospaced))
                    .textSelection(.enabled)
                    .accessibilityIdentifier("nimbo.launch.phase")
                if launch.persistenceFailed {
                    Text("Журнал этапов недоступен или его операция ещё не завершилась. Автоматический переход отключён; данные профилей не изменяются.")
                        .font(.system(.callout))
                }
                if launch.gate.timedOut {
                    Text("Ожидание завершилось. Если главный поток завис, этот экран не сможет обновляться. После повторного открытия останется диагностика незавершённого запуска.")
                        .font(.system(.callout))
                }
                if launch.rootToken == nil {
                    Button(launch.gate.failed || launch.gate.timedOut ? "Повторить подготовку" : "Продолжить запуск") {
                        launch.continueLaunch()
                    }
                    .buttonStyle(.borderedProminent)
                    .disabled(!launch.gate.canBegin(explicit: true))
                    .accessibilityIdentifier("nimbo.launch.continue")
                } else {
                    Button("Показать созданный интерфейс") { launch.showExistingRoot() }
                        .buttonStyle(.borderedProminent)
                        .disabled(!launch.canShowExistingRoot)
                    Text("Повторное создание интерфейса в этом процессе отключено: предыдущая операция ещё может выполняться. Для новой попытки закройте и снова откройте приложение.")
                        .font(.system(.footnote))
                }
                Button("Остаться в диагностике") { launch.stayNative() }
                    .buttonStyle(.bordered)
                    .accessibilityIdentifier("nimbo.launch.stay")
                ShareLink("Поделиться диагностикой запуска", item: launch.exportText)
                    .accessibilityIdentifier("nimbo.launch.share")
                Text("Отчёт содержит только версию, номер сборки, ревизию и этапы с временем. Показ окна и циклы дисплея не подтверждают отрисовку Compose.")
                    .font(.system(.footnote))
            }
            .padding(24)
            .frame(maxWidth: 600, alignment: .leading)
            .frame(maxWidth: .infinity, alignment: .center)
        }
        .frame(maxWidth: .infinity, maxHeight: .infinity)
        .background(Color(uiColor: .systemBackground).ignoresSafeArea())
        .foregroundStyle(Color.primary)
        .background {
            NimboLaunchAppearanceObserver { launch.nativeSurfaceAcknowledged() }
                .allowsHitTesting(false)
        }
        .accessibilityIdentifier("nimbo.launch.native")
    }

    private var status: String {
        if launch.gate.requiresRecovery {
            return "Предыдущий запуск не был завершён или его запись недоступна. Причина сбоя не установлена. Можно остаться здесь или явно продолжить."
        }
        if launch.gate.failed || launch.gate.timedOut { return "Запуск не завершён. Этап указан ниже; ошибка не скрыта." }
        if launch.gate.staysNative { return "Вы остались на нативном экране диагностики." }
        if !launch.gate.journalLoaded { return "Подготавливаем нативный экран и проверяем запись предыдущего запуска." }
        if launch.gate.token != nil { return "Запускаем интерфейс. Последний этап указан ниже." }
        return launch.gate.diagnostics
            ? "Диагностическая сборка. Интерфейс и его состояние создаются только после «Продолжить запуск»."
            : "Нативный экран подготовлен. Запускаем интерфейс."
    }
}
