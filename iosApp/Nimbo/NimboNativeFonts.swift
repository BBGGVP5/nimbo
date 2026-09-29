import CoreText
import SwiftUI

/// The same licensed full TTFs used by shared Compose and the approved preview.
enum NimboNativeFonts {
    static func register() {
        for name in ["golos_regular", "golos_medium", "golos_semibold", "golos_bold",
                     "manrope_regular", "manrope_medium", "manrope_semibold", "manrope_bold", "manrope_extrabold"] {
            if let url = Bundle.main.url(forResource: name, withExtension: "ttf")
                ?? Bundle.main.url(forResource: name, withExtension: "ttf", subdirectory: "Fonts") {
                CTFontManagerRegisterFontsForURL(url as CFURL, .process, nil)
            }
        }
    }

    static func face(relativeTo style: Font.TextStyle, weight: Font.Weight) -> String {
        let headings: [Font.TextStyle] = [.largeTitle, .title, .title2, .title3]
        let heading = headings.contains(style)
        let family = heading ? "Manrope" : "GolosText"
        let suffix: String
        switch weight {
        case .black, .heavy: suffix = heading ? "ExtraBold" : "Bold"
        case .bold: suffix = "Bold"
        case .semibold: suffix = "SemiBold"
        case .medium: suffix = "Medium"
        default: suffix = "Regular"
        }
        return "\(family)-\(suffix)"
    }
}
