package mihomocore

import (
	"github.com/metacubex/mihomo/common/utils"
	"github.com/metacubex/mihomo/component/sniffer"
	"github.com/metacubex/mihomo/config"
	C "github.com/metacubex/mihomo/constant"
	S "github.com/metacubex/mihomo/constant/sniffer"
	RC "github.com/metacubex/mihomo/rules/common"
	"github.com/metacubex/mihomo/tunnel"
)

// Small local list: no network download, geosite database or large rule provider.
var adDomains = []string{
	"doubleclick.net", "googlesyndication.com", "googleadservices.com",
	"googleads.g.doubleclick.net", "adservice.google.com", "ads.yahoo.com",
	"advertising.com", "adsrvr.org", "adnxs.com", "adform.net", "adroll.com",
	"taboola.com", "outbrain.com", "criteo.com", "criteo.net",
	"scorecardresearch.com", "quantserve.com", "ads.facebook.com",
	"app-measurement.com", "amazon-adsystem.com",
}

func applyAdBlocking(cfg *config.Config, enabled bool) error {
	if !enabled {
		return nil
	}
	if cfg == nil || cfg.General == nil || cfg.General.Mode != tunnel.Rule {
		return problem("AD_BLOCKING_REQUIRES_RULE_MODE", "mode", "ad blocking requires rule mode; source and mode have not been changed")
	}
	rules := make([]C.Rule, 0, len(adDomains)+len(cfg.Rules))
	for _, domain := range adDomains {
		rules = append(rules, RC.NewDomainSuffix(domain, "REJECT"))
	}
	cfg.Rules = append(rules, cfg.Rules...)
	// TUN packets may contain only IP destinations. Enable domain visibility
	// in the effective config, without replacing stored YAML, DNS or explicit
	// sniff exclusions. Never override the actual dial destination.
	if cfg.Sniffer == nil {
		cfg.Sniffer = &sniffer.Config{}
	}
	cfg.Sniffer.Enable = true
	cfg.Sniffer.ParsePureIp = true
	if cfg.Sniffer.Sniffers == nil {
		cfg.Sniffer.Sniffers = make(map[S.Type]sniffer.SnifferConfig)
	}
	for _, protocol := range []S.Type{S.HTTP, S.TLS, S.QUIC} {
		if _, exists := cfg.Sniffer.Sniffers[protocol]; !exists {
			ports := []string{"443"}
			if protocol == S.HTTP {
				ports = []string{"80", "8080"}
			}
			ranges, err := utils.NewUnsignedRangesFromList[uint16](ports)
			if err != nil {
				return err
			}
			cfg.Sniffer.Sniffers[protocol] = sniffer.SnifferConfig{Ports: ranges, OverrideDest: false}
		}
	}
	return nil
}
