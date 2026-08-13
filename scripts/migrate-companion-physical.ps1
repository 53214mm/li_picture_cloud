$taskRequiredVariables = @(
  'MYSQL_HOST', 'MYSQL_PORT', 'MYSQL_DATABASE', 'MYSQL_USERNAME', 'MYSQL_PASSWORD'
)
foreach ($taskVariable in $taskRequiredVariables) {
  if ([string]::IsNullOrWhiteSpace([Environment]::GetEnvironmentVariable($taskVariable))) {
    throw "Required environment variable is missing: $taskVariable"
  }
}

$taskRepositoryRoot = (Resolve-Path (Join-Path $PSScriptRoot '..')).Path
Push-Location $taskRepositoryRoot
try {
  & .\scripts\mvnw-java21.ps1 -q liquibase:update
} finally {
  Pop-Location
}
