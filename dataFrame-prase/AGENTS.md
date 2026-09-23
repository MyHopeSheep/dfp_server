# dataFrame-prase Agent Guide

## 项目概览

- 项目是基于 Java 17 和 Spring Boot 3.5.9 的本地 Web 数据帧解析工具。
- 服务仅监听 `127.0.0.1`。
- 主要流程：上传 KingstVIS 导出的 CSV，读取外部解析配置，完成信号分段、周期估算、协议帧解析，并返回 Excel 文件。
- 外部运行配置位于 `config/parser-config.json`，配置文件使用 UTF-8 编码。
- 前端静态资源位于 `src/main/resources/static`。

## 目录职责

- `src/main/java/com/dataframe/prase/bootstrap`：应用启动增强与端口回退。
- `src/main/java/com/dataframe/prase/controller`：HTTP 接口和异常处理。
- `src/main/java/com/dataframe/prase/service`：应用服务、配置服务和上传解析编排。
- `src/main/java/com/dataframe/prase/domain`：领域模型、协议算法、信号算法和 DTO/VO。
- `src/main/java/com/dataframe/prase/infrastructure`：配置文件、CSV 和 Excel 适配器。
- `src/main/resources`：Spring 配置、日志配置和静态页面。
- `src/test/java`：单元测试、Web 接口测试、架构测试和解析回归测试。
- `docs`：历史方案、验收记录和变更说明，仅在需要追溯设计时参考。

## 常用接口

- `GET /dfp/health`：健康检查。
- `GET /dfp/config/remoteProfiles`：读取遥控器摘要。
- `GET /dfp/config/parserSettings`：读取完整解析配置。
- `POST /dfp/csvParse`：上传 CSV 并下载解析结果 Excel。

## 开发约束

1. 使用 Java 17；PowerShell 中查看文本时显式使用 UTF-8。
2. 保持现有分层结构和 HTTP、JSON、配置文件契约，不要为了复用引入不必要的抽象。
3. 配置读取必须使用 `config/parser-config.json`，不要回退到旧配置、classpath 配置或其他备用路径。
4. 单次解析使用同一个不可变配置快照，避免解析、审计和 Excel 输出之间出现配置漂移。
5. 不删除数据库数据或项目文件；涉及索引、外部系统写入或其他不可逆操作时先征得用户同意。
6. 保留工作区中与当前任务无关的已有修改，不使用破坏性 Git 命令覆盖它们。
7. 未经用户批准不执行 Git commit、push 或发布操作。
8. 修改完成后说明改动内容和验证结果；当前项目规则要求不主动编译或运行测试，除非用户明确要求。

## 构建与运行参考

项目要求 JDK 17。运行时工作目录必须是项目根目录：

```powershell
$env:JAVA_HOME = 'D:\software\jdK\jdk-17.0.15'
$env:Path = "$env:JAVA_HOME\bin;$env:Path"
java -jar target\dataFrame-prase.jar
```

应用默认依次尝试端口 `8080`、`8081`、`8082`。日志输出到启动窗口和 `logs/dataFrame-prase.log`。

## Git 工作区注意事项

开始修改前先查看：

```powershell
git status --short --branch
```

完成修改后只检查相关 diff：

```powershell
git diff -- AGENTS.md <相关文件>
```
