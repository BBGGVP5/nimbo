import Foundation
import SwiftUI
import UIKit

/// Ход сопряжения и обмена — повторяет `CrossSyncPairingEngine` на Android:
/// `hello` → ожидание подтверждения на втором устройстве → `commit` с
/// выбранным направлением → `receipt`.
@MainActor
final class NimboSyncEngine: ObservableObject {
    enum Stage: Equatable {
        case idle
        case connecting
        case awaitingApproval(code: String?)
        case chooseDirection(peer: String)
        case working
        case completed(summary: String)
        case failed(reason: String)
    }

    @Published private(set) var stage: Stage = .idle
    @Published private(set) var peerName: String = ""

    private var session: NimboSyncProtocol.PairingSession?
    private var pollTask: Task<Void, Never>?

    private var deviceId: String {
        let key = "com.nimbo.sync.device-id"
        if let stored = UserDefaults.standard.string(forKey: key) { return stored }
        let created = UUID().uuidString
        UserDefaults.standard.set(created, forKey: key)
        return created
    }

    func cancel() {
        pollTask?.cancel()
        pollTask = nil
        session = nil
        stage = .idle
    }

    /// Точка входа: ссылка из QR на компьютере или Android.
    func start(link: String) {
        cancel()
        stage = .connecting
        Task { await handleLink(link) }
    }

    private func handleLink(_ link: String) async {
        do {
            let parsed = try NimboSyncProtocol.parsePairingLink(link)
            session = parsed
            let response = try await NimboSyncTransport.exchange(
                session: parsed,
                request: NimboSyncRequest(
                    action: "hello",
                    deviceId: deviceId,
                    deviceName: UIDevice.current.name,
                    bundle: NimboSyncBundleMapper.export()
                )
            )
            apply(response)
            if response.state == "awaiting_approval" {
                startPolling(parsed)
            }
        } catch {
            stage = .failed(reason: message(for: error))
        }
    }

    private func startPolling(_ session: NimboSyncProtocol.PairingSession) {
        pollTask?.cancel()
        pollTask = Task { [weak self] in
            while !Task.isCancelled, !session.isExpired {
                // Тот же интервал, что и на Android: чаще опрашивать нечего.
                try? await Task.sleep(nanoseconds: 800_000_000)
                guard let self else { return }
                do {
                    let response = try await NimboSyncTransport.exchange(
                        session: session,
                        request: NimboSyncRequest(action: "status", deviceId: self.deviceId)
                    )
                    await MainActor.run { self.apply(response) }
                    if ["paired", "completed", "rejected", "cancelled", "expired"].contains(response.state) {
                        if response.state == "paired", let direction = response.direction {
                            await self.commit(direction: direction)
                        }
                        return
                    }
                } catch {
                    await MainActor.run { self.stage = .failed(reason: self.message(for: error)) }
                    return
                }
            }
            await MainActor.run {
                if case .awaitingApproval = self?.stage {
                    self?.stage = .failed(reason: "Сеанс истёк — обновите QR на втором устройстве")
                }
            }
        }
    }

    /// Направление выбирает пользователь, когда вторая сторона не решила сама.
    func commit(direction: String) {
        Task { await commitAsync(direction: direction) }
    }

    private func commit(direction: String) async {
        await commitAsync(direction: direction)
    }

    private func commitAsync(direction: String) async {
        guard let session else { return }
        stage = .working
        do {
            let response = try await NimboSyncTransport.exchange(
                session: session,
                request: NimboSyncRequest(
                    action: "commit",
                    deviceId: deviceId,
                    deviceName: UIDevice.current.name,
                    bundle: NimboSyncBundleMapper.export(),
                    direction: direction,
                    categories: .all
                )
            )
            apply(response)

            if direction == "desktop_to_android", let bundle = response.desktopBundle {
                let summary = try await NimboSyncBundleMapper.apply(bundle)
                _ = try? await NimboSyncTransport.exchange(
                    session: session,
                    request: NimboSyncRequest(action: "receipt", deviceId: deviceId)
                )
                stage = .completed(summary: summary)
            } else if response.state == "completed" {
                _ = try? await NimboSyncTransport.exchange(
                    session: session,
                    request: NimboSyncRequest(action: "receipt", deviceId: deviceId)
                )
                stage = .completed(summary: "Данные отправлены на \(peerName.isEmpty ? "устройство" : peerName)")
            } else if response.state == "rejected" {
                stage = .failed(reason: response.message ?? "Обмен отклонён на втором устройстве")
            } else {
                startPolling(session)
            }
        } catch {
            stage = .failed(reason: message(for: error))
        }
    }

    private func apply(_ response: NimboSyncResponse) {
        if let name = response.desktopDeviceInfo?.name, !name.isEmpty {
            peerName = name
        }
        switch response.state {
        case "awaiting_approval":
            stage = .awaitingApproval(code: response.comparisonCode)
        case "paired":
            if response.direction == nil {
                stage = .chooseDirection(peer: peerName.isEmpty ? "второе устройство" : peerName)
            }
        case "rejected":
            stage = .failed(reason: response.message ?? "Сопряжение отклонено")
        case "cancelled":
            stage = .failed(reason: "Сопряжение отменено на втором устройстве")
        case "expired":
            stage = .failed(reason: "Сеанс истёк — обновите QR")
        default:
            break
        }
    }

    private func message(for error: Error) -> String {
        if let syncError = error as? NimboSyncError {
            return syncError.errorDescription ?? "Ошибка синхронизации"
        }
        return NimboRedactor.redact(error.localizedDescription)
    }
}

/// Перенос данных между общим форматом обмена и тем, что хранит iOS.
enum NimboSyncBundleMapper {
    static func export() -> NimboSyncBundle {
        let source = (try? NimboConfigurationStore.shared.loadSource()) ?? nil
        let meta = NimboSubscriptionMetaStore.current
        let subscriptions = source.map {
            [NimboSyncSubscription(url: $0, name: meta.title ?? "Nimbo", order: 0)]
        } ?? []

        let defaults = UserDefaults.standard
        let appearance = NimboSyncAppearance(
            themeMode: "dark",
            uiStyle: defaults.string(forKey: "com.nimbo.appearance.elementStyle") ?? "glass",
            accentColor: "#75a7ff",
            panelBrightness: 100,
            transparency: 0,
            blur: 25,
            rounding: 100,
            providerTheme: true,
            showSubscriptionLogo: true
        )
        let connection = NimboSyncConnection(
            killSwitch: false,
            tlsFragmentation: false,
            showSpeedChart: defaults.object(forKey: "com.nimbo.appearance.showSpeedWidget") == nil
                || defaults.bool(forKey: "com.nimbo.appearance.showSpeedWidget")
        )

        return NimboSyncBundle(
            schema: NimboSyncProtocol.schema,
            platform: "ios",
            deviceName: UIDevice.current.name,
            createdAtMs: Int64(Date().timeIntervalSince1970 * 1000),
            deviceInfo: NimboSyncDeviceInfo(
                name: UIDevice.current.name,
                platform: "ios",
                osName: "iOS",
                osVersion: UIDevice.current.systemVersion,
                appVersion: NimboPlatformInfo.displayVersion,
                architecture: "arm64"
            ),
            subscriptions: subscriptions,
            appearance: appearance,
            connection: connection,
            routingModules: storedModules()
        )
    }

    /// Модули лежат там же, где их пишет общий интерфейс.
    private static func storedModules() -> [NimboSyncRoutingModule] {
        guard let raw = UserDefaults.standard.string(forKey: "com.nimbo.routing.modules"),
              let data = raw.data(using: .utf8),
              let decoded = try? JSONDecoder().decode([NimboSyncRoutingModule].self, from: data) else {
            return []
        }
        return decoded
    }

    /// Возвращает короткое описание того, что применилось.
    @discardableResult
    static func apply(_ bundle: NimboSyncBundle) async throws -> String {
        var applied: [String] = []

        if let appearance = bundle.appearance {
            let defaults = UserDefaults.standard
            // Стили именуются одинаково на обеих платформах, кроме андроидных,
            // которых на iOS нет: неизвестное значение оставляем как есть.
            if ["glass", "material", "dotted", "signal", "manga"].contains(appearance.uiStyle) {
                defaults.set(appearance.uiStyle, forKey: "com.nimbo.appearance.elementStyle")
            }
            applied.append("оформление")
        }

        if let connection = bundle.connection {
            UserDefaults.standard.set(connection.showSpeedChart, forKey: "com.nimbo.appearance.showSpeedWidget")
        }

        if let incoming = bundle.routingModules, !incoming.isEmpty {
            // Совпадающие по идентификатору заменяются, остальные добавляются:
            // затирать чужие модули целиком нельзя — их могли написать здесь.
            var merged = storedModules()
            for module in incoming {
                if let index = merged.firstIndex(where: { $0.id == module.id }) {
                    merged[index] = module
                } else {
                    merged.append(module)
                }
            }
            if let data = try? JSONEncoder().encode(merged),
               let text = String(data: data, encoding: .utf8) {
                UserDefaults.standard.set(text, forKey: "com.nimbo.routing.modules")
                applied.append("модули (\(incoming.count))")
            }
        }

        // Подписка — главное: без неё остальное бессмысленно.
        if let subscription = bundle.subscriptions.first(where: { !$0.url.isEmpty }) {
            _ = try NimboSubscriptionRepository.shared.importPayload(
                Data(subscription.url.utf8),
                source: subscription.url
            )
            _ = try? await NimboSubscriptionRepository.shared.refresh()
            applied.append("подписка")
        }

        if applied.isEmpty { return "Нечего переносить" }
        return "Перенесено: " + applied.joined(separator: ", ")
    }
}
