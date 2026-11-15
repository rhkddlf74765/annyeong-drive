# release APK를 정식 키로 서명한다(11/15 결정). 비밀번호는 실행하는 사람이 직접 입력하고 어디에도 저장하지 않는다.
#
#   처음 한 번: 서명 키(keystore)를 만든다(비밀번호를 두 번 입력).
#   매번:       release를 빌드하고, 서명하고(비밀번호 입력), 서명을 확인한다.
#
# 키 파일은 저장소 밖(기본: %USERPROFILE%\.android\annyeong-release.jks)에 둔다. 커밋하지 않는다.
# 키나 비밀번호를 잃으면 같은 앱으로 업데이트를 낼 수 없으니 키 파일과 비밀번호를 따로 백업한다.
#
# 실행(저장소 루트에서):  powershell -ExecutionPolicy Bypass -File tools\sign_release.ps1

param(
    [string]$Keystore = "$env:USERPROFILE\.android\annyeong-release.jks",
    [string]$Alias = "annyeong",
    [switch]$SkipBuild
)

$ErrorActionPreference = "Stop"
$repo = Split-Path -Parent $PSScriptRoot

function Find-Keytool {
    $candidates = @()
    if ($env:JAVA_HOME) { $candidates += "$env:JAVA_HOME\bin\keytool.exe" }
    $candidates += "C:\Program Files\Android\Android Studio\jbr\bin\keytool.exe"
    $candidates += "C:\Program Files\Java\jdk-17\bin\keytool.exe"
    foreach ($c in $candidates) { if (Test-Path $c) { return $c } }
    throw "keytool을 찾지 못했습니다. JAVA_HOME을 정해 주세요."
}

function Find-Sdk {
    if ($env:ANDROID_HOME) { return $env:ANDROID_HOME }
    return "$env:LOCALAPPDATA\Android\Sdk"
}

function Find-Apksigner {
    $tools = Get-ChildItem "$(Find-Sdk)\build-tools" -Directory | Sort-Object { [version]($_.Name -replace '[^0-9.]', '') }
    return "$($tools[-1].FullName)\apksigner.bat"
}

# 1. 서명 키
if (-not (Test-Path $Keystore)) {
    Write-Host "서명 키가 없어 새로 만듭니다: $Keystore" -ForegroundColor Yellow
    Write-Host "비밀번호를 정해 두 번 입력하세요(6자 이상). 입력한 글자는 보이지 않습니다." -ForegroundColor Yellow
    New-Item -ItemType Directory -Force (Split-Path $Keystore) | Out-Null
    & (Find-Keytool) -genkeypair -v -keystore $Keystore -storetype PKCS12 -alias $Alias `
        -keyalg RSA -keysize 4096 -validity 10000 -dname "CN=Annyeong Drive, O=Annyeong Drive, C=KR"
    if ($LASTEXITCODE -ne 0) { throw "서명 키를 만들지 못했습니다." }
    Write-Host "서명 키를 만들었습니다. 키 파일과 비밀번호를 따로 백업해 두세요." -ForegroundColor Green
}

# 2. release 빌드
$env:ANDROID_HOME = Find-Sdk
if (-not $SkipBuild) {
    Write-Host "release 빌드 중…" -ForegroundColor Cyan
    & "$repo\gradlew.bat" -p $repo assembleRelease --console=plain -q
    if ($LASTEXITCODE -ne 0) { throw "release 빌드에 실패했습니다." }
}

# 3. 서명
$releaseDir = "$repo\app\build\outputs\apk\release"
$unsigned = "$releaseDir\app-release-unsigned.apk"
if (-not (Test-Path $unsigned)) { throw "서명할 APK가 없습니다: $unsigned" }
$versionName = (Select-String -Path "$repo\app\build.gradle.kts" -Pattern 'versionName\s*=\s*"([^"]+)"').Matches[0].Groups[1].Value
$signed = "$releaseDir\annyeong-drive-$versionName.apk"
Write-Host "서명합니다. 서명 키 비밀번호를 입력하세요." -ForegroundColor Cyan
& (Find-Apksigner) sign --ks $Keystore --ks-key-alias $Alias --out $signed $unsigned
if ($LASTEXITCODE -ne 0) { throw "서명에 실패했습니다." }

# 4. 확인
& (Find-Apksigner) verify --verbose --print-certs $signed | Select-String -Pattern "Verified using|Signer #1 certificate (DN|SHA-256)"
if ($LASTEXITCODE -ne 0) { throw "서명 확인에 실패했습니다." }
$hash = (Get-FileHash $signed -Algorithm SHA256).Hash
Write-Host ""
Write-Host "완료: $signed" -ForegroundColor Green
Write-Host "APK SHA-256: $hash"
