# E2B Java SDK（e2b 管理客户端）

## 依赖导入
Maven 仓库：该包未发布到官方 Maven 仓库，你需要手动下载项目并打包成 JAR 文件使用。

---

## 快速开始

```java
import io.openkruise.agents.client.e2b.*;

ConnectionConfig config = new ConnectionConfig.Builder()
    .apiKey("your-api-key")
    .domain("your.domain.com")
    .build();

SandboxApi api = new SandboxApi(config);

// 创建 → 使用 → 自动销毁
try (Sandbox sandbox = api.create("code-interpreter")) {
    sandbox.Commands.run("echo hello");
    sandbox.Files.writeText("/tmp/demo.txt", "Hello!");
}
```

完整演示：[生命周期管理完整示例](../examples/e2b/SandboxApiManagerExample.java) | [命令操作完整示例](../examples/e2b/SandboxCommandsExample.java) | [文件操作完整示例](../examples/e2b/SandboxFilesExample.java)

---

## 一、Sandbox 生命周期管理（SandboxApi）

`SandboxApi` 提供 Sandbox 的完整生命周期管理能力，包括创建、连接、查询、暂停、恢复和终止。

### 初始化

```java
ConnectionConfig config = new ConnectionConfig.Builder()
    .apiKey("your-api-key")
    .domain("your.domain.com")
    .build();

SandboxApi api = new SandboxApi(config);
```

### API 方法一览

| 方法                                                                                 | 说明                                              |
|------------------------------------------------------------------------------------|-------------------------------------------------|
| `create(String template)`                                                          | 从模板创建 sandbox                                   |
| `create(NewSandbox body)`                                                          | 创建 sandbox（全部参数）                                |
| `connect(String sandboxID)`                                                        | 连接已有 sandbox                                    |
| `connect(String sandboxID, int timeout)`                                           | 连接已有 sandbox（指定超时）                              |
| `list()`                                                                           | 列出所有运行中的 sandbox                                |
| `list(String metadata)`                                                            | 按 metadata 过滤列出                                 |
| `list(String metadata, List<SandboxState> state, String nextToken, Integer limit)` | 列出 sandbox（支持分页和状态过滤）                           |
| `getInfo(String sandboxID)`                                                        | 获取 sandbox 详情，404 抛出 `SandboxNotFoundException` |
| `kill(String sandboxID)`                                                           | 终止 sandbox；返回 `true` 成功，`false` 未找到             |
| `pause(String sandboxID)`                                                          | 暂停 sandbox                                      |
| `setTimeout(String sandboxID, int timeout)`                                        | 修改超时时间（秒）                                       |

### 生命周期示例

```java
SandboxApi api = new SandboxApi(config);

// ---- 创建 ----
Sandbox sandbox = api.create("code-interpreter");
String id = sandbox.getSandboxID();

// 也可传入完整参数
Sandbox sandbox2 = api.create(new NewSandbox()
    .templateID("code-interpreter")
    .timeout(600)
    .envVars(Map.of("FOO", "1")));

// ---- 查询 ----
SandboxInfo info = api.getInfo(id);
List<SandboxInfo> all = api.list();

// ---- 连接已有 sandbox ----
Sandbox reconnected = api.connect(id);

// ---- 暂停 / 修改超时 ----
api.pause(id);
api.setTimeout(id, 600);

// ---- 终止 ----
api.kill(id);
```

### Sandbox 实例

`SandboxApi.create()` / `connect()` 返回 `Sandbox` 实例，它是数据面操作的入口：

| 方法                   | 说明                                     |
|----------------------|----------------------------------------|
| `getSandboxID()`     | 返回 sandbox ID                          |
| `getSandboxURL()`    | 返回 sandbox envd URL                    |
| `getConfig()`        | 返回连接配置                                 |
| `getRuntimeClient()` | 返回底层 `RuntimeClient`                   |
| `close()`            | 关闭连接并终止 sandbox（支持 try-with-resources） |

`Sandbox` 暴露两个操作模块：

- **`sandbox.Commands`** — 命令执行
- **`sandbox.Files`** — 文件系统

---

## 二、命令执行（Commands）

通过 `sandbox.Commands` 操作容器内进程。底层走 envd 的 `Process` gRPC 服务，命令统一通过 `/bin/bash -l -c <cmd>` 执行。

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

### CommandResult

| 字段         | 类型       | 说明   |
|------------|----------|------|
| `stdout`   | `String` | 标准输出 |
| `stderr`   | `String` | 标准错误 |
| `exitCode` | `int`    | 退出码  |

### CommandHandle

由 `start` / `connectToProcess` 返回：

| 方法                    | 说明                        |
|-----------------------|---------------------------|
| `getPid()`            | 返回进程 PID                  |
| `isCompleted()`       | 是否已完成                     |
| `waitForCompletion()` | 阻塞等待结束，返回 `CommandResult` |
| `kill()`              | 终止进程                      |
| `close()`             | 关闭（实现 `AutoCloseable`）    |

### 示例

```java
// 前台执行
CommandResult res = sandbox.Commands.run("pwd");
System.out.println(res.getStdout());

// 带选项执行 + 流式输出
CommandResult res2 = sandbox.Commands.run("ls -la /tmp",
    new RunOptions().cwd("/tmp").onStdout(System.out::print));

// 后台启动 + 主动 Kill
CommandHandle handle = sandbox.Commands.start("sleep 60", new RunOptions());
System.out.println("pid = " + handle.getPid());
handle.kill();
handle.close();

// 列出进程
List<ProcessInfo> procs = sandbox.Commands.list();
for (ProcessInfo p : procs) {
    System.out.printf("PID: %d, Cmd: %s%n", p.getPid(), p.getCmd());
}
```

---

## 三、文件系统（Filesystem）

通过 `sandbox.Files` 操作容器内文件。元数据操作走 envd Filesystem gRPC，文件内容读写走 HTTP `/files` 端点。

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
| `read(String path, String user)`                                              | 读取文件内容（指定用户）             |
| `readText(String path)`                                                       | 读取文件内容（`String`，UTF-8）   |
| `writeText(String path, String content)`                                      | 写入文件（文本，UTF-8）           |
| `write(String path, byte[] data)`                                             | 写入文件（二进制），自动创建父目录        |
| `watchDir(String path, Consumer<FilesystemEvent> onEvent)`                    | 监听目录变更事件                 |
| `watchDir(String path, boolean recursive, Consumer<FilesystemEvent> onEvent)` | 监听目录变更事件（支持递归）           |

### 示例

```java
// 目录操作
sandbox.Files.makeDir("/tmp/work");
List<EntryInfo> entries = sandbox.Files.listDir("/tmp");
sandbox.Files.remove("/tmp/work");

// 文件读写
sandbox.Files.writeText("/tmp/hello.txt", "Hello, World!");
String content = sandbox.Files.readText("/tmp/hello.txt");

// 二进制读写
sandbox.Files.write("/tmp/data.bin", new byte[]{0x01, 0x02});
byte[] data = sandbox.Files.read("/tmp/data.bin");

// 目录监听
WatchHandle wh = sandbox.Files.watchDir("/tmp", true, event ->
    System.out.printf("Event: %s %s%n", event.getType(), event.getPath()));
wh.close();
```

---

## 四、连接配置（ConnectionConfig）

### Scheme 与 Protocol

连接行为由 **Scheme** 和 **Protocol** 两个正交维度决定 URL 形态。

#### Protocol（路由协议）

| 值              | 枚举值                | API URL                          | Sandbox URL                                     |
|----------------|--------------------|----------------------------------|-------------------------------------------------|
| **Native（默认）** | `Protocol.NATIVE`  | `https://api.<domain>`           | `https://<port>-<sandboxID>.<domain>`           |
| **Private**    | `Protocol.PRIVATE` | `<scheme>://<domain>/kruise/api` | `<scheme>://<domain>/kruise/<sandboxID>/<port>` |

- **Native**：基于子域名路由，对应原生公网部署
- **Private**：基于路径前缀通过统一网关路由，适用于私有化或端口转发场景

#### Builder 方法

| 方法                                     | 说明                                   |
|----------------------------------------|--------------------------------------|
| `.apiKey(String)`                      | API Key，写入请求头 `X-API-Key`            |
| `.accessToken(String)`                 | Access Token，写入请求头 `X-Access-Token`  |
| `.domain(String)`                      | 域名，默认 `your.domain.com`              |
| `.scheme(String)`                      | URL scheme，默认 `https`                |
| `.protocol(Protocol)`                  | 路由协议，默认 `Protocol.NATIVE`            |
| `.apiURL(String)`                      | **最高优先级**：直接覆盖 API base URL          |
| `.sandboxBaseURL(String)`              | **最高优先级**：直接覆盖 sandbox envd base URL |
| `.requestTimeoutMs(long)`              | HTTP 请求超时（毫秒），默认 60000               |
| `.port(int)`                           | envd 端口，默认 49983                     |
| `.debug(boolean)`                      | 调试模式，kill/setTimeout 跳过实际调用          |
| `.headers(Map<String, String>)`        | 自定义请求头（同时应用于管理面和 runtime）            |
| `.addHeader(String key, String value)` | 添加单个自定义请求头                           |

#### 优先级

`apiURL` / `sandboxBaseURL`（显式覆盖） > `protocol` + `domain` 拼装 > 环境变量（`X_API_KEY`、`SCHEME`、`PROTOCOL`） > 默认值
