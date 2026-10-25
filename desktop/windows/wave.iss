; Wave's Windows installer (Inno Setup 6).
; Built in CI: ISCC /DVersion=1.2.3 /DSource=<Wave app folder> /DOutDir=<dir> desktop\windows\wave.iss
;
; - Installs for you only (no admin prompt) into %LOCALAPPDATA%\Programs\Wave.
; - Installs over any earlier Wave, whatever its version, keeping your sign-in, settings and
;   queue (they live in %APPDATA%\Wave, which installing never touches).
; - Closes every running Wave first, and removes the older-style (MSI) Wave if it's there.
; - Options: a desktop shortcut, and opening Wave when done. Never starts with Windows.
; - Deletes its own installer file when it's done.
; - Updates from inside Wave run it silently: /VERYSILENT /RELAUNCH=1 (reopens Wave after).

#ifndef Version
  #define Version "0.0.0"
#endif
#ifndef Source
  #define Source "..\build\compose\binaries\main-release\app\Wave"
#endif
#ifndef OutDir
  #define OutDir "..\build\installer"
#endif

[Setup]
AppId={{B7E2C1A4-5D3F-4E8B-9A61-0C4D2E7F9A13}
AppName=Wave
AppVersion={#Version}
AppVerName=Wave {#Version}
AppPublisher=Wave
AppPublisherURL=https://github.com/ReUnknown/Wave
AppSupportURL=https://github.com/ReUnknown/Wave/issues
AppUpdatesURL=https://github.com/ReUnknown/Wave/releases
DefaultDirName={localappdata}\Programs\Wave
UsePreviousAppDir=yes
DisableDirPage=auto
DisableProgramGroupPage=yes
DisableReadyPage=yes
PrivilegesRequired=lowest
ArchitecturesAllowed=x64compatible
ArchitecturesInstallIn64BitMode=x64compatible
OutputDir={#OutDir}
OutputBaseFilename=Wave-{#Version}-windows-x64
SetupIconFile=..\icons\wave.ico
UninstallDisplayIcon={app}\Wave.exe
UninstallDisplayName=Wave
WizardStyle=modern
Compression=lzma2/ultra64
SolidCompression=yes
; Anything still holding Wave's files is closed (we also end Wave ourselves, below).
CloseApplications=force
RestartApplications=no

[Tasks]
Name: "desktopicon"; Description: "Add a shortcut to the &desktop"; GroupDescription: "Shortcuts:"

[InstallDelete]
; A clean copy every time: nothing left over from the version before.
Type: filesandordirs; Name: "{app}\app"
Type: filesandordirs; Name: "{app}\runtime"

[Files]
Source: "{#Source}\*"; DestDir: "{app}"; Flags: ignoreversion recursesubdirs createallsubdirs

[Icons]
Name: "{autoprograms}\Wave"; Filename: "{app}\Wave.exe"
Name: "{autodesktop}\Wave"; Filename: "{app}\Wave.exe"; Tasks: desktopicon

[Run]
Filename: "{app}\Wave.exe"; Description: "Open Wave"; Flags: nowait postinstall skipifsilent
; An update from inside Wave: open it again once it's installed.
Filename: "{app}\Wave.exe"; Flags: nowait; Check: Relaunch

[UninstallRun]
Filename: "{sys}\taskkill.exe"; Parameters: "/F /T /IM Wave.exe"; Flags: runhidden; RunOnceId: "CloseWave"

[Code]
var
  Finished: Boolean;

function Relaunch: Boolean;
begin
  Result := WizardSilent and (ExpandConstant('{param:RELAUNCH|0}') = '1');
end;

{ Ends every running Wave (and its sign-in window), so its files can be replaced. }
procedure CloseWave;
var
  Code: Integer;
begin
  Exec(ExpandConstant('{sys}\taskkill.exe'), '/F /T /IM Wave.exe', '', SW_HIDE, ewWaitUntilTerminated, Code);
  Exec(ExpandConstant('{sys}\taskkill.exe'), '/F /T /IM WaveSignIn.exe', '', SW_HIDE, ewWaitUntilTerminated, Code);
  Sleep(500);
end;

{ Wave up to 1.1.0-beta.2 was installed as an MSI package: remove it (your data stays). }
procedure RemoveOldInstall;
var
  Keys: TArrayOfString;
  I, Code: Integer;
  Name, Base: String;
  IsMsi: Cardinal;
begin
  Base := 'Software\Microsoft\Windows\CurrentVersion\Uninstall';
  if not RegGetSubkeyNames(HKCU, Base, Keys) then Exit;
  for I := 0 to GetArrayLength(Keys) - 1 do
  begin
    if RegQueryStringValue(HKCU, Base + '\' + Keys[I], 'DisplayName', Name) and (Name = 'Wave') and
       RegQueryDWordValue(HKCU, Base + '\' + Keys[I], 'WindowsInstaller', IsMsi) and (IsMsi = 1) then
      Exec(ExpandConstant('{sys}\msiexec.exe'), '/x ' + Keys[I] + ' /qn /norestart', '', SW_HIDE, ewWaitUntilTerminated, Code);
  end;
end;

function PrepareToInstall(var NeedsRestart: Boolean): String;
begin
  CloseWave;
  RemoveOldInstall;
  Result := '';
end;

procedure CurStepChanged(Step: TSetupStep);
begin
  if Step = ssDone then Finished := True;
end;

{ Installed: the installer deletes itself (a moment after it has exited). Not if you cancelled. }
procedure DeinitializeSetup;
var
  Code: Integer;
begin
  if not Finished then Exit;
  Exec(ExpandConstant('{cmd}'), '/c ping 127.0.0.1 -n 4 >nul & del /f /q "' + ExpandConstant('{srcexe}') + '"', '', SW_HIDE, ewNoWait, Code);
end;
