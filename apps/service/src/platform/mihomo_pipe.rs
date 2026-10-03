//! The legacy kill pipe never accepts privileged network commands.
use crate::mihomo_windows::MihomoOwner;
use nimbo_ipc::{windows as trust, Command, ErrorCode, Response};
use std::{
    os::windows::io::AsRawHandle,
    sync::{
        atomic::{AtomicBool, Ordering},
        Arc,
    },
    time::Duration,
};
use tokio::net::windows::named_pipe::{NamedPipeServer, ServerOptions};
fn instance(first: bool) -> Result<NamedPipeServer, String> {
    let descriptor = trust::Descriptor::new(trust::PIPE_SDDL)?;
    let mut attrs = descriptor.attributes();
    unsafe {
        ServerOptions::new()
            .first_pipe_instance(first)
            .reject_remote_clients(true)
            .create_with_security_attributes_raw(
                trust::TUN_PIPE,
                (&mut attrs as *mut windows_sys::Win32::Security::SECURITY_ATTRIBUTES).cast(),
            )
    }
    .map_err(|_| "HELPER_PIPE_FAILED".into())
}
fn alive(pipe: &NamedPipeServer, shutdown: &AtomicBool) -> bool {
    !shutdown.load(Ordering::SeqCst)
        && unsafe {
            windows_sys::Win32::System::Pipes::PeekNamedPipe(
                pipe.as_raw_handle() as _,
                std::ptr::null_mut(),
                0,
                std::ptr::null_mut(),
                std::ptr::null_mut(),
                std::ptr::null_mut(),
            )
        } != 0
}
fn error(message: String) -> Response {
    Response::Error {
        code: ErrorCode::PermissionDenied,
        message,
    }
}
async fn client(
    mut pipe: NamedPipeServer,
    id: u64,
    owner: Arc<MihomoOwner>,
    shutdown: Arc<AtomicBool>,
) {
    let mut identity = None;
    loop {
        let payload = tokio::select! {
            result=tokio::time::timeout(Duration::from_secs(3600),trust::read_frame(&mut pipe))=>match result{Ok(Ok(v))=>v,_=>break},
            _=async {while !shutdown.load(Ordering::SeqCst){tokio::time::sleep(Duration::from_millis(50)).await;}}=>break,
        };
        let peer = match trust::peer(&pipe) {
            Ok(p) => p,
            Err(_) => break,
        };
        if identity.as_ref().is_some_and(|p| p != &peer) {
            break;
        }
        let sid = peer.sid.clone();
        identity.get_or_insert(peer);
        let command = match nimbo_ipc::decode_command(&payload) {
            Ok(c) => c,
            Err(_) => break,
        };
        let response = match command {
            Command::MihomoStatus => {
                let o = owner.clone();
                match tokio::task::spawn_blocking(move || Response::MihomoAvailability {
                    binary_sha256: crate::mihomo_windows::expected_hash().into(),
                    available: o.available(),
                    running: o.running(),
                    both_available: o.available(),
                    kill_switch_available: o.available() && crate::mihomo_firewall::available(),
                })
                .await
                {
                    Ok(r) => r,
                    Err(_) => break,
                }
            }
            Command::MihomoPreflight(r) => {
                // Readiness failures are short static codes, never source/core logs.
                let o = owner.clone();
                let stop = shutdown.clone();
                let handle = pipe.as_raw_handle() as isize;
                let result = tokio::task::spawn_blocking(move || {
                    o.preflight(&r, &|| raw_alive(handle, &stop))
                })
                .await;
                match result {
                    Ok(Ok(())) => Response::Ok,
                    Ok(Err(e)) => error(e),
                    Err(_) => break,
                }
            }
            Command::MihomoUp(r) => {
                let o = owner.clone();
                let stop = shutdown.clone();
                let handle = pipe.as_raw_handle() as isize;
                let result = tokio::task::spawn_blocking(move || {
                    o.up(id, &sid, &r, &|| raw_alive(handle, &stop))
                })
                .await;
                match result {
                    Ok(Ok(r)) => Response::MihomoReady(r),
                    Ok(Err(e)) => error(e),
                    Err(_) => break,
                }
            }
            Command::MihomoResetKillSwitch => {
                let o = owner.clone();
                match tokio::task::spawn_blocking(move || o.reset_kill_switch(&sid)).await {
                    Ok(Ok(())) => Response::Ok,
                    Ok(Err(e)) => error(e),
                    Err(_) => break,
                }
            }
            Command::MihomoDown => {
                let o = owner.clone();
                match tokio::task::spawn_blocking(move || o.down(id)).await {
                    Ok(Ok(())) => Response::Ok,
                    Ok(Err(e)) => error(e),
                    Err(_) => break,
                }
            }
            _ => error("UNSUPPORTED_COMMAND".into()),
        };
        if !alive(&pipe, &shutdown) {
            break;
        }
        let Ok(bytes) = nimbo_ipc::encode_response(&response) else {
            break;
        };
        if !matches!(
            tokio::time::timeout(
                Duration::from_secs(5),
                trust::write_frame(&mut pipe, &bytes)
            )
            .await,
            Ok(Ok(()))
        ) {
            break;
        }
    }
    // Dropped GUI or a cancelled start ends exactly this lease, not another SID's.
    let _ = tokio::task::spawn_blocking(move || owner.down(id)).await;
}
fn raw_alive(handle: isize, shutdown: &AtomicBool) -> bool {
    !shutdown.load(Ordering::SeqCst)
        && unsafe {
            windows_sys::Win32::System::Pipes::PeekNamedPipe(
                handle as _,
                std::ptr::null_mut(),
                0,
                std::ptr::null_mut(),
                std::ptr::null_mut(),
                std::ptr::null_mut(),
            )
        } != 0
}
pub fn serve(shutdown: Arc<AtomicBool>) -> Result<(), String> {
    // Only the protected SCM-installed image may expose this endpoint.
    let image = std::env::current_exe().map_err(|_| "HELPER_AUTH_FAILED")?;
    let root = trust::root()?;
    if std::fs::canonicalize(&image).ok() != std::fs::canonicalize(root.join("nimbo-svc.exe")).ok()
        || !trust::protected_chain(&root)
        || !trust::protected(&image)
    {
        return Err("HELPER_AUTH_FAILED".into());
    }
    let runtime = tokio::runtime::Builder::new_multi_thread()
        .worker_threads(2)
        .enable_all()
        .build()
        .map_err(|_| "HELPER_RUNTIME_FAILED")?;
    runtime.block_on(async {
        let owner=Arc::new(MihomoOwner::default());
        let slots=Arc::new(tokio::sync::Semaphore::new(16));
        let mut tasks=tokio::task::JoinSet::new();
        let mut pipe=instance(true)?;let mut id=0u64;
        let result:Result<(),String>=async{
            loop{
                tokio::select!{
                    connected=pipe.connect()=>connected.map_err(|_|"HELPER_PIPE_FAILED")?,
                    _=async{while !shutdown.load(Ordering::SeqCst){tokio::time::sleep(Duration::from_millis(50)).await;}}=>break,
                }
                // Keep one server instance alive at all times; clients have no create right.
                let next=instance(false)?;let current=std::mem::replace(&mut pipe,next);
                let Ok(permit)=slots.clone().try_acquire_owned() else{drop(current);continue;};
                id=id.checked_add(1).ok_or("HELPER_PIPE_FAILED")?;
                let o=owner.clone();let stop=shutdown.clone();
                tasks.spawn(async move{let _permit=permit;client(current,id,o,stop).await;});
                while tasks.try_join_next().is_some(){}
            }Ok(())
        }.await;
        shutdown.store(true,Ordering::SeqCst);
        while tasks.join_next().await.is_some(){}
        owner.down_all();result
    })
}
#[cfg(test)]
mod tests {
    #[test]
    fn legacy_pipe_remains_network_denied() {
        let source = include_str!("pipe.rs");
        assert!(!source.contains("MihomoOwner"));
        assert!(!source.contains("Command::MihomoUp"));
    }
}
