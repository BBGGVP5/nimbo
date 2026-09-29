//go:build cgo

// Optional ABI wrapper. Embed this SOURCE into the existing mobile Go archive;
// do not link a second Go runtime beside LibXray. C caller owns returned strings.
package main

/*
#include <stdlib.h>
*/
import "C"
import (
	core "nimbo/mihomocore"
	"unsafe"
)

//export NimboMihomoInvokeV1
func NimboMihomoInvokeV1(input *C.char) *C.char {
	if input == nil {
		return C.CString(core.Invoke(""))
	}
	return C.CString(core.Invoke(C.GoString(input)))
}

//export NimboMihomoFreeV1
func NimboMihomoFreeV1(value *C.char) { C.free(unsafe.Pointer(value)) }

func main() {}
