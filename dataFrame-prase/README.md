# dataFrame-prase

`dataFrame-prase` 是一个 Java 17 本地 Web 工具：浏览器或 HTTP 客户端上传 KingstVIS 导出的稀疏边沿 CSV，后端按选中的遥控器配置完成信号解析，并直接下载 Excel 结果。

当前阶段只提供后端接口和 Windows 启动脚本，不包含静态网页，也不会自动打开浏览器。原命令行参数和命令行输出方式不再维护。

## 构建和测试

必须使用 JDK 17。在 PowerShell 中执行：

```powershell
$env:JAVA_HOME = 'D:\software\jdK\jdk-17.0.15'
$env:Path = "$env:JAVA_HOME\bin;$env:Path"
mvn "-DargLine=-Djava.io.tmpdir=D:\IdeaProgram\DFP\3-server\dataFrame-prase\.codex-tmp\junit" clean test
mvn "-DskipTests" clean package
```

可执行 JAR 生成在：

```text
target\dataFrame-prase.jar
```

## 交付目录和启动

```text
5-again/
├─ run.bat
├─ dataFrame-prase.jar
├─ jdk-17.0.15/
│  └─ bin/java.exe
├─ config/
│  └─ remote-configs.json
└─ logs/                    首次启动时自动创建
```

双击 `run.bat` 启动。脚本只使用同目录下的 JDK 17，并以前台方式运行 JAR；关闭黑窗口后服务随 Java 进程停止。启动异常时脚本会暂停并保留错误信息。

服务仅监听 `127.0.0.1`，依次尝试端口 `8080`、`8081`、`8082`。只有端口占用才会换端口，其他启动异常会直接退出。

## 遥控器配置

外部配置文件固定为 `config/remote-configs.json`，必须使用 UTF-8 编码：

```json
{
  "configs": [
    {
      "key": "remote-b04287",
      "name": "默认遥控器（B0 42 87）",
      "remoteId": "B0 42 87",
      "charset": "GBK",
      "idleThresholdMs": 40,
      "idleLevel": 0,
      "levelMapping": "inverted"
    }
  ]
}
```

每套遥控器都有唯一 `key` 和前端显示用 `name`。`remoteId` 必须是三个十六进制字节；`idleThresholdMs` 必须大于 0；`idleLevel` 只能是 0 或 1；`levelMapping` 只能是 `direct` 或 `inverted`。

`/dfp/configs` 和 `/dfp/csvParse` 每次调用都会重新读取并校验该 JSON，不使用缓存。保存修改后，下一个接口请求立即生效，无需重启 `run.bat`。

## 后端接口

### 健康检查

```text
GET /dfp/health
```

返回 `status`、实际监听 `port` 和程序 `version`，且不读取遥控器 JSON。

### 配置列表

```text
GET /dfp/configs
```

只返回每套配置的 `key` 和 `name`，完整解析参数不会返回前端。

### CSV 解析与 Excel 下载

```text
POST /dfp/csvParse
Content-Type: multipart/form-data
file=<CSV 文件>
configKey=<配置 key>
```

成功响应是标准 XLSX 下载，文件名为 `原CSV文件名-解析结果.xlsx`；失败响应是包含 `code` 和 `message` 的 JSON。单个上传文件上限是 20MB，单次请求上限是 25MB。输入 CSV 和生成的 Excel 只存在于每个请求的独立系统临时目录中，响应内容读取完成后即清理，不在服务端长期保存。

日志同时显示在启动窗口并写入 `logs/dataFrame-prase.log`。单个日志文件最大 10MB，最多保留 5 个历史文件，日志不记录 CSV 原始内容。

## 当前边界

静态 HTML、配置下拉框、浏览器 `localStorage` 和启动后自动打开页面属于第二阶段，本阶段不实现。
