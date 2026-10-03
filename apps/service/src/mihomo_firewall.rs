//! External session Kill Switch. Static WFP objects outlive the native child and
//! the helper handle. They end only on verified explicit release or BFE restart.
//! No global Windows Firewall policy, registry or foreign-filter operations.
use nimbo_ipc::windows::{private_directory, protected, protected_chain, root, wide, Descriptor};
use serde::{Deserialize, Serialize};
use std::{
    ffi::OsStr,
    fs,
    path::{Path, PathBuf},
    ptr,
};
use uuid::Uuid;
use windows_sys::{
    core::GUID,
    Win32::{
        Foundation::*,
        NetworkManagement::{
            IpHelper::ConvertInterfaceAliasToLuid, Ndis::NET_LUID_LH, WindowsFilteringPlatform::*,
        },
    },
};
const FAILURE: &str = "KILL_SWITCH_FAILED";
const OWNER: &str = "KILL_SWITCH_NOT_OWNED";
const ROLES: u8 = 5; // block, core, loopback, DHCP, exact TUN LUID; two IP families
fn checked(code: u32) -> Result<(), String> {
    if code == 0 {
        Ok(())
    } else {
        tracing::warn!(code, "native WFP operation rejected");
        Err(FAILURE.into())
    }
}
fn path() -> Result<PathBuf, String> {
    Ok(root()?.join("kill-switch").join("owner.json"))
}
pub fn pending() -> bool {
    path().map_or(
        true,
        |p| !matches!(fs::symlink_metadata(p), Err(e) if e.kind()==std::io::ErrorKind::NotFound),
    )
}
#[derive(Serialize, Deserialize)]
#[serde(deny_unknown_fields)]
struct Journal {
    version: u32,
    sid: String,
    sublayer: Uuid,
    #[serde(default)]
    native: Option<crate::mihomo_adapter::NativeProcess>,
    #[serde(default)]
    device: Option<crate::mihomo_adapter::Device>,
}
impl Journal {
    fn authorize(&self, sid: &str) -> Result<(), String> {
        if self.version != 1 || self.sid != sid {
            Err(OWNER.into())
        } else {
            Ok(())
        }
    }
    fn key(&self, family: u8, role: u8) -> GUID {
        let mut bytes = *self.sublayer.as_bytes();
        bytes[0] ^= 0x80;
        bytes[14] ^= family;
        bytes[15] ^= role;
        GUID::from_u128(u128::from_be_bytes(bytes))
    }
    fn layer_key(&self) -> GUID {
        GUID::from_u128(self.sublayer.as_u128())
    }
}
struct Engine(HANDLE);
// RPC engine handle may be owned by the serialized broker across worker threads.
unsafe impl Send for Engine {}
impl Engine {
    fn open() -> Result<Self, String> {
        let mut handle = ptr::null_mut();
        // Deliberately NOT a dynamic session: native/helper crash must not unblock.
        checked(unsafe {
            FwpmEngineOpen0(ptr::null(), 10, ptr::null(), ptr::null(), &mut handle)
        })?;
        Ok(Self(handle))
    }
    fn transaction(&self, f: impl FnOnce() -> Result<(), String>) -> Result<(), String> {
        checked(unsafe { FwpmTransactionBegin0(self.0, 0) })?;
        let result = f().and_then(|_| checked(unsafe { FwpmTransactionCommit0(self.0) }));
        if result.is_err() {
            checked(unsafe { FwpmTransactionAbort0(self.0) })?;
        }
        result
    }
    fn add(
        &self,
        j: &Journal,
        family: u8,
        role: u8,
        conditions: &mut [FWPM_FILTER_CONDITION0],
    ) -> Result<(), String> {
        let mut f: FWPM_FILTER0 = unsafe { std::mem::zeroed() };
        let mut name = wide(OsStr::new("Nimbo Mihomo session Kill Switch"));
        let sd = Descriptor::new("D:P(A;;GA;;;SY)(A;;GA;;;BA)")?;
        f.filterKey = j.key(family, role);
        f.displayData.name = name.as_mut_ptr();
        f.layerKey = if family == 0 {
            FWPM_LAYER_ALE_AUTH_CONNECT_V4
        } else {
            FWPM_LAYER_ALE_AUTH_CONNECT_V6
        };
        f.subLayerKey = j.layer_key();
        f.weight.r#type = FWP_UINT8;
        f.weight.Anonymous.uint8 = if role == 0 { 0 } else { 15 };
        // Permits are soft; never CLEAR_ACTION_RIGHT to bypass another firewall.
        f.action.r#type = if role == 0 {
            FWP_ACTION_BLOCK
        } else {
            FWP_ACTION_PERMIT
        };
        f.numFilterConditions = conditions.len() as u32;
        f.filterCondition = conditions.as_mut_ptr();
        checked(unsafe {
            FwpmFilterAdd0(
                self.0,
                &f,
                sd.attributes().lpSecurityDescriptor,
                ptr::null_mut(),
            )
        })
    }
}
impl Drop for Engine {
    fn drop(&mut self) {
        unsafe {
            FwpmEngineClose0(self.0);
        }
    }
}
struct AppId(*mut FWP_BYTE_BLOB);
impl AppId {
    fn new(path: &Path) -> Result<Self, String> {
        let mut blob = ptr::null_mut();
        checked(unsafe { FwpmGetAppIdFromFileName0(wide(path.as_os_str()).as_ptr(), &mut blob) })?;
        Ok(Self(blob))
    }
    fn condition(&self) -> FWPM_FILTER_CONDITION0 {
        let mut c = condition(FWPM_CONDITION_ALE_APP_ID, FWP_BYTE_BLOB_TYPE);
        c.conditionValue.Anonymous.byteBlob = self.0;
        c
    }
}
impl Drop for AppId {
    fn drop(&mut self) {
        unsafe {
            FwpmFreeMemory0((&mut self.0 as *mut *mut FWP_BYTE_BLOB).cast());
        }
    }
}
fn condition(field: GUID, kind: FWP_DATA_TYPE) -> FWPM_FILTER_CONDITION0 {
    let mut c: FWPM_FILTER_CONDITION0 = unsafe { std::mem::zeroed() };
    c.fieldKey = field;
    c.matchType = FWP_MATCH_EQUAL;
    c.conditionValue.r#type = kind;
    c
}
fn port(field: GUID, value: u16) -> FWPM_FILTER_CONDITION0 {
    let mut c = condition(field, FWP_UINT16);
    c.conditionValue.Anonymous.uint16 = value;
    c
}
fn load() -> Result<Option<Journal>, String> {
    let p = path()?;
    match fs::symlink_metadata(&p) {
        Err(e) if e.kind() == std::io::ErrorKind::NotFound => Ok(None),
        Ok(m)
            if m.is_file()
                && m.len() < 8192
                && protected_chain(p.parent().unwrap())
                && protected(&p) =>
        {
            serde_json::from_slice(&fs::read(p).map_err(|_| FAILURE)?)
                .map(Some)
                .map_err(|_| FAILURE.into())
        }
        _ => Err(FAILURE.into()),
    }
}
/// Positive enumeration, not an ambiguous alias-to-LUID error code. All TCP/IP
/// interfaces (including hidden virtual adapters) must lack the exact owner alias.
pub fn adapter_retired() -> Result<bool, String> {
    use windows_sys::Win32::NetworkManagement::IpHelper::{
        FreeMibTable, GetIfTable2, MIB_IF_TABLE2,
    };
    let mut table: *mut MIB_IF_TABLE2 = ptr::null_mut();
    checked(unsafe { GetIfTable2(&mut table) })?;
    if table.is_null() {
        return Err(FAILURE.into());
    }
    let rows = unsafe {
        std::slice::from_raw_parts((*table).Table.as_ptr(), (*table).NumEntries as usize)
    };
    let retired = rows.iter().all(|row| {
        let n = row
            .Alias
            .iter()
            .position(|c| *c == 0)
            .unwrap_or(row.Alias.len());
        !String::from_utf16_lossy(&row.Alias[..n]).eq_ignore_ascii_case("nimbo-mh0")
    });
    unsafe {
        FreeMibTable(table.cast());
    }
    Ok(retired)
}
pub fn available() -> bool {
    Engine::open().is_ok()
}
pub struct Firewall {
    engine: Engine,
    journal: Journal,
}
impl Firewall {
    pub fn arm(sid: &str, core: &Path, child: &std::process::Child) -> Result<Self, String> {
        if pending() {
            return Err("KILL_SWITCH_RESET_REQUIRED".into());
        }
        if !adapter_retired()? {
            return Err("TUN_IN_USE".into());
        }
        if !protected(core) || !protected_chain(core.parent().ok_or(FAILURE)?) {
            return Err(FAILURE.into());
        }
        let engine = Engine::open()?;
        let journal = Journal {
            version: 1,
            sid: sid.into(),
            sublayer: Uuid::new_v4(),
            native: Some(crate::mihomo_adapter::NativeProcess::capture(child)?),
            device: None,
        };
        let p = path()?;
        private_directory(p.parent().ok_or(FAILURE)?)?;
        // Atomic, flushed recovery record precedes the first network mutation.
        crate::mihomo_windows::replace_private(
            &p,
            &serde_json::to_vec(&journal).map_err(|_| FAILURE)?,
        )?;
        let result = engine.transaction(|| {
            let mut s: FWPM_SUBLAYER0 = unsafe { std::mem::zeroed() };
            let mut name = wide(OsStr::new("Nimbo Mihomo external Kill Switch"));
            let sd = Descriptor::new("D:P(A;;GA;;;SY)(A;;GA;;;BA)")?;
            s.subLayerKey = journal.layer_key();
            s.displayData.name = name.as_mut_ptr();
            s.weight = 0xfffe;
            checked(unsafe {
                FwpmSubLayerAdd0(engine.0, &s, sd.attributes().lpSecurityDescriptor)
            })?;
            let app = AppId::new(core)?;
            let mut system = [0u16; 32768];
            let n = unsafe {
                windows_sys::Win32::System::SystemInformation::GetSystemDirectoryW(
                    system.as_mut_ptr(),
                    system.len() as u32,
                )
            };
            if n == 0 || n as usize >= system.len() {
                return Err(FAILURE.into());
            }
            let dhcp = AppId::new(
                &PathBuf::from(String::from_utf16_lossy(&system[..n as usize])).join("svchost.exe"),
            )?;
            for family in 0..2 {
                engine.add(&journal, family, 0, &mut [])?;
                engine.add(&journal, family, 1, &mut [app.condition()])?;
                let mut loopback = condition(FWPM_CONDITION_FLAGS, FWP_UINT32);
                loopback.matchType = FWP_MATCH_FLAGS_ALL_SET;
                loopback.conditionValue.Anonymous.uint32 = FWP_CONDITION_FLAG_IS_LOOPBACK;
                engine.add(&journal, family, 2, &mut [loopback])?;
                let mut protocol = condition(FWPM_CONDITION_IP_PROTOCOL, FWP_UINT8);
                protocol.conditionValue.Anonymous.uint8 = 17;
                engine.add(
                    &journal,
                    family,
                    3,
                    &mut [
                        dhcp.condition(),
                        protocol,
                        port(
                            FWPM_CONDITION_IP_LOCAL_PORT,
                            if family == 0 { 68 } else { 546 },
                        ),
                        port(
                            FWPM_CONDITION_IP_REMOTE_PORT,
                            if family == 0 { 67 } else { 547 },
                        ),
                    ],
                )?;
            }
            Ok(())
        });
        if let Err(e) = result {
            // Explicit exact-key recovery, including commit/abort uncertainty.
            // Failed recovery keeps the private journal and fails closed.
            let _ = release(sid);
            return Err(e);
        }
        Ok(Self { engine, journal })
    }
    pub fn allow_tun(&mut self) -> Result<(), String> {
        let mut luid: NET_LUID_LH = unsafe { std::mem::zeroed() };
        checked(unsafe {
            ConvertInterfaceAliasToLuid(wide(OsStr::new("nimbo-mh0")).as_ptr(), &mut luid)
        })?;
        let mut value = unsafe { luid.Value };
        self.journal.device = Some(crate::mihomo_adapter::capture(value)?);
        // Exact kernel/device identity, never a client-supplied path or alias.
        crate::mihomo_windows::replace_private(
            &path()?,
            &serde_json::to_vec(&self.journal).map_err(|_| FAILURE)?,
        )?;
        self.engine.transaction(|| {
            for family in 0..2 {
                let mut c = condition(FWPM_CONDITION_IP_LOCAL_INTERFACE, FWP_UINT64);
                c.conditionValue.Anonymous.uint64 = &mut value;
                self.engine.add(&self.journal, family, 4, &mut [c])?;
            }
            Ok(())
        })
    }
    pub fn release(&self) -> Result<(), String> {
        release(&self.journal.sid)
    }
    // No Drop cleanup: abnormal native/helper exit intentionally retains filters.
}
/// Only explicit same-peer reset may retire a proven crash-retained adapter.
/// Original native exit is still reported as failure, never clean disconnect.
pub fn retire_owned_adapter(sid: &str) -> Result<(), String> {
    let Some(j) = load()? else {
        return if adapter_retired()? {
            Ok(())
        } else {
            Err("TUN_CLEANUP_FAILED".into())
        };
    };
    j.authorize(sid)?;
    if let Some(native) = &j.native {
        if !native.retired()? {
            return Err("DISCONNECT_BEFORE_CORE_CHANGE".into());
        }
    } else if !adapter_retired()? {
        return Err("TUN_CLEANUP_FAILED".into());
    }
    if let Some(device) = &j.device {
        crate::mihomo_adapter::retire(device)?;
    }
    if !adapter_retired()? {
        return Err("TUN_CLEANUP_FAILED".into());
    }
    Ok(())
}
pub fn release(sid: &str) -> Result<(), String> {
    let Some(j) = load()? else {
        return Ok(());
    };
    j.authorize(sid)?;
    let e = Engine::open()?;
    e.transaction(|| {
        for family in 0..2 {
            for role in 0..ROLES {
                let code = unsafe { FwpmFilterDeleteByKey0(e.0, &j.key(family, role)) };
                if code != FWP_E_FILTER_NOT_FOUND as u32 {
                    checked(code)?;
                }
            }
        }
        let code = unsafe { FwpmSubLayerDeleteByKey0(e.0, &j.layer_key()) };
        if code != FWP_E_SUBLAYER_NOT_FOUND as u32 {
            checked(code)?;
        }
        Ok(())
    })?;
    fs::remove_file(path()?).map_err(|_| FAILURE.into())
}
/// Explicit elevated uninstallation ends this product's own policy, even after
/// a helper crash. Never exposed on IPC; no foreign/global firewall operations.
pub(crate) fn release_for_uninstall() -> Result<(), String> {
    if let Some(journal) = load()? {
        release(&journal.sid)?;
    }
    Ok(())
}
#[cfg(test)]
mod tests {
    use super::*;
    #[test]
    fn journal_owner_is_not_a_client_supplied_exception() {
        let j = Journal {
            version: 1,
            sid: "S-1-5-21-42".into(),
            sublayer: Uuid::new_v4(),
            native: None,
            device: None,
        };
        assert!(j.authorize("S-1-5-21-42").is_ok());
        assert_eq!(j.authorize("S-1-5-21-43").unwrap_err(), OWNER);
        assert!(serde_json::from_str::<Journal>(r#"{"version":1,"sid":"a","sublayer":"00000000-0000-0000-0000-000000000000","executable":"foreign"}"#).is_err());
    }
    #[test]
    fn every_owned_key_is_unique_and_not_the_sublayer() {
        let j = Journal {
            version: 1,
            sid: "a".into(),
            sublayer: Uuid::new_v4(),
            native: None,
            device: None,
        };
        let mut keys = std::collections::HashSet::new();
        for f in 0..2 {
            for r in 0..ROLES {
                let k = j.key(f, r);
                assert!(keys.insert((k.data1, k.data2, k.data3, k.data4)));
            }
        }
        let k = j.layer_key();
        assert!(!keys.contains(&(k.data1, k.data2, k.data3, k.data4)));
    }
}
