@echo off
setlocal
set "DATASCALPEL_CMD_PATH=%~f0"
set "DATASCALPEL_CMD_ARG1=%~1"
powershell.exe -NoLogo -NoProfile -ExecutionPolicy Bypass -Command "$source=[IO.File]::ReadAllText($env:DATASCALPEL_CMD_PATH,[Text.Encoding]::UTF8);$marker='# DATASCALPEL_POWERSHELL'+'_BODY';$offset=$source.IndexOf($marker);if($offset -lt 0){throw 'PowerShell body marker was not found.'};& ([ScriptBlock]::Create($source.Substring($offset+$marker.Length)))"
set "DATASCALPEL_EXIT_CODE=%ERRORLEVEL%"
endlocal & exit /b %DATASCALPEL_EXIT_CODE%

# DATASCALPEL_POWERSHELL_BODY

Set-StrictMode -Version Latest
$ErrorActionPreference = 'Stop'
$utf8 = New-Object System.Text.UTF8Encoding($false)
[Console]::InputEncoding = $utf8
[Console]::OutputEncoding = $utf8
$OutputEncoding = $utf8

$scriptPath = [IO.Path]::GetFullPath($env:DATASCALPEL_CMD_PATH)
$projectRoot = Split-Path -Parent $scriptPath
$mode = if ([string]::IsNullOrWhiteSpace($env:DATASCALPEL_CMD_ARG1)) {
    'deploy'
} else {
    $env:DATASCALPEL_CMD_ARG1.Trim().ToLowerInvariant()
}
$sshTarget = if ([string]::IsNullOrWhiteSpace($env:DATASCALPEL_SERVICE_ENGINE_SSH_TARGET)) {
    'linux5'
} else {
    $env:DATASCALPEL_SERVICE_ENGINE_SSH_TARGET.Trim()
}
$composeDirectory = if ([string]::IsNullOrWhiteSpace($env:DATASCALPEL_SERVICE_ENGINE_COMPOSE_DIR)) {
    '/root/docker-compose/datascalpel-service-engine'
} else {
    $env:DATASCALPEL_SERVICE_ENGINE_COMPOSE_DIR.TrimEnd('/')
}
$serviceName = 'service-engine'
$containerJarPath = '/opt/datascalpel/app.jar'

function Write-Step {
    param([Parameter(Mandatory = $true)][string]$Message)
    Write-Host ''
    Write-Host "==> $Message" -ForegroundColor Cyan
}

function Invoke-Native {
    param(
        [Parameter(Mandatory = $true)][string]$FilePath,
        [Parameter(Mandatory = $true)][string[]]$Arguments,
        [Parameter(Mandatory = $true)][string]$FailureMessage,
        [switch]$Capture
    )
    if ($Capture) {
        $previousErrorAction = $ErrorActionPreference
        try {
            $ErrorActionPreference = 'Continue'
            $output = & $FilePath @Arguments 2>&1
            $exitCode = $LASTEXITCODE
        } finally {
            $ErrorActionPreference = $previousErrorAction
        }
        if ($exitCode -ne 0) {
            throw "$FailureMessage`n$($output -join [Environment]::NewLine)"
        }
        return ($output -join [Environment]::NewLine).Trim()
    }
    & $FilePath @Arguments
    if ($LASTEXITCODE -ne 0) {
        throw "$FailureMessage（退出码：$LASTEXITCODE）"
    }
}

function Invoke-Ssh {
    param(
        [Parameter(Mandatory = $true)][string]$Command,
        [Parameter(Mandatory = $true)][string]$FailureMessage,
        [switch]$Capture
    )
    $normalizedCommand = $Command.Replace("`r", '')
    return Invoke-Native -FilePath 'ssh.exe' -Arguments @(
        '-n',
        '-o', 'BatchMode=yes',
        '-o', 'ConnectTimeout=10',
        $sshTarget,
        $normalizedCommand
    ) -FailureMessage $FailureMessage -Capture:$Capture
}

function Wait-ContainerHealthy {
    param(
        [Parameter(Mandatory = $true)][string]$ContainerName,
        [int]$TimeoutSeconds = 180
    )
    $waitCommand = @'
set -eu
deadline=$(($(date +%s) + __TIMEOUT__))
last_status=
while [ "$(date +%s)" -lt "$deadline" ]; do
    status=$(docker inspect --format '{{if .State.Health}}{{.State.Health.Status}}{{else}}{{.State.Status}}{{end}}' '__CONTAINER__' 2>/dev/null || printf missing)
    if [ "$status" != "$last_status" ]; then
        printf '容器状态：%s\n' "$status"
        last_status=$status
    fi
    case "$status" in
        healthy) exit 0 ;;
        unhealthy|exited|dead) exit 1 ;;
    esac
    sleep 3
done
printf '容器健康检查超时，最后状态：%s\n' "$last_status" >&2
exit 1
'@
    $waitCommand = $waitCommand.Replace('__TIMEOUT__', [string]$TimeoutSeconds)
    $waitCommand = $waitCommand.Replace('__CONTAINER__', $ContainerName)
    [void](Invoke-Ssh -Command $waitCommand -FailureMessage "容器在 ${TimeoutSeconds}s 内未变为 healthy")
}

try {
    if ($mode -notin @('deploy', 'check')) {
        throw "不支持的参数：$mode（支持：deploy、check）"
    }
    if ($sshTarget -notmatch '^[a-zA-Z0-9_.@-]+$') {
        throw 'SSH 目标包含不支持的字符'
    }
    if ($composeDirectory -notmatch '^/[a-zA-Z0-9_./-]+$' -or $composeDirectory.Contains('..')) {
        throw 'Compose 目录必须是不包含 .. 的 Linux 绝对路径'
    }
    foreach ($command in @('ssh.exe', 'scp.exe')) {
        if ($null -eq (Get-Command $command -ErrorAction SilentlyContinue)) {
            throw "找不到命令：$command"
        }
    }
    $mavenWrapper = Join-Path $projectRoot 'mvnw.cmd'
    if (-not (Test-Path -LiteralPath $mavenWrapper)) {
        throw "找不到 Maven Wrapper：$mavenWrapper"
    }

    Write-Step "检查 $sshTarget 上的 Compose 服务"
    $inspectionCommand = @'
set -eu
cd '__COMPOSE__'
test -f docker-compose.yml -o -f compose.yaml
docker compose config --services | grep -Fx '__SERVICE__' >/dev/null
container_id=$(docker compose ps -q '__SERVICE__')
test -n "$container_id"
container_name=$(docker inspect --format '{{.Name}}' "$container_id" | sed 's#^/##')
jar_path=$(docker inspect --format '{{range .Mounts}}{{println .Destination .Source}}{{end}}' "$container_id" | grep -F '__CONTAINER_JAR__ ' | cut -d ' ' -f 2-)
test -n "$jar_path"
test -f "$jar_path"
jar_hash=$(sha256sum "$jar_path" | cut -d ' ' -f 1)
health=$(docker inspect --format '{{if .State.Health}}{{.State.Health.Status}}{{else}}{{.State.Status}}{{end}}' "$container_id")
printf '%s\n%s\n%s\n%s\n' "$container_name" "$jar_path" "$jar_hash" "$health"
'@
    $inspectionCommand = $inspectionCommand.Replace('__COMPOSE__', $composeDirectory)
    $inspectionCommand = $inspectionCommand.Replace('__SERVICE__', $serviceName)
    $inspectionCommand = $inspectionCommand.Replace('__CONTAINER_JAR__', $containerJarPath)
    $inspection = Invoke-Ssh -Command $inspectionCommand -FailureMessage '读取远端 Service Engine 状态失败' -Capture
    $inspectionLines = @($inspection -split '\r?\n')
    if ($inspectionLines.Count -ne 4) {
        throw "无法识别远端检查结果：$inspection"
    }
    $containerName = $inspectionLines[0].Trim()
    $remoteJarPath = $inspectionLines[1].Trim()
    $currentRemoteHash = $inspectionLines[2].Trim()
    $currentStatus = $inspectionLines[3].Trim()
    if ($containerName -notmatch '^[a-zA-Z0-9_.-]+$') {
        throw "识别到的容器名不安全：$containerName"
    }
    if ($remoteJarPath -notmatch '^/data/[a-zA-Z0-9_./-]+\.jar$' -or $remoteJarPath.Contains('..')) {
        throw "识别到的远端 JAR 路径不安全：$remoteJarPath"
    }
    if ($currentRemoteHash -notmatch '^[a-f0-9]{64}$') {
        throw "识别到的远端 SHA-256 无效：$currentRemoteHash"
    }
    $remoteAppDirectory = $remoteJarPath.Substring(0, $remoteJarPath.LastIndexOf('/'))

    Write-Host "Compose：$composeDirectory"
    Write-Host "服务：$serviceName"
    Write-Host "容器：$containerName"
    Write-Host "JAR：$remoteJarPath"
    Write-Host "当前 SHA-256：$currentRemoteHash"
    Write-Host "当前状态：$currentStatus"

    if ($mode -eq 'check') {
        Write-Host ''
        Write-Host '检查通过，未构建、上传或重启服务。' -ForegroundColor Green
        exit 0
    }

    Write-Step '使用工程 Maven Wrapper 构建 Service Engine'
    Push-Location $projectRoot
    try {
        Invoke-Native -FilePath $mavenWrapper -Arguments @(
            '-pl', 'data-scalpel-service-engine',
            '-am',
            'package',
            '-DskipTests'
        ) -FailureMessage 'Service Engine Maven 构建失败'
    } finally {
        Pop-Location
    }

    $jar = Get-ChildItem -LiteralPath (Join-Path $projectRoot 'data-scalpel-service-engine\target') `
        -Filter 'data-scalpel-service-engine-*.jar' |
        Where-Object { $_.Name -notmatch '(\.original$|-sources\.jar$|-javadoc\.jar$)' } |
        Sort-Object LastWriteTimeUtc -Descending |
        Select-Object -First 1
    if ($null -eq $jar) {
        throw '构建完成后未找到 Service Engine 可执行 JAR'
    }
    $localHash = (Get-FileHash -LiteralPath $jar.FullName -Algorithm SHA256).Hash.ToLowerInvariant()
    Write-Host "本地 JAR：$($jar.FullName)"
    Write-Host "本地 SHA-256：$localHash"
    if ($localHash -eq $currentRemoteHash) {
        Write-Host ''
        Write-Host '远端已是相同版本，无需更新。' -ForegroundColor Green
        exit 0
    }

    $buildTag = [DateTime]::Now.ToString('yyyyMMdd-HHmmss')
    $remoteTemporaryJar = "$remoteJarPath.upload-$buildTag"
    $remoteBackupDirectory = "$remoteAppDirectory/backups"
    $remoteBackupJar = "$remoteBackupDirectory/data-scalpel-service-engine-$buildTag-$($currentRemoteHash.Substring(0, 12)).jar"
    $swapped = $false

    try {
        Write-Step '上传新 JAR 并校验 SHA-256'
        [void](Invoke-Ssh -Command (
            "set -eu; mkdir -p '$remoteBackupDirectory'; test -w '$remoteAppDirectory'; " +
            "rm -f '$remoteTemporaryJar'"
        ) -FailureMessage '准备远端上传目录失败')
        Invoke-Native -FilePath 'scp.exe' -Arguments @(
            '-o', 'BatchMode=yes',
            $jar.FullName,
            "${sshTarget}:$remoteTemporaryJar"
        ) -FailureMessage '上传 Service Engine JAR 失败'
        $uploadedHash = Invoke-Ssh -Command (
            "set -eu; sha256sum '$remoteTemporaryJar' | cut -d ' ' -f 1"
        ) -FailureMessage '读取上传文件校验值失败' -Capture
        if ($uploadedHash -ne $localHash) {
            throw "上传文件 SHA-256 不一致，本地 $localHash，远端 $uploadedHash"
        }

        Write-Step "备份旧版本并原子替换 JAR"
        [void](Invoke-Ssh -Command (
            "set -eu; " +
            "cp -a '$remoteJarPath' '$remoteBackupJar'; " +
            "chown --reference='$remoteJarPath' '$remoteTemporaryJar'; " +
            "chmod --reference='$remoteJarPath' '$remoteTemporaryJar'; " +
            "if command -v chcon >/dev/null 2>&1; then chcon --reference='$remoteJarPath' '$remoteTemporaryJar' || true; fi; " +
            "mv -f '$remoteTemporaryJar' '$remoteJarPath'"
        ) -FailureMessage '替换远端 Service Engine JAR 失败')
        $swapped = $true

        Write-Step '重建 Service Engine 容器'
        [void](Invoke-Ssh -Command (
            "set -eu; cd '$composeDirectory'; " +
            "docker compose up -d --force-recreate '$serviceName'"
        ) -FailureMessage 'Docker Compose 更新失败')

        Write-Step '等待 Service Engine 健康'
        Wait-ContainerHealthy -ContainerName $containerName -TimeoutSeconds 180
        $deployedHash = Invoke-Ssh -Command (
            "set -eu; sha256sum '$remoteJarPath' | cut -d ' ' -f 1"
        ) -FailureMessage '读取已部署 JAR 校验值失败' -Capture
        if ($deployedHash -ne $localHash) {
            throw "部署后 JAR SHA-256 不一致，期望 $localHash，实际 $deployedHash"
        }

        Write-Host ''
        Write-Host 'Service Engine 更新成功。' -ForegroundColor Green
        Write-Host "容器：$containerName"
        Write-Host "新 SHA-256：$deployedHash"
        Write-Host "旧版本备份：$remoteBackupJar"
    } catch {
        $deploymentError = $_.Exception.Message
        Write-Host ''
        Write-Host "更新失败：$deploymentError" -ForegroundColor Red
        try {
            Invoke-Ssh -Command "docker logs --tail 200 '$containerName'" `
                -FailureMessage '读取 Service Engine 日志失败'
        } catch {
            Write-Warning $_.Exception.Message
        }
        if ($swapped) {
            Write-Step '自动恢复旧 JAR 并重建容器'
            $rollbackTemporaryJar = "$remoteJarPath.rollback-$buildTag"
            try {
                [void](Invoke-Ssh -Command (
                    "set -eu; " +
                    "cp -a '$remoteBackupJar' '$rollbackTemporaryJar'; " +
                    "mv -f '$rollbackTemporaryJar' '$remoteJarPath'; " +
                    "cd '$composeDirectory'; " +
                    "docker compose up -d --force-recreate '$serviceName'"
                ) -FailureMessage '恢复旧 Service Engine JAR 失败')
                Wait-ContainerHealthy -ContainerName $containerName -TimeoutSeconds 180
                Write-Host "已恢复旧版本：$remoteBackupJar" -ForegroundColor Yellow
            } catch {
                throw "更新失败且自动回滚失败。原始错误：$deploymentError；回滚错误：$($_.Exception.Message)"
            }
        }
        throw $deploymentError
    } finally {
        try {
            [void](Invoke-Ssh -Command "rm -f '$remoteTemporaryJar'" `
                -FailureMessage '清理远端临时 JAR 失败')
        } catch {
            Write-Warning $_.Exception.Message
        }
    }
} catch {
    Write-Host ''
    Write-Host "部署失败：$($_.Exception.Message)" -ForegroundColor Red
    exit 1
}
