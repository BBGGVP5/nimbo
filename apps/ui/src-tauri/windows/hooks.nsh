!macro NSIS_HOOK_PREINSTALL
  ; Upgrade-safe extraction: kill the UI (user-owned process — no admin
  ; needed) and rename existing binaries to .old. Windows allows renaming
  ; a locked .exe, so the helper service can keep running on the renamed
  ; image until the post-install --install step stops, reconfigures, and
  ; restarts it on the freshly extracted binary.
  ${If} ${FileExists} "$INSTDIR\Nimbo.exe"
    nsExec::Exec 'taskkill.exe /F /IM Nimbo.exe /T'
    Pop $0
    Sleep 300
    Delete "$INSTDIR\Nimbo.exe.old"
    Rename "$INSTDIR\Nimbo.exe" "$INSTDIR\Nimbo.exe.old"
    ${If} ${Errors}
      Sleep 600
      ClearErrors
      Rename "$INSTDIR\Nimbo.exe" "$INSTDIR\Nimbo.exe.old"
    ${EndIf}
  ${EndIf}
  ${If} ${FileExists} "$INSTDIR\nimbo-svc.exe"
    Delete "$INSTDIR\nimbo-svc.exe.old"
    Rename "$INSTDIR\nimbo-svc.exe" "$INSTDIR\nimbo-svc.exe.old"
    ${If} ${Errors}
      Sleep 600
      ClearErrors
      Rename "$INSTDIR\nimbo-svc.exe" "$INSTDIR\nimbo-svc.exe.old"
    ${EndIf}
  ${EndIf}

!macroend

!macro NSIS_HOOK_POSTINSTALL
  StrCpy $R8 "0"
  CreateDirectory "$APPDATA\Nimbo\bin"

  ${If} ${FileExists} "$INSTDIR\resources\resources\tun\tun2socks.exe"
    CopyFiles /SILENT "$INSTDIR\resources\resources\tun\tun2socks.exe" "$APPDATA\Nimbo\bin\tun2socks.exe"
  ${EndIf}

  ${If} ${FileExists} "$INSTDIR\resources\resources\tun\wintun.dll"
    CopyFiles /SILENT "$INSTDIR\resources\resources\tun\wintun.dll" "$APPDATA\Nimbo\bin\wintun.dll"
  ${EndIf}

  ${If} ${FileExists} "$INSTDIR\resources\tun\tun2socks.exe"
    CopyFiles /SILENT "$INSTDIR\resources\tun\tun2socks.exe" "$APPDATA\Nimbo\bin\tun2socks.exe"
  ${EndIf}

  ${If} ${FileExists} "$INSTDIR\resources\tun\wintun.dll"
    CopyFiles /SILENT "$INSTDIR\resources\tun\wintun.dll" "$APPDATA\Nimbo\bin\wintun.dll"
  ${EndIf}

  ; --install-tun does not need elevation; Nimbo.exe is windows-subsystem,
  ; so ExecWait is fine — no console flash, no UAC.
  ${If} ${FileExists} "$INSTDIR\Nimbo.exe"
    DetailPrint "Installing Nimbo TUN dependencies..."
    ExecWait '"$INSTDIR\Nimbo.exe" --install-tun' $0
    ${If} $0 != 0
      DetailPrint "Nimbo TUN dependency installation finished with code $0"
      StrCpy $R8 "$0"
    ${EndIf}
  ${EndIf}

  ; Tauri's NSIS bundler defaults to perMachine which runs the whole
  ; installer admin, so the child inherits the admin token and nimbo-svc
  ; sees is_elevated()=true. Plain ExecWait — no nested UAC, no console.
  ${If} ${FileExists} "$INSTDIR\nimbo-svc.exe"
    DetailPrint "Registering Nimbo helper service..."
    ExecWait '"$INSTDIR\nimbo-svc.exe" --install' $1
    ${If} $1 != 0
      DetailPrint "Nimbo helper service install finished with code $1"
      StrCpy $R8 "$1"
    ${EndIf}
  ${EndIf}

  ; Validate the freshly installed application before discarding the
  ; upgrade-safe .old binaries created by NSIS_HOOK_PREINSTALL.
  ${If} ${FileExists} "$INSTDIR\Nimbo.exe"
    StrCpy $2 "$R8"
    ${If} $R8 == "0"
      DetailPrint "Checking the updated Nimbo build..."
      ExecWait '"$INSTDIR\Nimbo.exe" --update-health-check' $2
    ${EndIf}
    ${If} $2 != 0
      DetailPrint "Health check failed with code $2; restoring the previous build"
      ${If} ${FileExists} "$INSTDIR\nimbo-svc.exe"
        ExecWait '"$INSTDIR\nimbo-svc.exe" --uninstall' $3
      ${EndIf}
      Delete "$INSTDIR\Nimbo.exe"
      Delete "$INSTDIR\nimbo-svc.exe"
      ${If} ${FileExists} "$INSTDIR\Nimbo.exe.old"
        Rename "$INSTDIR\Nimbo.exe.old" "$INSTDIR\Nimbo.exe"
      ${EndIf}
      ${If} ${FileExists} "$INSTDIR\nimbo-svc.exe.old"
        Rename "$INSTDIR\nimbo-svc.exe.old" "$INSTDIR\nimbo-svc.exe"
        ExecWait '"$INSTDIR\nimbo-svc.exe" --install' $4
      ${EndIf}
      MessageBox MB_OK|MB_ICONSTOP "Nimbo update failed its health check. The previous version was restored."
      SetErrorLevel 1
      Abort
    ${EndIf}
    Delete "$INSTDIR\Nimbo.exe.old"
    Delete "$INSTDIR\nimbo-svc.exe.old"
  ${EndIf}
!macroend
