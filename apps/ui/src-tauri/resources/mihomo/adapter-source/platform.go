package mihomocore

import (
	"encoding/json"
	"errors"
	"sync"
	"syscall"

	"github.com/metacubex/mihomo/component/dialer"
)

var protectorLock sync.RWMutex
var socketProtector SocketProtector

func init() {
	// Install once, before any runtime goroutine. Do not race updates to Mihomo's
	// unsynchronized global hook when restarting. This is NOT a TUN readiness proof.
	dialer.DefaultSocketHook = func(network, address string, conn syscall.RawConn) (err error) {
		defer func() {
			if recover() != nil {
				err = errors.New("platform socket protection callback failed")
			}
		}()
		singleton.mu.Lock()
		allowed := (singleton.state == "starting" || singleton.state == "running") && singleton.session != nil && singleton.session.ctx.Err() == nil
		singleton.mu.Unlock()
		if !allowed {
			return errors.New("Nimbo runtime is stopped")
		}
		protectorLock.RLock()
		p := socketProtector
		protectorLock.RUnlock()
		if p == nil {
			return nil
		}
		protected := false
		if err := conn.Control(func(fd uintptr) { protected = p.Protect(int64(fd)) }); err != nil {
			return err
		}
		if !protected {
			return errors.New("platform socket protection failed")
		}
		return nil
	}
}

// SetSocketProtector must be called while stopped, in the same Go runtime as the
// other native engines. nil is permitted only for desktop-proxy mode. Calls into
// the protector must not reenter Invoke (socket callbacks can occur during start).
func SetSocketProtector(p SocketProtector) string {
	singleton.op.Lock()
	defer singleton.op.Unlock()
	singleton.mu.Lock()
	defer singleton.mu.Unlock()
	r := response{APIVersion: 1, Generation: singleton.generation}
	if singleton.state != "stopped" {
		r.Error = problem("BUSY", "", "stop before changing socket protection")
	} else {
		protectorLock.Lock()
		socketProtector = p
		protectorLock.Unlock()
		r.Success = true
		r.Data = map[string]any{"installed": p != nil}
	}
	b, _ := json.Marshal(r)
	return string(b)
}
