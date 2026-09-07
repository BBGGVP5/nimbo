import Darwin
import Foundation

/// Настоящие показания туннеля.
///
/// Пакеты проходят мимо приложения: дескриптор utun читает ядро Xray внутри
/// расширения, поэтому считать байты в приложении нечем. Зато счётчики самого
/// интерфейса ведёт ядро системы — их и снимаем через `getifaddrs`. Интерфейс
/// узнаём по адресу, который выдаёт туннель (`PacketTunnelNetwork.settings`).
enum NimboTunnelMetrics {
    struct Counters: Equatable {
        let received: UInt64
        let sent: UInt64
    }

    /// Адрес туннеля из настроек Packet Tunnel: по нему находим свой utun.
    static let tunnelAddress = "198.18.0.1"

    /// Счётчики туннеля.
    ///
    /// Сначала пробуем найти интерфейс по адресу, который выдаёт туннель, но
    /// адрес меняется вместе с настройками, и раньше в этом случае скорость
    /// навсегда оставалась нулевой. Поэтому есть запасной путь: самый
    /// нагруженный utun.
    static func tunnelCounters(address: String = tunnelAddress) -> Counters? {
        var storage: UnsafeMutablePointer<ifaddrs>?
        guard getifaddrs(&storage) == 0, let first = storage else { return nil }
        defer { freeifaddrs(storage) }

        if let name = interfaceName(withAddress: address, in: first),
           let byAddress = counters(forInterface: name, in: first) {
            return byAddress
        }
        return NimboInterfaceCounters.busiestTunnel().map {
            Counters(received: $0.received, sent: $0.sent)
        }
    }

    /// Память процесса приложения — запасное значение, когда расширение
    /// молчит. Настоящий предел система ставит расширению, поэтому обычно
    /// показывается его память.
    static func memoryFootprintMb() -> Int {
        var info = task_vm_info_data_t()
        var count = mach_msg_type_number_t(
            MemoryLayout<task_vm_info_data_t>.size / MemoryLayout<natural_t>.size
        )
        let result = withUnsafeMutablePointer(to: &info) { pointer in
            pointer.withMemoryRebound(to: integer_t.self, capacity: Int(count)) { rebound in
                task_info(mach_task_self_, task_flavor_t(TASK_VM_INFO), rebound, &count)
            }
        }
        guard result == KERN_SUCCESS else { return 0 }
        return Int(Double(info.phys_footprint) / 1_048_576.0)
    }

    private static func interfaceName(
        withAddress address: String,
        in first: UnsafeMutablePointer<ifaddrs>
    ) -> String? {
        var pointer: UnsafeMutablePointer<ifaddrs>? = first
        while let current = pointer {
            let entry = current.pointee
            if let socketAddress = entry.ifa_addr,
               socketAddress.pointee.sa_family == UInt8(AF_INET) {
                var host = [CChar](repeating: 0, count: Int(NI_MAXHOST))
                let resolved = getnameinfo(
                    socketAddress,
                    socklen_t(socketAddress.pointee.sa_len),
                    &host,
                    socklen_t(host.count),
                    nil,
                    0,
                    NI_NUMERICHOST
                )
                if resolved == 0, String(cString: host) == address {
                    return String(cString: entry.ifa_name)
                }
            }
            pointer = entry.ifa_next
        }
        return nil
    }

    private static func counters(
        forInterface name: String,
        in first: UnsafeMutablePointer<ifaddrs>
    ) -> Counters? {
        var pointer: UnsafeMutablePointer<ifaddrs>? = first
        while let current = pointer {
            let entry = current.pointee
            if String(cString: entry.ifa_name) == name,
               let socketAddress = entry.ifa_addr,
               socketAddress.pointee.sa_family == UInt8(AF_LINK),
               let raw = entry.ifa_data {
                let data = raw.assumingMemoryBound(to: if_data.self).pointee
                return Counters(received: UInt64(data.ifi_ibytes), sent: UInt64(data.ifi_obytes))
            }
            pointer = entry.ifa_next
        }
        return nil
    }
}

/// Копит показания в скорость и итоги сессии.
///
/// Счётчики интерфейса 32-битные и переполняются каждые 4 ГБ, поэтому итоги
/// накапливаем сами по разнице, а не берём абсолютное значение.
final class NimboMetricsAccumulator {
    private(set) var uploadSpeed: UInt64 = 0
    private(set) var downloadSpeed: UInt64 = 0
    private(set) var uploadTotal: UInt64 = 0
    private(set) var downloadTotal: UInt64 = 0
    private(set) var uploadSamples: [UInt64] = []
    private(set) var downloadSamples: [UInt64] = []
    private(set) var memoryMb: Int = 0
    private(set) var memorySamples: [Int] = []

    private var previous: NimboTunnelMetrics.Counters?
    private var previousAt: Date?

    /// Столько же точек держит график на Android.
    private let sampleLimit = 60

    func reset() {
        uploadSpeed = 0
        downloadSpeed = 0
        uploadTotal = 0
        downloadTotal = 0
        uploadSamples = []
        downloadSamples = []
        memorySamples = []
        previous = nil
        previousAt = nil
    }

    /// Показания расширения, если оно ответило.
    ///
    /// Приложение считает то же самое запасным путём, но у расширения числа
    /// точные: оно знает своё имя интерфейса и свою память.
    func tick(reported: (received: UInt64, sent: UInt64, memoryMb: Int)?, now: Date = Date()) {
        if let reported {
            applyCounters(
                received: reported.received,
                sent: reported.sent,
                memoryMb: reported.memoryMb,
                now: now
            )
            return
        }
        tick(now: now)
    }

    func tick(now: Date = Date()) {
        guard let counters = NimboTunnelMetrics.tunnelCounters() else {
            memoryMb = NimboTunnelMetrics.memoryFootprintMb()
            appendMemory(memoryMb)
            previous = nil
            previousAt = nil
            appendSpeed(upload: 0, download: 0)
            uploadSpeed = 0
            downloadSpeed = 0
            return
        }
        applyCounters(
            received: counters.received,
            sent: counters.sent,
            memoryMb: NimboTunnelMetrics.memoryFootprintMb(),
            now: now
        )
    }

    /// Общий расчёт: разница счётчиков за прошедшее время.
    ///
    /// Источник счётчиков разный — расширение или собственный опрос
    /// интерфейса, — а арифметика одна, и разводить её на две копии нельзя.
    private func applyCounters(received: UInt64, sent: UInt64, memoryMb value: Int, now: Date) {
        memoryMb = value
        appendMemory(value)

        let counters = NimboTunnelMetrics.Counters(received: received, sent: sent)
        defer {
            previous = counters
            previousAt = now
        }

        guard let previous, let previousAt else {
            appendSpeed(upload: 0, download: 0)
            return
        }

        let seconds = now.timeIntervalSince(previousAt)
        guard seconds > 0.2 else { return }

        let sentDelta = delta(previous: previous.sent, current: counters.sent)
        let receivedDelta = delta(previous: previous.received, current: counters.received)
        uploadTotal &+= sentDelta
        downloadTotal &+= receivedDelta
        uploadSpeed = UInt64(Double(sentDelta) / seconds)
        downloadSpeed = UInt64(Double(receivedDelta) / seconds)
        appendSpeed(upload: uploadSpeed, download: downloadSpeed)
    }

    /// Счётчик мог переполниться или обнулиться при переподключении — в обоих
    /// случаях засчитываем текущее значение, а не отрицательную разницу.
    private func delta(previous: UInt64, current: UInt64) -> UInt64 {
        current >= previous ? current - previous : current
    }

    private func appendSpeed(upload: UInt64, download: UInt64) {
        uploadSamples.append(upload)
        downloadSamples.append(download)
        if uploadSamples.count > sampleLimit { uploadSamples.removeFirst(uploadSamples.count - sampleLimit) }
        if downloadSamples.count > sampleLimit { downloadSamples.removeFirst(downloadSamples.count - sampleLimit) }
    }

    private func appendMemory(_ value: Int) {
        memorySamples.append(value)
        if memorySamples.count > sampleLimit { memorySamples.removeFirst(memorySamples.count - sampleLimit) }
    }
}
