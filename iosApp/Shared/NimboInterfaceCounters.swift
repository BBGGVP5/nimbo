import Darwin
import Foundation

/// Счётчики байтов сетевых интерфейсов.
///
/// Лежит в общей папке, потому что нужен обеим сторонам: расширение читает
/// счётчики своего туннеля, приложение — как запасной путь, когда расширение
/// не отвечает.
enum NimboInterfaceCounters {
    struct Counters: Equatable {
        let received: UInt64
        let sent: UInt64
    }

    /// Счётчики самого нагруженного utun-интерфейса.
    ///
    /// Искать по фиксированному адресу нельзя: у туннеля он меняется вместе с
    /// настройками, а на устройстве обычно несколько utun — их заводят и
    /// системные службы. Тот, через который реально идёт трафик, узнаётся по
    /// наибольшей сумме счётчиков.
    static func busiestTunnel() -> Counters? {
        var storage: UnsafeMutablePointer<ifaddrs>?
        guard getifaddrs(&storage) == 0, let first = storage else { return nil }
        defer { freeifaddrs(storage) }

        var best: Counters?
        var pointer: UnsafeMutablePointer<ifaddrs>? = first
        while let current = pointer {
            defer { pointer = current.pointee.ifa_next }
            let entry = current.pointee
            guard entry.ifa_addr?.pointee.sa_family == UInt8(AF_LINK) else { continue }
            let name = String(cString: entry.ifa_name)
            guard name.hasPrefix("utun") else { continue }
            guard let data = entry.ifa_data?.assumingMemoryBound(to: if_data.self) else { continue }
            let counters = Counters(
                received: UInt64(data.pointee.ifi_ibytes),
                sent: UInt64(data.pointee.ifi_obytes)
            )
            if counters.received + counters.sent > (best.map { $0.received + $0.sent } ?? 0) {
                best = counters
            }
        }
        return best
    }

    /// Счётчики конкретного интерфейса по имени.
    static func counters(interface: String) -> Counters? {
        var storage: UnsafeMutablePointer<ifaddrs>?
        guard getifaddrs(&storage) == 0, let first = storage else { return nil }
        defer { freeifaddrs(storage) }

        var pointer: UnsafeMutablePointer<ifaddrs>? = first
        while let current = pointer {
            defer { pointer = current.pointee.ifa_next }
            let entry = current.pointee
            guard entry.ifa_addr?.pointee.sa_family == UInt8(AF_LINK),
                  String(cString: entry.ifa_name) == interface,
                  let data = entry.ifa_data?.assumingMemoryBound(to: if_data.self) else { continue }
            return Counters(
                received: UInt64(data.pointee.ifi_ibytes),
                sent: UInt64(data.pointee.ifi_obytes)
            )
        }
        return nil
    }

    /// Занятая память текущего процесса, МБ.
    ///
    /// В расширении это важнее, чем в приложении: система отводит туннелю
    /// жёсткий предел и убивает его при превышении — по этому числу видно,
    /// насколько близко край.
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
}
