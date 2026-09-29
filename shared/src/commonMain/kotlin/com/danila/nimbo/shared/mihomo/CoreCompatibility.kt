package com.danila.nimbo.shared.mihomo

import kotlinx.serialization.Serializable

@Serializable
enum class NimboCore { XRAY, AWG, MIHOMO }

/** Native document formats only. A share-link protocol is not evidence of core compatibility. */
@Serializable
enum class NativeConfigurationFormat { XRAY_JSON, AMNEZIAWG_INI, MIHOMO_YAML }

enum class CoreCompatibilityDecision { INCOMPATIBLE, REQUIRES_NATIVE_VALIDATION }

object CoreCompatibility {
    fun requiredCore(format: NativeConfigurationFormat): NimboCore = when (format) {
        NativeConfigurationFormat.XRAY_JSON -> NimboCore.XRAY
        NativeConfigurationFormat.AMNEZIAWG_INI -> NimboCore.AWG
        NativeConfigurationFormat.MIHOMO_YAML -> NimboCore.MIHOMO
    }

    fun assess(format: NativeConfigurationFormat, core: NimboCore): CoreCompatibilityDecision =
        if (requiredCore(format) == core) CoreCompatibilityDecision.REQUIRES_NATIVE_VALIDATION
        else CoreCompatibilityDecision.INCOMPATIBLE
}

enum class MihomoPreflightDecision { REJECTED, REQUIRES_NATIVE_VALIDATION }

data class MihomoPreflightResult(
    /** Always the unchanged input, including on rejection. Never a partial/filtered config. */
    val document: MihomoDocument,
    val issues: List<MihomoIssue>,
) {
    val decision: MihomoPreflightDecision
        get() = if (issues.isEmpty()) MihomoPreflightDecision.REQUIRES_NATIVE_VALIDATION else MihomoPreflightDecision.REJECTED
}

object MihomoPreflight {
    fun inspect(document: MihomoDocument, selectedCore: NimboCore): MihomoPreflightResult {
        val issues = mutableListOf<MihomoIssue>()
        if (document.originalText.isBlank()) issues += MihomoIssue(MihomoIssueCode.EMPTY_DOCUMENT, "$")
        if (CoreCompatibility.assess(NativeConfigurationFormat.MIHOMO_YAML, selectedCore) == CoreCompatibilityDecision.INCOMPATIBLE) {
            issues += MihomoIssue(MihomoIssueCode.INCOMPATIBLE_CORE, "core")
        }
        document.graph?.let { issues += MihomoGraphValidator.validate(it) }
        document.unsupportedFeatures.forEach { feature ->
            issues += MihomoIssue(MihomoIssueCode.UNSUPPORTED_FEATURE, feature.path)
        }
        return MihomoPreflightResult(document, issues.toList())
    }
}
