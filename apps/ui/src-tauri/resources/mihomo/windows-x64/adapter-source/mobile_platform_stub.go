//go:build !android || !with_gvisor

package mihomocore

import "io"

import "github.com/metacubex/mihomo/config"

const androidTunCompiled = false

func androidPlatformCheck(fd int64) error {
	if fd <= 0 {
		return problem("INVALID_FD", "borrowedFD", "positive borrowed FD required")
	}
	return problem("PLATFORM_UNAVAILABLE", "borrowedFD", "Android with_gvisor build required")
}
func startAndroidTun(fd int64, _ *config.Config) (io.Closer, error) {
	return nil, androidPlatformCheck(fd)
}
