# Runtime Java SDK（运行时客户端）

## 依赖导入

Maven 仓库：该包未发布到官方 Maven 仓库，你需要手动下载项目并打包成 JAR 文件使用。

---

## 包结构

```
runtime/
├── RuntimeClient.java            # 统一入口：create / newFromK8s
├── RuntimeConfig.java            # Builder 模式配置：Domain / Scheme / RuntimeToken / ...
├── K8sHelper.java                # 从 Sandbox CR annotation 提取 runtimeToken
├── EnvdMethods.java              # envd gRPC 服务名和方法名常量
├── commands/                     # 命令执行
│   ├── Commands.java             # Run / Start / Kill / SendStdin / List / ConnectToProcess
│   ├── CommandHandle.java        # 后台进程句柄：waitForCompletion / kill
│   └── CommandResult.java        # 命令执行结果：stdout / stderr / exitCode
├── filesystem/                   # 文件系统
│   ├── Filesystem.java           # ListDir / Read / Write / MakeDir / Remove / Watch
│   ├── WatchHandle.java          # 目录监听句柄
│   └── WatchDirResponseObserver.java
├── utils/                        # 工具类
│   └── ConnectStreamReader.java  # Connect Protocol 流式响应解析
├── exceptions/                   # 异常
│   └── SandboxException.java
└── envd/                         # protobuf 生成代码
    ├── process/                  # envd Process gRPC
    └── filesystem/               # envd Filesystem gRPC
```

---

## 快速开始

在集群内或有 kubeconfig 权限时，使用 `newFromK8s` 自动从 Sandbox CR 解析 `sandboxID` 和 `runtimeToken`：

```java
import io.openkruise.agents.client.runtime.*;
import io.openkruise.agents.client.runtime.commands.CommandResult;

public class QuickStart {
    public static void main(String[] args) throws Exception {
        // domain 是 sandbox gateway 的地址
        // 集群内访问：K8s Service DNS，如 "sandbox-gateway.sandbox-system.svc:7788"
        // 本地开发：port-forward 地址，如 "127.0.0.1:7788"
        RuntimeConfig config = new RuntimeConfig.Builder()
            .domain("sandbox-gateway.sandbox-system.svc:7788")
            .scheme("http")
            .build();

        try (RuntimeClient client = RuntimeClient.newFromK8s("default", "your-sandbox-name", config)) {
            System.out.println("Runtime URL: " + client.getRuntimeURL());

            CommandResult res = client.Commands.run("uname -a");
            System.out.println(res.getStdout());
        }
    }
}
```

**关键说明：**

- `newFromK8s` 查询 Sandbox CR 并从 annotation `agents.kruise.io/runtime-access-token` 提取 `runtimeToken`
- `sandboxID` 格式为 `namespace--name`（双横线连接）
- kubeconfig 解析顺序：`KUBECONFIG` 环境变量 → `~/.kube/config` → in-cluster config

完整演示：[K8sDirectConnectExample.java](../examples/runtime/K8sDirectConnectExample.java)

---

## 连接配置（RuntimeConfig）

运行时客户端**不涉及 Protocol**，只需 `Scheme` + `Domain` 即可确定 envd 地址（`<scheme>://<domain>`）。

### Builder 方法

通过 `new RuntimeConfig.Builder().xxx().build()` 构建：

| 方法                                     | 说明                                             |
|----------------------------------------|------------------------------------------------|
| `.domain(String)`                      | envd 域名，默认 `domain.app`                        |
| `.scheme(String)`                      | URL scheme，默认 `http`                           |
| `.runtimeToken(String)`                | 运行时 Token，写入请求头 `X-Access-Token`               |
| `.runtimeUrl(String)`                  | **最高优先级**：直接覆盖 URL 拼装，`getSandboxURL()` 直接返回此值 |
| `.apiKey(String)`                      | API Key，写入请求头 `X-API-Key`                      |
| `.authHeader(String)`                  | 覆盖默认的 Authorization 头                          |
| `.headers(Map<String, String>)`        | 合并多个自定义 headers                                |
| `.addHeader(String key, String value)` | 添加单个自定义 header                                 |
| `.requestTimeoutMs(long)`              | HTTP 超时（毫秒），默认 60000                           |

### 优先级

`runtimeUrl`（显式覆盖） > `scheme` + `domain` 拼装 > 默认值

---

## 命令执行（Commands）

通过 `client.Commands` 操作容器内进程。底层走 envd 的 `Process` gRPC 服务，命令统一通过 `/bin/bash -l -c <cmd>` 执行。

### 方法

| 方法                                      | 说明                                    |
|-----------------------------------------|---------------------------------------|
| `run(String cmd)`                       | **前台执行**：启动命令并等待完成，返回 `CommandResult` |
| `run(String cmd, RunOptions options)`   | 前台执行（带选项）                             |
| `start(String cmd, RunOptions options)` | **后台启动**：返回 `CommandHandle`，调用方决定何时等待 |
| `list()`                                | 列出所有运行中的进程，返回 `List<ProcessInfo>`     |
| `kill(int pid)`                         | 向指定 PID 发送 SIGKILL                    |
| `sendStdin(int pid, String data)`       | 向指定进程的 stdin 写入数据                     |
| `connectToProcess(int pid)`             | 重新连接到已运行的进程，订阅其后续输出                   |

### RunOptions

```java
RunOptions opts = new RunOptions()
    .envs(Map.of("LANG", "C"))                       // 环境变量
    .cwd("/tmp")                                      // 工作目录
    .onStdout(line -> System.out.print(line))         // 流式 stdout 回调
    .onStderr(line -> System.err.print(line));        // 流式 stderr 回调
```

### CommandHandle

由 `start` / `connectToProcess` 返回，用于交互或等待：

| 方法                    | 说明                        |
|-----------------------|---------------------------|
| `getPid()`            | 返回进程 PID                  |
| `isCompleted()`       | 是否已完成                     |
| `waitForCompletion()` | 阻塞等待结束，返回 `CommandResult` |
| `kill()`              | 终止进程                      |
| `close()`             | 关闭（实现 `AutoCloseable`）    |

### CommandResult

| 字段         | 类型       | 说明   |
|------------|----------|------|
| `stdout`   | `String` | 标准输出 |
| `stderr`   | `String` | 标准错误 |
| `exitCode` | `int`    | 退出码  |

### 示例

```java
// 前台执行
CommandResult res = client.Commands.run("pwd");
System.out.println(res.getStdout());

// 带选项执行 + 流式输出
CommandResult res2 = client.Commands.run("ls -la /tmp",
    new RunOptions().cwd("/tmp").onStdout(System.out::print));

// 后台启动 + 主动 Kill
CommandHandle handle = client.Commands.start("sleep 60", new RunOptions());
System.out.println("pid = " + handle.getPid());
handle.kill();
handle.close();

// 列出进程
List<ProcessInfo> procs = client.Commands.list();
for (ProcessInfo p : procs) {
    System.out.printf("PID: %d, Cmd: %s%n", p.getPid(), p.getCmd());
}
```

---

## 文件系统（Filesystem）

通过 `client.Files` 操作容器内文件。元数据操作走 envd Filesystem gRPC，文件内容读写走 HTTP `/files` 端点。

### 方法

| 方法                                                                            | 说明                       |
|-------------------------------------------------------------------------------|--------------------------|
| `listDir(String path)`                                                        | 列出目录条目（depth=1）          |
| `listDir(String path, int depth)`                                             | 列出目录条目（指定深度）             |
| `exists(String path)`                                                         | 路径是否存在                   |
| `getInfo(String path)`                                                        | 获取文件/目录信息，返回 `EntryInfo` |
| `makeDir(String path)`                                                        | 递归创建目录；已存在返回 `false`     |
| `remove(String path)`                                                         | 删除文件或目录                  |
| `read(String path)`                                                           | 读取文件内容（`byte[]`）         |
| `readText(String path)`                                                       | 读取文件内容（`String`，UTF-8）   |
| `write(String path, byte[] data)`                                             | 写入文件（二进制），自动创建父目录        |
| `writeText(String path, String content)`                                      | 写入文件（文本，UTF-8）           |
| `watchDir(String path, Consumer<FilesystemEvent> onEvent)`                    | 监听目录变更事件                 |
| `watchDir(String path, boolean recursive, Consumer<FilesystemEvent> onEvent)` | 监听目录变更事件（支持递归）           |

### 示例

```java
// 目录操作
client.Files.makeDir("/tmp/work");
List<EntryInfo> entries = client.Files.listDir("/tmp");
client.Files.remove("/tmp/work");

// 文件读写
client.Files.writeText("/tmp/hello.txt", "Hello, World!");
String content = client.Files.readText("/tmp/hello.txt");
System.out.println(content); // Hello, World!

// 二进制读写
client.Files.write("/tmp/data.bin", new byte[]{0x01, 0x02, 0x03});
byte[] data = client.Files.read("/tmp/data.bin");

// 目录监听
WatchHandle wh = client.Files.watchDir("/tmp", true, event ->
    System.out.printf("Event: %s %s%n", event.getType(), event.getPath()));
wh.close();
```
