//! Test-only ownership guard. Backends supply the existing proxy semantics.
use std::{
    io::Write,
    panic::{catch_unwind, AssertUnwindSafe},
};

/// Catch each poll so the caller can inspect an externally owned Session before
/// resuming the original panic. No executor, network or policy is created here.
pub async fn catch_fixture_panic<F: std::future::Future>(
    future: F,
) -> Result<F::Output, Box<dyn std::any::Any + Send>> {
    let mut future = std::pin::pin!(future);
    std::future::poll_fn(
        |cx| match catch_unwind(AssertUnwindSafe(|| future.as_mut().poll(cx))) {
            Ok(std::task::Poll::Ready(value)) => std::task::Poll::Ready(Ok(value)),
            Ok(std::task::Poll::Pending) => std::task::Poll::Pending,
            Err(error) => std::task::Poll::Ready(Err(error)),
        },
    )
    .await
}

pub trait ProxyBackend {
    type Snapshot: Clone + PartialEq;
    fn snapshot(&self) -> Result<Self::Snapshot, String>;
    fn owns(&self, port: u16) -> Result<bool, String>;
    fn restore(&self, saved: Self::Snapshot) -> Result<(), String>;
}

pub struct ProxyGuard<'a, B: ProxyBackend> {
    backend: &'a B,
    saved: B::Snapshot,
    port: u16,
    armed: bool,
}
impl<'a, B: ProxyBackend> ProxyGuard<'a, B> {
    pub fn new(backend: &'a B, port: u16) -> Result<Self, String> {
        Ok(Self {
            backend,
            saved: backend.snapshot()?,
            port,
            armed: true,
        })
    }
    pub fn saved(&self) -> &B::Snapshot {
        &self.saved
    }
    pub fn finish(&mut self) -> Result<(), String> {
        if !self.armed {
            return Ok(());
        }
        // One attempt: an explicit failure is fatal, never retried by Drop.
        self.armed = false;
        if self.backend.snapshot()? == self.saved {
            return Ok(());
        }
        if !self.backend.owns(self.port)? {
            return Err("fixture proxy ownership lost".into());
        }
        self.backend.restore(self.saved.clone())?;
        if self.backend.snapshot()? != self.saved {
            return Err("fixture proxy restore verification failed".into());
        }
        Ok(())
    }
    pub fn finish_after_failure(&mut self) -> Result<(), String> {
        match catch_unwind(AssertUnwindSafe(|| self.finish())) {
            Ok(result) => result,
            Err(_) => Err("fixture proxy backend panicked during unwind".into()),
        }
    }
}

/// The proxy guard belongs to the caller, not to either caught future. Capture
/// finishes (or reports a panic) before the first cleanup/restoration attempt.
pub async fn capture_before_proxy_cleanup<B: ProxyBackend, F: std::future::Future<Output = ()>>(
    capture: F,
    guard: &mut Option<ProxyGuard<'_, B>>,
) -> (bool, Result<(), String>) {
    let diagnostic_panicked = catch_fixture_panic(capture).await.is_err();
    let cleanup = guard
        .as_mut()
        .map_or(Ok(()), |guard| guard.finish_after_failure());
    (diagnostic_panicked, cleanup)
}
impl<B: ProxyBackend> Drop for ProxyGuard<'_, B> {
    fn drop(&mut self) {
        if !self.armed {
            return;
        }
        let message = match self.finish_after_failure() {
            Ok(()) => return,
            Err(error) => error,
        };
        // Do not panic on either a backend or stderr failure during unwinding.
        let _ = writeln!(
            std::io::stderr().lock(),
            "Fixture proxy cleanup failed: {message}"
        );
    }
}
