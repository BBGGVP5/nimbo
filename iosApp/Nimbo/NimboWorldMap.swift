import CoreGraphics
import Foundation

/// Точка на карте в градусах.
struct NimboGeoPoint: Equatable, Sendable {
    var lat: Double
    var lon: Double
}

/// Общая карта Нимбо: сетка суши 1° и координаты стран/часовых поясов.
///
/// Источник — `assets/geo/nimbo-world-map.json` (формат описан в
/// `assets/geo/README.md`), тот же файл, что у Android и десктопа.
/// Файл маленький (~17 КБ), разбирается один раз при первом обращении.
struct NimboWorldMap: Sendable {
    static let shared: NimboWorldMap? = NimboWorldMap(bundle: .main)

    /// Москва — последний вариант «вы здесь», если ни пояс, ни регион не помогли.
    static let fallbackHere = NimboGeoPoint(lat: 55.7558, lon: 37.6173)

    let step: Double
    /// Центры клеток суши.
    let land: [NimboGeoPoint]
    /// ISO 3166-1 alpha-2 → город дата-центра страны.
    let countries: [String: NimboGeoPoint]
    /// IANA-зона → точка «вы здесь».
    let timezones: [String: NimboGeoPoint]

    init?(bundle: Bundle) {
        guard let url = bundle.url(forResource: "nimbo-world-map", withExtension: "json"),
              let data = try? Data(contentsOf: url) else { return nil }
        self.init(data: data)
    }

    init?(data: Data) {
        guard let root = (try? JSONSerialization.jsonObject(with: data)) as? [String: Any],
              let grid = root["grid"] as? [String: Any],
              let rows = root["land"] as? [String] else { return nil }
        let step = (grid["step"] as? NSNumber)?.doubleValue ?? 1
        let lonWest = (grid["lonWest"] as? NSNumber)?.doubleValue ?? -180
        let latNorth = (grid["latNorth"] as? NSNumber)?.doubleValue ?? 80
        let cols = (grid["cols"] as? NSNumber)?.intValue ?? 360
        guard step > 0, cols > 0 else { return nil }

        var land: [NimboGeoPoint] = []
        land.reserveCapacity(12_000)
        for (row, hex) in rows.enumerated() {
            let lat = latNorth - (Double(row) + 0.5) * step
            for (index, character) in hex.enumerated() {
                guard let nibble = character.hexDigitValue, nibble != 0 else { continue }
                for bit in 0..<4 where nibble & (8 >> bit) != 0 {
                    let col = index * 4 + bit
                    guard col < cols else { continue }
                    land.append(NimboGeoPoint(lat: lat, lon: lonWest + (Double(col) + 0.5) * step))
                }
            }
        }
        self.step = step
        self.land = land
        self.countries = Self.points(root["countries"])
        self.timezones = Self.points(root["timezones"])
    }

    private static func points(_ value: Any?) -> [String: NimboGeoPoint] {
        guard let table = value as? [String: Any] else { return [:] }
        var result: [String: NimboGeoPoint] = [:]
        for (key, raw) in table {
            guard let pair = raw as? [NSNumber], pair.count >= 2 else { continue }
            result[key] = NimboGeoPoint(lat: pair[0].doubleValue, lon: pair[1].doubleValue)
        }
        return result
    }

    /// «Вы здесь»: часовой пояс устройства → страна региона → Москва. Сеть не нужна.
    func here(timeZone: TimeZone = .current, locale: Locale = .current) -> NimboGeoPoint {
        if let point = timezones[timeZone.identifier] { return point }
        if let region = locale.region?.identifier.uppercased(), let point = countries[region] { return point }
        return Self.fallbackHere
    }
}

/// Страна сервера по его имени и описанию из подписки.
///
/// Порядок: флаг-эмодзи → название страны или крупного города (рус./англ.) →
/// отдельный двухбуквенный код («DE», «[NL]»). Ничего не нашлось — `nil`.
enum NimboServerCountry {
    static func code(name: String, description: String = "") -> String? {
        if let flag = flagCode(in: name) ?? flagCode(in: description) { return flag }
        let text = (name + " " + description)
        let lowered = text.lowercased()
        let words = Set(lowered.split(whereSeparator: { !$0.isLetter }).map(String.init))
        for entry in needles {
            if entry.wholeWord ? words.contains(entry.needle) : lowered.contains(entry.needle) {
                return entry.code
            }
        }
        let tokens = text.split(whereSeparator: { !$0.isLetter }).map(String.init)
        for token in tokens where token.count == 2 && token == token.uppercased() {
            let code = token == "UK" ? "GB" : token
            if NimboWorldMap.shared?.countries[code] != nil { return code }
        }
        return nil
    }

    /// Флаг-эмодзи: пара региональных индикаторов U+1F1E6…U+1F1FF.
    static func flagCode(in text: String) -> String? {
        var letters: [Character] = []
        for scalar in text.unicodeScalars {
            if (0x1F1E6...0x1F1FF).contains(scalar.value),
               let letter = Unicode.Scalar(scalar.value - 0x1F1E6 + 65) {
                letters.append(Character(letter))
                if letters.count == 2 { return String(letters) }
            } else {
                letters.removeAll()
            }
        }
        return nil
    }

    /// Флаг-эмодзи страны по коду.
    static func flag(for code: String?) -> String {
        guard let code, code.count == 2 else { return "🌐" }
        var result = ""
        for scalar in code.uppercased().unicodeScalars {
            guard (65...90).contains(scalar.value),
                  let indicator = Unicode.Scalar(0x1F1E6 + scalar.value - 65) else { return "🌐" }
            result.unicodeScalars.append(indicator)
        }
        return result
    }

    /// Название страны по-русски.
    static func displayName(for code: String?) -> String? {
        guard let code else { return nil }
        return Locale(identifier: "ru_RU").localizedString(forRegionCode: code)
    }

    private struct Needle {
        let needle: String
        let code: String
        let wholeWord: Bool
    }

    /// Названия стран из Locale (без ручного словаря на 96 стран), плюс
    /// разговорные варианты и города дата-центров. Длинные — первыми, чтобы
    /// «Южная Корея» не перехватывалась чем-то короче.
    private static let needles: [Needle] = {
        var result: [Needle] = []
        let codes = NimboWorldMap.shared.map { Array($0.countries.keys) } ?? []
        let russian = Locale(identifier: "ru_RU")
        let english = Locale(identifier: "en_US")
        for code in codes {
            if let name = russian.localizedString(forRegionCode: code)?.lowercased() {
                result.append(Needle(needle: russianStem(name), code: code, wholeWord: false))
            }
            if let name = english.localizedString(forRegionCode: code)?.lowercased(), name.count > 3 {
                result.append(Needle(needle: name, code: code, wholeWord: false))
            }
        }
        let aliases: [(String, String)] = [
            ("сша", "US"), ("usa", "US"), ("америк", "US"), ("united states", "US"),
            ("нью-йорк", "US"), ("new york", "US"), ("los angeles", "US"), ("лос-анджелес", "US"),
            ("чикаго", "US"), ("chicago", "US"), ("майами", "US"), ("miami", "US"), ("dallas", "US"),
            ("великобритан", "GB"), ("британ", "GB"), ("англи", "GB"), ("britain", "GB"), ("england", "GB"),
            ("лондон", "GB"), ("london", "GB"),
            ("голланди", "NL"), ("holland", "NL"), ("амстердам", "NL"), ("amsterdam", "NL"),
            ("франкфурт", "DE"), ("frankfurt", "DE"), ("берлин", "DE"), ("berlin", "DE"), ("мюнхен", "DE"),
            ("munich", "DE"), ("nuremberg", "DE"), ("нюрнберг", "DE"),
            ("москв", "RU"), ("moscow", "RU"), ("петербург", "RU"), ("petersburg", "RU"), ("russia", "RU"),
            ("хельсинки", "FI"), ("helsinki", "FI"), ("стокгольм", "SE"), ("stockholm", "SE"),
            ("париж", "FR"), ("paris", "FR"), ("варшав", "PL"), ("warsaw", "PL"),
            ("рига", "LV"), ("riga", "LV"), ("таллин", "EE"), ("tallinn", "EE"), ("вильнюс", "LT"), ("vilnius", "LT"),
            ("стамбул", "TR"), ("istanbul", "TR"), ("турци", "TR"), ("turkey", "TR"), ("türkiye", "TR"),
            ("алмат", "KZ"), ("almaty", "KZ"), ("астан", "KZ"), ("astana", "KZ"),
            ("токио", "JP"), ("tokyo", "JP"), ("сеул", "KR"), ("seoul", "KR"), ("south korea", "KR"), ("корея", "KR"),
            ("дубай", "AE"), ("dubai", "AE"), ("эмират", "AE"), ("emirates", "AE"),
            ("гонконг", "HK"), ("hong kong", "HK"), ("сингапур", "SG"), ("singapore", "SG"),
            ("чехи", "CZ"), ("czech", "CZ"), ("прага", "CZ"), ("prague", "CZ"),
            ("вена", "AT"), ("vienna", "AT"), ("цюрих", "CH"), ("zurich", "CH"), ("швейцар", "CH"),
            ("мадрид", "ES"), ("madrid", "ES"), ("милан", "IT"), ("milan", "IT"),
            ("торонто", "CA"), ("toronto", "CA"), ("тбилиси", "GE"), ("tbilisi", "GE"),
            ("ереван", "AM"), ("yerevan", "AM"), ("кишин", "MD"), ("chisinau", "MD")
        ]
        for (needle, code) in aliases { result.append(Needle(needle: needle, code: code, wholeWord: false)) }
        for (word, code) in [("uk", "GB"), ("uae", "AE"), ("оаэ", "AE"), ("рф", "RU"), ("us", "US")] {
            result.append(Needle(needle: word, code: code, wholeWord: true))
        }
        return result.sorted { $0.needle.count > $1.needle.count }
    }()

    /// «Германия» → «германи»: так совпадают и «Германии», и «Германией».
    private static func russianStem(_ name: String) -> String {
        guard name.count > 5, let last = name.last, "аяиыйоеь".contains(last) else { return name }
        return String(name.dropLast())
    }
}

/// Окно карты: прямоугольник в градусах, подогнанный под пропорции холста.
struct NimboMapViewport: Equatable {
    var minLon: Double
    var maxLon: Double
    var minLat: Double
    var maxLat: Double

    /// Рамка вокруг точек с отступом; ширина по долготе поправлена на cos(средней широты).
    static func fitting(_ points: [NimboGeoPoint], aspect: Double) -> NimboMapViewport {
        let source = points.isEmpty ? [NimboWorldMap.fallbackHere] : points
        var minLon = source.map(\.lon).min() ?? 0
        var maxLon = source.map(\.lon).max() ?? 0
        var minLat = source.map(\.lat).min() ?? 0
        var maxLat = source.map(\.lat).max() ?? 0

        // Отступ, чтобы флаги и дуга не прилипали к краю, и минимальный размер окна.
        let padLon = max((maxLon - minLon) * 0.22, 9)
        let padLat = max((maxLat - minLat) * 0.30, 6)
        minLon -= padLon; maxLon += padLon
        minLat -= padLat; maxLat += padLat

        let midLat = (minLat + maxLat) / 2
        let cosine = max(cos(midLat * .pi / 180), 0.2)
        var lonSpan = maxLon - minLon
        var latSpan = maxLat - minLat
        let safeAspect = max(aspect, 0.2)
        if lonSpan * cosine / latSpan < safeAspect {
            lonSpan = safeAspect * latSpan / cosine
        } else {
            latSpan = lonSpan * cosine / safeAspect
        }
        lonSpan = min(lonSpan, 360)
        latSpan = min(latSpan, 136)

        let centerLon = (minLon + maxLon) / 2
        var centerLat = midLat
        // Не уезжаем за пределы сетки (80° с.ш. … 56° ю.ш.), иначе сверху пустота.
        centerLat = min(max(centerLat, -56 + latSpan / 2), 80 - latSpan / 2)
        return NimboMapViewport(minLon: centerLon - lonSpan / 2, maxLon: centerLon + lonSpan / 2,
                                minLat: centerLat - latSpan / 2, maxLat: centerLat + latSpan / 2)
    }

    var lonSpan: Double { maxLon - minLon }
    var latSpan: Double { maxLat - minLat }

    func project(_ point: NimboGeoPoint, in size: CGSize) -> CGPoint {
        CGPoint(x: (point.lon - minLon) / lonSpan * size.width,
                y: (maxLat - point.lat) / latSpan * size.height)
    }

    func contains(_ point: NimboGeoPoint, margin: Double) -> Bool {
        point.lon >= minLon - margin && point.lon <= maxLon + margin &&
            point.lat >= minLat - margin && point.lat <= maxLat + margin
    }
}
