package main

/*
#cgo darwin LDFLAGS: -framework CoreFoundation
#cgo ios LDFLAGS: -framework CoreFoundation
#include <stdlib.h>
#include <string.h>
#ifdef __APPLE__
#include <CoreFoundation/CoreFoundation.h>
static int nimboDiagnosticInExtension(void) {
    CFBundleRef bundle = CFBundleGetMainBundle();
    if (!bundle) return 1;
    CFURLRef url = CFBundleCopyBundleURL(bundle);
    if (!url) return 1;
    CFStringRef extension = CFURLCopyPathExtension(url);
    int result = extension && CFStringCompare(extension, CFSTR("appex"), kCFCompareCaseInsensitive) == kCFCompareEqualTo;
    if (extension) CFRelease(extension);
    CFRelease(url);
    return result;
}
#else
static int nimboDiagnosticInExtension(void) { return 0; }
#endif
*/
import "C"

// App-process only. Every non-NULL result is released with upstream CGoFree.
// Native serialization lasts until all resources are closed, including cancel.
//
//export NimboDiagnosticRun
func NimboDiagnosticRun(requestJSON *C.char) *C.char {
	if requestJSON == nil || C.strnlen(requestJSON, diagnosticMaxRequest+1) > diagnosticMaxRequest {
		return diagnosticCString(diagnosticResult{Latency: -1, Error: "DIAGNOSTIC_REQUEST"})
	}
	return diagnosticCString(runDiagnosticJSON(C.GoString(requestJSON), C.nimboDiagnosticInExtension() != 0))
}

//export NimboDiagnosticCancel
func NimboDiagnosticCancel(requestJSON *C.char) *C.char {
	if requestJSON == nil || C.strnlen(requestJSON, 4097) > 4096 {
		return diagnosticCString(diagnosticCancelResult{Error: "DIAGNOSTIC_REQUEST"})
	}
	return diagnosticCString(cancelDiagnosticJSON(C.GoString(requestJSON)))
}

func diagnosticCString(value any) *C.char {
	return C.CString(diagnosticJSON(value))
}
