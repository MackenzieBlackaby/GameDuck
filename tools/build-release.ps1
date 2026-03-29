[CmdletBinding()]
param(
    [string]$Version = "0.3.0",
    [string]$MainClass = "com.blackaby.Main",
    [string]$AppName = "GameDuck",
    [string]$IconPath,
    [switch]$RunTests
)

$ErrorActionPreference = "Stop"
Set-StrictMode -Version Latest

function Get-RepoRoot {
    $scriptDir = $PSScriptRoot
    if (-not $scriptDir) {
        $scriptDir = Split-Path -Parent $PSCommandPath
    }
    if (-not $scriptDir) {
        throw "Could not determine script directory. Save this script as tools\build-release.ps1 inside the repo."
    }
    $candidate = Split-Path -Parent $scriptDir
    if (Test-Path (Join-Path $candidate "pom.xml")) {
        return $candidate
    }
    if (Test-Path (Join-Path (Get-Location) "pom.xml")) {
        return (Get-Location).Path
    }
    throw "Could not find pom.xml. Save this script as tools\\build-release.ps1 inside the repo, or run it from the repo root."
}

function Require-Command([string]$Name) {
    if (-not (Get-Command $Name -ErrorAction SilentlyContinue)) {
        throw "Required command '$Name' was not found in PATH."
    }
}

function Remove-IfExists([string]$PathToRemove) {
    if (Test-Path $PathToRemove) {
        Remove-Item -Recurse -Force $PathToRemove
    }
}

function Get-ArtifactJar([string]$TargetDir) {
    $jar = Get-ChildItem -Path $TargetDir -Filter "*.jar" -File |
        Where-Object {
            $_.Name -notmatch "(?i)(sources|javadoc|tests|original)" -and
            $_.DirectoryName -notmatch "[\\/]archive-tmp$"
        } |
        Sort-Object LastWriteTime -Descending |
        Select-Object -First 1

    if (-not $jar) {
        throw "No runnable jar found under $TargetDir"
    }

    return $jar
}

function Copy-IfExists([string]$Source, [string]$Destination) {
    if (Test-Path $Source) {
        Copy-Item $Source $Destination -Recurse -Force
    }
}

function Write-LittleEndianUInt16([System.IO.BinaryWriter]$Writer, [UInt16]$Value) {
    $Writer.Write([System.BitConverter]::GetBytes($Value))
}

function Write-LittleEndianUInt32([System.IO.BinaryWriter]$Writer, [UInt32]$Value) {
    $Writer.Write([System.BitConverter]::GetBytes($Value))
}

function Resolve-ReleaseIconSource([string]$RepoRoot, [string]$RequestedIconPath) {
    $candidates = @()
    if ($RequestedIconPath) {
        if ([System.IO.Path]::IsPathRooted($RequestedIconPath)) {
            $candidates += $RequestedIconPath
        }
        else {
            $candidates += (Join-Path $RepoRoot $RequestedIconPath)
        }
    }

    $candidates += @(
        (Join-Path $RepoRoot "src\main\resources\Images\LogoNoBG128.png"),
        (Join-Path $RepoRoot "design\LogoNoBG128.png"),
        (Join-Path $RepoRoot "src\main\resources\Images\LogoBG128.png"),
        (Join-Path $RepoRoot "design\LogoBG128.png"),
        (Join-Path $RepoRoot "src\main\resources\Images\LogoCroppedNoBG128.png"),
        (Join-Path $RepoRoot "design\LogoCroppedNoBG128.png")
    )

    foreach ($candidate in $candidates) {
        if ($candidate -and (Test-Path $candidate)) {
            return (Resolve-Path $candidate).Path
        }
    }

    return $null
}

function New-IcoFromPng([string]$SourcePng, [string]$DestinationIco) {
    Add-Type -AssemblyName System.Drawing

    $image = [System.Drawing.Image]::FromFile($SourcePng)
    try {
        if ($image.Width -ne $image.Height) {
            throw "Icon source must be square, but '$SourcePng' is $($image.Width)x$($image.Height)."
        }
        if ($image.Width -gt 256 -or $image.Height -gt 256) {
            throw "Icon source must be 256x256 or smaller, but '$SourcePng' is $($image.Width)x$($image.Height)."
        }

        $pngBytes = [System.IO.File]::ReadAllBytes($SourcePng)
        $iconDir = Split-Path -Parent $DestinationIco
        New-Item -ItemType Directory -Force -Path $iconDir | Out-Null

        $fileStream = [System.IO.File]::Open($DestinationIco, [System.IO.FileMode]::Create, [System.IO.FileAccess]::Write)
        try {
            $writer = New-Object System.IO.BinaryWriter($fileStream)
            try {
                Write-LittleEndianUInt16 $writer 0
                Write-LittleEndianUInt16 $writer 1
                Write-LittleEndianUInt16 $writer 1
                $writer.Write([byte]($(if ($image.Width -ge 256) { 0 } else { $image.Width })))
                $writer.Write([byte]($(if ($image.Height -ge 256) { 0 } else { $image.Height })))
                $writer.Write([byte]0)
                $writer.Write([byte]0)
                Write-LittleEndianUInt16 $writer 1
                Write-LittleEndianUInt16 $writer 32
                Write-LittleEndianUInt32 $writer ([UInt32]$pngBytes.Length)
                Write-LittleEndianUInt32 $writer 22
                $writer.Write($pngBytes)
            }
            finally {
                $writer.Dispose()
            }
        }
        finally {
            $fileStream.Dispose()
        }
    }
    finally {
        $image.Dispose()
    }
}

$repoRoot = Get-RepoRoot
Set-Location $repoRoot

Require-Command "mvn"
Require-Command "jpackage"

$releaseRoot = Join-Path $repoRoot "release"
$versionFolder = Join-Path $releaseRoot ("v{0}" -f $Version)
$buildRoot = Join-Path $repoRoot "build"
$stageDir = Join-Path $buildRoot "release-stage"
$appImageDest = Join-Path $buildRoot "app-image"
$releaseAssetsDir = Join-Path $buildRoot "release-assets"
$appFolderName = "{0}-v{1}-windows-x64" -f $AppName, $Version
$appImageDir = Join-Path $versionFolder $appFolderName
$zipName = "{0}-v{1}.0-windows-x64.zip" -f $AppName, $Version
$zipPath = Join-Path $versionFolder $zipName
$checksumsPath = Join-Path $versionFolder "SHA256SUMS.txt"
$publishPath = Join-Path $versionFolder "PUBLISH.txt"
$releaseNotesPath = Join-Path $versionFolder "GITHUB-RELEASE.md"
$generatedIconPath = Join-Path $releaseAssetsDir ("{0}.ico" -f $AppName)
$iconSourcePath = Resolve-ReleaseIconSource -RepoRoot $repoRoot -RequestedIconPath $IconPath
$dependencyJarNames = @()

Write-Host "==> Repo root: $repoRoot"
Write-Host "==> Cleaning previous staged release output"
Remove-IfExists $stageDir
Remove-IfExists $appImageDest
Remove-IfExists $releaseAssetsDir
Remove-IfExists $versionFolder
New-Item -ItemType Directory -Force $stageDir | Out-Null
New-Item -ItemType Directory -Force $versionFolder | Out-Null

$pomPath = Join-Path $repoRoot "pom.xml"
$pomXml = [xml](Get-Content $pomPath)
$originalPomVersion = $null
if ($pomXml.project.version) {
    $originalPomVersion = [string]$pomXml.project.version
}

if ($originalPomVersion -and $originalPomVersion -ne $Version) {
    Write-Host "==> Setting Maven project version to $Version for the release build"
    mvn versions:set "-DnewVersion=$Version" "-DgenerateBackupPoms=false"
}

try {
    $mvnArgs = @()
    if (-not $RunTests) {
        $mvnArgs += "-DskipTests"
    }
    $mvnArgs += @("clean", "package")

    Write-Host ("==> Running Maven build: mvn {0}" -f ($mvnArgs -join " "))
    & mvn @mvnArgs
    if ($LASTEXITCODE -ne 0) {
        throw "Maven build failed with exit code $LASTEXITCODE"
    }

    $targetDir = Join-Path $repoRoot "target"
    $jar = Get-ArtifactJar $TargetDir
    Write-Host "==> Using jar: $($jar.Name)"

    $dependencyArgs = @(
        "dependency:copy-dependencies",
        "-DincludeScope=runtime",
        "-DoutputDirectory=$stageDir"
    )
    Write-Host ("==> Copying runtime dependencies: mvn {0}" -f ($dependencyArgs -join " "))
    & mvn @dependencyArgs
    if ($LASTEXITCODE -ne 0) {
        throw "Maven dependency copy failed with exit code $LASTEXITCODE"
    }

    Copy-Item $jar.FullName $stageDir -Force
    $dependencyJarNames = Get-ChildItem -Path $stageDir -Filter "*.jar" -File |
        Where-Object { $_.Name -ne $jar.Name } |
        Sort-Object Name |
        Select-Object -ExpandProperty Name

    $jpackageArgs = @(
        "--type", "app-image",
        "--name", $AppName,
        "--input", $stageDir,
        "--main-jar", $jar.Name,
        "--main-class", $MainClass,
        "--app-version", $Version,
        "--dest", $appImageDest
    )

    if ($iconSourcePath) {
        Write-Host "==> Generating Windows icon from: $iconSourcePath"
        New-IcoFromPng -SourcePng $iconSourcePath -DestinationIco $generatedIconPath
        $jpackageArgs += @("--icon", $generatedIconPath)
    }
    else {
        Write-Warning "No PNG icon source was found. The Windows app image will use the default jpackage icon."
    }

    Write-Host "==> Building Windows app image with jpackage"
    & jpackage @jpackageArgs

    if ($LASTEXITCODE -ne 0) {
        throw "jpackage failed with exit code $LASTEXITCODE"
    }

    $rawAppDir = Join-Path $appImageDest $AppName
    if (-not (Test-Path $rawAppDir)) {
        throw "Expected packaged app image at $rawAppDir but it was not created."
    }

    Move-Item $rawAppDir $appImageDir

    Write-Host "==> Seeding portable folders"
    New-Item -ItemType Directory -Force (Join-Path $appImageDir "cache") | Out-Null
    New-Item -ItemType Directory -Force (Join-Path $appImageDir "library") | Out-Null
    New-Item -ItemType Directory -Force (Join-Path $appImageDir "library\\roms") | Out-Null
    New-Item -ItemType Directory -Force (Join-Path $appImageDir "quickstates") | Out-Null
    New-Item -ItemType Directory -Force (Join-Path $appImageDir "saves") | Out-Null

    Write-Host "==> Copying extra release files if present"
    Copy-IfExists (Join-Path $repoRoot "README.md") $appImageDir

    $licenseCandidates = @(
        "LICENSE",
        "LICENSE.txt",
        "LICENCE",
        "LICENCE.txt"
    ) | ForEach-Object { Join-Path $repoRoot $_ }

    $copiedLicense = $false
    foreach ($licensePath in $licenseCandidates) {
        if (Test-Path $licensePath) {
            $destinationName = Split-Path $licensePath -Leaf
            Copy-Item $licensePath (Join-Path $appImageDir $destinationName) -Force
            $copiedLicense = $true
            break
        }
    }

    $hasDemoRoms = Test-Path (Join-Path $repoRoot "demo-roms")
    if ($hasDemoRoms) {
        Copy-IfExists (Join-Path $repoRoot "demo-roms") (Join-Path $appImageDir "demo-roms")
    }

    Write-Host "==> Creating zip archive"
    if (Test-Path $zipPath) {
        Remove-Item -Force $zipPath
    }
    Compress-Archive -Path $appImageDir -DestinationPath $zipPath -Force

    Write-Host "==> Writing checksum + publish metadata"
    $zipHash = (Get-FileHash $zipPath -Algorithm SHA256).Hash.ToLower()
    $zipLeaf = Split-Path $zipPath -Leaf

    @(
        "$zipHash  $zipLeaf"
    ) | Set-Content -Path $checksumsPath -Encoding UTF8

    $publishLines = @(
        "Release folder: $versionFolder",
        "Upload to GitHub release: $zipLeaf",
        "Launcher after extract: $appFolderName\\$AppName.exe",
        "Main class: $MainClass",
        "Packaged main jar: app\\$($jar.Name)",
        "Packaged runtime dependency jars: $($dependencyJarNames.Count)",
        "Built with: mvn $($mvnArgs -join ' ')",
        "jpackage: app-image",
        "Portable folders seeded: cache, library\\roms, quickstates, saves"
    )

    if ($dependencyJarNames.Count -gt 0) {
        $publishLines += "Dependency jars: $($dependencyJarNames -join ', ')"
    }
    if ($iconSourcePath) {
        $publishLines += "Windows icon source: $iconSourcePath"
    }

    if (-not $copiedLicense) {
        $publishLines += "Note: no LICENSE/LICENCE file was found at repo root to copy into the release bundle."
    }

    $publishLines | Set-Content -Path $publishPath -Encoding UTF8

    $releaseNotesLines = @(
        "# $AppName v$Version",
        "",
        "Initial Windows x64 release bundle for $AppName.",
        "",
        "## Downloads",
        "",
        "- $zipLeaf",
        "",
        "## What is included",
        "",
        "- Click-to-run Windows app image with bundled Java runtime",
        "- $AppName.exe launcher",
        "- Packaged runnable jar under app\\$($jar.Name)",
        "- Runtime dependency jars bundled under app\\",
        "- Portable working folders: cache, library\\roms, quickstates, saves",
        "- Project README"
    )

    if ($copiedLicense) {
        $releaseNotesLines += "- Licence file"
    }
    if ($hasDemoRoms) {
        $releaseNotesLines += "- demo-roms folder"
    }

    $releaseNotesLines += @(
        "",
        "## Build notes",
        "",
        "- Built with mvn $($mvnArgs -join ' ')",
        "- Packaged using jpackage --type app-image",
        "- Main class: $MainClass",
        "- Runtime dependency jars bundled: $($dependencyJarNames.Count)",
        "",
        "## Checksum",
        "",
        '```text',
        "$zipHash  $zipLeaf",
        '```'
    )

    $releaseNotesLines | Set-Content -Path $releaseNotesPath -Encoding UTF8

    Write-Host ""
    Write-Host "Release complete." -ForegroundColor Green
    Write-Host "Folder: $versionFolder"
    Write-Host "Zip:    $zipPath"
}
finally {
    if ($originalPomVersion -and $originalPomVersion -ne $Version) {
        Write-Host "==> Restoring pom.xml version back to $originalPomVersion"
        mvn versions:set "-DnewVersion=$originalPomVersion" "-DgenerateBackupPoms=false" | Out-Null
    }
}
