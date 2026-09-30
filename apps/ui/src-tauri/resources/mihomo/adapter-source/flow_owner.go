package mihomocore

import (
	"encoding/json"
	"errors"
	"github.com/metacubex/mihomo/component/process"
	C "github.com/metacubex/mihomo/constant"
	"net"
	"sync"
)

// Android resolves the owner of the ORIGINAL TUN tuple, not the proxy socket.
// JSON avoids binding unsigned integers and permits explicit lookup failure.
type FlowOwnerResolver interface {
	Resolve(network, source string, sourcePort int64, destination string, destinationPort int64) string
}

var flowOwnerLock sync.RWMutex
var flowOwner FlowOwnerResolver

func resolveFlowOwner(metadata *C.Metadata) (name string, err error) {
	defer func() {
		if recover() != nil {
			name = ""
			err = errors.New("flow owner callback failed")
		}
	}()
	metadata.Uid = ^uint32(0)
	flowOwnerLock.RLock()
	callback := flowOwner
	flowOwnerLock.RUnlock()
	if callback == nil {
		return "", process.ErrNotFound
	}
	source, sourcePort := metadata.SrcIP.String(), int64(metadata.SrcPort)
	destination, destinationPort := metadata.DstIP.String(), int64(metadata.DstPort)
	// Fake-IP handling clears/replaces DstIP before rule evaluation. Android's
	// socket table contains the original destination, retained by sing-tun.
	switch address := metadata.RawDstAddr.(type) {
	case *net.TCPAddr:
		destination, destinationPort = address.IP.String(), int64(address.Port)
	case *net.UDPAddr:
		destination, destinationPort = address.IP.String(), int64(address.Port)
	}
	switch address := metadata.RawSrcAddr.(type) {
	case *net.TCPAddr:
		source, sourcePort = address.IP.String(), int64(address.Port)
	case *net.UDPAddr:
		source, sourcePort = address.IP.String(), int64(address.Port)
	}
	var owner struct {
		UID     *uint32 `json:"uid"`
		Package string  `json:"package"`
	}
	if json.Unmarshal([]byte(callback.Resolve(metadata.NetWork.String(), source, sourcePort, destination, destinationPort)), &owner) != nil || owner.Package == "" || owner.UID == nil {
		return "", process.ErrNotFound
	}
	metadata.Uid = *owner.UID
	return owner.Package, nil
}

func SetFlowOwnerResolver(callback FlowOwnerResolver) string {
	singleton.op.Lock()
	defer singleton.op.Unlock()
	singleton.mu.Lock()
	defer singleton.mu.Unlock()
	r := response{APIVersion: 1, Generation: singleton.generation}
	if singleton.state != "stopped" {
		r.Error = problem("BUSY", "", "stop before changing flow owner")
	} else {
		flowOwnerLock.Lock()
		flowOwner = callback
		flowOwnerLock.Unlock()
		if callback == nil {
			process.DefaultPackageNameResolver = nil
		} else {
			process.DefaultPackageNameResolver = resolveFlowOwner
		}
		r.Success = true
		r.Data = map[string]any{"installed": callback != nil}
	}
	b, _ := json.Marshal(r)
	return string(b)
}
