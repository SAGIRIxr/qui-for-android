# Provision once on Windows; keep the generated directory outside the checkout.
# Passwords are saved with Windows DPAPI for the current account and never printed.
[CmdletBinding()]
param(
    [string]$BackupDirectory = (Join-Path $env:USERPROFILE '.android\qui-release-signing'),
    [ValidatePattern('^[A-Za-z0-9_.-]+/[A-Za-z0-9_.-]+$')]
    [string]$Repository = 'SAGIRIxr/qui-for-android'
)
$ErrorActionPreference = 'Stop'
$keytool = (Get-Command keytool.exe -ErrorAction Stop).Source
$gh = (Get-Command gh.exe -ErrorAction Stop).Source
$backupPath = [IO.Path]::GetFullPath($BackupDirectory)
$null = New-Item -ItemType Directory -Path $backupPath -Force
$identity = [Security.Principal.WindowsIdentity]::GetCurrent().Name
# icacls changes only the DACL. Set-Acl on some Windows hosts also requests
# audit privileges, which an ordinary account does not have.
& icacls.exe $backupPath /inheritance:r /grant:r "${identity}:(OI)(CI)F" | Out-Null
if ($LASTEXITCODE -ne 0) { throw 'Cannot restrict signing backup permissions.' }
$keystore = Join-Path $backupPath 'release.jks'
$credentials = Join-Path $backupPath 'credentials.dpapi.xml'
$certificate = Join-Path $backupPath 'certificate.der'

if ((Test-Path -LiteralPath $keystore) -xor (Test-Path -LiteralPath $credentials)) {
    throw 'Incomplete existing backup. Refusing to replace signing material.'
}
if (!(Test-Path -LiteralPath $credentials)) {
    $random = New-Object byte[] 32
    $rng = [Security.Cryptography.RandomNumberGenerator]::Create()
    $rng.GetBytes($random)
    $rng.Dispose()
    $password = [Convert]::ToBase64String($random)
    $credential = [PSCredential]::new('qui-release', (ConvertTo-SecureString $password -AsPlainText -Force))
    $credential | Export-Clixml -LiteralPath $credentials
    $env:QUI_SETUP_PASSWORD = $password
    try {
        & $keytool -genkeypair -keystore $keystore -storetype JKS -alias qui-release -keyalg RSA -keysize 3072 -validity 10000 -dname 'CN=qui Android Release, O=SAGIRIxr' -storepass:env QUI_SETUP_PASSWORD -keypass:env QUI_SETUP_PASSWORD
        if ($LASTEXITCODE -ne 0) { throw 'Signing key generation failed.' }
    } finally {
        Remove-Item Env:\QUI_SETUP_PASSWORD
    }
} else {
    $credential = Import-Clixml -LiteralPath $credentials
    $password = $credential.GetNetworkCredential().Password
}

$env:QUI_SETUP_PASSWORD = $password
try {
    & $keytool -exportcert -keystore $keystore -alias qui-release -storepass:env QUI_SETUP_PASSWORD -file $certificate
    if ($LASTEXITCODE -ne 0) { throw 'Cannot verify signing key.' }
} finally {
    Remove-Item Env:\QUI_SETUP_PASSWORD
}
$fingerprint = (Get-FileHash -LiteralPath $certificate -Algorithm SHA256).Hash.ToLowerInvariant()
$secrets = @{
    QUI_KEYSTORE_BASE64 = [Convert]::ToBase64String([IO.File]::ReadAllBytes($keystore))
    QUI_KEYSTORE_PASSWORD = $password
    QUI_KEY_ALIAS = 'qui-release'
    QUI_KEY_PASSWORD = $password
    QUI_SIGNING_CERT_SHA256 = $fingerprint
}
foreach ($name in $secrets.Keys) {
    # PowerShell pipelines append a newline. Send exact bytes so passwords and
    # certificate fingerprints are not silently changed on the way into Secrets.
    $startInfo = [Diagnostics.ProcessStartInfo]::new()
    $startInfo.FileName = $gh
    $startInfo.Arguments = "secret set $name --repo $Repository"
    $startInfo.UseShellExecute = $false
    $startInfo.CreateNoWindow = $true
    $startInfo.WindowStyle = [Diagnostics.ProcessWindowStyle]::Hidden
    $startInfo.RedirectStandardInput = $true
    $process = [Diagnostics.Process]::Start($startInfo)
    $process.StandardInput.Write($secrets[$name])
    $process.StandardInput.Close()
    $process.WaitForExit()
    $secretExit = $process.ExitCode
    $process.Dispose()
    if ($secretExit -ne 0) { throw "Failed to configure $name. Backup retained at $backupPath" }
}
$secrets.Clear()
$password = $null
Write-Output "Signing backup: $backupPath"
Write-Output "Certificate SHA256: $fingerprint"
Write-Output 'Five repository secrets configured. Keep the backup and Windows account recovery material safe.'
