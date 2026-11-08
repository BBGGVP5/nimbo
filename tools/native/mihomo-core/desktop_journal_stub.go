//go:build !linux || android

package mihomocore

import (
	LC "github.com/metacubex/mihomo/listener/config"
	"io"
)

func configureDesktopRuleJournal(o LC.Tun, _ io.Closer) LC.Tun { return o }
func RecoverDesktopTun() error {
	return problem("PLATFORM_UNAVAILABLE", "", "native rule recovery is Linux-only")
}
