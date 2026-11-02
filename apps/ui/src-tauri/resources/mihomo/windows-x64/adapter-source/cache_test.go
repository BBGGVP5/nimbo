package mihomocore

import (
	"os"
	"path/filepath"
	"testing"
	"time"

	"github.com/metacubex/bbolt"
	C "github.com/metacubex/mihomo/constant"
)

func TestCacheLifetimeAcrossDifferentDataDirectories(t *testing.T) {
	stopTest(t)
	defer stopTest(t)
	first, second := t.TempDir(), t.TempDir()
	for _, dir := range []string{first, second, first} {
		fields := startFields(t, simpleConfig)
		fields["options"].(map[string]any)["dataDir"] = dir
		requireOK(t, call(t, "start", fields))
		if ownedCache == nil || ownedCache.DB == nil || filepath.Clean(ownedCache.DB.Path()) != filepath.Join(dir, "cache.db") {
			t.Fatal("cache did not follow dataDir")
		}
		// The application selection is intentionally session-only for this revision.
		requireOK(t, call(t, "select", map[string]any{"group": "Choice", "name": "REJECT"}))
		requireOK(t, call(t, "stop", nil))
		if ownedCache.DB != nil {
			t.Fatal("stopped session retains singleton DB")
		}
		db, err := bbolt.Open(filepath.Join(dir, "cache.db"), 0600, &bbolt.Options{Timeout: 100 * time.Millisecond})
		if err != nil {
			t.Fatalf("cache still locked after stop: %v", err)
		}
		if err = db.Close(); err != nil {
			t.Fatal(err)
		}
		// Explicit file rename proves there is no live Windows mapping/handle, rather
		// than hiding a teardown error by keeping a process-lifetime fixture directory.
		path := filepath.Join(dir, "cache.db")
		if err = os.Rename(path, path+".closed"); err != nil {
			t.Fatal(err)
		}
		if err = os.Rename(path+".closed", path); err != nil {
			t.Fatal(err)
		}
	}
}
func TestValidateAndInspectDoNotOpenCacheOrWriteData(t *testing.T) {
	stopTest(t)
	dir := t.TempDir()
	old := C.Path.HomeDir()
	C.SetHomeDir(dir)
	defer C.SetHomeDir(old)
	source := simpleConfig + "proxy-providers: {Remote: {type: http, url: 'https://example.invalid/never-fetched.yaml'}}\n"
	requireOK(t, call(t, "inspect", map[string]any{"yaml": source}))
	requireOK(t, call(t, "validate", map[string]any{"yaml": source}))
	entries, err := os.ReadDir(dir)
	if err != nil || len(entries) != 0 {
		t.Fatalf("validation performed disk writes: %v %v", entries, err)
	}
	if ownedCache != nil && ownedCache.DB != nil {
		t.Fatal("validation opened cache")
	}
}
