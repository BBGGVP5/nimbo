Unicode true
SetCompressor /SOLID lzma

!define PRODUCT_NAME "Nimbo"
!define PRODUCT_VERSION "0.1.0"
!define PRODUCT_PUBLISHER "BBGGVP5"
!define PRODUCT_EXE "Nimbo.exe"
!define APP_ID "Nimbo"
!define ROOT_DIR "${__FILEDIR__}\..\..\..\.."
!define TAURI_DIR "${__FILEDIR__}\.."

!ifndef PRODUCT_ARCH
  !define PRODUCT_ARCH "x64"
!endif

!ifndef RELEASE_EXE
  !define RELEASE_EXE "${ROOT_DIR}\target\release\nimbo-ui.exe"
!endif

!ifndef HELPER_EXE
  !define HELPER_EXE "${ROOT_DIR}\target\release\nimbo-svc.exe"
!endif

!ifndef OUT_FILE
  !define OUT_FILE "${ROOT_DIR}\target\release\bundle\nsis\Nimbo_0.1.0_${PRODUCT_ARCH}-setup.exe"
!endif

Name "${PRODUCT_NAME}"
OutFile "${OUT_FILE}"
InstallDir "$LOCALAPPDATA\Programs\Nimbo"
InstallDirRegKey HKCU "Software\${APP_ID}" "InstallDir"
; We launch as `user` so that the installer can show its own friendly
; "needs admin / will close helper" dialog before any UAC prompt. The
; .onInit function below detects non-admin and offers to relaunch
; itself via ExecShell "runas" (Windows will then show UAC). Under
; Admin Approval Mode (the Windows default), elevation keeps the same
; user token, so $LOCALAPPDATA and HKCU continue to refer to the
; original user's profile / hive.
RequestExecutionLevel user

Icon "${TAURI_DIR}\icons\icon.ico"
UninstallIcon "${TAURI_DIR}\icons\icon.ico"
VIProductVersion "0.1.0.0"
VIAddVersionKey "ProductName" "${PRODUCT_NAME}"
VIAddVersionKey "CompanyName" "${PRODUCT_PUBLISHER}"
VIAddVersionKey "FileDescription" "Установщик ${PRODUCT_NAME}"
VIAddVersionKey "FileVersion" "${PRODUCT_VERSION}"
VIAddVersionKey "LegalCopyright" "Copyright (c) 2026 ${PRODUCT_PUBLISHER}"
VIAddVersionKey "ProductVersion" "${PRODUCT_VERSION}"

!include "MUI2.nsh"
!include "LogicLib.nsh"
!include "StrFunc.nsh"
!include "FileFunc.nsh"

!insertmacro GetSize

; StrFunc.nsh requires explicit declaration of each helper before use.
${StrStr}

!define MUI_ABORTWARNING
!define MUI_ICON "${TAURI_DIR}\icons\icon.ico"
!define MUI_UNICON "${TAURI_DIR}\icons\icon.ico"
!define MUI_HEADERIMAGE
!define MUI_HEADERIMAGE_RIGHT
!define MUI_HEADERIMAGE_BITMAP "${TAURI_DIR}\windows\installer-header.bmp"
!define MUI_HEADERIMAGE_UNBITMAP "${TAURI_DIR}\windows\installer-header.bmp"
!define MUI_WELCOMEFINISHPAGE_BITMAP "${TAURI_DIR}\windows\installer-welcome.bmp"
!define MUI_UNWELCOMEFINISHPAGE_BITMAP "${TAURI_DIR}\windows\installer-welcome.bmp"
!define MUI_WELCOMEPAGE_TITLE "Установка Nimbo"
!define MUI_WELCOMEPAGE_TEXT "Мастер установит Nimbo на этот компьютер.$\r$\n$\r$\nПеред продолжением закройте запущенное приложение Nimbo, если оно открыто."
!define MUI_COMPONENTSPAGE_TEXT_TOP "Выберите дополнительные действия для установки Nimbo."
!define MUI_DIRECTORYPAGE_TEXT_TOP "Выберите папку, в которую будет установлен Nimbo."
!define MUI_FINISHPAGE_TITLE "Nimbo установлен"
!define MUI_FINISHPAGE_TEXT "Установка завершена. Можно сразу открыть приложение."
!define MUI_FINISHPAGE_RUN "$INSTDIR\${PRODUCT_EXE}"
!define MUI_FINISHPAGE_RUN_TEXT "Открыть Nimbo"

!insertmacro MUI_PAGE_WELCOME
!insertmacro MUI_PAGE_COMPONENTS
!insertmacro MUI_PAGE_DIRECTORY
!insertmacro MUI_PAGE_INSTFILES
!insertmacro MUI_PAGE_FINISH
!insertmacro MUI_UNPAGE_CONFIRM
!insertmacro MUI_UNPAGE_INSTFILES

!insertmacro MUI_LANGUAGE "Russian"

LangString DESC_SecApp ${LANG_RUSSIAN} "Основные файлы приложения Nimbo."
LangString DESC_SecStartMenu ${LANG_RUSSIAN} "Добавить ярлык Nimbo в меню «Пуск»."
LangString DESC_SecDesktop ${LANG_RUSSIAN} "Добавить ярлык Nimbo на рабочий стол."

Section "!Nimbo" SEC_APP
  SectionIn RO

  ; --- Upgrade-safe extraction. ---
  ; Running Nimbo would normally lock Nimbo.exe; the helper service locks
  ; nimbo-svc.exe. Windows allows *renaming* a running .exe even when it
  ; is locked, so we rename the existing binaries to .old (no admin
  ; required) and extract the new ones onto an unblocked slot. The old
  ; service keeps running on the renamed image until --install later
  ; stops it, updates the config, and starts the freshly extracted exe.
  ; All subprocesses are spawned via nsExec::Exec (CREATE_NO_WINDOW),
  ; and nimbo-svc.exe is windows-subsystem — no console flash.
  ${If} ${FileExists} "$INSTDIR\${PRODUCT_EXE}"
    nsExec::Exec 'taskkill.exe /F /IM ${PRODUCT_EXE} /T'
    Pop $0
    Sleep 300
    Delete "$INSTDIR\${PRODUCT_EXE}.old"
    Rename "$INSTDIR\${PRODUCT_EXE}" "$INSTDIR\${PRODUCT_EXE}.old"
    ${If} ${Errors}
      DetailPrint "Не удалось переименовать ${PRODUCT_EXE} (повторяю через задержку)"
      Sleep 600
      ClearErrors
      Rename "$INSTDIR\${PRODUCT_EXE}" "$INSTDIR\${PRODUCT_EXE}.old"
    ${EndIf}
  ${EndIf}
  ${If} ${FileExists} "$INSTDIR\nimbo-svc.exe"
    Delete "$INSTDIR\nimbo-svc.exe.old"
    Rename "$INSTDIR\nimbo-svc.exe" "$INSTDIR\nimbo-svc.exe.old"
    ${If} ${Errors}
      DetailPrint "Не удалось переименовать nimbo-svc.exe (повторяю через задержку)"
      Sleep 600
      ClearErrors
      Rename "$INSTDIR\nimbo-svc.exe" "$INSTDIR\nimbo-svc.exe.old"
    ${EndIf}
  ${EndIf}

  SetOutPath "$INSTDIR"
  File "/oname=${PRODUCT_EXE}" "${RELEASE_EXE}"
  File "/oname=nimbo-svc.exe" "${HELPER_EXE}"
  File "${TAURI_DIR}\icons\icon.ico"

  CreateDirectory "$APPDATA\Nimbo\bin"
  SetOutPath "$APPDATA\Nimbo\bin"
  File "${TAURI_DIR}\resources\tun\tun2socks.exe"
  File "${TAURI_DIR}\resources\tun\wintun.dll"

  SetOutPath "$INSTDIR"
  WriteRegStr HKCU "Software\${APP_ID}" "InstallDir" "$INSTDIR"

  WriteRegStr HKCU "Software\Microsoft\Windows\CurrentVersion\Uninstall\${APP_ID}" "DisplayName" "${PRODUCT_NAME}"
  WriteRegStr HKCU "Software\Microsoft\Windows\CurrentVersion\Uninstall\${APP_ID}" "DisplayVersion" "${PRODUCT_VERSION}"
  WriteRegStr HKCU "Software\Microsoft\Windows\CurrentVersion\Uninstall\${APP_ID}" "Publisher" "${PRODUCT_PUBLISHER}"
  WriteRegStr HKCU "Software\Microsoft\Windows\CurrentVersion\Uninstall\${APP_ID}" "DisplayIcon" "$INSTDIR\${PRODUCT_EXE}"
  WriteRegStr HKCU "Software\Microsoft\Windows\CurrentVersion\Uninstall\${APP_ID}" "InstallLocation" "$INSTDIR"
  WriteRegStr HKCU "Software\Microsoft\Windows\CurrentVersion\Uninstall\${APP_ID}" "UninstallString" '"$INSTDIR\Uninstall.exe"'
  WriteRegDWORD HKCU "Software\Microsoft\Windows\CurrentVersion\Uninstall\${APP_ID}" "NoModify" 1
  WriteRegDWORD HKCU "Software\Microsoft\Windows\CurrentVersion\Uninstall\${APP_ID}" "NoRepair" 1

  ; Dynamically calculate the actual size of the installed folders (in KB)
  ${GetSize} "$INSTDIR" "/S=0K /G=1" $2 $3 $4
  ${GetSize} "$APPDATA\Nimbo" "/S=0K /G=1" $5 $3 $4
  IntOp $2 $2 + $5
  WriteRegDWORD HKCU "Software\Microsoft\Windows\CurrentVersion\Uninstall\${APP_ID}" "EstimatedSize" $2

  WriteRegStr HKCU "Software\Classes\nimbo" "" "URL:Nimbo Protocol"
  WriteRegStr HKCU "Software\Classes\nimbo" "URL Protocol" ""
  WriteRegStr HKCU "Software\Classes\nimbo\DefaultIcon" "" "$INSTDIR\${PRODUCT_EXE},0"
  WriteRegStr HKCU "Software\Classes\nimbo\shell\open\command" "" '"$INSTDIR\${PRODUCT_EXE}" "%1"'

  WriteUninstaller "$INSTDIR\Uninstall.exe"

  ; --install-tun does not need elevation and Nimbo.exe is windows-
  ; subsystem, so ExecWait is fine — no console flash, no UAC.
  DetailPrint "Установка TUN-компонентов..."
  ExecWait '"$INSTDIR\${PRODUCT_EXE}" --install-tun' $0
  ${If} $0 != 0
    DetailPrint "Установка TUN-компонентов завершилась с кодом $0"
  ${EndIf}

  ; Installer already runs admin (RequestExecutionLevel admin), so the
  ; child inherits admin and nimbo-svc.exe sees is_elevated()=true —
  ; no UAC, no ShellExecuteEx, no nested elevation. Plain ExecWait.
  DetailPrint "Регистрация вспомогательного сервиса Nimbo..."
  ExecWait '"$INSTDIR\nimbo-svc.exe" --install' $1
  ${If} $1 != 0
    DetailPrint "Установка хелпер-сервиса завершилась с кодом $1"
  ${EndIf}
SectionEnd

Section "Ярлык в меню Пуск" SEC_START_MENU
  CreateDirectory "$SMPROGRAMS\Nimbo"
  CreateShortcut "$SMPROGRAMS\Nimbo\Nimbo.lnk" "$INSTDIR\${PRODUCT_EXE}"
SectionEnd

Section "Ярлык на рабочем столе" SEC_DESKTOP
  CreateShortcut "$DESKTOP\Nimbo.lnk" "$INSTDIR\${PRODUCT_EXE}"
SectionEnd

Section "Uninstall"
  Delete "$DESKTOP\Nimbo.lnk"
  Delete "$SMPROGRAMS\Nimbo\Nimbo.lnk"
  RMDir "$SMPROGRAMS\Nimbo"

  DeleteRegKey HKCU "Software\Classes\nimbo"
  DeleteRegKey HKCU "Software\Microsoft\Windows\CurrentVersion\Uninstall\${APP_ID}"
  DeleteRegKey HKCU "Software\${APP_ID}"

  ${If} ${FileExists} "$INSTDIR\${PRODUCT_EXE}"
    nsExec::Exec 'taskkill.exe /F /IM ${PRODUCT_EXE} /T'
    Pop $0
  ${EndIf}
  ${If} ${FileExists} "$INSTDIR\nimbo-svc.exe"
    DetailPrint "Удаление вспомогательного сервиса Nimbo..."
    ExecWait '"$INSTDIR\nimbo-svc.exe" --uninstall' $0
    ${If} $0 != 0
      DetailPrint "Удаление вспомогательного сервиса завершилось с кодом $0"
    ${EndIf}
  ${EndIf}
  Sleep 600

  Delete "$APPDATA\Nimbo\bin\tun2socks.exe"
  Delete "$APPDATA\Nimbo\bin\wintun.dll"
  RMDir "$APPDATA\Nimbo\bin"

  Delete "$INSTDIR\nimbo-svc.exe"
  Delete "$INSTDIR\icon.ico"
  Delete "$INSTDIR\${PRODUCT_EXE}"
  Delete "$INSTDIR\Uninstall.exe"
  RMDir "$INSTDIR"
SectionEnd

!insertmacro MUI_FUNCTION_DESCRIPTION_BEGIN
  !insertmacro MUI_DESCRIPTION_TEXT ${SEC_APP} $(DESC_SecApp)
  !insertmacro MUI_DESCRIPTION_TEXT ${SEC_START_MENU} $(DESC_SecStartMenu)
  !insertmacro MUI_DESCRIPTION_TEXT ${SEC_DESKTOP} $(DESC_SecDesktop)
!insertmacro MUI_FUNCTION_DESCRIPTION_END

; ──────────────────────────────────────────────────────────────────────────
; Helpers
; ──────────────────────────────────────────────────────────────────────────

; .onInit fires before any page is shown.
;
;   * Launched **as admin** (right-click → "Run as administrator", or
;     this is the relaunched-elevated instance of ourselves) → no
;     dialogs, no questions, straight to the install pages. Section APP
;     will silently taskkill Nimbo, rename the old binaries, and update
;     the service.
;
;   * Launched **as user** → we need admin to register the service. Show
;     one MessageBox that explains why (mentioning the running helper
;     service if applicable) and offers a relaunch via UAC. Cancel
;     aborts the installer; OK relaunches us with the "runas" verb.
Function .onInit
  UserInfo::GetAccountType
  Pop $0
  ${If} $0 == "Admin"
    Return
  ${EndIf}

  ; Not admin. Detect if the helper service is currently running so we
  ; can tell the user what to expect.
  Call CheckHelperServiceRunning
  Pop $1

  ${If} $1 == "1"
    MessageBox MB_OKCANCEL|MB_ICONEXCLAMATION \
      "Установщик Nimbo требует прав администратора.$\r$\n$\r$\nСейчас запущен вспомогательный сервис Nimbo — он будет закрыт после перезапуска от имени администратора.$\r$\n$\r$\nПерезапустить от имени администратора?" \
      IDOK lbl_relaunch_admin
  ${Else}
    MessageBox MB_OKCANCEL|MB_ICONEXCLAMATION \
      "Установщик Nimbo требует прав администратора для регистрации вспомогательного сервиса.$\r$\n$\r$\nПерезапустить от имени администратора?" \
      IDOK lbl_relaunch_admin
  ${EndIf}
  Abort

  lbl_relaunch_admin:
    ; Fire-and-forget relaunch under the "runas" verb — Windows shows
    ; the UAC consent dialog, then starts a fresh installer instance
    ; with the elevated token. The current (un-elevated) instance
    ; quits so no leftover non-admin process sticks around.
    ExecShell "runas" "$EXEPATH"
    Quit
FunctionEnd
; Pushes "1" if the NimboHelper service is in the RUNNING state, "0"
; otherwise (including: service does not exist, not started, sc query
; failed). Uses sc.exe via nsExec so there is no console flash.
; Clobbers $2, $3, $4 — callers (so far only .onInit) must not rely on
; those after the call.
Function CheckHelperServiceRunning
  nsExec::ExecToStack 'sc.exe query NimboHelper'
  Pop $2   ; nsExec exit code
  Pop $3   ; combined stdout
  StrCpy $4 "0"
  ${If} $2 == 0
    ${StrStr} $2 $3 "RUNNING"
    ${If} $2 != ""
      StrCpy $4 "1"
    ${EndIf}
  ${EndIf}
  Push $4
FunctionEnd
