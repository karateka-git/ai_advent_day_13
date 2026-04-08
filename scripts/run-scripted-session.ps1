[CmdletBinding()]
param(
    [Parameter(Mandatory = $true)]
    [string]$ScenarioFile,

    [string]$OutputFile,

    [switch]$ClearConversations,

    [switch]$NoIncludeModelPrompt
)

$ErrorActionPreference = "Stop"

$repoRoot = Split-Path -Parent $PSScriptRoot
$batPath = Join-Path $repoRoot "build\install\ai_advent_day_13\bin\ai_advent_day_13.bat"
$conversationsDir = Join-Path $repoRoot "config\conversations"

if (-not (Test-Path -LiteralPath $batPath)) {
    throw "Built bat file not found: $batPath. Run .\\gradlew.bat build and .\\gradlew.bat installDist first."
}

$resolvedScenarioFile = (Resolve-Path -LiteralPath $ScenarioFile).Path
if (-not $OutputFile) {
    $scenarioName = [System.IO.Path]::GetFileNameWithoutExtension($resolvedScenarioFile)
    $OutputFile = Join-Path $repoRoot "build\smoke-check\$scenarioName-output.txt"
}

$resolvedOutputFile = [System.IO.Path]::GetFullPath($OutputFile)
$outputDir = Split-Path -Parent $resolvedOutputFile
if (-not (Test-Path -LiteralPath $outputDir)) {
    New-Item -ItemType Directory -Path $outputDir | Out-Null
}

$scenarioBaseName = [System.IO.Path]::GetFileNameWithoutExtension($resolvedOutputFile)
$traceFile = Join-Path $outputDir "$scenarioBaseName-trace.jsonl"
$stdoutFile = Join-Path $outputDir "$scenarioBaseName-stdout.txt"

if ($ClearConversations -and (Test-Path -LiteralPath $conversationsDir)) {
    Get-ChildItem -LiteralPath $conversationsDir -File | Remove-Item -Force
}

Remove-Item -LiteralPath $traceFile -Force -ErrorAction SilentlyContinue
Remove-Item -LiteralPath $stdoutFile -Force -ErrorAction SilentlyContinue

[Console]::InputEncoding = [System.Text.Encoding]::UTF8
[Console]::OutputEncoding = [System.Text.Encoding]::UTF8
$OutputEncoding = [System.Text.Encoding]::UTF8

$psi = New-Object System.Diagnostics.ProcessStartInfo
$psi.FileName = "cmd.exe"
$psi.Arguments = "/c chcp 65001>nul && `"$batPath`""
$psi.WorkingDirectory = $repoRoot
$psi.UseShellExecute = $false
$psi.RedirectStandardInput = $true
$psi.RedirectStandardOutput = $true
$psi.RedirectStandardError = $true
$psi.StandardOutputEncoding = [System.Text.Encoding]::UTF8
$psi.StandardErrorEncoding = [System.Text.Encoding]::UTF8
$psi.Environment["JAVA_TOOL_OPTIONS"] = "-Dfile.encoding=UTF-8 -Dstdout.encoding=UTF-8 -Dstderr.encoding=UTF-8"
$psi.Environment["DEBUG_TRACE_FILE"] = $traceFile
if (-not $NoIncludeModelPrompt) {
    $psi.Environment["DEBUG_SHOW_MODEL_PROMPT"] = "true"
}

$process = New-Object System.Diagnostics.Process
$process.StartInfo = $psi
$exitCode = $null

function Format-TraceRecord {
    param([pscustomobject]$Record)

    $builder = [System.Text.StringBuilder]::new()
    [void]$builder.AppendLine()
    [void]$builder.AppendLine("[[$($Record.title)]]")

    foreach ($line in $Record.lines) {
        if ([string]::IsNullOrEmpty($line)) {
            [void]$builder.AppendLine("|")
        } else {
            [void]$builder.AppendLine("| $line")
        }
    }

    [void]$builder.AppendLine("[[/]]")
    [void]$builder.AppendLine()
    return $builder.ToString()
}

try {
    $null = $process.Start()

    $scenarioContent = Get-Content -Encoding UTF8 -LiteralPath $resolvedScenarioFile -Raw
    $utf8NoBom = [System.Text.UTF8Encoding]::new($false)
    $stdinBytes = $utf8NoBom.GetBytes($scenarioContent)
    $process.StandardInput.BaseStream.Write($stdinBytes, 0, $stdinBytes.Length)
    if (-not $scenarioContent.EndsWith("`n")) {
        $newlineBytes = $utf8NoBom.GetBytes("`n")
        $process.StandardInput.BaseStream.Write($newlineBytes, 0, $newlineBytes.Length)
    }
    $process.StandardInput.BaseStream.Flush()
    $process.StandardInput.Close()

    $stdout = $process.StandardOutput.ReadToEnd()
    $stderr = $process.StandardError.ReadToEnd()
    $process.WaitForExit()
    $exitCode = $process.ExitCode
}
finally {
    if ($process -and -not $process.HasExited) {
        $process.Kill()
    }
    $process.Dispose()
}

Set-Content -LiteralPath $stdoutFile -Value $stdout -Encoding UTF8

$traceOutput = [System.Text.StringBuilder]::new()
if (Test-Path -LiteralPath $traceFile) {
    $traceLines = Get-Content -Encoding UTF8 -LiteralPath $traceFile | Where-Object { -not [string]::IsNullOrWhiteSpace($_) }
    foreach ($traceLine in $traceLines) {
        $record = $traceLine | ConvertFrom-Json
        [void]$traceOutput.Append((Format-TraceRecord -Record $record))
    }
}

$combinedOutput = $traceOutput.ToString()
if ([string]::IsNullOrWhiteSpace($combinedOutput)) {
    $combinedOutput = $stdout
}

if (-not [string]::IsNullOrWhiteSpace($stderr)) {
    $combinedOutput = "$combinedOutput`r`n[stderr]`r`n$stderr"
}

Set-Content -LiteralPath $resolvedOutputFile -Value $combinedOutput -Encoding UTF8

Write-Host "Scenario: $resolvedScenarioFile"
Write-Host "Output saved to: $resolvedOutputFile"
Write-Host "Raw stdout saved to: $stdoutFile"
Write-Host "Trace saved to: $traceFile"
Write-Host "Exit code: $exitCode"

if ($exitCode -ne 0) {
    throw "Scripted session finished with exit code $exitCode."
}
