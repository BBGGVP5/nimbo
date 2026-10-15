//go:build !with_gvisor

package mihomocore

const packetFlowCompiled = false

func startPacketRuntime(s *session) (ownedPacketFlow, error) {
	return nil, problem("PLATFORM_UNAVAILABLE", "", "packet-flow gVisor not compiled")
}
