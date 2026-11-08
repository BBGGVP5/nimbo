// Compatibility for the EXACT Cronet iOS archives verified by check_archive.py.
// See README.md for the upstream source/BUILD.gn mismatch and removal criteria.
#include <TargetConditionals.h>
#if !TARGET_OS_IOS || TARGET_OS_MACCATALYST
#error This compatibility object is only for the pinned iOS Cronet slices
#endif

// No C++ class is redeclared: its ABI symbol is satisfied without an ODR change.
// Cronet uses MessagePumpIOSForIO (CFRunLoop), not MessagePumpKqueue. The missing
// method only sets g_timer_slack, private to the absent kqueue translation unit.
// Thus this initializer has no state to initialize for this build. This does
// NOT replace any I/O, TLS, proxy, timer or certificate-verification operation.
extern "C" void NimboUnusedKqueueFeatureInit()
    __asm__("__ZN4base17MessagePumpKqueue18InitializeFeaturesEv");
extern "C" void NimboUnusedKqueueFeatureInit() {}
