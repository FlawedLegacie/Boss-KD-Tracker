param(
    [switch]$Clean
)

$ErrorActionPreference = 'Stop'

$ProjectRoot = Split-Path -Parent $MyInvocation.MyCommand.Path
$GradleVersion = '8.10.2'
$BootstrapRoot = Join-Path $ProjectRoot '.gradle-bootstrap'
$GradleHome = Join-Path $BootstrapRoot "gradle-$GradleVersion"
$GradleExe = Join-Path $GradleHome 'bin\gradle.bat'
$GradleZip = Join-Path $BootstrapRoot "gradle-$GradleVersion-bin.zip"
$GradleUrl = "https://services.gradle.org/distributions/gradle-$GradleVersion-bin.zip"

Write-Host ''
Write-Host '============================================================' -ForegroundColor Cyan
Write-Host '             BOSS DEATH TRACKER - DEV LAUNCHER' -ForegroundColor Cyan
Write-Host '============================================================' -ForegroundColor Cyan
Write-Host ''
Write-Host "Project: $ProjectRoot"
Write-Host ''

# Java check
$Java = Get-Command java.exe -ErrorAction SilentlyContinue
if (-not $Java) {
    Write-Host '[ERROR] Java was not found in PATH.' -ForegroundColor Red
    Write-Host ''
    Write-Host 'Install a JDK and reopen PowerShell/VS Code, then run this launcher again.'
    Write-Host 'RuneLite plugin development requires Java to compile and launch.'
    exit 1
}

Write-Host '[OK] Java found:' -ForegroundColor Green
& java.exe -version
if ($LASTEXITCODE -ne 0) {
    Write-Host '[ERROR] Java exists but could not be executed.' -ForegroundColor Red
    exit 1
}

Write-Host ''

if ($Clean -and (Test-Path -LiteralPath $BootstrapRoot)) {
    Write-Host '[INFO] Removing local Gradle bootstrap...' -ForegroundColor Yellow
    Remove-Item -LiteralPath $BootstrapRoot -Recurse -Force
}

if (-not (Test-Path -LiteralPath $GradleExe)) {
    New-Item -ItemType Directory -Force -Path $BootstrapRoot | Out-Null

    if (-not (Test-Path -LiteralPath $GradleZip)) {
        Write-Host "[INFO] Downloading Gradle $GradleVersion..." -ForegroundColor Yellow
        Write-Host "       $GradleUrl"
        Write-Host ''

        try {
            Invoke-WebRequest `
                -Uri $GradleUrl `
                -OutFile $GradleZip `
                -UseBasicParsing
        }
        catch {
            Write-Host '[ERROR] Gradle download failed.' -ForegroundColor Red
            Write-Host $_.Exception.Message
            exit 1
        }
    }

    Write-Host "[INFO] Extracting Gradle $GradleVersion..." -ForegroundColor Yellow

    try {
        Expand-Archive `
            -LiteralPath $GradleZip `
            -DestinationPath $BootstrapRoot `
            -Force
    }
    catch {
        Write-Host '[ERROR] Gradle extraction failed.' -ForegroundColor Red
        Write-Host $_.Exception.Message
        exit 1
    }

    if (-not (Test-Path -LiteralPath $GradleExe)) {
        Write-Host "[ERROR] Expected Gradle executable was not created:" -ForegroundColor Red
        Write-Host "        $GradleExe"
        exit 1
    }
}

Write-Host "[OK] Local Gradle $GradleVersion ready." -ForegroundColor Green
Write-Host ''
Write-Host '[INFO] Starting RuneLite development client...' -ForegroundColor Yellow
Write-Host ''
Write-Host 'The first launch may take a few minutes while RuneLite dependencies download.'
Write-Host ''

Push-Location $ProjectRoot
try {
    & $GradleExe run --stacktrace
    $ExitCode = $LASTEXITCODE
}
finally {
    Pop-Location
}

Write-Host ''
if ($ExitCode -eq 0) {
    Write-Host '[OK] RuneLite development process exited normally.' -ForegroundColor Green
}
else {
    Write-Host "[ERROR] Gradle/RuneLite exited with code $ExitCode." -ForegroundColor Red
    Write-Host 'Copy the terminal output and send it back so the plugin can be patched.'
}

exit $ExitCode
