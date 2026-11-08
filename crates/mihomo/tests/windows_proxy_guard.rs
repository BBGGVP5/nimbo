//! Pure fake-backend tests. Never access the real Windows proxy or networking.
#[path = "support/windows_proxy_guard.rs"]
mod guard;
use guard::{ProxyBackend, ProxyGuard};
use std::{
    cell::{Cell, RefCell},
    panic::{catch_unwind, AssertUnwindSafe},
};

#[derive(Clone, Debug, PartialEq, Eq)]
struct Snapshot {
    address: u16,
    enabled: bool,
}
struct Fake {
    events: RefCell<Vec<&'static str>>,
    current: RefCell<Snapshot>,
    writes: Cell<usize>,
    fail: Cell<bool>,
    panic_restore: Cell<bool>,
    fail_snapshot: Cell<bool>,
    ignore_restore: Cell<bool>,
}
impl Fake {
    fn new() -> Self {
        Self {
            events: RefCell::new(Vec::new()),
            current: RefCell::new(Snapshot {
                address: 42,
                enabled: false,
            }),
            writes: Cell::new(0),
            fail: Cell::new(false),
            panic_restore: Cell::new(false),
            fail_snapshot: Cell::new(false),
            ignore_restore: Cell::new(false),
        }
    }
    fn apply(&self, enabled: bool) {
        self.events.borrow_mut().push("apply");
        *self.current.borrow_mut() = Snapshot {
            address: 1234,
            enabled,
        };
    }
}
impl ProxyBackend for Fake {
    type Snapshot = Snapshot;
    fn snapshot(&self) -> Result<Snapshot, String> {
        if self.fail_snapshot.get() {
            return Err("snapshot failed".into());
        }
        Ok(self.current.borrow().clone())
    }
    fn owns(&self, port: u16) -> Result<bool, String> {
        Ok(self.current.borrow().address == port)
    }
    fn restore(&self, saved: Snapshot) -> Result<(), String> {
        self.events.borrow_mut().push("restore");
        self.writes.set(self.writes.get() + 1);
        assert!(!self.panic_restore.get(), "fake backend panic");
        if self.fail.get() {
            return Err("restore failed".into());
        }
        if !self.ignore_restore.get() {
            *self.current.borrow_mut() = saved;
        }
        Ok(())
    }
}
#[test]
fn normal_restore_is_verified_and_not_repeated_on_drop() {
    let fake = Fake::new();
    {
        let mut guard = ProxyGuard::new(&fake, 1234).unwrap();
        assert_eq!(guard.saved().address, 42);
        fake.apply(true);
        guard.finish().unwrap();
    }
    assert_eq!(fake.current.borrow().address, 42);
    assert_eq!(fake.writes.get(), 1);
}
#[test]
fn unwinding_restores_owned_proxy_and_preserves_primary_panic() {
    let fake = Fake::new();
    let error = catch_unwind(AssertUnwindSafe(|| {
        let _guard = ProxyGuard::new(&fake, 1234).unwrap();
        fake.apply(true);
        panic!("native DNS receive failed");
    }))
    .unwrap_err();
    assert_eq!(
        error.downcast_ref::<&str>(),
        Some(&"native DNS receive failed")
    );
    assert_eq!(fake.current.borrow().address, 42);
}
#[test]
fn partial_apply_before_enable_is_owned_and_restored() {
    let fake = Fake::new();
    {
        let _guard = ProxyGuard::new(&fake, 1234).unwrap();
        fake.apply(false);
    }
    assert_eq!(fake.current.borrow().address, 42);
    assert_eq!(fake.writes.get(), 1);
}
#[test]
fn foreign_owner_is_never_overwritten() {
    let fake = Fake::new();
    {
        let mut guard = ProxyGuard::new(&fake, 1234).unwrap();
        fake.apply(true);
        fake.current.borrow_mut().address = 999;
        assert!(guard.finish().unwrap_err().contains("ownership"));
    }
    assert_eq!(fake.current.borrow().address, 999);
    assert_eq!(fake.writes.get(), 0);
}
#[test]
fn foreign_owner_during_unwind_is_never_overwritten() {
    let fake = Fake::new();
    let _ = catch_unwind(AssertUnwindSafe(|| {
        let _guard = ProxyGuard::new(&fake, 1234).unwrap();
        fake.current.borrow_mut().address = 999;
        panic!("primary");
    }));
    assert_eq!(fake.current.borrow().address, 999);
    assert_eq!(fake.writes.get(), 0);
}
#[test]
fn unreadable_snapshot_never_authorizes_a_restore() {
    let fake = Fake::new();
    {
        let mut guard = ProxyGuard::new(&fake, 1234).unwrap();
        fake.apply(true);
        fake.fail_snapshot.set(true);
        assert_eq!(guard.finish().unwrap_err(), "snapshot failed");
    }
    assert_eq!(fake.writes.get(), 0);
}
#[test]
fn restore_without_snapshot_equality_is_a_failure() {
    let fake = Fake::new();
    {
        let mut guard = ProxyGuard::new(&fake, 1234).unwrap();
        fake.apply(true);
        fake.ignore_restore.set(true);
        assert!(guard.finish().unwrap_err().contains("verification"));
    }
    assert_eq!(fake.writes.get(), 1);
}
#[test]
fn already_restored_state_is_a_noop() {
    let fake = Fake::new();
    {
        let mut guard = ProxyGuard::new(&fake, 1234).unwrap();
        guard.finish().unwrap();
    }
    assert_eq!(fake.writes.get(), 0);
}
#[test]
fn explicit_restore_error_is_fatal_and_not_retried_on_drop() {
    let fake = Fake::new();
    {
        let mut guard = ProxyGuard::new(&fake, 1234).unwrap();
        fake.apply(true);
        fake.fail.set(true);
        assert_eq!(guard.finish().unwrap_err(), "restore failed");
    }
    assert_eq!(fake.writes.get(), 1);
    assert_eq!(fake.current.borrow().address, 1234);
}
#[test]
fn restore_failure_during_unwind_never_double_panics() {
    for panic_backend in [false, true] {
        let fake = Fake::new();
        let error = catch_unwind(AssertUnwindSafe(|| {
            let _guard = ProxyGuard::new(&fake, 1234).unwrap();
            fake.apply(true);
            fake.fail.set(true);
            fake.panic_restore.set(panic_backend);
            panic!("original failure");
        }))
        .unwrap_err();
        assert_eq!(error.downcast_ref::<&str>(), Some(&"original failure"));
        assert_eq!(fake.writes.get(), 1);
    }
}

#[test]
fn panic_after_pending_poll_keeps_external_owner_alive_until_diagnostics() {
    use std::{
        future::Future,
        sync::Arc,
        task::{Context, Poll, Wake, Waker},
    };
    struct Noop;
    impl Wake for Noop {
        fn wake(self: Arc<Self>) {}
    }
    struct Owner<'a>(&'a Cell<bool>);
    impl Drop for Owner<'_> {
        fn drop(&mut self) {
            self.0.set(false);
        }
    }
    let alive = Cell::new(true);
    let owner = Owner(&alive);
    let mut first = true;
    let work = std::future::poll_fn(|cx| -> Poll<()> {
        if first {
            first = false;
            cx.waker().wake_by_ref();
            return Poll::Pending;
        }
        assert!(owner.0.get());
        panic!("DNS receive timeout");
    });
    let mut caught = Box::pin(guard::catch_fixture_panic(work));
    let waker = Waker::from(Arc::new(Noop));
    let mut cx = Context::from_waker(&waker);
    assert!(caught.as_mut().poll(&mut cx).is_pending());
    let Poll::Ready(Err(error)) = caught.as_mut().poll(&mut cx) else {
        panic!("failure was masked");
    };
    drop(caught);
    assert!(
        alive.get(),
        "diagnostics must run before external owner teardown"
    );
    assert_eq!(error.downcast_ref::<&str>(), Some(&"DNS receive timeout"));
    drop(owner);
    assert!(!alive.get());
}

#[test]
fn capture_precedes_proxy_restore_even_when_diagnostics_or_restore_fail() {
    use std::{
        future::Future,
        sync::Arc,
        task::{Context, Poll, Wake, Waker},
    };
    fn complete<F: Future>(future: F) -> F::Output {
        struct Noop;
        impl Wake for Noop {
            fn wake(self: Arc<Self>) {}
        }
        let waker = Waker::from(Arc::new(Noop));
        let mut cx = Context::from_waker(&waker);
        let mut future = Box::pin(future);
        for _ in 0..8 {
            if let Poll::Ready(value) = future.as_mut().poll(&mut cx) {
                return value;
            }
        }
        panic!("pure fixture future did not complete");
    }
    async fn pending_once() {
        let mut first = true;
        std::future::poll_fn(|cx| {
            if first {
                first = false;
                cx.waker().wake_by_ref();
                Poll::Pending
            } else {
                Poll::Ready(())
            }
        })
        .await
    }
    for (fail_restore, panic_restore, panic_diagnostic) in [
        (false, false, false),
        (true, false, false),
        (false, true, false),
        (false, false, true),
        (true, true, true),
    ] {
        let fake = Fake::new();
        fake.fail.set(fail_restore);
        fake.panic_restore.set(panic_restore);
        let primary = catch_unwind(AssertUnwindSafe(|| {
            // Same actual helpers and outer Option ownership as windows_tun.rs.
            let mut proxy_guard = None;
            let outcome = complete(guard::catch_fixture_panic(async {
                proxy_guard = Some(ProxyGuard::new(&fake, 1234).unwrap());
                fake.apply(true);
                pending_once().await;
                panic!("original DNS receive failure");
            }));
            let Err(original) = outcome else {
                panic!("mandatory case became successful");
            };
            let (diagnostic_panicked, cleanup) = complete(guard::capture_before_proxy_cleanup(
                async {
                    assert_eq!(
                        fake.current.borrow().address,
                        1234,
                        "cleanup ran before capture"
                    );
                    assert_eq!(fake.writes.get(), 0);
                    fake.events.borrow_mut().push("capture");
                    pending_once().await;
                    assert_eq!(
                        fake.writes.get(),
                        0,
                        "cleanup ran while capture was pending"
                    );
                    assert!(!panic_diagnostic, "fake diagnostic panic");
                },
                &mut proxy_guard,
            ));
            assert_eq!(diagnostic_panicked, panic_diagnostic);
            assert_eq!(cleanup.is_err(), fail_restore || panic_restore);
            std::panic::resume_unwind(original);
        }))
        .unwrap_err();
        assert_eq!(
            primary.downcast_ref::<&str>(),
            Some(&"original DNS receive failure")
        );
        assert_eq!(*fake.events.borrow(), vec!["apply", "capture", "restore"]);
        assert_eq!(
            fake.writes.get(),
            1,
            "no Drop retry or second cleanup attempt"
        );
    }
}
