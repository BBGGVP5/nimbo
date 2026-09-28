package main

/*
#include <stdint.h>
#include <stdlib.h>
#include <string.h>

// Caller-owned context must remain alive until stopped + successful unregister.
// Callback must return exactly 1 on success and must not reenter native commands.
typedef int (*NimboMihomoSocketProtectorV1)(int64_t fd, void *context);
static inline int nimboMihomoProtectV1(NimboMihomoSocketProtectorV1 callback, int64_t fd, void *context) {
    return callback ? callback(fd, context) : 0;
}
*/
import "C"

import "unsafe"

// All non-NULL results are malloc-owned strings released once by CGoFree or
// NimboMihomoFreeV1. Existing API3/AWG/diagnostic symbols and ABI are unchanged.
func mihomoCInput(input *C.char) string {
	if input == nil || C.strnlen(input, mihomoMaxRequest+1) > mihomoMaxRequest {
		return ""
	}
	return C.GoString(input)
}

//export NimboMihomoInvokeV1
func NimboMihomoInvokeV1(input *C.char) *C.char {
	return C.CString(mihomoInvokeJSON(mihomoCInput(input)))
}

//export NimboMihomoCancelV1
func NimboMihomoCancelV1(input *C.char) *C.char {
	return C.CString(mihomoCancelJSON(mihomoCInput(input)))
}

//export NimboMihomoStartIOSV1
func NimboMihomoStartIOSV1(input *C.char, borrowedFD C.int64_t) *C.char {
	return C.CString(mihomoStartIOSJSON(mihomoCInput(input), int64(borrowedFD)))
}

//export NimboMihomoSetSocketProtectorV1
func NimboMihomoSetSocketProtectorV1(callback C.NimboMihomoSocketProtectorV1, context unsafe.Pointer) *C.char {
	if callback == nil {
		return C.CString(mihomoSetSocketProtector(nil))
	}
	return C.CString(mihomoSetSocketProtector(func(fd int64) bool {
		return C.nimboMihomoProtectV1(callback, C.int64_t(fd), context) == 1
	}))
}

//export NimboMihomoFreeV1
func NimboMihomoFreeV1(value *C.char) {
	CGoFree(value)
}
