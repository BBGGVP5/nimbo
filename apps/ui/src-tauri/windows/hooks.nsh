!macro NSIS_HOOK_POSTINSTALL
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

  ${If} ${FileExists} "$INSTDIR\Nimbo.exe"
    ExecWait '"$INSTDIR\Nimbo.exe" --install-tun' $0
    ${If} $0 != 0
      DetailPrint "Nimbo TUN dependency installation finished with code $0"
    ${EndIf}
  ${EndIf}
!macroend
