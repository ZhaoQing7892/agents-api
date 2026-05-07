# E2B Sandbox Go SDK

本包提供两种使用方式操作 E2B Sandbox 内的环境：

| 包               | 导入路径                                               | 定位                                                                                                                  |
|-----------------|----------------------------------------------------|---------------------------------------------------------------------------------------------------------------------|
| **sandbox**     | `github.com/openkruise/agents-api/e2b/sandbox`     | **管理客户端（Management Client）**：Sandbox 生命周期管理（Create / Connect / Pause / Kill）+ 容器内操作（Commands / Files），走 Protocol 路由 |
| **envd/client** | `github.com/openkruise/agents-api/e2b/envd/client` | **运行时客户端（Runtime Client）**：直接操作运行中的沙箱容器内的 envd 服务，进行命令执行和文件操作，不涉及生命周期管理                                             |

> 两者共享同一套 Commands / Filesystem 底层实现，区别仅在**是否经过 Protocol 路由和管理 API**。

---

## 包结构

```
e2b/
├── sandbox/                          # 管理客户端（package sandbox）
│   ├── sandbox.go                    #   Sandbox 结构体：Create / Connect / Pause / Kill
│   ├── sandbox_api.go                #   底层 REST 客户端（SandboxApi）：List / GetInfo / Kill / ...
│   └── config.go                     #   ConnectionConfig：Protocol / Scheme / Domain / API URL
│
├── envd/                             # envd 运行时层
│   ├── client/                       #   运行时客户端（package client）
│   │   ├── client.go                 #     Client 结构体：New / NewWithConfig
│   │   ├── config.go                 #     Config 与 Options：Domain / Scheme / RuntimeToken / ...
│   │   ├── commands.go               #     Commands：Run / Start / Kill / SendStdin / List / ConnectToProcess
│   │   ├── command_handle.go         #     CommandHandle：Wait / Disconnect / Kill
│   │   └── filesystem.go             #     Filesystem：List / Exists / GetInfo / MakeDir / Rename / Remove
│   ├── process/                      #   protobuf 生成代码（envd Process gRPC）
│   │   ├── process.pb.go
│   │   └── processconnect/
│   └── filesystem/                   #   protobuf 生成代码（envd Filesystem gRPC）
│       ├── filesystem.pb.go
│       └── filesystemconnect/
│
├── client/                           # 自动生成的 E2B REST API 客户端（OpenAPI）
│
├── example/
│   ├── sandbox/main.go               # 管理客户端示例
│   └── envd_client/main.go           # 运行时客户端示例
│
├── README.md
└── README_zh-CH.md
```

### 核心关系

```
┌──────────────────────────────────────────────────────┐
│                    sandbox.Sandbox                    │
│  （Create / Connect / Pause / Kill + Protocol 路由）  │
│                                                      │
│   嵌入 ──► envd/client.Client                        │
│            （Commands + Files + Config）              │
│                                                      │
│   使用 ──► client.*（E2B REST API）                   │
└──────────────────────────────────────────────────────┘
                        │
                        ▼
┌──────────────────────────────────────────────────────┐
│                 envd/client.Client                    │
│         （运行时客户端，不涉及生命周期管理）              │
│                                                      │
│   Commands.Rpc ──► processconnect.ProcessClient       │
│   Files.Rpc    ──► filesystemconnect.FilesystemClient │
└──────────────────────────────────────────────────────┘
```

- **`sandbox`**（管理客户端）嵌入了 `envd/client.Client`，在其基础上增加了生命周期管理和 Protocol 路由
- **`envd/client`**（运行时客户端）可独立使用 —— 只需提供 sandbox ID 和 token + 域名即可直接操作运行中的沙箱
- **`Commands.Rpc`** / **`Files.Rpc`** 已导出，用户可直接调用高层 API 未覆盖的原生 gRPC 方法

---

## 快速开始：sandbox（管理客户端）

```go
package main

import (
	"context"
	"fmt"
	"log"

	"github.com/openkruise/agents-api/e2b/sandbox"
)

func main() {
	ctx := context.Background()

	sb, err := sandbox.Create(ctx, "code-interpreter",
		sandbox.WithConfig(
			sandbox.WithAPIKey("your-api-key"),
			sandbox.WithDomain("your.domain.com"),
		),
	)
	if err != nil {
		log.Fatal(err)
	}
	defer sb.Close(ctx)

	res, _ := sb.Commands.Run(ctx, "echo hello")
	fmt.Println(res.Stdout)

	sb.Files.MakeDir(ctx, "/tmp/demo")
}
```

[完整示例](https://github.com/openkruise/agents-api/blob/master/e2b/example/sandbox/main.go)

---

## 快速开始：envd/client（运行时客户端）

当你已经知道 sandbox 正在运行，不需要通过管理 API 创建/销毁，只想直接操作容器内环境时：

```go
package main

import (
	"context"
	"fmt"

	"github.com/openkruise/agents-api/e2b/envd/client"
)

func main() {
	ctx := context.Background()

	c := client.New("your-sandbox-id",
		client.WithDomain("your.domain.com"),
		client.WithScheme("http"),
		client.WithRuntimeToken("your-runtime-token"),
	)

	res, _ := c.Commands.Run(ctx, "uname -a")
	fmt.Println(res.Stdout)

	c.Files.MakeDir(ctx, "/tmp/demo")
}
```

[完整示例](https://github.com/openkruise/agents-api/blob/master/e2b/example/envd_client/main.go)

---

## 连接配置

### sandbox 包：Scheme 与 Protocol

sandbox 包的连接行为通过 `ConnectionConfig` 控制，由 **Scheme** 和 **Protocol** 两个正交维度决定 URL 形态。

#### Protocol（路由协议）

| 值              | 常量                        | API URL                          | Sandbox URL                                     |
|----------------|---------------------------|----------------------------------|-------------------------------------------------|
| **Native（默认）** | `sandbox.ProtocolNative`  | `https://api.<domain>`           | `https://<port>-<sandboxID>.<domain>`           |
| **Private**    | `sandbox.ProtocolPrivate` | `<scheme>://<domain>/kruise/api` | `<scheme>://<domain>/kruise/<sandboxID>/<port>` |

- **Native**：基于子域名路由，对应 E2B 原生公网部署
- **Private**：基于路径前缀（`/kruise/...`）通过统一网关路由，适用于私有化或本地端口转发场景

#### Scheme（协议头）

| 值                 | 适用场景              |
|-------------------|-------------------|
| **`"https"`（默认）** | 生产环境 / 公网         |
| **`"http"`**      | 本地端口转发、内网无 TLS 调试 |

#### 4 种典型组合

| Protocol | Scheme | 配置示例                                                   | 用途                |
|----------|--------|--------------------------------------------------------|-------------------|
| Native   | https  | （全部使用默认）                                               | 公网 SaaS / 标准部署    |
| Private  | https  | `WithProtocol(ProtocolPrivate)`                        | 私有化网关代理           |
| Private  | http   | `WithProtocol(ProtocolPrivate)` + `WithScheme("http")` | 本地端口转发调试          |
| Native   | http   | `WithScheme("http")`                                   | 不带 TLS 的子域名部署（少见） |

#### ConnectionConfigOption 列表

通过 `sandbox.NewConnectionConfig(opts...)` 或在 `Create/Connect` 中嵌入 `WithConfig(...)` 应用：

| 选项                                    | 说明                                                |
|---------------------------------------|---------------------------------------------------|
| `WithAPIKey(key string)`              | API Key，写入请求头 `X-API-Key`                         |
| `WithAccessToken(token string)`       | OAuth2 Access Token（与 APIKey 二选一）                 |
| `WithDomain(domain string)`           | 域名，默认 `e2b.app`                                   |
| `WithScheme(scheme string)`           | URL scheme，默认 `https`                             |
| `WithProtocol(p Protocol)`            | 路由协议，默认 `ProtocolNative`                          |
| `WithAPIURL(url string)`              | **最高优先级**：直接覆盖 API base URL，绕过 Protocol/Domain 拼装 |
| `WithSandboxBaseURL(url string)`      | **最高优先级**：直接覆盖 sandbox envd base URL              |
| `WithRequestTimeout(d time.Duration)` | HTTP 请求超时，默认 60s                                  |
| `WithDebug(debug bool)`               | 调试模式：跳过部分 API 调用                                  |

#### 环境变量回退

| 环境变量               | 对应字段                           |
|--------------------|--------------------------------|
| `E2B_API_KEY`      | APIKey                         |
| `E2B_ACCESS_TOKEN` | AccessToken                    |
| `E2B_DOMAIN`       | Domain                         |
| `E2B_SCHEME`       | Scheme                         |
| `E2B_PROTOCOL`     | Protocol（`native` / `private`） |
| `E2B_API_URL`      | APIURL                         |

#### 优先级

`WithAPIURL` / `WithSandboxBaseURL`（显式覆盖） > `WithProtocol` + `WithDomain` 拼装 > 环境变量 > 默认值

---

### envd/client 包：运行时客户端配置

运行时客户端**不涉及 Protocol**，只需 `Scheme` + `Domain` 即可确定 envd 地址（`<scheme>://<domain>`）。

#### Option 列表

| 选项                                    | 说明                               |
|---------------------------------------|----------------------------------|
| `WithDomain(domain string)`           | envd 域名，默认 `e2b.app`             |
| `WithScheme(scheme string)`           | URL scheme，默认 `https`            |
| `WithRuntimeToken(token string)`      | 运行时 Token，写入请求头 `X-Access-Token` |
| `WithEnvdPort(port int)`              | envd 端口，默认 `49983`               |
| `WithAPIKey(apiKey string)`           | 可选 API Key（当 ingress 校验时使用）      |
| `WithAuthHeader(header string)`       | 覆盖默认的 Authorization 头            |
| `WithSandboxBaseURL(url string)`      | 完全覆盖 URL 拼装                      |
| `WithHeader(key, value string)`       | 添加单个自定义 header                   |
| `WithHeaders(headers map)`            | 合并多个自定义 headers                  |
| `WithRequestTimeout(d time.Duration)` | HTTP 超时，默认 60s                   |

---

## 创建 / 连接 Sandbox

### `Create(ctx, template, opts...) (*Sandbox, error)`

从一个模板创建新 sandbox。`template` 为空时默认 `"code-interpreter"`。

```go
sb, err := sandbox.Create(ctx, "code-interpreter",
sandbox.WithConfig(
sandbox.WithAPIKey("xxx"),
sandbox.WithDomain("example.com"),
sandbox.WithProtocol(sandbox.ProtocolPrivate),
),
sandbox.WithTimeout(600),
sandbox.WithMetadata(map[string]string{"k": "v"}),
sandbox.WithEnvVars(map[string]string{"FOO": "1"}),
sandbox.WithAutoPause(true),
sandbox.WithSecure(true),
)
```

### `Connect(ctx, sandboxID, opts...) (*Sandbox, error)`

连接到已存在（含暂停状态）的 sandbox，会自动唤醒。

```go
sb, err := sandbox.Connect(ctx, "default--xxx-xxx",
sandbox.WithConfig(sandbox.WithAPIKey("xxx"), sandbox.WithDomain("example.com")),
)
```

### SandboxOption 列表

| 选项                           | 说明                            |
|------------------------------|-------------------------------|
| `WithConfig(opts...)`        | 内嵌一组 `ConnectionConfigOption` |
| `WithTimeout(seconds int32)` | sandbox 存活超时，默认 300 秒         |
| `WithMetadata(map)`          | sandbox 元数据                   |
| `WithEnvVars(map)`           | 注入 sandbox 的环境变量              |
| `WithAutoPause(bool)`        | 是否启用自动暂停                      |
| `WithSecure(bool)`           | 启用安全模式                        |

### Sandbox 实例方法

| 方法                                     | 说明                       |
|----------------------------------------|--------------------------|
| `SandboxID() string`                   | 返回 sandbox ID            |
| `TemplateID() string`                  | 返回模板 ID                  |
| `GetInfo(ctx) (*SandboxInfo, error)`   | 获取 sandbox 详情            |
| `SetTimeout(ctx, timeout int32) error` | 修改超时时间                   |
| `Pause(ctx) (string, error)`           | 暂停                       |
| `Kill(ctx) (bool, error)`              | 销毁                       |
| `Close(ctx) error`                     | `Kill` 的别名，方便 `defer` 使用 |

`Sandbox` 暴露两个子模块：

- `sb.Commands` — 命令执行（`*Commands`）
- `sb.Files` — 文件系统（`*Filesystem`）

---

## Sandbox 管理 API（SandboxApi）

`SandboxApi` 是底层 REST 客户端，可在不创建具体 Sandbox 实例时单独使用（例如列出所有 sandbox）。

```go
api := sandbox.NewSandboxApi(sandbox.NewConnectionConfig(
sandbox.WithAPIKey("xxx"),
sandbox.WithDomain("example.com"),
))
```

| 方法                                                                           | 说明                                  |
|------------------------------------------------------------------------------|-------------------------------------|
| `List(ctx) ([]SandboxInfo, error)`                                           | 列出所有运行中的 sandbox                    |
| `GetInfo(ctx, sandboxID) (*SandboxInfo, error)`                              | 获取 sandbox 详情，404 返回 `not found` 错误 |
| `Kill(ctx, sandboxID) (bool, error)`                                         | 销毁 sandbox；`Debug` 模式下直接返回 `true`   |
| `SetTimeout(ctx, sandboxID, timeout int32) error`                            | 修改超时时间                              |
| `CreateSandbox(ctx, opts CreateSandboxOpts) (*SandboxCreateResponse, error)` | 底层创建接口                              |
| `ConnectSandbox(ctx, sandboxID, timeout int32) (*client.Sandbox, error)`     | 底层连接接口（自动唤醒暂停态）                     |
| `Pause(ctx, sandboxID) (string, error)`                                      | 暂停 sandbox                          |

### `SandboxInfo` 字段

```go
type SandboxInfo struct {
SandboxID   string
TemplateID  string
Alias       string
ClientID    string
StartedAt   time.Time
EndAt       time.Time
CpuCount    int32
MemoryMB    int32
DiskSizeMB  int32
EnvdVersion string
Metadata    map[string]string
State       string
}
```

---

## 命令执行（Commands）

通过 `sb.Commands`（sandbox 模式）或 `c.Commands`（直连模式）操作容器内进程。底层走 envd 的 `Process` gRPC 服务。

### 方法

| 方法                                                          | 说明                                           |
|-------------------------------------------------------------|----------------------------------------------|
| `Run(ctx, cmd, opts...) (*CommandResult, error)`            | **前台执行**：启动命令并等待完成，返回 stdout/stderr/exitCode |
| `Start(ctx, cmd, opts...) (*CommandHandle, error)`          | **后台启动**：返回 handle，调用方决定何时 `Wait`            |
| `List(ctx) ([]ProcessInfo, error)`                          | 列出所有运行中的进程                                   |
| `Kill(ctx, pid uint32) (bool, error)`                       | 向指定 PID 发送 SIGKILL；进程已不存在返回 `false, nil`     |
| `SendStdin(ctx, pid uint32, data string) error`             | 向指定进程的 stdin 写入数据                            |
| `ConnectToProcess(ctx, pid uint32) (*CommandHandle, error)` | 重新连接到一个已运行的进程，订阅其后续输出                        |

### `RunOpts` 字段

```go
type RunOpts struct {
Envs       map[string]string // 进程环境变量
Cwd        string            // 工作目录
Stdin      bool // 是否允许通过 SendStdin 写入
Background bool // 后台执行（保留字段）
OnStdout   func (string) // 流式 stdout 回调（前台执行）
OnStderr   func (string) // 流式 stderr 回调（前台执行）
}
```

> 命令统一通过 `/bin/bash -l -c <cmd>` 执行，保留登录环境。

### `CommandHandle`

由 `Start` / `ConnectToProcess` 返回，用于交互或等待：

| 方法                                                 | 说明                                    |
|----------------------------------------------------|---------------------------------------|
| `Pid() uint32`                                     | 返回进程 PID                              |
| `Wait(onStdout, onStderr) (*CommandResult, error)` | 阻塞等待结束；非 0 退出码会附带 `*CommandExitError` |
| `Disconnect()`                                     | 断开订阅但**不杀进程**                         |
| `Kill() bool`                                      | 杀掉进程                                  |

### `CommandResult` / `CommandExitError`

```go
type CommandResult struct {
Stdout   string
Stderr   string
ExitCode int32
Error    string
}

// 退出码非 0 时返回此错误（同时返回 *CommandResult）
type CommandExitError struct {
Stdout, Stderr string
ExitCode       int32
ErrorMessage   string
}
```

### 示例

```go
// 前台执行 + 流式输出
res, err := sb.Commands.Run(ctx, "ls -la /tmp", client.RunOpts{
Cwd:      "/tmp",
Envs:     map[string]string{"LANG": "C"},
OnStdout: func (line string) { fmt.Print(line) },
})

// 后台启动 + 主动 Kill
h, _ := sb.Commands.Start(ctx, "sleep 60")
fmt.Println("pid =", h.Pid())
h.Kill()
```

---

## 文件系统（Filesystem）

通过 `sb.Files`（sandbox 模式）或 `c.Files`（直连模式）操作容器内文件。仅封装官方 envd Filesystem gRPC 提供的能力。

### 方法

| 方法                                                  | 说明                             |
|-----------------------------------------------------|--------------------------------|
| `List(ctx, path, depth...) ([]EntryInfo, error)`    | 列出目录条目；`depth` 默认 1            |
| `Exists(ctx, path) (bool, error)`                   | 路径是否存在（基于 `Stat`，404 返回 false） |
| `GetInfo(ctx, path) (*EntryInfo, error)`            | 获取文件 / 目录信息                    |
| `MakeDir(ctx, path) (bool, error)`                  | 递归创建目录；已存在返回 `false, nil`      |
| `Rename(ctx, oldPath, newPath) (*EntryInfo, error)` | 重命名 / 移动                       |
| `Remove(ctx, path) error`                           | 删除文件或目录                        |

### `EntryInfo` 字段

```go
type EntryInfo struct {
Name          string
Type          EntryType // "file" / "directory" / "symlink"
Path          string
Size          int64
Mode          uint32
Permissions   string
Owner         string
Group         string
ModifiedTime  time.Time
SymlinkTarget *string
}
```

### 示例

```go
c.Files.MakeDir(ctx, "/tmp/work")

entries, _ := c.Files.List(ctx, "/tmp")
for _, e := range entries {
fmt.Printf("%s %s (%d bytes)\n", e.Type, e.Name, e.Size)
}

c.Files.Rename(ctx, "/tmp/work", "/tmp/done")
c.Files.Remove(ctx, "/tmp/done")
```

---

## 完整示例

### sandbox 模式（管理客户端）

```go
package main

import (
	"context"
	"fmt"
	"log"

	"github.com/openkruise/agents-api/e2b/sandbox"
)

func main() {
	ctx := context.Background()

	sb, err := sandbox.Create(ctx, "code-interpreter",
		sandbox.WithConfig(
			sandbox.WithAPIKey("your-api-key"),
			sandbox.WithDomain("your.domain.com"),
			sandbox.WithProtocol(sandbox.ProtocolPrivate),
		),
		sandbox.WithTimeout(600),
	)
	if err != nil {
		log.Fatal(err)
	}
	defer sb.Close(ctx)

	fmt.Println("sandbox:", sb.SandboxID())

	if res, err := sb.Commands.Run(ctx, "uname -a"); err == nil {
		fmt.Println(res.Stdout)
	}

	sb.Files.MakeDir(ctx, "/tmp/demo")
}
```

### envd/client 模式（运行时客户端）

```go
package main

import (
	"context"
	"fmt"

	"github.com/openkruise/agents-api/e2b/envd/client"
)

func main() {
	ctx := context.Background()

	c := client.New("your-sandbox-id",
		client.WithDomain("your.domain.com"),
		client.WithScheme("http"),
		client.WithRuntimeToken("your-runtime-token"),
	)

	if res, err := c.Commands.Run(ctx, "uname -a"); err == nil {
		fmt.Println(res.Stdout)
	}

	c.Files.MakeDir(ctx, "/tmp/demo")
}
```

更完整的演示可参考：

- [管理客户端示例](https://github.com/openkruise/agents-api/blob/master/e2b/example/sandbox/main.go)
- [运行时客户端示例](https://github.com/openkruise/agents-api/blob/master/e2b/example/envd_client/main.go)
