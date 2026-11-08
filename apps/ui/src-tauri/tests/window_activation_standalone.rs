//! Offline Windows gate: rustc --edition=2021 --test this-file.rs -o <scratch>/activation.exe
//! These ABI declarations replace only the windows-sys import for this standalone
//! build. The production module below runs against real kernel32 event objects.
#![cfg(windows)]

extern crate self as windows_sys;

#[allow(non_snake_case, non_camel_case_types)]
pub mod Win32 {
    pub mod Foundation {
        pub type HANDLE = *mut std::ffi::c_void;
        pub const WAIT_OBJECT_0: u32 = 0;
        pub const WAIT_TIMEOUT: u32 = 258;
        #[link(name = "kernel32")]
        extern "system" {
            pub fn CloseHandle(handle: HANDLE) -> i32;
        }
    }
    pub mod System {
        pub mod Threading {
            use super::super::Foundation::HANDLE;
            pub const EVENT_MODIFY_STATE: u32 = 2;
            pub const SYNCHRONIZATION_SYNCHRONIZE: u32 = 0x0010_0000;
            #[link(name = "kernel32")]
            extern "system" {
                pub fn CreateEventExW(
                    attributes: *const std::ffi::c_void,
                    name: *const u16,
                    flags: u32,
                    access: u32,
                ) -> HANDLE;
                pub fn SetEvent(handle: HANDLE) -> i32;
                pub fn WaitForSingleObject(handle: HANDLE, timeout: u32) -> u32;
            }
        }
    }
}

#[path = "../src/window_activation.rs"]
mod window_activation;
use window_activation::WindowActivation;

fn unique_name() -> String {
    static NEXT: std::sync::atomic::AtomicU64 = std::sync::atomic::AtomicU64::new(0);
    format!(
        "Local\\Nimbo.Activation.Test.{}.{}.{}",
        std::process::id(),
        std::time::SystemTime::now()
            .duration_since(std::time::UNIX_EPOCH)
            .unwrap()
            .as_nanos(),
        NEXT.fetch_add(1, std::sync::atomic::Ordering::Relaxed)
    )
}

#[test]
fn duplicate_request_survives_sender_exit_and_is_consumed_once() {
    let name = unique_name();
    let owner = WindowActivation::open(&name).unwrap();
    assert!(!owner.take_request().unwrap());
    {
        let duplicate = WindowActivation::open(&name).unwrap();
        duplicate.request().unwrap();
    }
    assert!(owner.take_request().unwrap());
    assert!(!owner.take_request().unwrap());
}

#[test]
fn requests_coalesce_but_a_later_request_still_reveals() {
    let name = unique_name();
    let owner = WindowActivation::open(&name).unwrap();
    let duplicate = WindowActivation::open(&name).unwrap();
    duplicate.request().unwrap();
    duplicate.request().unwrap();
    assert!(owner.take_request().unwrap());
    assert!(!owner.take_request().unwrap());
    duplicate.request().unwrap();
    assert!(owner.take_request().unwrap());
}

#[test]
fn a_fresh_owner_does_not_receive_a_stale_request() {
    let name = unique_name();
    {
        let previous = WindowActivation::open(&name).unwrap();
        previous.request().unwrap();
    }
    let next = WindowActivation::open(&name).unwrap();
    assert!(!next.take_request().unwrap());
}

#[test]
fn separate_names_do_not_wake_each_other() {
    let first = WindowActivation::open(&unique_name()).unwrap();
    let second = WindowActivation::open(&unique_name()).unwrap();
    first.request().unwrap();
    assert!(!second.take_request().unwrap());
    assert!(first.take_request().unwrap());
}

#[test]
fn notification_crosses_a_process_boundary_before_owner_polls() {
    let name = unique_name();
    let owner = WindowActivation::open(&name).unwrap();
    let child = std::process::Command::new(std::env::current_exe().unwrap())
        .args(["--exact", "child_sender", "--nocapture"])
        .env("NIMBO_ACTIVATION_TEST_EVENT", &name)
        .status()
        .unwrap();
    assert!(child.success());
    assert!(owner.take_request().unwrap());
    assert!(!owner.take_request().unwrap());
}

#[test]
fn child_sender() {
    if let Ok(name) = std::env::var("NIMBO_ACTIVATION_TEST_EVENT") {
        assert!(name.starts_with("Local\\Nimbo.Activation.Test."));
        WindowActivation::open(&name).unwrap().request().unwrap();
    }
}
