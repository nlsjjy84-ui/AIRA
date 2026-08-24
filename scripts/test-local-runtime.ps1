[CmdletBinding()]
param(
    [ValidateNotNullOrEmpty()]
    [string]$ApiBaseUrl = 'http://127.0.0.1:8080',

    [ValidateNotNullOrEmpty()]
    [string]$WebBaseUrl = 'http://127.0.0.1:5173'
)

$ErrorActionPreference = 'Stop'

function Assert-HttpOk {
    param(
        [string]$Name,
        [string]$Uri
    )

    try {
        $response = Invoke-WebRequest -Uri $Uri -Method Get -UseBasicParsing -TimeoutSec 10
        if ($response.StatusCode -lt 200 -or $response.StatusCode -ge 300) {
            throw "HTTP $($response.StatusCode)"
        }
        Write-Host "PASS: $Name ($($response.StatusCode))"
    }
    catch {
        throw "FAIL: $Name did not return a successful HTTP response."
    }
}

Assert-HttpOk -Name 'API health' -Uri "$ApiBaseUrl/actuator/health"
Assert-HttpOk -Name 'Web application' -Uri $WebBaseUrl
Assert-HttpOk -Name 'Web-to-API proxy' -Uri "$WebBaseUrl/api/companies"

Write-Host 'Local runtime checks passed.'
