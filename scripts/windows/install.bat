@echo off
REM SimpMusic offline installer for Windows (x64 / ARM64 via Prism emulation).
REM Double-click this file. UAC will prompt for admin (needed for cert + sideload).
REM
REM Bundle requirement (same folder as this .bat):
REM   - simpmusic.crt
REM   - simpmusic-<version>.x64.msix

setlocal EnableExtensions
cd /d "%~dp0"
set "SIMPMUSIC_DIR=%~dp0"

REM --- 0. Pre-flight check (ensure files were extracted from ZIP) --------------
if not exist "%SIMPMUSIC_DIR%simpmusic.crt" (
    echo.
    echo [ERRO / ERROR]
    echo Os arquivos nao foram encontrados nesta pasta!
    echo Certifique-se de EXTRAIR o conteudo do arquivo ZIP antes de executar o instalador.
    echo (Nao execute o arquivo diretamente de dentro do ZIP compactado).
    echo.
    echo Files not found next to install.bat.
    echo Please EXTRACT all files from the ZIP before running install.bat.
    echo.
    pause
    exit /b 1
)

set "SIMPMUSIC_MSIX="
for %%f in ("%SIMPMUSIC_DIR%simpmusic-*.msix") do set "SIMPMUSIC_MSIX=%%f"

if not defined SIMPMUSIC_MSIX (
    echo.
    echo [ERRO / ERROR]
    echo Nenhum pacote simpmusic-*.msix foi encontrado nesta pasta.
    echo Extraia todos os arquivos do instalador ZIP para uma pasta comum e tente novamente.
    echo.
    echo No simpmusic-*.msix package found in this folder.
    echo.
    pause
    exit /b 1
)

REM --- 1. Self-elevate to admin -----------------------------------------------
net session >nul 2>&1
if %errorlevel% neq 0 (
    echo Solicitando elevacao de administrador / Requesting admin elevation...
    powershell -NoProfile -ExecutionPolicy Bypass -Command "Start-Process -FilePath '%comspec%' -ArgumentList '/c `\"`\"%~f0`\"`\"' -Verb RunAs"
    exit /b
)

echo.
echo ===========================================
echo       SimpMusic Offline Installer
echo ===========================================
echo.

REM --- 2. Install cert to Trusted People (idempotent via certutil) -----------
echo [1/3] Importando certificado simpmusic.crt para TrustedPeople...
certutil -addstore -f "TrustedPeople" "%SIMPMUSIC_DIR%simpmusic.crt"
if %errorlevel% neq 0 (
    echo.
    echo [ERRO] Falha ao importar o certificado de assinatura / Certificate import failed.
    pause
    exit /b 1
)

REM --- 3. Close lingering processes and install MSIX -------------------------
echo.
echo [2/3] Instalando/atualizando o SimpMusic...
set "SIMPMUSIC_CRT=%SIMPMUSIC_DIR%simpmusic.crt"
powershell -NoProfile -ExecutionPolicy Bypass -Command ^
    "$msix = $env:SIMPMUSIC_MSIX; " ^
    "Get-Process -Name 'SimpMusic','updatecheck' -ErrorAction SilentlyContinue | Stop-Process -Force -ErrorAction SilentlyContinue; " ^
    "try { " ^
    "    Add-AppxPackage -Path $msix -ForceApplicationShutdown -ForceUpdateFromAnyVersion -ErrorAction Stop; " ^
    "    Write-Host 'Pacote MSIX instalado/atualizado com sucesso.'; " ^
    "    exit 0 " ^
    "} catch { " ^
    "    Write-Host 'Atualizacao direta falhou. Removendo versao anterior e reinstalando...'; " ^
    "    Get-AppxPackage -Name '*Simpmusic*' -ErrorAction SilentlyContinue | Remove-AppxPackage -ErrorAction SilentlyContinue; " ^
    "    try { " ^
    "        Add-AppxPackage -Path $msix -ForceApplicationShutdown -ErrorAction Stop; " ^
    "        Write-Host 'Instalacao limpa concluida com sucesso.'; " ^
    "        exit 0 " ^
    "    } catch { " ^
    "        Write-Host $_; " ^
    "        exit 1 " ^
    "    } " ^
    "}"

if %errorlevel% neq 0 (
    echo.
    echo [ERRO] Falha ao instalar o aplicativo via Add-AppxPackage.
    echo Sugestoes para resolver:
    echo   1. Ative o 'Modo de Desenvolvedor' no Windows:
    echo      Configuracoes -^> Privacidade e Seguranca -^> Para desenvolvedores -^> Modo de Desenvolvedor
    echo   2. Verifique se o Windows Update esta ativo
    echo   3. Reinicie o computador e execute install.bat novamente
    echo.
    pause
    exit /b 1
)

REM --- 4. Launch app ---------------------------------------------------------
echo.
echo [3/3] Iniciando o SimpMusic / Launching SimpMusic ...
start "" "shell:appsFolder\Simpmusic_ejp2bhxmz1qq6!Simpmusic"

echo.
echo SimpMusic instalado com sucesso! / SimpMusic installed successfully.
echo.
pause
endlocal
