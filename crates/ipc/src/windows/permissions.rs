//! Opt-in, root-only recovery of one known grant; never a recursive ACL reset.
use super::*;
use std::{fs::File, io::Write, os::windows::io::FromRawHandle};

const UNSAFE: &str = "UNSAFE_SERVICE_DIRECTORY";
const DRIVE_FIXED: u32 = 3;
struct Snapshot {
    sd: Descriptor,
    owner: PSID,
    group: PSID,
    dacl: *mut ACL,
    control: u16,
}
impl Snapshot {
    fn from_descriptor(sd: Descriptor) -> Result<Self, String> {
        let (mut owner, mut group, mut dacl) = (ptr::null_mut(), ptr::null_mut(), ptr::null_mut());
        let (mut present, mut defaulted, mut control, mut revision) = (0, 0, 0, 0);
        if unsafe {
            IsValidSecurityDescriptor(sd.0) == 0
                || GetSecurityDescriptorOwner(sd.0, &mut owner, &mut defaulted) == 0
                || GetSecurityDescriptorGroup(sd.0, &mut group, &mut defaulted) == 0
                || GetSecurityDescriptorDacl(sd.0, &mut present, &mut dacl, &mut defaulted) == 0
                || GetSecurityDescriptorControl(sd.0, &mut control, &mut revision) == 0
                || present == 0
                || dacl.is_null()
                || IsValidAcl(dacl) == 0
        } {
            return Err(UNSAFE.into());
        }
        Ok(Self {
            sd,
            owner,
            group,
            dacl,
            control,
        })
    }
    fn read(handle: &Handle) -> Result<Self, String> {
        let mut sd = ptr::null_mut();
        let error = unsafe {
            GetSecurityInfo(
                handle.0 as _,
                SE_FILE_OBJECT,
                OWNER_SECURITY_INFORMATION | GROUP_SECURITY_INFORMATION | DACL_SECURITY_INFORMATION,
                ptr::null_mut(),
                ptr::null_mut(),
                ptr::null_mut(),
                ptr::null_mut(),
                &mut sd,
            )
        };
        if error != 0 {
            return Err(UNSAFE.into());
        }
        Self::from_descriptor(Descriptor(sd))
    }
    fn acl_bytes(&self) -> &[u8] {
        unsafe { std::slice::from_raw_parts(self.dacl.cast(), (*self.dacl).AclSize as usize) }
    }
    fn sddl(&self) -> Result<String, String> {
        let mut value = ptr::null_mut();
        if unsafe {
            ConvertSecurityDescriptorToStringSecurityDescriptorW(
                self.sd.0,
                1,
                OWNER_SECURITY_INFORMATION | GROUP_SECURITY_INFORMATION | DACL_SECURITY_INFORMATION,
                &mut value,
                ptr::null_mut(),
            )
        } == 0
        {
            return Err(UNSAFE.into());
        }
        let mut n = 0;
        unsafe {
            while *value.add(n) != 0 {
                n += 1;
            }
        }
        let text = unsafe { String::from_utf16_lossy(std::slice::from_raw_parts(value, n)) };
        unsafe {
            LocalFree(value.cast());
        }
        Ok(text)
    }
    fn same_metadata(&self, other: &Self) -> bool {
        self.control == other.control
            && sid_string(self.owner).ok() == sid_string(other.owner).ok()
            && sid_string(self.group).ok() == sid_string(other.group).ok()
    }
}
// Word storage preserves the alignment required by Windows ACL APIs.
struct AclCopy {
    words: Vec<usize>,
    length: usize,
}
impl AclCopy {
    fn new(bytes: &[u8]) -> Self {
        let mut result = Self {
            words: vec![0; bytes.len().div_ceil(std::mem::size_of::<usize>())],
            length: bytes.len(),
        };
        unsafe {
            ptr::copy_nonoverlapping(
                bytes.as_ptr(),
                result.words.as_mut_ptr().cast(),
                bytes.len(),
            );
        }
        result
    }
    fn ptr(&self) -> *mut ACL {
        self.words.as_ptr().cast_mut().cast()
    }
    fn bytes(&self) -> &[u8] {
        unsafe { std::slice::from_raw_parts(self.words.as_ptr().cast(), self.length) }
    }
}
fn candidate(before: &Snapshot, user: &str) -> Result<AclCopy, String> {
    if !user.starts_with("S-1-5-21-")
        || trusted_writer(user)
        || before.control & SE_DACL_PROTECTED == 0
        || !trusted_writer(&sid_string(before.owner)?)
    {
        return Err(UNSAFE.into());
    }
    let changed = AclCopy::new(before.acl_bytes());
    let (mut count, mut system, mut admins, mut readable) = (0, false, false, false);
    for i in 0..unsafe { (*changed.ptr()).AceCount } {
        let mut raw = ptr::null_mut();
        if unsafe { GetAce(changed.ptr(), i as u32, &mut raw) } == 0 {
            return Err(UNSAFE.into());
        }
        let header = unsafe { &*(raw as *const ACE_HEADER) };
        let flags = header.AceFlags;
        // This recovery deliberately does not reason about deny/object/callback ACEs.
        if header.AceType != 0 {
            return Err(UNSAFE.into());
        }
        let ace = unsafe { &*(raw as *const ACCESS_ALLOWED_ACE) };
        let sid = sid_string((&ace.SidStart as *const u32).cast_mut().cast())?;
        if flags & INHERIT_ONLY_ACE as u8 == 0 {
            system |= sid == "S-1-5-18" && ace.Mask == 0x1f01ff;
            admins |= sid == "S-1-5-32-544" && ace.Mask == 0x1f01ff;
            readable |= sid == "S-1-5-32-545" && ace.Mask & 0x1200a9 == 0x1200a9;
        }
        if sid == user && flags & INHERIT_ONLY_ACE as u8 == 0 {
            if flags != (OBJECT_INHERIT_ACE | CONTAINER_INHERIT_ACE) as u8 || ace.Mask != 0x1f01ff {
                return Err(UNSAFE.into());
            }
            unsafe {
                (*raw.cast::<ACE_HEADER>()).AceFlags = flags | INHERIT_ONLY_ACE as u8;
            }
            count += 1;
        }
    }
    if count != 1 || !system || !admins || !readable {
        return Err(UNSAFE.into());
    }
    validate_acl(before.owner, changed.ptr(), true)?;
    Ok(changed)
}
pub fn current_user() -> Result<String, String> {
    let mut token = ptr::null_mut();
    if unsafe { OpenProcessToken(GetCurrentProcess(), TOKEN_QUERY, &mut token) } == 0 {
        return Err(UNSAFE.into());
    }
    let _token = Handle(token as isize);
    let mut length = 0;
    unsafe {
        GetTokenInformation(token, TokenUser, ptr::null_mut(), 0, &mut length);
    }
    if length == 0 || length > 65536 {
        return Err(UNSAFE.into());
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
        return Err(UNSAFE.into());
    }
    sid_string(unsafe { (*(buffer.as_ptr() as *const TOKEN_USER)).User.Sid })
}
fn open_directory(path: &Path, access: u32) -> Result<Handle, String> {
    let handle = unsafe {
        CreateFileW(
            wide(path.as_os_str()).as_ptr(),
            access,
            // A volume root cannot be renamed/deleted. Sharing DELETE here is
            // required for compatibility with existing OS directory handles.
            // Non-root anchors still exclude DELETE to pin their identities.
            FILE_SHARE_READ
                | FILE_SHARE_WRITE
                | if path.parent().is_none() {
                    FILE_SHARE_DELETE
                } else {
                    0
                },
            ptr::null(),
            OPEN_EXISTING,
            FILE_FLAG_BACKUP_SEMANTICS | FILE_FLAG_OPEN_REPARSE_POINT,
            ptr::null_mut(),
        )
    };
    if handle == INVALID_HANDLE_VALUE {
        return Err(UNSAFE.into());
    }
    let handle = Handle(handle as isize);
    let mut info: BY_HANDLE_FILE_INFORMATION = unsafe { std::mem::zeroed() };
    if unsafe { GetFileInformationByHandle(handle.0 as _, &mut info) } == 0
        || info.dwFileAttributes & FILE_ATTRIBUTE_REPARSE_POINT != 0
        || info.dwFileAttributes & FILE_ATTRIBUTE_DIRECTORY == 0
    {
        return Err(UNSAFE.into());
    }
    Ok(handle)
}
fn fixed_volume() -> Result<(PathBuf, PathBuf), String> {
    let destination = root()?;
    let volume = destination.ancestors().last().ok_or(UNSAFE)?.to_path_buf();
    let name = wide(volume.as_os_str());
    // Local fixed NTFS only; never UNC, removable storage or a client path.
    if name.len() != 4
        || name[1] != b':' as u16
        || name[2] != b'\\' as u16
        || unsafe { GetDriveTypeW(name.as_ptr()) } != DRIVE_FIXED
    {
        return Err(UNSAFE.into());
    }
    let mut fs_name = [0u16; 32];
    if unsafe {
        GetVolumeInformationW(
            name.as_ptr(),
            ptr::null_mut(),
            0,
            ptr::null_mut(),
            ptr::null_mut(),
            ptr::null_mut(),
            fs_name.as_mut_ptr(),
            fs_name.len() as u32,
        )
    } == 0
        || !String::from_utf16_lossy(&fs_name)
            .trim_end_matches('\0')
            .eq_ignore_ascii_case("NTFS")
    {
        return Err(UNSAFE.into());
    }
    Ok((destination, volume))
}
fn anchors(destination: &Path, volume: &Path) -> Result<Vec<Handle>, String> {
    let mut handles = Vec::new();
    for path in destination.ancestors().take_while(|p| *p != volume) {
        if path == destination && !path.exists() {
            continue;
        }
        let handle = open_directory(path, READ_CONTROL)?;
        let snapshot = Snapshot::read(&handle)?;
        validate_acl(snapshot.owner, snapshot.dacl, false)?;
        handles.push(handle); // no DELETE share: do not allow ancestor replacement during repair
    }
    for name in ["nimbo-svc.exe", "nimbo-mihomo.exe"] {
        let path = destination.join(name);
        if std::fs::symlink_metadata(&path).is_ok() && (!path.is_file() || !protected(&path)) {
            return Err(UNSAFE.into());
        }
    }
    Ok(handles)
}
/// Read-only eligibility; does not create a backup, elevate, log or run a service.
pub fn repair_available() -> bool {
    (|| -> Result<(), String> {
        let (destination, volume) = fixed_volume()?;
        let _anchors = anchors(&destination, &volume)?;
        let handle = open_directory(&volume, READ_CONTROL)?;
        candidate(&Snapshot::read(&handle)?, &current_user()?)?;
        Ok(())
    })()
    .is_ok()
}
fn set_descriptor(path: &Path, sd: PSECURITY_DESCRIPTOR) -> Result<(), String> {
    // Intentionally use the documented non-propagating file backup API here.
    // SetSecurityInfo/MAXIMUM_ALLOWED cannot open a busy Windows volume root;
    // SetNamedSecurityInfo would recursively rewrite existing child ACLs.
    // The production path is a fixed, non-renameable local NTFS volume root.
    // https://learn.microsoft.com/windows/win32/api/winbase/nf-winbase-setfilesecuritya
    let (mut control, mut revision) = (0, 0);
    if unsafe { GetSecurityDescriptorControl(sd, &mut control, &mut revision) } == 0
        || control & SE_SELF_RELATIVE == 0
    {
        return Err(UNSAFE.into());
    }
    let copy = AclCopy::new(unsafe {
        std::slice::from_raw_parts(sd.cast(), GetSecurityDescriptorLength(sd) as usize)
    });
    // The legacy backup API requires AUTO_INHERIT_REQ to preserve an existing
    // AUTO_INHERITED model flag. Without it, even rollback silently clears AI.
    // This controls this descriptor's model only, not a recursive child walk.
    if control & SE_DACL_AUTO_INHERITED != 0
        && unsafe {
            SetSecurityDescriptorControl(
                copy.ptr().cast(),
                SE_DACL_AUTO_INHERIT_REQ,
                SE_DACL_AUTO_INHERIT_REQ,
            )
        } == 0
    {
        return Err("PERMISSIONS_REPAIR_FAILED".into());
    }
    if unsafe {
        SetFileSecurityW(
            wide(path.as_os_str()).as_ptr(),
            DACL_SECURITY_INFORMATION,
            copy.ptr().cast(),
        )
    } != 0
    {
        Ok(())
    } else {
        Err("PERMISSIONS_REPAIR_FAILED".into())
    }
}
fn apply_verified(
    handle: &Handle,
    path: &Path,
    before: &Snapshot,
    changed: &AclCopy,
    check: impl FnOnce() -> bool,
) -> Result<(), String> {
    // Abort if permissions changed since the backup was taken.
    let now = Snapshot::read(handle)?;
    if !before.same_metadata(&now) || before.acl_bytes() != now.acl_bytes() {
        return Err(UNSAFE.into());
    }
    let length = unsafe { GetSecurityDescriptorLength(before.sd.0) } as usize;
    let copied = AclCopy::new(unsafe { std::slice::from_raw_parts(before.sd.0.cast(), length) });
    let (mut dacl, mut present, mut defaulted) = (ptr::null_mut(), 0, 0);
    if unsafe {
        GetSecurityDescriptorDacl(copied.ptr().cast(), &mut present, &mut dacl, &mut defaulted)
    } == 0
        || present == 0
        || dacl.is_null()
        || unsafe { (*dacl).AclSize } as usize != changed.length
    {
        return Err(UNSAFE.into());
    }
    unsafe {
        ptr::copy_nonoverlapping(changed.bytes().as_ptr(), dacl.cast(), changed.length);
    }
    set_descriptor(path, copied.ptr().cast())?;
    let verified = Snapshot::read(handle)
        .is_ok_and(|after| before.same_metadata(&after) && after.acl_bytes() == changed.bytes())
        && check();
    if verified {
        return Ok(());
    }
    set_descriptor(path, before.sd.0).map_err(|_| "PERMISSIONS_ROLLBACK_FAILED")?;
    let restored = Snapshot::read(handle)?;
    if !before.same_metadata(&restored) || before.acl_bytes() != restored.acl_bytes() {
        return Err("PERMISSIONS_ROLLBACK_FAILED".into());
    }
    Err("PERMISSIONS_REPAIR_FAILED".into())
}
/// Explicit installer action only. The original user identity must survive UAC.
pub fn repair(expected_user: &str) -> Result<PathBuf, String> {
    let user = current_user()?;
    if user != expected_user {
        return Err("PERMISSIONS_ACCOUNT_CHANGED".into());
    }
    // Name-based rollback must retain administrator WRITE_DAC after the user's
    // effective root grant is removed. Never allow a filtered/non-admin token.
    if !elevated_administrator() {
        return Err("PERMISSIONS_ELEVATION_REQUIRED".into());
    }
    let (destination, volume) = fixed_volume()?;
    let _anchors = anchors(&destination, &volume)?;
    // The CLI requests elevation first. Both SYSTEM and Administrators grants
    // are preserved, so rollback can reopen the fixed root after this change.
    let handle = open_directory(&volume, READ_CONTROL | WRITE_DAC)?;
    let before = Snapshot::read(&handle)?;
    let changed = candidate(&before, &user)?;
    let (backup, _backup_handle) = backup_acl(&volume, &before)?;
    apply_verified(&handle, &volume, &before, &changed, || {
        protected_chain(destination.parent().unwrap())
    })?;
    Ok(backup)
}
fn backup_acl(volume: &Path, before: &Snapshot) -> Result<(PathBuf, File), String> {
    let timestamp = std::time::SystemTime::now()
        .duration_since(std::time::UNIX_EPOCH)
        .map_err(|_| UNSAFE)?
        .as_nanos();
    let backup = volume.join(format!(
        "Nimbo-permissions-backup-{}-{timestamp}.sddl",
        std::process::id()
    ));
    // create-new plus an open non-delete-shared handle: no overwrite or symlink following.
    let private = Descriptor::new("O:BAG:BAD:P(A;;FA;;;SY)(A;;FA;;;BA)")?;
    let attributes = private.attributes();
    let raw = unsafe {
        CreateFileW(
            wide(backup.as_os_str()).as_ptr(),
            GENERIC_READ | GENERIC_WRITE,
            FILE_SHARE_READ,
            &attributes,
            CREATE_NEW,
            FILE_ATTRIBUTE_NORMAL | FILE_FLAG_OPEN_REPARSE_POINT,
            ptr::null_mut(),
        )
    };
    if raw == INVALID_HANDLE_VALUE {
        return Err("PERMISSIONS_BACKUP_FAILED".into());
    }
    let mut file = unsafe { File::from_raw_handle(raw as _) };
    file.write_all(before.sddl()?.as_bytes())
        .and_then(|_| file.sync_all())
        .map_err(|_| "PERMISSIONS_BACKUP_FAILED")?;
    if std::fs::read(&backup).map_err(|_| "PERMISSIONS_BACKUP_FAILED")? != before.sddl()?.as_bytes()
    {
        return Err("PERMISSIONS_BACKUP_FAILED".into());
    }
    Ok((backup, file))
}
fn elevated_administrator() -> bool {
    let mut sid = ptr::null_mut();
    if unsafe { ConvertStringSidToSidW(wide(OsStr::new("S-1-5-32-544")).as_ptr(), &mut sid) } == 0 {
        return false;
    }
    let mut member = 0;
    let ok = unsafe { CheckTokenMembership(ptr::null_mut(), sid, &mut member) };
    unsafe {
        LocalFree(sid);
    }
    ok != 0 && member != 0
}

#[cfg(test)]
mod tests {
    use super::*;
    const USER: &str = "S-1-5-21-123-456-789-1001";
    fn fixture(owner: &str, grant: &str) -> Descriptor {
        Descriptor::new(&format!(
            "O:{owner}G:SYD:PAI(A;OICI;FA;;;SY)(A;OICI;FA;;;BA)(A;OICI;0x1200a9;;;BU){grant}"
        ))
        .unwrap()
    }
    #[test]
    fn only_one_flag_changes() {
        let sd = fixture("SY", &format!("(A;OICI;FA;;;{USER})"));
        let snapshot = Snapshot::from_descriptor(sd).unwrap();
        let before = snapshot.acl_bytes().to_vec();
        let changed = candidate(&snapshot, USER).unwrap();
        let differences: Vec<_> = before
            .iter()
            .zip(changed.bytes())
            .enumerate()
            .filter(|(_, (a, b))| a != b)
            .collect();
        assert_eq!(differences.len(), 1);
        assert_eq!((*differences[0].1 .0, *differences[0].1 .1), (3, 11));
        assert!(validate_acl(snapshot.owner, changed.ptr(), true).is_ok());
        assert_eq!(snapshot.acl_bytes(), before);
    }
    #[test]
    fn ambiguous_or_foreign_grants_are_not_repaired() {
        for grant in [
            format!("(A;;FA;;;{USER})"),
            format!("(A;OICIID;FA;;;{USER})"),
            format!("(A;OICIIO;FA;;;{USER})"),
            format!("(A;OICI;FW;;;{USER})"),
            "(A;OICI;FA;;;WD)".into(),
            format!("(A;OICI;FA;;;{USER})(A;OICI;FA;;;{USER})"),
            format!("(A;OICI;FA;;;{USER})(A;;WD;;;AU)"),
        ] {
            let snapshot = Snapshot::from_descriptor(fixture("SY", &grant)).unwrap();
            assert!(candidate(&snapshot, USER).is_err(), "{grant}");
        }
        let snapshot =
            Snapshot::from_descriptor(fixture(USER, &format!("(A;OICI;FA;;;{USER})"))).unwrap();
        assert!(candidate(&snapshot, USER).is_err());
    }
    #[test]
    fn filtered_tokens_cannot_start_a_root_write() {
        if !elevated_administrator() {
            assert_eq!(
                repair(&current_user().unwrap()),
                Err("PERMISSIONS_ELEVATION_REQUIRED".into())
            );
        }
        assert_eq!(
            repair("S-1-5-21-0-0-0-1234"),
            Err("PERMISSIONS_ACCOUNT_CHANGED".into())
        );
    }
    #[test]
    fn actual_volume_eligibility_is_read_only() {
        let (_, volume) = fixed_volume().unwrap();
        let handle = open_directory(&volume, READ_CONTROL).unwrap();
        let before = Snapshot::read(&handle).unwrap().sddl().unwrap();
        let available = repair_available();
        assert_eq!(available, repair_available());
        assert_eq!(before, Snapshot::read(&handle).unwrap().sddl().unwrap());
        eprintln!("read-only recovery eligibility={available}");
    }
    #[test]
    fn elevated_private_backup_roundtrip_in_owned_fixture_only() {
        // Runs on the elevated disposable Windows CI runner, never writes a
        // real root/Program Files. Ordinary local tokens cannot create BA-owned backups.
        if !elevated_administrator() {
            return;
        }
        let stamp = std::time::SystemTime::now()
            .duration_since(std::time::UNIX_EPOCH)
            .unwrap()
            .as_nanos();
        let directory =
            std::env::temp_dir().join(format!("nimbo-backup-test-{}-{stamp}", std::process::id()));
        std::fs::create_dir(&directory).unwrap();
        let handle = open_directory(&directory, READ_CONTROL).unwrap();
        let before = Snapshot::read(&handle).unwrap();
        let (backup, file) = backup_acl(&directory, &before).unwrap();
        assert_eq!(
            std::fs::read(&backup).unwrap(),
            before.sddl().unwrap().as_bytes()
        );
        assert!(protected(&backup));
        assert_eq!(
            Snapshot::read(&handle).unwrap().sddl().unwrap(),
            before.sddl().unwrap()
        );
        drop(file);
        drop(handle);
        std::fs::remove_file(backup).unwrap();
        std::fs::remove_dir(directory).unwrap();
    }
    #[test]
    fn file_backup_transaction_never_changes_children_and_rolls_back() {
        let user = current_user().unwrap();
        let stamp = std::time::SystemTime::now()
            .duration_since(std::time::UNIX_EPOCH)
            .unwrap()
            .as_nanos();
        let directory =
            std::env::temp_dir().join(format!("nimbo-acl-test-{}-{stamp}", std::process::id()));
        let sd = fixture(&user, &format!("(A;OICI;FA;;;{user})"));
        let attributes = sd.attributes();
        assert_ne!(
            unsafe { CreateDirectoryW(wide(directory.as_os_str()).as_ptr(), &attributes) },
            0
        );
        let handle = open_directory(&directory, READ_CONTROL | WRITE_DAC).unwrap();
        // Exercise the real volume root's P|AI control flags, not only a new
        // directory's P-only defaults. The backup API must preserve both.
        let initial = Snapshot::read(&handle).unwrap();
        // Empty, owned fixture only: seed the modern model before making children.
        assert_eq!(
            unsafe {
                SetSecurityInfo(
                    handle.0 as _,
                    SE_FILE_OBJECT,
                    DACL_SECURITY_INFORMATION,
                    ptr::null_mut(),
                    ptr::null_mut(),
                    initial.dacl,
                    ptr::null_mut(),
                )
            },
            0
        );
        assert_ne!(
            Snapshot::read(&handle).unwrap().control & SE_DACL_AUTO_INHERITED,
            0
        );
        let child = directory.join("child");
        std::fs::create_dir(&child).unwrap();
        let grandchild = child.join("file.txt");
        std::fs::write(&grandchild, b"untouched").unwrap();
        let capture = |path: &Path| {
            let mut result = ptr::null_mut();
            assert_eq!(
                unsafe {
                    GetNamedSecurityInfoW(
                        wide(path.as_os_str()).as_ptr(),
                        SE_FILE_OBJECT,
                        OWNER_SECURITY_INFORMATION
                            | GROUP_SECURITY_INFORMATION
                            | DACL_SECURITY_INFORMATION,
                        ptr::null_mut(),
                        ptr::null_mut(),
                        ptr::null_mut(),
                        ptr::null_mut(),
                        &mut result,
                    )
                },
                0
            );
            Snapshot::from_descriptor(Descriptor(result))
                .unwrap()
                .sddl()
                .unwrap()
        };
        let children_before = (capture(&child), capture(&grandchild));
        let before = Snapshot::read(&handle).unwrap();
        // Fixture supplies a trusted owner solely to exercise the pure transform;
        // the transaction itself preserves the real temporary directory's owner.
        let planned =
            Snapshot::from_descriptor(fixture("SY", &format!("(A;OICI;FA;;;{user})"))).unwrap();
        assert_eq!(before.acl_bytes(), planned.acl_bytes());
        let changed = candidate(&planned, &user).unwrap();
        apply_verified(&handle, &directory, &before, &changed, || true).unwrap();
        assert_eq!((capture(&child), capture(&grandchild)), children_before);
        set_descriptor(&directory, before.sd.0).unwrap();
        assert_eq!(
            Snapshot::read(&handle).unwrap().sddl().unwrap(),
            before.sddl().unwrap()
        );
        assert_eq!(
            apply_verified(&handle, &directory, &before, &changed, || false),
            Err("PERMISSIONS_REPAIR_FAILED".into())
        );
        assert_eq!(
            Snapshot::read(&handle).unwrap().sddl().unwrap(),
            before.sddl().unwrap()
        );
        assert_eq!((capture(&child), capture(&grandchild)), children_before);
        drop(handle);
        // Exact owned fixture paths only, no recursive filesystem operation.
        std::fs::remove_file(grandchild).unwrap();
        std::fs::remove_dir(child).unwrap();
        std::fs::remove_dir(directory).unwrap();
    }
}
