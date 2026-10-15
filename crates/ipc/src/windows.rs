//! Separate Windows TUN pipe; never reuse the legacy NULL-DACL kill pipe.
use std::{
    ffi::OsStr,
    os::windows::ffi::OsStrExt,
    path::{Path, PathBuf},
    ptr,
};
use tokio::io::{AsyncRead, AsyncReadExt, AsyncWrite, AsyncWriteExt};
use windows_sys::Win32::{
    Foundation::*,
    Security::Authorization::*,
    Security::*,
    Storage::FileSystem::*,
    System::{Com::CoTaskMemFree, Pipes::*, Services::*, Threading::*},
    UI::Shell::{FOLDERID_ProgramFiles, SHGetKnownFolderPath},
};
pub const TUN_PIPE: &str = r"\\.\pipe\Nimbo.Mihomo.Tun.v1";
// Specific file rights omit bit 4 (FILE_CREATE_PIPE_INSTANCE / append data).
pub const CLIENT_ACCESS: u32 = 0x12019b;
pub const PIPE_SDDL: &str = "D:P(A;;GA;;;SY)(A;;GA;;;BA)(A;;0x12019b;;;IU)";
const PRIVATE_SDDL: &str = "D:P(A;OICI;FA;;;SY)(A;OICI;FA;;;BA)";
pub mod permissions;
pub fn wide(s: &OsStr) -> Vec<u16> {
    s.encode_wide().chain(Some(0)).collect()
}
pub struct Handle(pub isize);
impl Drop for Handle {
    fn drop(&mut self) {
        unsafe {
            CloseHandle(self.0 as _);
        }
    }
}
pub struct Descriptor(PSECURITY_DESCRIPTOR);
impl Descriptor {
    pub fn new(sddl: &str) -> Result<Self, String> {
        let mut sd = ptr::null_mut();
        if unsafe {
            ConvertStringSecurityDescriptorToSecurityDescriptorW(
                wide(OsStr::new(sddl)).as_ptr(),
                1,
                &mut sd,
                ptr::null_mut(),
            )
        } == 0
        {
            return Err("HELPER_SECURITY_FAILED".into());
        }
        Ok(Self(sd))
    }
    pub fn attributes(&self) -> SECURITY_ATTRIBUTES {
        SECURITY_ATTRIBUTES {
            nLength: std::mem::size_of::<SECURITY_ATTRIBUTES>() as u32,
            lpSecurityDescriptor: self.0,
            bInheritHandle: 0,
        }
    }
}
impl Drop for Descriptor {
    fn drop(&mut self) {
        unsafe {
            LocalFree(self.0);
        }
    }
}
fn sid_string(sid: PSID) -> Result<String, String> {
    let mut raw = ptr::null_mut();
    if sid.is_null() || unsafe { ConvertSidToStringSidW(sid, &mut raw) } == 0 {
        return Err("HELPER_AUTH_FAILED".into());
    }
    let mut n = 0;
    unsafe {
        while *raw.add(n) != 0 {
            n += 1;
        }
    }
    let s = unsafe { String::from_utf16_lossy(std::slice::from_raw_parts(raw, n)) };
    unsafe {
        LocalFree(raw.cast());
    }
    Ok(s)
}
fn trusted_writer(sid: &str) -> bool {
    matches!(
        sid,
        "S-1-5-18"
            | "S-1-5-32-544"
            | "S-1-5-80-956008885-3418522649-1831038044-1853292631-2271478464"
    )
}
/// Fail closed on reparses, NULL DACL, unknown ACEs or any non-admin write grant.
pub fn protected(path: &Path) -> bool {
    protected_result(path).is_ok()
}
fn protected_result(path: &Path) -> Result<(), String> {
    let name = wide(path.as_os_str());
    let attrs = unsafe { GetFileAttributesW(name.as_ptr()) };
    if attrs == INVALID_FILE_ATTRIBUTES || attrs & FILE_ATTRIBUTE_REPARSE_POINT != 0 {
        return Err("UNSAFE_SERVICE_DIRECTORY".into());
    }
    let (mut owner, mut dacl, mut sd) = (ptr::null_mut(), ptr::null_mut(), ptr::null_mut());
    let error = unsafe {
        GetNamedSecurityInfoW(
            name.as_ptr(),
            SE_FILE_OBJECT,
            OWNER_SECURITY_INFORMATION | DACL_SECURITY_INFORMATION,
            &mut owner,
            ptr::null_mut(),
            &mut dacl,
            ptr::null_mut(),
            &mut sd,
        )
    };
    if error != 0 {
        return Err("UNSAFE_SERVICE_DIRECTORY".into());
    }
    let _descriptor = Descriptor(sd);
    validate_acl(owner, dacl, path.parent().is_none())
}
fn validate_acl(owner: PSID, dacl: *const ACL, volume_root: bool) -> Result<(), String> {
    if !trusted_writer(&sid_string(owner)?) || dacl.is_null() {
        return Err("UNSAFE_SERVICE_DIRECTORY".into());
    }
    // Writes, deletes, ownership/DACL changes and generic all/write.
    const WRITE_RIGHTS: u32 = 0x500d0156;
    for i in 0..unsafe { (*dacl).AceCount } {
        let mut raw = ptr::null_mut();
        if unsafe { GetAce(dacl, i as u32, &mut raw) } == 0 {
            return Err("UNSAFE_SERVICE_DIRECTORY".into());
        }
        let header = unsafe { &*(raw as *const ACE_HEADER) };
        if header.AceFlags & 8 != 0 {
            continue;
        } // INHERIT_ONLY_ACE cannot mutate this object.
        match header.AceType {
            0 => {
                let ace = unsafe { &*(raw as *const ACCESS_ALLOWED_ACE) };
                let sid = (&ace.SidStart as *const u32).cast_mut().cast();
                let rights = if volume_root {
                    WRITE_RIGHTS & !6
                } else {
                    WRITE_RIGHTS
                };
                if ace.Mask & rights != 0 && !trusted_writer(&sid_string(sid)?) {
                    return Err("UNSAFE_SERVICE_DIRECTORY".into());
                }
            }
            1 => {} // deny grants no authority
            _ => return Err("UNSAFE_SERVICE_DIRECTORY".into()),
        }
    }
    Ok(())
}
pub fn protected_chain(path: &Path) -> bool {
    path.ancestors().all(protected)
}
pub fn root() -> Result<PathBuf, String> {
    let mut value = ptr::null_mut();
    if unsafe { SHGetKnownFolderPath(&FOLDERID_ProgramFiles, 0, ptr::null_mut(), &mut value) } < 0 {
        return Err("UNSAFE_SERVICE_DIRECTORY".into());
    }
    let mut n = 0;
    unsafe {
        while *value.add(n) != 0 {
            n += 1;
        }
    }
    let path =
        PathBuf::from(unsafe { String::from_utf16_lossy(std::slice::from_raw_parts(value, n)) });
    unsafe {
        CoTaskMemFree(value.cast());
    }
    Ok(path.join("NimboNativeTun"))
}
/// Parent is already protected; never repair or adopt an unsafe existing object.
pub fn private_directory(path: &Path) -> Result<(), String> {
    directory_with_acl(path, PRIVATE_SDDL)
}
pub fn installation_directory(path: &Path) -> Result<(), String> {
    directory_with_acl(
        path,
        "D:P(A;OICI;FA;;;SY)(A;OICI;FA;;;BA)(A;OICI;0x1200a9;;;BU)",
    )
}
fn directory_with_acl(path: &Path, sddl: &str) -> Result<(), String> {
    if !protected_chain(path.parent().ok_or("UNSAFE_SERVICE_DIRECTORY")?) {
        return Err("UNSAFE_SERVICE_DIRECTORY".into());
    }
    if path.exists() {
        return if path.is_dir() && protected(path) {
            Ok(())
        } else {
            Err("UNSAFE_SERVICE_DIRECTORY".into())
        };
    }
    let sd = Descriptor::new(sddl)?;
    let attrs = sd.attributes();
    if unsafe { CreateDirectoryW(wide(path.as_os_str()).as_ptr(), &attrs) } == 0 || !protected(path)
    {
        return Err("UNSAFE_SERVICE_DIRECTORY".into());
    }
    Ok(())
}
#[derive(Clone, Debug, PartialEq, Eq)]
pub struct Peer {
    pub sid: String,
    pub session: u32,
}
pub fn peer(pipe: &impl std::os::windows::io::AsRawHandle) -> Result<Peer, String> {
    if unsafe { ImpersonateNamedPipeClient(pipe.as_raw_handle() as _) } == 0 {
        return Err("HELPER_AUTH_FAILED".into());
    }
    struct Revert;
    impl Drop for Revert {
        fn drop(&mut self) {
            if unsafe { RevertToSelf() } == 0 {
                std::process::abort();
            }
        }
    }
    let _revert = Revert;
    let mut token = ptr::null_mut();
    if unsafe { OpenThreadToken(GetCurrentThread(), TOKEN_QUERY, 1, &mut token) } == 0 {
        return Err("HELPER_AUTH_FAILED".into());
    }
    let _token = Handle(token as isize);
    let mut length = 0;
    unsafe {
        GetTokenInformation(token, TokenUser, ptr::null_mut(), 0, &mut length);
    }
    if length == 0 || length > 65536 {
        return Err("HELPER_AUTH_FAILED".into());
    }
    let mut buffer = vec![0usize; (length as usize).div_ceil(std::mem::size_of::<usize>())];
    if unsafe {
        GetTokenInformation(
            token,
            TokenUser,
            buffer.as_mut_ptr().cast(),
            length,
            &mut length,
        )
    } == 0
    {
        return Err("HELPER_AUTH_FAILED".into());
    }
    let user = unsafe { &*(buffer.as_ptr() as *const TOKEN_USER) };
    let sid = sid_string(user.User.Sid)?;
    let mut session = 0u32;
    if unsafe {
        GetTokenInformation(
            token,
            TokenSessionId,
            (&mut session as *mut u32).cast(),
            4,
            &mut length,
        )
    } == 0
    {
        return Err("HELPER_AUTH_FAILED".into());
    }
    if session == 0 && sid != "S-1-5-18" {
        let mut administrators = std::ptr::null_mut();
        if unsafe {
            ConvertStringSidToSidW(
                wide(OsStr::new("S-1-5-32-544")).as_ptr(),
                &mut administrators,
            )
        } == 0
        {
            return Err("HELPER_AUTH_FAILED".into());
        }
        let mut member = 0;
        let ok = unsafe { CheckTokenMembership(token, administrators, &mut member) };
        unsafe {
            LocalFree(administrators);
        }
        if ok == 0 || member == 0 {
            return Err("HELPER_AUTH_FAILED".into());
        }
    }
    Ok(Peer { sid, session })
}
/// OS-owned SCM record, not a client-provided process/path or a pipe reply.
pub fn authenticate_server(pipe: &impl std::os::windows::io::AsRawHandle) -> Result<(), String> {
    let mut pid = 0;
    if unsafe { GetNamedPipeServerProcessId(pipe.as_raw_handle() as _, &mut pid) } == 0 || pid == 0
    {
        return Err("HELPER_AUTH_FAILED".into());
    }
    unsafe {
        let scm = OpenSCManagerW(ptr::null(), ptr::null(), SC_MANAGER_CONNECT);
        if scm.is_null() {
            return Err("HELPER_AUTH_FAILED".into());
        }
        let svc = OpenServiceW(
            scm,
            wide(OsStr::new("NimboHelper")).as_ptr(),
            SERVICE_QUERY_STATUS,
        );
        if svc.is_null() {
            CloseServiceHandle(scm);
            return Err("HELPER_AUTH_FAILED".into());
        }
        let mut status: SERVICE_STATUS_PROCESS = std::mem::zeroed();
        let mut length = 0;
        let valid = QueryServiceStatusEx(
            svc,
            SC_STATUS_PROCESS_INFO,
            (&mut status as *mut SERVICE_STATUS_PROCESS).cast(),
            std::mem::size_of_val(&status) as u32,
            &mut length,
        ) != 0
            && status.dwCurrentState == SERVICE_RUNNING
            && status.dwProcessId == pid;
        CloseServiceHandle(svc);
        CloseServiceHandle(scm);
        if !valid {
            return Err("HELPER_AUTH_FAILED".into());
        }
    }
    // The new installer puts the service here; old/portable unsafe services are not adopted.
    let image = root()?.join("nimbo-svc.exe");
    if !protected_chain(image.parent().unwrap()) || !protected(&image) {
        return Err("HELPER_AUTH_FAILED".into());
    }
    let process = unsafe { OpenProcess(PROCESS_QUERY_LIMITED_INFORMATION, 0, pid) };
    if process.is_null() {
        return Err("HELPER_AUTH_FAILED".into());
    }
    let _process = Handle(process as isize);
    let mut name = vec![0u16; 32768];
    let mut size = name.len() as u32;
    if unsafe { QueryFullProcessImageNameW(process, 0, name.as_mut_ptr(), &mut size) } == 0 {
        return Err("HELPER_AUTH_FAILED".into());
    }
    let actual = PathBuf::from(String::from_utf16_lossy(&name[..size as usize]));
    if std::fs::canonicalize(actual).ok() != std::fs::canonicalize(image).ok() {
        return Err("HELPER_AUTH_FAILED".into());
    }
    Ok(())
}
pub async fn read_frame(io: &mut (impl AsyncRead + Unpin)) -> Result<Vec<u8>, String> {
    let n = io.read_u32().await.map_err(|_| "HELPER_IO_FAILED")?;
    if n == 0 || n > crate::FRAME_MAX_BYTES {
        return Err("INVALID_REQUEST".into());
    }
    let mut data = vec![0; n as usize];
    io.read_exact(&mut data)
        .await
        .map_err(|_| "HELPER_IO_FAILED")?;
    Ok(data)
}
pub async fn write_frame(io: &mut (impl AsyncWrite + Unpin), bytes: &[u8]) -> Result<(), String> {
    if bytes.is_empty() || bytes.len() > crate::FRAME_MAX_BYTES as usize {
        return Err("INVALID_REQUEST".into());
    }
    io.write_u32(bytes.len() as u32)
        .await
        .map_err(|_| "HELPER_IO_FAILED")?;
    io.write_all(bytes).await.map_err(|_| "HELPER_IO_FAILED")?;
    io.flush().await.map_err(|_| "HELPER_IO_FAILED")?;
    Ok(())
}

#[cfg(test)]
mod tests {
    use super::*;
    #[test]
    fn interactive_clients_cannot_create_pipe_instances() {
        assert_eq!(CLIENT_ACCESS & 4, 0);
        assert!(!PIPE_SDDL.contains("GW"));
        assert!(PIPE_SDDL.contains("IU"));
    }
    #[test]
    fn program_files_acl_is_read_only_inspected() {
        let directory = root().unwrap();
        // Inspect only the existing OS directory. A custom writable volume root
        // is allowed to fail the stricter installation chain; never repair it.
        assert!(protected(directory.parent().unwrap()));
    }
    #[test]
    fn only_administrative_principals_can_write_native_images() {
        assert!(trusted_writer("S-1-5-18"));
        assert!(trusted_writer("S-1-5-32-544"));
        assert!(!trusted_writer("S-1-1-0"));
        assert!(!trusted_writer("S-1-5-4"));
        assert!(!trusted_writer("S-1-5-21-123-456-789-1001"));
    }
}
