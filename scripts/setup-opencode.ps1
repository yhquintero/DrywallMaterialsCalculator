#!/usr/bin/env pwsh
# =============================================================================
# Script de Configuración — OpenCode + Drywall Materials Calculator
# =============================================================================
# Requiere: PowerShell 7+, Node.js 18+, Java 17+
# =============================================================================

$ErrorActionPreference = "Stop"
$ProjectRoot = Split-Path -Parent $PSScriptRoot
Write-Output "========================================"
Write-Output " Drywall Materials Calculator — OpenCode Setup"
Write-Output "========================================"
Write-Output "Project root: $ProjectRoot"

# --- Step 1: Verificar herramientas base ---
Write-Output "`n[1/6] Verificando herramientas base..."
$tools = @(
    @{Name="Node.js"; Cmd="node --version"},
    @{Name="npm"; Cmd="npm --version"},
    @{Name="Java"; Cmd="java -version 2>&1"},
    @{Name="PowerShell 7+"; Cmd="pwsh --version"}
)
foreach ($tool in $tools) {
    try {
        $ver = Invoke-Expression $tool.Cmd
        Write-Output "  [OK] $($tool.Name): $($ver -join ' ')"
    } catch {
        Write-Output "  [FAIL] $($tool.Name) no encontrado. Instalar antes de continuar."
    }
}

# --- Step 2: Instalar Kotlin Language Server (fwcd/kotlin-language-server) ---
Write-Output "`n[2/6] Instalando Kotlin Language Server..."
$klsDir = "$env:LOCALAPPDATA\kotlin-language-server"
if (-not (Test-Path "$klsDir\bin\kotlin-language-server.bat")) {
    New-Item -ItemType Directory -Path $klsDir -Force | Out-Null
    $zipUrl = "https://github.com/fwcd/kotlin-language-server/releases/download/1.3.13/server.zip"
    $zipPath = "$env:TEMP\kotlin-lsp-server.zip"
    try {
        Write-Output "  Descargando desde $zipUrl ..."
        $wc = New-Object System.Net.WebClient
        $wc.DownloadFile($zipUrl, $zipPath)
        Expand-Archive -Path $zipPath -DestinationPath "$env:TEMP\kotlin-lsp-extracted" -Force
        Copy-Item -Path "$env:TEMP\kotlin-lsp-extracted\server\*" -Destination $klsDir -Recurse -Force
        Remove-Item -Path $zipPath -Force -ErrorAction SilentlyContinue
        Remove-Item -Path "$env:TEMP\kotlin-lsp-extracted" -Recurse -Force -ErrorAction SilentlyContinue

        # Agregar al PATH del usuario
        $userPath = [Environment]::GetEnvironmentVariable("PATH", "User")
        if ($userPath -notlike "*$klsDir\bin*") {
            $newPath = "$klsDir\bin;$userPath"
            [Environment]::SetEnvironmentVariable("PATH", $newPath, "User")
            $env:PATH = "$klsDir\bin;$env:PATH"
        }
        Write-Output "  [OK] Kotlin Language Server instalado en $klsDir"
    } catch {
        Write-Output "  [WARN] No se pudo descargar/instalar Kotlin Language Server: $_"
        Write-Output "         Instalar manualmente desde: https://github.com/fwcd/kotlin-language-server/releases"
    }
} else {
    Write-Output "  [OK] Kotlin Language Server ya instalado"
}

# --- Step 3: Instalar Gradle Language Server (desde VSCode Gradle extension) ---
Write-Output "`n[3/6] Instalando Gradle Language Server..."
$glsDir = "$env:LOCALAPPDATA\gradle-language-server"
$glsJar = "$glsDir\gradle-language-server.jar"
if (-not (Test-Path $glsJar)) {
    New-Item -ItemType Directory -Path $glsDir -Force | Out-Null
    $vsixUrl = "https://openvsx.eclipse.org/api/v2/vscjava.vscode-gradle/3.17.3/file/vscjava.vscode-gradle-3.17.3.vsix"
    $vsixPath = "$env:TEMP\gradle-lsp.vsix"
    try {
        Write-Output "  Descargando desde $vsixUrl ..."
        $wc = New-Object System.Net.WebClient
        $wc.DownloadFile($vsixUrl, $vsixPath)
        # VSIX is a ZIP
        Expand-Archive -Path $vsixPath -DestinationPath "$env:TEMP\gradle-lsp-extracted" -Force
        Copy-Item -Path "$env:TEMP\gradle-lsp-extracted\extension\lib\gradle-language-server.jar" -Destination $glsJar -Force
        Remove-Item -Path $vsixPath -Force -ErrorAction SilentlyContinue
        Remove-Item -Path "$env:TEMP\gradle-lsp-extracted" -Recurse -Force -ErrorAction SilentlyContinue

        # Crear script de lanzamiento
        $launcher = @"
@echo off
java -jar "$glsJar" %*
"@
        $launcherPath = "$glsDir\gradle-language-server.cmd"
        Set-Content -Path $launcherPath -Value $launcher

        # PowerShell launcher
        $psLauncher = @"
java -jar "$glsJar" @args
"@
        $psLauncherPath = "$glsDir\gradle-language-server.ps1"
        Set-Content -Path $psLauncherPath -Value $psLauncher

        # Agregar al PATH
        $userPath = [Environment]::GetEnvironmentVariable("PATH", "User")
        if ($userPath -notlike "*$glsDir*") {
            $newPath = "$glsDir;$userPath"
            [Environment]::SetEnvironmentVariable("PATH", $newPath, "User")
            $env:PATH = "$glsDir;$env:PATH"
        }
        Write-Output "  [OK] Gradle Language Server instalado en $glsDir"
    } catch {
        Write-Output "  [WARN] No se pudo descargar/instalar Gradle Language Server: $_"
        Write-Output "         Instalar manualmente desde la extension VSCode: vscjava.vscode-gradle"
    }
} else {
    Write-Output "  [OK] Gradle Language Server ya instalado"
}

# --- Step 4: Instalar ktlint ---
Write-Output "`n[4/6] Instalando ktlint (formateador Kotlin)..."
try {
    $ktlintUrl = "https://github.com/pinterest/ktlint/releases/download/1.5.0/ktlint"
    $ktlintPath = "$env:LOCALAPPDATA\ktlint\ktlint.jar"
    $ktlintScript = "$env:LOCALAPPDATA\ktlint\ktlint.cmd"
    if (-not (Test-Path $ktlintScript)) {
        New-Item -ItemType Directory -Path "$env:LOCALAPPDATA\ktlint" -Force | Out-Null
        $wc = New-Object System.Net.WebClient
        $wc.DownloadFile($ktlintUrl, $ktlintPath)
        $launcher = @"
@echo off
java -jar "$ktlintPath" %*
"@
        Set-Content -Path $ktlintScript -Value $launcher

        $userPath = [Environment]::GetEnvironmentVariable("PATH", "User")
        if ($userPath -notlike "*$env:LOCALAPPDATA\ktlint*") {
            $newPath = "$env:LOCALAPPDATA\ktlint;$userPath"
            [Environment]::SetEnvironmentVariable("PATH", $newPath, "User")
        }
        Write-Output "  [OK] ktlint instalado"
    } else {
        Write-Output "  [OK] ktlint ya instalado"
    }
} catch {
    Write-Output "  [WARN] No se pudo instalar ktlint: $_"
    Write-Output "         Instalar manualmente desde: https://github.com/pinterest/ktlint"
}

# --- Step 5: Configurar variables de entorno ---
Write-Output "`n[5/6] Configurando variables de entorno..."
$envVars = @(
    @{Name="GITHUB_TOKEN"; Desc="Token de GitHub para MCP github server"}
    @{Name="MODELS_DEV_API_KEY"; Desc="API Key de models.dev para OpenCode"}
)
foreach ($ev in $envVars) {
    $current = [Environment]::GetEnvironmentVariable($ev.Name, "User")
    if ([string]::IsNullOrEmpty($current)) {
        Write-Output "  [PENDIENTE] $($ev.Name) no configurada — $($ev.Desc)"
    } else {
        Write-Output "  [OK] $($ev.Name) ya configurada"
    }
}

# --- Step 6: Verificar configuracion final ---
Write-Output "`n[6/6] Resumen final:"
Write-Output "  opencode.json       : $((Test-Path "$ProjectRoot\opencode.json").ToString())"
Write-Output "  .opencode/          : $((Test-Path "$ProjectRoot\.opencode").ToString())"
Write-Output "  .opencode/agents/   : $((Test-Path "$ProjectRoot\.opencode\agents").ToString())"
Write-Output "  .opencode/skills/   : $((Test-Path "$ProjectRoot\.opencode\skills").ToString())"
Write-Output "  .env.example        : $((Test-Path "$ProjectRoot\.env.example").ToString())"

$agentCount = (Get-ChildItem "$ProjectRoot\.opencode\agents\*.md" -ErrorAction SilentlyContinue).Count
$skillCount = (Get-ChildItem "$ProjectRoot\.opencode\skills\*\SKILL.md" -ErrorAction SilentlyContinue).Count
Write-Output "  Agentes             : $agentCount"
Write-Output "  Skills              : $skillCount"

Write-Output "`n========================================"
Write-Output " Setup completado."
Write-Output "========================================"
Write-Output "`nProximos pasos:"
Write-Output "  1. Abrir una NUEVA terminal (para que PATH se actualice)"
Write-Output "  2. cd $ProjectRoot"
Write-Output "  3. opencode"
Write-Output "  4. Probar: opencode 'Hola, presenta el proyecto'"
Write-Output "  5. Probar agentes: opencode '@android-dev muestra la estructura'"
Write-Output "`nDocumentacion completa: INFORME_CONFIG_OPENCODE.md"
