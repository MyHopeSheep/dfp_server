# dataFrame-prase

`dataFrame-prase` 是一个 Java 17 本地 Web 工具。用户在浏览器中选择遥控器配置并上传 KingstVIS 导出的稀疏边沿 CSV，后端按统一协议配置完成解析并下载 Excel 结果。

当前页面包含“解析任务”“遥控器参数”“协议算法规则”三个页签。配置页只读，不提供保存、新增或删除能力。

## 构建和测试

必须使用 JDK 17。在 PowerShell 中从本模块根目录执行：

```powershell
$env:JAVA_HOME = 'D:\software\jdK\jdk-17.0.15'
$env:Path = "$env:JAVA_HOME\bin;$env:Path"
New-Item -ItemType Directory -Force -Path 'target\junit'
mvn "-DargLine=-Djava.io.tmpdir=D:\IdeaProgram\DFP\3-server\dataFrame-prase\target\junit -Dfile.encoding=UTF-8" test
mvn "-DskipTests" clean package
```

可执行 JAR 生成在 `target\dataFrame-prase.jar`。

## 配置文件

运行时唯一配置源为：

```text
config/parser-config.json
```

文件使用 UTF-8，顶层包含：

```text
schemaVersion
remoteConfigs
protocolRuleSets
```

`remoteConfigs[*].protocolRuleKey` 关联对应的协议规则集。配置包含遥控器 ID、CSV 编码、空闲分段、电平映射、8 个时序参数、固定帧声明和命令映射。后端拒绝未知字段、重复 key、无效关联、无效 Hex、非法数值和被修改的固定帧结构。

开发运行时，Java 进程工作目录必须是本模块根目录：

```text
D:\IdeaProgram\DFP\3-server\dataFrame-prase
```

解压交付运行时，Java 进程工作目录必须是解压根目录，实际配置位于 `<解压根目录>\config\parser-config.json`。交付脚本应在启动 Java 前执行：

```bat
pushd "%~dp0"
```

代码不会回退读取旧配置文件、classpath 配置或其他备用路径。

## 配置一致性

两个配置 GET 接口是独立请求，每次各自重新读取并完整校验配置，因此跨请求采用弱一致。前端刷新时并行请求两个接口，只有都成功才替换当前配置状态；失败时保留已选 CSV 和上次成功数据，并禁用解析。

一次 CSV 解析只在请求开始时读取一次配置。解析、审计、结果组装和 Excel 输出共用同一个不可变配置快照，保证单次解析强一致。

## 后端接口

### 健康检查

```text
GET /dfp/health
```

返回 `status`、实际监听 `port` 和程序 `version`。

### 遥控器摘要

```text
GET /dfp/config/remoteProfiles
```

成功响应是裸数组，包含解析页使用的遥控器参数和关联规则名称。

### 完整解析配置

```text
GET /dfp/config/parserSettings
```

成功响应是裸对象，包含统一配置和后端计算的派生参数。接口只读，不提供对应的 `PUT`、新增或删除接口。

### CSV 解析与 Excel 下载

```text
POST /dfp/csvParse
Content-Type: multipart/form-data
file=<CSV 文件>
configKey=<遥控器配置 key>
```

成功响应是标准 XLSX 下载，文件名为 `原CSV文件名-解析结果.xlsx`。失败响应为 `{code,message}` JSON。单个文件上限 20MB，单次请求上限 25MB；请求临时文件在响应内容读取完成后清理。

## 运行边界

服务仅监听 `127.0.0.1`。日志输出到启动窗口和 `logs/dataFrame-prase.log`，不记录 CSV 原始内容。当前版本不提供配置写入、登录鉴权、配置历史、日志监控或多人协作能力。
