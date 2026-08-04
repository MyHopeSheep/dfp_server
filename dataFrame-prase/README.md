# dataFrame-prase

`dataFrame-prase` 是一个 Java 17 命令行程序，用于从 KingstVIS 导出的边沿 CSV 中恢复固定周期 bit 流，识别自定义协议核心帧，并生成 Excel 解析结果。

## 环境要求

- JDK 17
- Maven 3.8 或更高版本
- 输入 CSV 默认使用 GBK；其他编码通过 `--charset` 指定

当前电脑可在 PowerShell 中使用以下 JDK 17：

```powershell
$env:JAVA_HOME = 'D:\software\jdK\jdk-17.0.15'
$env:Path = "$env:JAVA_HOME\bin;$env:Path"
java -version
mvn -version
```

两个版本命令都应显示 Java 17，不能使用系统默认的 Java 8 构建本工程。

## 构建和测试

在 `dataFrame-prase` 目录执行：

```powershell
mvn clean test
mvn clean package
```

只执行指定测试类时，带点号或其他 `-D` 参数统一加引号：

```powershell
mvn "-Dtest=CliOptionsTest" test
```

成功打包后生成：

```text
target\dataFrame-prase.jar
```

## 命令行使用

查看帮助：

```powershell
& "$env:JAVA_HOME\bin\java.exe" -jar '.\target\dataFrame-prase.jar' --help
```

处理一份样例 CSV：

未指定 `--output` 时，结果写入 CSV 所在目录下的 `data_praseResult`；目录不存在会自动创建，同名文件会追加 `(1)`、`(2)` 编号。

```powershell
& "$env:JAVA_HOME\bin\java.exe" -jar '.\target\dataFrame-prase.jar' `
  --input 'D:\IdeaProgram\DFP\2-data\2026-08-02_01-26-27通道1停按第1下3帧数据.csv' `
  --remote-id 'B0 42 87'
```

指定全部主要参数：

```powershell
& "$env:JAVA_HOME\bin\java.exe" -jar '.\target\dataFrame-prase.jar' `
  --input 'D:\IdeaProgram\DFP\2-data\2026-08-02_01-26-27通道1停按第1下3帧数据.csv' `
  --remote-id 'B0 42 87' `
  --output 'D:\IdeaProgram\DFP\2-data\data_praseResult\第1下-解析结果.xlsx' `
  --charset 'GBK' `
  --bit-period-us '416.67' `
  --phase-step-us '26.041875' `
  --idle-threshold-ms '40' `
  --idle-level '0' `
  --dedup-tolerance-us '208.335' `
  --level-mapping 'inverted'
```

## 人工验收

1. 控制台应显示输入路径、边沿数、候选区段数、有效帧数、非有效帧数、边界残片及实际参数。
2. 输出文件应包含且只包含 `解析结果`、`处理摘要` 两个工作表。
3. `解析结果` 应冻结前两行，数据从第 3 行开始。
4. A:K 应为 `未采集`，L 应为同步字节 `0xAA`，M:Y 应为其余核心帧字段。
5. AB:AQ 应包含帧序号、区段序号、时间、判断结果、失败原因、采样参数、bit 串和字节串。
6. 非有效帧必须有失败原因；文件边界残片只计入摘要，不计入非有效帧数量。
7. `电平映射` 应显示反相映射；现有三份样例使用 `CSV 0 -> 逻辑 1、CSV 1 -> 逻辑 0`。
8. 三份样例在默认空闲配置下的静态候选区段数应分别为 3、3、6；有效帧数量必须以实际恢复和校验结果为准，不能根据文件名断言。

完整业务规则以项目上一级目录中的 `自定义协议CSV有效帧识别器-MVP需求文档.md` 和 `有效帧识别规则说明.md` 为准。

逐项实现状态和仍需运行的验收项见 `docs/acceptance-audit.md`。
