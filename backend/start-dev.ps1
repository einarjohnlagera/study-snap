$ErrorActionPreference = "Stop"

$envPath = Join-Path $PSScriptRoot ".env"
if (Test-Path $envPath) {
  Get-Content $envPath | ForEach-Object {
    if ($_ -match '^\s*#' -or $_ -match '^\s*$') {
      return
    }

    $key, $value = $_ -split '=', 2
    Set-Item -Path "Env:$($key.Trim())" -Value $value.Trim()
  }
}

& "$PSScriptRoot\mvnw.cmd" spring-boot:run
