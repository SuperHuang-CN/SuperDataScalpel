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
$deployDirectory = Join-Path $projectRoot 'deploy\compute-engine'
$configPath = if ([string]::IsNullOrWhiteSpace($env:DATASCALPEL_DEPLOY_CONFIG)) {
    Join-Path $deployDirectory 'deploy-local.env'
} else {
    [IO.Path]::GetFullPath($env:DATASCALPEL_DEPLOY_CONFIG)
}
$exampleConfigPath = Join-Path $deployDirectory 'deploy-local.env.example'
$dockerfilePath = Join-Path $deployDirectory 'Dockerfile'
$composePath = Join-Path $deployDirectory 'compose.yaml'
$connectionInfoPath = Join-Path $deployDirectory 'connection-info.txt'
$mode = if ([string]::IsNullOrWhiteSpace($env:DATASCALPEL_CMD_ARG1)) { 'deploy' } else {
    $env:DATASCALPEL_CMD_ARG1.Trim().ToLowerInvariant()
}
$stackName = 'datascalpel-compute-engine'
$containerName = 'datascalpel-compute-engine'
$deploymentTimeZone = 'Asia/Shanghai'

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

function Read-DotEnv {
    param([Parameter(Mandatory = $true)][string]$Path)
    $result = [ordered]@{}
    foreach ($rawLine in [IO.File]::ReadAllLines($Path, [Text.Encoding]::UTF8)) {
        $line = $rawLine.Trim()
        if ($line.Length -eq 0 -or $line.StartsWith('#')) {
            continue
        }
        $separator = $line.IndexOf('=')
        if ($separator -lt 1) {
            throw "配置行格式无效：$rawLine"
        }
        $name = $line.Substring(0, $separator).Trim()
        $value = $line.Substring($separator + 1)
        if ($value.Length -ge 2) {
            if (($value.StartsWith('"') -and $value.EndsWith('"')) -or
                ($value.StartsWith("'") -and $value.EndsWith("'"))) {
                $value = $value.Substring(1, $value.Length - 2)
            }
        }
        $processValue = [Environment]::GetEnvironmentVariable($name, 'Process')
        $result[$name] = if ([string]::IsNullOrWhiteSpace($processValue)) { $value } else { $processValue }
    }
    return $result
}

function Require-Config {
    param(
        [Parameter(Mandatory = $true)][System.Collections.IDictionary]$Config,
        [Parameter(Mandatory = $true)][string]$Name,
        [switch]$AllowEmpty
    )
    if (-not $Config.Contains($Name)) {
        throw "缺少配置：$Name"
    }
    $value = [string]$Config[$Name]
    if (-not $AllowEmpty -and [string]::IsNullOrWhiteSpace($value)) {
        throw "配置不能为空：$Name"
    }
    if ($value -match '^<.+>$') {
        throw "请先在 deploy-local.env 中填写：$Name"
    }
    if ($value.Contains("`r") -or $value.Contains("`n") -or $value.Contains([char]0)) {
        throw "配置包含非法换行或 NUL 字符：$Name"
    }
    return $value
}

function New-RandomToken {
    $bytes = New-Object byte[] 32
    $rng = [Security.Cryptography.RandomNumberGenerator]::Create()
    try {
        $rng.GetBytes($bytes)
    } finally {
        $rng.Dispose()
    }
    return ([Convert]::ToBase64String($bytes)).TrimEnd('=').Replace('+', '-').Replace('/', '_')
}

function Set-DotEnvValue {
    param(
        [Parameter(Mandatory = $true)][string]$Path,
        [Parameter(Mandatory = $true)][string]$Name,
        [Parameter(Mandatory = $true)][string]$Value
    )
    $lines = [Collections.Generic.List[string]]::new()
    $found = $false
    foreach ($line in [IO.File]::ReadAllLines($Path, [Text.Encoding]::UTF8)) {
        if ($line -match ('^' + [regex]::Escape($Name) + '=')) {
            $lines.Add("$Name=$Value")
            $found = $true
        } else {
            $lines.Add($line)
        }
    }
    if (-not $found) {
        $lines.Add("$Name=$Value")
    }
    [IO.File]::WriteAllLines($Path, $lines, $utf8)
}

function ConvertTo-DotEnvLiteral {
    param([Parameter(Mandatory = $true)][AllowEmptyString()][string]$Value)
    if ($Value.Contains("`r") -or $Value.Contains("`n")) {
        throw 'dotenv 值不能包含换行'
    }
    return "'" + $Value.Replace('\', '\\').Replace("'", "\'") + "'"
}

function Test-True {
    param([AllowEmptyString()][string]$Value)
    return $Value -match '^(?i:true|1|yes|y)$'
}

function Enable-InsecureTlsForCurrentProcess {
    if ($null -eq ('DataScalpelCertificatePolicy' -as [type])) {
        Add-Type -TypeDefinition @'
using System.Net;

public static class DataScalpelCertificatePolicy
{
    public static void Enable()
    {
        ServicePointManager.ServerCertificateValidationCallback =
            (sender, certificate, chain, errors) => true;
    }
}
'@
    }
    [Net.ServicePointManager]::SecurityProtocol = [Net.SecurityProtocolType]::Tls12
    [DataScalpelCertificatePolicy]::Enable()
}

function Invoke-Ssh {
    param(
        [Parameter(Mandatory = $true)][string]$Target,
        [Parameter(Mandatory = $true)][string]$Command,
        [Parameter(Mandatory = $true)][string]$FailureMessage,
        [switch]$Capture
    )
    return Invoke-Native -FilePath 'ssh.exe' -Arguments @(
        '-o', 'BatchMode=yes',
        '-o', 'ConnectTimeout=10',
        $Target,
        $Command
    ) -FailureMessage $FailureMessage -Capture:$Capture
}

function Invoke-Portainer {
    param(
        [Parameter(Mandatory = $true)][string]$Method,
        [Parameter(Mandatory = $true)][string]$Uri,
        [Parameter(Mandatory = $true)][string]$ApiKey,
        [AllowNull()][object]$Body
    )
    $parameters = @{
        Method = $Method
        Uri = $Uri
        Headers = @{ 'X-API-Key' = $ApiKey }
        UseBasicParsing = $true
        TimeoutSec = 60
    }
    if ($null -ne $Body) {
        $parameters['ContentType'] = 'application/json'
        $parameters['Body'] = ($Body | ConvertTo-Json -Depth 8 -Compress)
    }
    return Invoke-RestMethod @parameters
}

function Wait-DispatcherReady {
    param(
        [Parameter(Mandatory = $true)][string]$BaseUrl,
        [int]$TimeoutSeconds = 180
    )
    $deadline = [DateTime]::UtcNow.AddSeconds($TimeoutSeconds)
    do {
        try {
            $response = Invoke-WebRequest -UseBasicParsing -Uri "$BaseUrl/health/ready" -TimeoutSec 5
            if ($response.StatusCode -eq 200) {
                return
            }
        } catch {
            Start-Sleep -Seconds 3
        }
    } while ([DateTime]::UtcNow -lt $deadline)
    throw "Dispatcher 在 ${TimeoutSeconds}s 内未就绪：$BaseUrl/health/ready"
}

try {
    if (-not (Test-Path -LiteralPath $configPath)) {
        if (-not (Test-Path -LiteralPath $exampleConfigPath)) {
            throw "找不到配置模板：$exampleConfigPath"
        }
        Copy-Item -LiteralPath $exampleConfigPath -Destination $configPath
        Set-DotEnvValue -Path $configPath -Name 'DATASCALPEL_TASK_DISPATCHER_TOKEN' -Value (New-RandomToken)
        Write-Host "已创建本地部署配置：$configPath" -ForegroundColor Yellow
        Write-Host '请填写其中的 <set-me> 项。Portainer API Key 可以暂时留空。' -ForegroundColor Yellow
        Start-Process -FilePath 'notepad.exe' -ArgumentList @($configPath)
        exit 2
    }

    $config = Read-DotEnv -Path $configPath
    $javaHome = Require-Config -Config $config -Name 'LOCAL_JAVA_HOME'
    $sshTarget = Require-Config -Config $config -Name 'SSH_TARGET'
    $remoteHostIp = Require-Config -Config $config -Name 'REMOTE_HOST_IP'
    $remoteRoot = Require-Config -Config $config -Name 'REMOTE_ROOT'
    $portainerUrl = (Require-Config -Config $config -Name 'PORTAINER_URL').TrimEnd('/')
    $portainerApiKey = Require-Config -Config $config -Name 'PORTAINER_API_KEY' -AllowEmpty
    $portainerEndpointId = Require-Config -Config $config -Name 'PORTAINER_ENDPOINT_ID' -AllowEmpty
    $portainerSkipTls = Require-Config -Config $config -Name 'PORTAINER_SKIP_TLS_VERIFY'

    if ($sshTarget -notmatch '^[a-zA-Z0-9_.@-]+$') {
        throw 'SSH_TARGET 包含不支持的字符'
    }
    if ($remoteHostIp -notmatch '^[a-zA-Z0-9_.:-]+$') {
        throw 'REMOTE_HOST_IP 包含不支持的字符'
    }
    if ($remoteRoot -notmatch '^/data/[a-zA-Z0-9_./-]+$' -or $remoteRoot.Contains('..')) {
        throw 'REMOTE_ROOT 必须是 /data 下不包含 .. 的绝对路径'
    }
    if (-not (Test-Path -LiteralPath (Join-Path $javaHome 'bin\java.exe'))) {
        throw "Java 21 不存在：$javaHome"
    }
    foreach ($requiredFile in @($dockerfilePath, $composePath, (Join-Path $projectRoot 'mvnw.cmd'))) {
        if (-not (Test-Path -LiteralPath $requiredFile)) {
            throw "缺少部署文件：$requiredFile"
        }
    }

    $requiredRuntimeKeys = @(
        'DATASCALPEL_TASK_DISPATCHER_TOKEN',
        'DATASCALPEL_DB_URL',
        'DATASCALPEL_DB_USERNAME',
        'DATASCALPEL_DB_PASSWORD',
        'DATASCALPEL_KAFKA_BOOTSTRAP_SERVERS',
        'DATASCALPEL_KAFKA_RUNNER_BOOTSTRAP_SERVERS',
        'DATASCALPEL_FILE_STORAGE_ENDPOINT',
        'DATASCALPEL_FILE_STORAGE_RUNNER_ENDPOINT',
        'DATASCALPEL_FILE_STORAGE_ACCESS_KEY',
        'DATASCALPEL_FILE_STORAGE_SECRET_KEY',
        'DATASCALPEL_COMMAND_TOPIC',
        'DATASCALPEL_RUNNER_EVENT_TOPIC',
        'DATASCALPEL_ADMIN_EVENT_TOPIC',
        'DATASCALPEL_MAX_QUEUED_EXECUTIONS',
        'DATASCALPEL_MAX_CONCURRENT_SUBMISSIONS',
        'DATASCALPEL_MAX_IN_FLIGHT_APPLICATIONS'
    )
    foreach ($key in $requiredRuntimeKeys) {
        [void](Require-Config -Config $config -Name $key)
    }

    [Environment]::SetEnvironmentVariable('JAVA_HOME', $javaHome, 'Process')
    [Environment]::SetEnvironmentVariable('PATH', "$javaHome\bin;$env:PATH", 'Process')
    $javaVersion = Invoke-Native -FilePath (Join-Path $javaHome 'bin\java.exe') -Arguments @('-version') `
        -FailureMessage 'Java 版本检查失败' -Capture
    if ($javaVersion -notmatch 'version\s+"21(?:[."]|\s)') {
        throw "需要 Java 21，当前输出：$javaVersion"
    }

    Write-Step '检查 SSH、Docker、数据目录和 Portainer'
    $remoteCheck = Invoke-Ssh -Target $sshTarget -Command (
        "set -eu; test `"`$(id -u)`" = 0; " +
        "docker version --format '{{.Server.Version}}'; " +
        "docker compose version; " +
        "test -d /data; test -w /data; " +
        "getenforce 2>/dev/null || true"
    ) -FailureMessage 'linux69 环境检查失败' -Capture
    Write-Host $remoteCheck

    try {
        $status = Invoke-RestMethod -UseBasicParsing -Uri "$portainerUrl/api/system/status" -TimeoutSec 10
    } catch {
        if (Test-True $portainerSkipTls) {
            Enable-InsecureTlsForCurrentProcess
            $status = Invoke-RestMethod -UseBasicParsing -Uri "$portainerUrl/api/system/status" -TimeoutSec 10
        } else {
            throw
        }
    }
    Write-Host "Portainer $($status.Version)：$portainerUrl"

    if ($mode -eq 'check') {
        Write-Host ''
        Write-Host '配置与基础连接检查通过。未构建、未上传、未更新远端容器。' -ForegroundColor Green
        exit 0
    }
    if ($mode -ne 'deploy') {
        throw "不支持的参数：$mode（支持：deploy、check）"
    }

    Write-Step '使用 Maven Wrapper 构建 Dispatcher 和 Local Runner'
    Push-Location $projectRoot
    try {
        Invoke-Native -FilePath (Join-Path $projectRoot 'mvnw.cmd') -Arguments @(
            '-pl', 'data-scalpel-task-dispatcher,data-scalpel-task-engine',
            '-am', 'package', '-DskipTests'
        ) -FailureMessage 'Maven 构建失败'
    } finally {
        Pop-Location
    }

    $dispatcherJar = Get-ChildItem -LiteralPath (Join-Path $projectRoot 'data-scalpel-task-dispatcher\target') `
        -Filter 'data-scalpel-task-dispatcher-*.jar' |
        Where-Object { $_.Name -notmatch '(\.original$|-sources\.jar$|-javadoc\.jar$)' } |
        Sort-Object LastWriteTimeUtc -Descending |
        Select-Object -First 1
    $runnerJar = Get-ChildItem -LiteralPath (Join-Path $projectRoot 'data-scalpel-task-engine\target') `
        -Filter 'data-scalpel-task-engine-*-runner-local.jar' |
        Sort-Object LastWriteTimeUtc -Descending |
        Select-Object -First 1
    if ($null -eq $dispatcherJar -or $null -eq $runnerJar) {
        throw '构建完成后未找到 Dispatcher 或 runner-local JAR'
    }

    $buildTag = [DateTime]::Now.ToString('yyyyMMdd-HHmmss')
    $runtimeDockerfileHash = (Get-FileHash -LiteralPath $dockerfilePath -Algorithm SHA256).Hash
    $runtimeHash = $runtimeDockerfileHash.Substring(0, 12).ToLowerInvariant()
    $runtimeImage = "datascalpel-compute-engine-runtime:java21-$runtimeHash"
    $remoteRelease = "$remoteRoot/releases/$buildTag"
    $remoteRuntimeDirectory = "$remoteRoot/runtime/$runtimeHash"
    $remoteComposeDirectory = "$remoteRoot/compose"
    $remoteDispatcherJar = "$remoteRelease/dispatcher.jar"
    $remoteRunnerJar = "$remoteRelease/task-runner-local.jar"
    $temporaryDirectory = Join-Path ([IO.Path]::GetTempPath()) ("datascalpel-deploy-" + [guid]::NewGuid())
    [void](New-Item -ItemType Directory -Path $temporaryDirectory)
    try {
        $remoteEnvPath = Join-Path $temporaryDirectory '.env'
        $runtime = [ordered]@{}
        foreach ($entry in $config.GetEnumerator()) {
            if ($entry.Key -like 'DATASCALPEL_*') {
                $runtime[$entry.Key] = [string]$entry.Value
            }
        }
        $runtime['COMPUTE_ENGINE_RUNTIME_IMAGE'] = $runtimeImage
        $runtime['COMPUTE_ENGINE_BIND_ADDRESS'] = $remoteHostIp
        $runtime['COMPUTE_ENGINE_ROOT'] = $remoteRoot
        $runtime['DATASCALPEL_TASK_DISPATCHER_APP_JAR'] = $remoteDispatcherJar
        $runtime['DATASCALPEL_TASK_DISPATCHER_RUNNER_JAR'] = $remoteRunnerJar
        $runtime['COMPUTE_ENGINE_TIME_ZONE'] = $deploymentTimeZone
        $runtime['COMPUTE_ENGINE_JAVA_TOOL_OPTIONS'] =
            "-Djava.net.preferIPv4Stack=true -Duser.timezone=$deploymentTimeZone"
        $runnerJavaOptions = if ($runtime.Contains('DATASCALPEL_TASK_DISPATCHER_RUNNER_JAVA_OPTIONS')) {
            [string]$runtime['DATASCALPEL_TASK_DISPATCHER_RUNNER_JAVA_OPTIONS']
        } else {
            '-Xms512m -Xmx3g'
        }
        if ($runnerJavaOptions -notmatch '(?:^|\s)-Duser\.timezone(?:=|\s)') {
            $runnerJavaOptions = "$runnerJavaOptions -Duser.timezone=$deploymentTimeZone"
        }
        $runtime['DATASCALPEL_TASK_DISPATCHER_RUNNER_JAVA_OPTIONS'] = $runnerJavaOptions

        $envLines = [Collections.Generic.List[string]]::new()
        foreach ($entry in $runtime.GetEnumerator()) {
            $envLines.Add("$($entry.Key)=$(ConvertTo-DotEnvLiteral -Value ([string]$entry.Value))")
        }
        [IO.File]::WriteAllLines($remoteEnvPath, $envLines, $utf8)

        Write-Step "上传发布包到 $remoteRelease"
        [void](Invoke-Ssh -Target $sshTarget -Command (
            "set -eu; mkdir -p '$remoteRelease' '$remoteRuntimeDirectory' '$remoteComposeDirectory' " +
            "'$remoteRoot/state/task-executions' '$remoteRoot/state/task-streaming-checkpoints'; " +
            "chmod 700 '$remoteRoot' '$remoteRoot/state' '$remoteRoot/state/task-executions' " +
            "'$remoteRoot/state/task-streaming-checkpoints'"
        ) -FailureMessage '创建远端部署目录失败')

        Invoke-Native -FilePath 'scp.exe' -Arguments @(
            '-o', 'BatchMode=yes',
            $dispatcherJar.FullName,
            "${sshTarget}:$remoteRelease/dispatcher.jar"
        ) -FailureMessage '上传 Dispatcher JAR 失败'
        Invoke-Native -FilePath 'scp.exe' -Arguments @(
            '-o', 'BatchMode=yes',
            $runnerJar.FullName,
            "${sshTarget}:$remoteRelease/task-runner-local.jar"
        ) -FailureMessage '上传 Runner JAR 失败'
        Invoke-Native -FilePath 'scp.exe' -Arguments @(
            '-o', 'BatchMode=yes',
            $composePath,
            "${sshTarget}:$remoteComposeDirectory/compose.yaml"
        ) -FailureMessage '上传 Compose 文件失败'
        Invoke-Native -FilePath 'scp.exe' -Arguments @(
            '-o', 'BatchMode=yes',
            $remoteEnvPath,
            "${sshTarget}:$remoteComposeDirectory/.env"
        ) -FailureMessage '上传远端环境文件失败'

        [void](Invoke-Ssh -Target $sshTarget -Command (
            "set -eu; chmod 600 '$remoteComposeDirectory/.env'"
        ) -FailureMessage '设置远端部署文件权限失败')

        $runtimeImageState = Invoke-Ssh -Target $sshTarget -Command (
            "if docker image inspect '$runtimeImage' >/dev/null 2>&1; " +
            "then printf present; else printf missing; fi"
        ) -FailureMessage '检查远端运行时镜像失败' -Capture
        if ($runtimeImageState -eq 'missing') {
            Write-Step "首次构建 Java 21 + Docker CLI 运行时镜像：$runtimeImage"
            Invoke-Native -FilePath 'scp.exe' -Arguments @(
                '-o', 'BatchMode=yes',
                $dockerfilePath,
                "${sshTarget}:$remoteRuntimeDirectory/Dockerfile"
            ) -FailureMessage '上传运行时 Dockerfile 失败'
            [void](Invoke-Ssh -Target $sshTarget -Command (
                "set -eu; cd '$remoteRuntimeDirectory'; " +
                "(docker build -t '$runtimeImage' . || " +
                "{ sleep 10; docker build -t '$runtimeImage' .; } || " +
                "{ sleep 20; docker build -t '$runtimeImage' .; })"
            ) -FailureMessage '远端运行时镜像构建失败')
        } elseif ($runtimeImageState -eq 'present') {
            Write-Host "复用远端运行时镜像：$runtimeImage"
        } else {
            throw "无法识别远端运行时镜像状态：$runtimeImageState"
        }

        $composeContent = [IO.File]::ReadAllText($composePath, [Text.Encoding]::UTF8)
        if (-not [string]::IsNullOrWhiteSpace($portainerApiKey)) {
            Write-Step "通过 Portainer API 更新 Stack：$stackName"
            if (Test-True $portainerSkipTls) {
                Enable-InsecureTlsForCurrentProcess
            }
            if ([string]::IsNullOrWhiteSpace($portainerEndpointId)) {
                $endpoints = @(Invoke-Portainer -Method 'GET' -Uri "$portainerUrl/api/endpoints" `
                    -ApiKey $portainerApiKey -Body $null)
                if ($endpoints.Count -ne 1) {
                    throw 'Portainer 中不是恰好一个 Environment，请在配置中填写 PORTAINER_ENDPOINT_ID'
                }
                $portainerEndpointId = [string]$endpoints[0].Id
            }
            $stackEnvironment = @()
            foreach ($entry in $runtime.GetEnumerator()) {
                $stackEnvironment += @{ name = [string]$entry.Key; value = [string]$entry.Value }
            }
            $stacks = @(Invoke-Portainer -Method 'GET' `
                -Uri "$portainerUrl/api/stacks?endpointId=$portainerEndpointId" `
                -ApiKey $portainerApiKey -Body $null)
            $stack = $stacks | Where-Object { $_.Name -eq $stackName } | Select-Object -First 1
            if ($null -eq $stack) {
                $body = @{
                    name = $stackName
                    stackFileContent = $composeContent
                    env = $stackEnvironment
                }
                [void](Invoke-Portainer -Method 'POST' `
                    -Uri "$portainerUrl/api/stacks/create/standalone/string?endpointId=$portainerEndpointId" `
                    -ApiKey $portainerApiKey -Body $body)
            } else {
                $body = @{
                    stackFileContent = $composeContent
                    env = $stackEnvironment
                    prune = $false
                    pullImage = $false
                }
                [void](Invoke-Portainer -Method 'PUT' `
                    -Uri "$portainerUrl/api/stacks/$($stack.Id)?endpointId=$portainerEndpointId" `
                    -ApiKey $portainerApiKey -Body $body)
            }
            $deploymentMode = "Portainer managed Stack（Environment ID: $portainerEndpointId）"
        } else {
            Write-Step "通过 SSH Docker Compose 更新 Stack：$stackName"
            [void](Invoke-Ssh -Target $sshTarget -Command (
                "set -eu; cd '$remoteComposeDirectory'; " +
                "docker compose --project-name '$stackName' --env-file .env -f compose.yaml up -d --force-recreate"
            ) -FailureMessage 'Docker Compose 部署失败')
            $deploymentMode = 'SSH Docker Compose（Portainer 中显示为 external Stack）'
        }

        Write-Step '等待 Dispatcher 就绪'
        $dispatcherBaseUrl = "http://${remoteHostIp}:18092"
        try {
            Wait-DispatcherReady -BaseUrl $dispatcherBaseUrl -TimeoutSeconds 180
        } catch {
            Write-Host 'Dispatcher 未就绪，输出远端最近日志：' -ForegroundColor Yellow
            try {
                Invoke-Ssh -Target $sshTarget -Command "docker logs --tail 200 '$containerName'" `
                    -FailureMessage '读取 Dispatcher 日志失败'
            } catch {
                Write-Warning $_
            }
            throw
        }

        $dispatcherInfo = Invoke-RestMethod -UseBasicParsing -Uri "$dispatcherBaseUrl/api/v1/dispatcher/info" `
            -Headers @{ Authorization = "Bearer $($runtime['DATASCALPEL_TASK_DISPATCHER_TOKEN'])" } `
            -TimeoutSec 15

        $connectionLines = @(
            "name=linux69-local-docker",
            "dispatcherBaseUrl=$dispatcherBaseUrl",
            "accessToken=$($runtime['DATASCALPEL_TASK_DISPATCHER_TOKEN'])",
            "expectedBackendType=LOCAL_DOCKER",
            "commandTopic=$($runtime['DATASCALPEL_COMMAND_TOPIC'])",
            "runnerEventTopic=$($runtime['DATASCALPEL_RUNNER_EVENT_TOPIC'])",
            "adminEventTopic=$($runtime['DATASCALPEL_ADMIN_EVENT_TOPIC'])",
            "maxQueuedExecutions=$($runtime['DATASCALPEL_MAX_QUEUED_EXECUTIONS'])",
            "maxConcurrentSubmissions=$($runtime['DATASCALPEL_MAX_CONCURRENT_SUBMISSIONS'])",
            "maxInFlightApplications=$($runtime['DATASCALPEL_MAX_IN_FLIGHT_APPLICATIONS'])",
            "healthLive=$dispatcherBaseUrl/health/live",
            "healthReady=$dispatcherBaseUrl/health/ready",
            "stackName=$stackName",
            "containerName=$containerName",
            "runtimeImage=$runtimeImage",
            "release=$remoteRelease",
            "dispatcherJar=$remoteDispatcherJar",
            "runnerJar=$remoteRunnerJar",
            "deploymentMode=$deploymentMode"
        )
        [IO.File]::WriteAllLines($connectionInfoPath, $connectionLines, $utf8)

        Write-Host ''
        Write-Host '部署成功。' -ForegroundColor Green
        Write-Host "Stack/容器：$stackName"
        Write-Host "运行时镜像：$runtimeImage"
        Write-Host "发布目录：$remoteRelease"
        Write-Host "Dispatcher：$dispatcherBaseUrl"
        Write-Host "后端：$($dispatcherInfo.backendType)"
        Write-Host "部署方式：$deploymentMode"
        Write-Host "完整连接信息（含 Token）：$connectionInfoPath"
        Write-Host ''
        Write-Host 'Admin 计算引擎连接参数：' -ForegroundColor Cyan
        Write-Host "  dispatcherBaseUrl = $dispatcherBaseUrl"
        Write-Host '  expectedBackendType = LOCAL_DOCKER'
        Write-Host "  commandTopic = $($runtime['DATASCALPEL_COMMAND_TOPIC'])"
        Write-Host "  runnerEventTopic = $($runtime['DATASCALPEL_RUNNER_EVENT_TOPIC'])"
        Write-Host "  adminEventTopic = $($runtime['DATASCALPEL_ADMIN_EVENT_TOPIC'])"
        Write-Host '  accessToken = 请从 connection-info.txt 复制'
    } finally {
        if (Test-Path -LiteralPath $temporaryDirectory) {
            Remove-Item -LiteralPath $temporaryDirectory -Recurse -Force
        }
    }
} catch {
    Write-Host ''
    Write-Host "部署失败：$($_.Exception.Message)" -ForegroundColor Red
    exit 1
}
