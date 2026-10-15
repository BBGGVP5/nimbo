//! Exact, journal-bound Wintun crash recovery. Never remove a device by alias.
use nimbo_ipc::windows::{wide, Handle};
use serde::{Deserialize, Serialize};
use std::{
    ffi::OsStr,
    ptr,
    time::{Duration, Instant},
};
use uuid::Uuid;
use windows_sys::{
    core::GUID,
    Win32::{
        Devices::DeviceAndDriverInstallation::*,
        Foundation::*,
        NetworkManagement::IpHelper::{FreeMibTable, GetIfTable2, MIB_IF_ROW2, MIB_IF_TABLE2},
        System::{Registry::*, Threading::*},
    },
};
const FAILURE: &str = "TUN_CLEANUP_FAILED";
fn guid(g: GUID) -> Uuid {
    Uuid::from_fields(g.data1, g.data2, g.data3, &g.data4)
}
fn text(s: &[u16]) -> Result<String, String> {
    let n = s.iter().position(|c| *c == 0).ok_or(FAILURE)?;
    String::from_utf16(&s[..n]).map_err(|_| FAILURE.into())
}
fn interfaces() -> Result<Vec<MIB_IF_ROW2>, String> {
    let mut table: *mut MIB_IF_TABLE2 = ptr::null_mut();
    if unsafe { GetIfTable2(&mut table) } != 0 || table.is_null() {
        return Err(FAILURE.into());
    }
    let rows = unsafe {
        std::slice::from_raw_parts((*table).Table.as_ptr(), (*table).NumEntries as usize).to_vec()
    };
    unsafe { FreeMibTable(table.cast()) };
    Ok(rows)
}
#[derive(Debug, Clone, Serialize, Deserialize, PartialEq, Eq)]
#[serde(deny_unknown_fields)]
pub struct Device {
    interface: Uuid,
    luid: u64,
    instance: String,
}
impl Device {
    fn matches(&self, other: &Self) -> bool {
        self == other
    }
}
/// Creation time prevents a reused PID from masquerading as the original child.
#[derive(Serialize, Deserialize)]
#[serde(deny_unknown_fields)]
pub struct NativeProcess {
    pid: u32,
    created: u64,
}
fn created(handle: HANDLE) -> Result<u64, String> {
    let mut times: [FILETIME; 4] = unsafe { std::mem::zeroed() };
    if unsafe {
        GetProcessTimes(
            handle,
            &mut times[0],
            &mut times[1],
            &mut times[2],
            &mut times[3],
        )
    } == 0
    {
        return Err(FAILURE.into());
    }
    Ok((u64::from(times[0].dwHighDateTime) << 32) | u64::from(times[0].dwLowDateTime))
}
impl NativeProcess {
    pub fn capture(child: &std::process::Child) -> Result<Self, String> {
        use std::os::windows::io::AsRawHandle;
        Ok(Self {
            pid: child.id(),
            created: created(child.as_raw_handle() as _)?,
        })
    }
    pub fn retired(&self) -> Result<bool, String> {
        let h = unsafe {
            OpenProcess(
                PROCESS_QUERY_LIMITED_INFORMATION | PROCESS_SYNCHRONIZE,
                0,
                self.pid,
            )
        };
        if h.is_null() {
            return if unsafe { GetLastError() } == ERROR_INVALID_PARAMETER {
                Ok(true)
            } else {
                Err(FAILURE.into())
            };
        }
        let _handle = Handle(h as isize);
        if created(h)? != self.created {
            return Ok(true);
        }
        match unsafe { WaitForSingleObject(h, 0) } {
            WAIT_OBJECT_0 => Ok(true),
            WAIT_TIMEOUT => Ok(false),
            _ => Err(FAILURE.into()),
        }
    }
}
struct Devices(HDEVINFO);
impl Devices {
    fn open() -> Result<Self, String> {
        let h = unsafe {
            SetupDiGetClassDevsW(
                &GUID_DEVCLASS_NET,
                ptr::null(),
                ptr::null_mut(),
                DIGCF_PRESENT,
            )
        };
        if h == -1 {
            Err(FAILURE.into())
        } else {
            Ok(Self(h))
        }
    }
    fn interface(&self, row: &SP_DEVINFO_DATA) -> Result<Uuid, String> {
        let key = unsafe {
            SetupDiOpenDevRegKey(self.0, row, DICS_FLAG_GLOBAL, 0, DIREG_DRV, KEY_QUERY_VALUE)
        };
        if key as isize == -1 {
            return Err(FAILURE.into());
        }
        let mut value = [0u16; 64];
        let mut kind = 0;
        let mut len = std::mem::size_of_val(&value) as u32;
        let code = unsafe {
            RegQueryValueExW(
                key,
                wide(OsStr::new("NetCfgInstanceId")).as_ptr(),
                ptr::null(),
                &mut kind,
                value.as_mut_ptr().cast(),
                &mut len,
            )
        };
        unsafe { RegCloseKey(key) };
        if code != 0
            || kind != REG_SZ
            || len as usize > std::mem::size_of_val(&value)
            || len % 2 != 0
        {
            return Err(FAILURE.into());
        }
        Uuid::parse_str(&text(&value[..len as usize / 2])?).map_err(|_| FAILURE.into())
    }
    fn identity(
        &self,
        row: &SP_DEVINFO_DATA,
        interface: Uuid,
        luid: u64,
    ) -> Result<Device, String> {
        if guid(row.ClassGuid) != guid(GUID_DEVCLASS_NET) || self.interface(row)? != interface {
            return Err(FAILURE.into());
        }
        let mut hardware = [0u16; 512];
        let mut kind = 0;
        let mut len = 0;
        if unsafe {
            SetupDiGetDeviceRegistryPropertyW(
                self.0,
                row,
                SPDRP_HARDWAREID,
                &mut kind,
                hardware.as_mut_ptr().cast(),
                std::mem::size_of_val(&hardware) as u32,
                &mut len,
            )
        } == 0
            || kind != REG_MULTI_SZ
            || len as usize > std::mem::size_of_val(&hardware)
            || len % 2 != 0
            || !hardware[..len as usize / 2]
                .split(|c| *c == 0)
                .any(|s| String::from_utf16(s).is_ok_and(|s| s.eq_ignore_ascii_case("Wintun")))
        {
            return Err(FAILURE.into());
        }
        let mut instance = [0u16; 512];
        if unsafe {
            SetupDiGetDeviceInstanceIdW(
                self.0,
                row,
                instance.as_mut_ptr(),
                instance.len() as u32,
                ptr::null_mut(),
            )
        } == 0
        {
            return Err(FAILURE.into());
        }
        Ok(Device {
            interface,
            luid,
            instance: text(&instance)?,
        })
    }
}
impl Drop for Devices {
    fn drop(&mut self) {
        unsafe { SetupDiDestroyDeviceInfoList(self.0) };
    }
}
pub fn capture(luid: u64) -> Result<Device, String> {
    let rows = interfaces()?;
    let row = rows
        .iter()
        .find(|r| unsafe { r.InterfaceLuid.Value } == luid)
        .ok_or(FAILURE)?;
    if !text(&row.Alias)?.eq_ignore_ascii_case("nimbo-mh0") {
        return Err(FAILURE.into());
    }
    let interface = guid(row.InterfaceGuid);
    let devices = Devices::open()?;
    for i in 0..4096 {
        let mut row: SP_DEVINFO_DATA = unsafe { std::mem::zeroed() };
        row.cbSize = std::mem::size_of_val(&row) as u32;
        if unsafe { SetupDiEnumDeviceInfo(devices.0, i, &mut row) } == 0 {
            return Err(FAILURE.into());
        }
        if devices.interface(&row).is_ok_and(|g| g == interface) {
            return devices.identity(&row, interface, luid);
        }
    }
    Err(FAILURE.into())
}
pub fn retire(owned: &Device) -> Result<(), String> {
    let rows = interfaces()?;
    let Some(current) = rows
        .iter()
        .find(|r| guid(r.InterfaceGuid) == owned.interface)
    else {
        return if crate::mihomo_firewall::adapter_retired()? {
            Ok(())
        } else {
            Err(FAILURE.into())
        };
    };
    if unsafe { current.InterfaceLuid.Value } != owned.luid
        || !text(&current.Alias)?.eq_ignore_ascii_case("nimbo-mh0")
    {
        return Err(FAILURE.into());
    }
    let devices = Devices::open()?;
    let mut row: SP_DEVINFO_DATA = unsafe { std::mem::zeroed() };
    row.cbSize = std::mem::size_of_val(&row) as u32;
    if unsafe {
        SetupDiOpenDeviceInfoW(
            devices.0,
            wide(OsStr::new(&owned.instance)).as_ptr(),
            ptr::null_mut(),
            0,
            &mut row,
        )
    } == 0
        || !owned.matches(&devices.identity(&row, owned.interface, owned.luid)?)
    {
        return Err(FAILURE.into());
    }
    let mut remove: SP_REMOVEDEVICE_PARAMS = unsafe { std::mem::zeroed() };
    remove.ClassInstallHeader.cbSize = std::mem::size_of::<SP_CLASSINSTALL_HEADER>() as u32;
    remove.ClassInstallHeader.InstallFunction = DIF_REMOVE;
    remove.Scope = DI_REMOVEDEVICE_GLOBAL;
    if unsafe {
        SetupDiSetClassInstallParamsW(
            devices.0,
            &row,
            &remove.ClassInstallHeader,
            std::mem::size_of_val(&remove) as u32,
        )
    } == 0
        || unsafe { SetupDiCallClassInstaller(DIF_REMOVE, devices.0, &row) } == 0
    {
        return Err(FAILURE.into());
    }
    let mut install: SP_DEVINSTALL_PARAMS_W = unsafe { std::mem::zeroed() };
    install.cbSize = std::mem::size_of_val(&install) as u32;
    if unsafe { SetupDiGetDeviceInstallParamsW(devices.0, &row, &mut install) } == 0
        || install.Flags & (DI_NEEDREBOOT | DI_NEEDRESTART) != 0
    {
        // Pending restart is not verified retirement; preserve external blocking.
        return Err(FAILURE.into());
    }
    drop(devices);
    let deadline = Instant::now() + Duration::from_secs(3);
    loop {
        if crate::mihomo_firewall::adapter_retired()? {
            return Ok(());
        }
        if Instant::now() >= deadline {
            return Err(FAILURE.into());
        }
        std::thread::sleep(Duration::from_millis(25));
    }
}
#[cfg(test)]
mod tests {
    use super::*;
    #[test]
    fn native_creation_time_prevents_live_or_reused_pid_confusion() {
        use std::{
            os::windows::process::CommandExt,
            process::{Command, Stdio},
        };
        let mut child = Command::new("powershell.exe")
            .args([
                "-NoProfile",
                "-NonInteractive",
                "-Command",
                "[Console]::In.ReadToEnd() | Out-Null",
            ])
            .creation_flags(0x08000000)
            .stdin(Stdio::piped())
            .stdout(Stdio::null())
            .stderr(Stdio::null())
            .spawn()
            .unwrap();
        let native = NativeProcess::capture(&child).unwrap();
        let live = native.retired();
        let reused = NativeProcess {
            pid: native.pid,
            created: native.created ^ 1,
        }
        .retired();
        drop(child.stdin.take());
        let _ = child.kill();
        child.wait().unwrap();
        assert_eq!(live, Ok(false));
        assert_eq!(reused, Ok(true));
        assert_eq!(native.retired(), Ok(true));
    }
    #[test]
    fn replacement_is_never_an_owned_device() {
        let owned = Device {
            interface: Uuid::new_v4(),
            luid: 42,
            instance: "SWD\\WINTUN\\owned".into(),
        };
        assert!(owned.matches(&owned.clone()));
        let mut other = owned.clone();
        other.interface = Uuid::new_v4();
        assert!(!owned.matches(&other));
        let mut other = owned.clone();
        other.luid += 1;
        assert!(!owned.matches(&other));
        let mut other = owned.clone();
        other.instance.push('x');
        assert!(!owned.matches(&other));
        assert!(serde_json::from_str::<Device>(r#"{"interface":"00000000-0000-0000-0000-000000000000","luid":42,"instance":"x","path":"foreign"}"#).is_err());
    }
}
