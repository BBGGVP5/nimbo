//! Session-local, coalescing notification to reveal the existing UI. The
//! singleton mutex remains the only authority for starting an application.
use std::io;
use windows_sys::Win32::Foundation::{CloseHandle, HANDLE, WAIT_OBJECT_0, WAIT_TIMEOUT};
use windows_sys::Win32::System::Threading::{
    CreateEventExW, SetEvent, WaitForSingleObject, EVENT_MODIFY_STATE, SYNCHRONIZATION_SYNCHRONIZE,
};

pub(crate) struct WindowActivation(HANDLE);

// SAFETY: Windows event handles support concurrent SetEvent/wait operations.
// The owned handle is closed only when this value is dropped.
unsafe impl Send for WindowActivation {}
unsafe impl Sync for WindowActivation {}

impl WindowActivation {
    pub(crate) fn open(name: &str) -> io::Result<Self> {
        let name: Vec<u16> = name.encode_utf16().chain(std::iter::once(0)).collect();
        // Flags=0: auto-reset, initially unsignaled. Opening an existing event
        // preserves its state, including a request received during startup.
        // Request only signaling/waiting rights, not EVENT_ALL_ACCESS.
        let handle = unsafe {
            CreateEventExW(
                std::ptr::null(),
                name.as_ptr(),
                0,
                EVENT_MODIFY_STATE | SYNCHRONIZATION_SYNCHRONIZE,
            )
        };
        if handle.is_null() {
            Err(io::Error::last_os_error())
        } else {
            Ok(Self(handle))
        }
    }

    pub(crate) fn request(&self) -> io::Result<()> {
        if unsafe { SetEvent(self.0) } == 0 {
            Err(io::Error::last_os_error())
        } else {
            Ok(())
        }
    }

    pub(crate) fn take_request(&self) -> io::Result<bool> {
        // Never block the existing request loop. A successful wait atomically
        // consumes the signal; no ResetEvent race can discard a later request.
        match unsafe { WaitForSingleObject(self.0, 0) } {
            WAIT_OBJECT_0 => Ok(true),
            WAIT_TIMEOUT => Ok(false),
            _ => Err(io::Error::last_os_error()),
        }
    }
}

impl Drop for WindowActivation {
    fn drop(&mut self) {
        unsafe {
            let _ = CloseHandle(self.0);
        }
    }
}
