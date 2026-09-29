package mihomocore

import (
	"fmt"
	"os"
	"time"

	"github.com/metacubex/bbolt"
	"github.com/metacubex/mihomo/component/profile/cachefile"
	C "github.com/metacubex/mihomo/constant"
)

// Mihomo Cache() has sync.Once and Close() does NOT reopen/reset it. The adapter
// owns the exposed DB handle for its process-global singleton, opens after parse,
// closes only after provider operations finish, and explicitly reopens on restart.
// No linkname, cache-module mutation or deliberately failing filesystem path.
var ownedCache *cachefile.CacheFile

func openSessionCache() error {
	path := C.Path.Cache()
	// Preflight protects corrupt user cache: upstream initCache would delete it.
	db, err := bbolt.Open(path, 0600, &bbolt.Options{Timeout: time.Second, NoStatistics: true})
	if err != nil {
		return fmt.Errorf("open app cache: %w", err)
	}
	if ownedCache == nil {
		if err = db.Close(); err != nil {
			return err
		}
		ownedCache = cachefile.Cache()
		if ownedCache.DB == nil {
			return fmt.Errorf("native app cache initialization failed")
		}
		if ownedCache.DB.Path() != path {
			return fmt.Errorf("Mihomo cache was initialized by a different owner")
		}
	} else {
		if ownedCache.DB != nil {
			_ = db.Close()
			return fmt.Errorf("Mihomo cache already owned by another live session")
		}
		ownedCache.DB = db
	}
	_ = os.Chmod(path, 0600)
	return nil
}
func closeSessionCache() {
	if ownedCache != nil && ownedCache.DB != nil {
		_ = ownedCache.DB.Close()
		ownedCache.DB = nil
	}
}
