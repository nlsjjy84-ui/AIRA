[CmdletBinding()]
param(
    [ValidatePattern('^[A-Za-z0-9_]+$')]
    [string]$DatabaseName = 'aira_local',

    [ValidateNotNullOrEmpty()]
    [string]$DatabaseHost = '127.0.0.1',

    [ValidateRange(1, 65535)]
    [int]$DatabasePort = 5432,

    [ValidateNotNullOrEmpty()]
    [string]$DatabaseUser = 'postgres',

    [switch]$InitializeSchema
)

$ErrorActionPreference = 'Stop'

if ($DatabaseName -ieq 'aira') {
    throw "The existing 'aira' development database is protected. Use a dedicated local database such as 'aira_local'."
}

$repositoryRoot = Split-Path -Parent $PSScriptRoot
$apiDirectory = Join-Path $repositoryRoot 'apps\api'
$gradleWrapper = Join-Path $apiDirectory 'gradlew.bat'
if (-not (Test-Path -LiteralPath $gradleWrapper -PathType Leaf)) {
    throw 'Gradle wrapper was not found under apps/api.'
}

function New-Base64Key {
    $bytes = New-Object byte[] 32
    $generator = [System.Security.Cryptography.RandomNumberGenerator]::Create()
    try {
        $generator.GetBytes($bytes)
        return [Convert]::ToBase64String($bytes)
    }
    finally {
        $generator.Dispose()
        [Array]::Clear($bytes, 0, $bytes.Length)
    }
}

$managedVariables = @(
    'DB_PASSWORD',
    'SPRING_DATASOURCE_URL',
    'SPRING_DATASOURCE_USERNAME',
    'FLYWAY_ENABLED',
    'AIRA_SESSION_COOKIE_SECURE',
    'AIRA_RECOVERY_EMAIL_ENCRYPTION_KEY',
    'AIRA_RECOVERY_EMAIL_LOOKUP_KEY'
)
$originalValues = @{}
foreach ($name in $managedVariables) {
    $originalValues[$name] = [Environment]::GetEnvironmentVariable($name, 'Process')
}

$passwordPointer = [IntPtr]::Zero
try {
    if ([string]::IsNullOrWhiteSpace($env:DB_PASSWORD)) {
        $securePassword = Read-Host 'Local PostgreSQL password' -AsSecureString
        $passwordPointer = [Runtime.InteropServices.Marshal]::SecureStringToBSTR($securePassword)
        $env:DB_PASSWORD = [Runtime.InteropServices.Marshal]::PtrToStringBSTR($passwordPointer)
    }

    if ([string]::IsNullOrWhiteSpace($env:AIRA_RECOVERY_EMAIL_ENCRYPTION_KEY)) {
        $env:AIRA_RECOVERY_EMAIL_ENCRYPTION_KEY = New-Base64Key
    }
    if ([string]::IsNullOrWhiteSpace($env:AIRA_RECOVERY_EMAIL_LOOKUP_KEY)) {
        $env:AIRA_RECOVERY_EMAIL_LOOKUP_KEY = New-Base64Key
    }

    $env:SPRING_DATASOURCE_URL = "jdbc:postgresql://${DatabaseHost}:${DatabasePort}/${DatabaseName}"
    $env:SPRING_DATASOURCE_USERNAME = $DatabaseUser
    $env:FLYWAY_ENABLED = $InitializeSchema.IsPresent.ToString().ToLowerInvariant()
    $env:AIRA_SESSION_COOKIE_SECURE = 'false'

    Write-Host "Starting AIRA API with database '$DatabaseName' on ${DatabaseHost}:${DatabasePort}."
    Write-Host "Flyway enabled for this start: $($env:FLYWAY_ENABLED)."
    Write-Host 'No secret values are written to disk by this script.'

    Push-Location $apiDirectory
    try {
        & $gradleWrapper --no-daemon bootRun
        if ($LASTEXITCODE -ne 0) {
            throw "AIRA API exited with code $LASTEXITCODE."
        }
    }
    finally {
        Pop-Location
    }
}
finally {
    if ($passwordPointer -ne [IntPtr]::Zero) {
        [Runtime.InteropServices.Marshal]::ZeroFreeBSTR($passwordPointer)
    }
    foreach ($name in $managedVariables) {
        [Environment]::SetEnvironmentVariable($name, $originalValues[$name], 'Process')
    }
}
