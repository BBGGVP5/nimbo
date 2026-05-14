Unicode true
SetCompressor /SOLID lzma

!define PRODUCT_NAME "Nimbo"
!define PRODUCT_VERSION "0.1.0"
!define PRODUCT_PUBLISHER "Danila"
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

!ifndef OUT_FILE
  !define OUT_FILE "${ROOT_DIR}\target\release\bundle\nsis\Nimbo_0.1.0_${PRODUCT_ARCH}-setup.exe"
!endif

Name "${PRODUCT_NAME}"
OutFile "${OUT_FILE}"
InstallDir "$LOCALAPPDATA\Programs\Nimbo"
InstallDirRegKey HKCU "Software\${APP_ID}" "InstallDir"
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

  SetOutPath "$INSTDIR"
  File "/oname=${PRODUCT_EXE}" "${RELEASE_EXE}"
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

  WriteRegStr HKCU "Software\Classes\nimbo" "" "URL:Nimbo Protocol"
  WriteRegStr HKCU "Software\Classes\nimbo" "URL Protocol" ""
  WriteRegStr HKCU "Software\Classes\nimbo\DefaultIcon" "" "$INSTDIR\${PRODUCT_EXE},0"
  WriteRegStr HKCU "Software\Classes\nimbo\shell\open\command" "" '"$INSTDIR\${PRODUCT_EXE}" "%1"'

  WriteUninstaller "$INSTDIR\Uninstall.exe"

  ExecWait '"$INSTDIR\${PRODUCT_EXE}" --install-tun' $0
  ${If} $0 != 0
    DetailPrint "Установка TUN-компонентов завершилась с кодом $0"
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

  Delete "$APPDATA\Nimbo\bin\tun2socks.exe"
  Delete "$APPDATA\Nimbo\bin\wintun.dll"
  RMDir "$APPDATA\Nimbo\bin"

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
