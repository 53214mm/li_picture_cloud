$taskMavenArguments = @($args)

if (-not $env:JAVA_HOME) {
  $taskJavaExecutable = (Get-Command java -ErrorAction Stop).Source
  $taskJdkHome = Split-Path -Parent (Split-Path -Parent $taskJavaExecutable)
  if (-not (Test-Path (Join-Path $taskJdkHome 'bin\javac.exe'))) {
    throw 'JAVA_HOME is unset and java on PATH is not a full JDK 21'
  }
  $env:JAVA_HOME = $taskJdkHome
}
if (-not (Test-Path (Join-Path $env:JAVA_HOME 'bin\javac.exe'))) {
  throw 'JAVA_HOME does not point to a full JDK'
}
$taskJavaVersionOutput = & (Join-Path $env:JAVA_HOME 'bin\java.exe') -XshowSettings:properties -version 2>&1
$taskJavaSpecification = $taskJavaVersionOutput |
  Select-String -Pattern '^\s*java\.specification\.version\s*=\s*(\S+)\s*$' |
  Select-Object -First 1
if ($null -eq $taskJavaSpecification -or $taskJavaSpecification.Matches[0].Groups[1].Value -ne '21') {
  throw 'JAVA_HOME must point to JDK 21'
}

$taskRepositoryRoot = (Resolve-Path (Join-Path $PSScriptRoot '..')).Path
Push-Location $taskRepositoryRoot
try {
  & .\mvnw.cmd @taskMavenArguments
  $taskMavenExitCode = $LASTEXITCODE
} finally {
  Pop-Location
}
if ($taskMavenExitCode -ne 0) {
  throw "Maven failed with exit code $taskMavenExitCode"
}
