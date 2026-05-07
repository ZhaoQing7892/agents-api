# E2B Sandbox Go SDK

This package provides two ways to operate within an E2B Sandbox environment:

| Package         | Import Path                                        | Purpose                                                                                                                                                         |
|-----------------|----------------------------------------------------|-----------------------------------------------------------------------------------------------------------------------------------------------------------------|
| **sandbox**     | `github.com/openkruise/agents-api/e2b/sandbox`     | **Management Client**: Sandbox lifecycle management (Create / Connect / Pause / Kill) + in-container operations (Commands / Files), with Protocol-based routing |
| **envd/client** | `github.com/openkruise/agents-api/e2b/envd/client` | **Runtime Client**: Directly operates on a running sandbox's envd service for command execution and file operations, without any lifecycle management           |

> Both share the same underlying Commands / Filesystem implementation. The only difference is **whether requests go
through Protocol routing and the management API**.

---

## Package Structure

```
e2b/
├── sandbox/                          # Management Client (package sandbox)
│   ├── sandbox.go                    #   Sandbox struct: Create / Connect / Pause / Kill
│   ├── sandbox_api.go                #   Low-level REST client (SandboxApi): List / GetInfo / Kill / ...
│   └── config.go                     #   ConnectionConfig: Protocol / Scheme / Domain / API URL
│
├── envd/                             # envd runtime layer
│   ├── client/                       #   Runtime Client (package client)
│   │   ├── client.go                 #     Client struct: New / NewWithConfig
│   │   ├── config.go                 #     Config & Options: Domain / Scheme / RuntimeToken / ...
│   │   ├── commands.go               #     Commands: Run / Start / Kill / SendStdin / List / ConnectToProcess
│   │   ├── command_handle.go         #     CommandHandle: Wait / Disconnect / Kill
│   │   └── filesystem.go             #     Filesystem: List / Exists / GetInfo / MakeDir / Rename / Remove
│   ├── process/                      #   protobuf generated code (envd Process gRPC)
│   │   ├── process.pb.go
│   │   └── processconnect/
│   └── filesystem/                   #   protobuf generated code (envd Filesystem gRPC)
│       ├── filesystem.pb.go
│       └── filesystemconnect/
│
├── client/                           # Auto-generated E2B REST API client (OpenAPI)
│
├── example/
│   ├── sandbox/main.go               # Management Client example
│   └── envd_client/main.go           # Runtime Client example
│
├── README.md
└── README_zh-CH.md
```

### Key Relationships

```
┌──────────────────────────────────────────────────────┐
│                    sandbox.Sandbox                    │
│  (Create / Connect / Pause / Kill + Protocol routing)│
│                                                      │
│   embeds ──► envd/client.Client                      │
│              (Commands + Files + Config)              │
│                                                      │
│   uses ────► client.*  (E2B REST API)                │
└──────────────────────────────────────────────────────┘
                        │
                        ▼
┌──────────────────────────────────────────────────────┐
│                 envd/client.Client                    │
│          (Runtime Client, no lifecycle mgmt)          │
│                                                      │
│   Commands.Rpc ──► processconnect.ProcessClient       │
│   Files.Rpc    ──► filesystemconnect.FilesystemClient │
└──────────────────────────────────────────────────────┘
```

- **`sandbox`** (Management Client) embeds `envd/client.Client`, adding lifecycle management and Protocol-based URL
  routing on top
- **`envd/client`** (Runtime Client) can be used standalone — just provide a sandbox ID and Token + domain to operate a
  running
  sandbox directly
- **`Commands.Rpc`** / **`Files.Rpc`** allowing users to call native gRPC methods not covered by the
  high-level API

---

## Quick Start: sandbox (Management Client)

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

[Full example](https://github.com/openkruise/agents-api/blob/master/e2b/example/sandbox/main.go)

---

## Quick Start: envd/client (Runtime Client)

When you already know a sandbox is running and don't need the management API to create/destroy it — just operate the
in-container environment directly:

```go
package main

import (
	"context"
	"fmt"

	envdclient "github.com/openkruise/agents-api/e2b/envd/client"
)

func main() {
	ctx := context.Background()

	c := envdclient.New("your-sandbox-id",
		envdclient.WithDomain("your.domain.com"),
		envdclient.WithScheme("http"),
		envdclient.WithRuntimeToken("your-runtime-token"),
	)

	res, _ := c.Commands.Run(ctx, "uname -a")
	fmt.Println(res.Stdout)

	c.Files.MakeDir(ctx, "/tmp/demo")
}
```

[Full example](https://github.com/openkruise/agents-api/blob/master/e2b/example/envd_client/main.go)

---

## Connection Configuration

### sandbox Package: Scheme & Protocol

Connection behavior in the sandbox package is controlled via `ConnectionConfig`, determined by two **orthogonal**
dimensions: **Scheme** and **Protocol**.

#### Protocol (Routing Mode)

| Value                | Constant                  | API URL                          | Sandbox URL                                     |
|----------------------|---------------------------|----------------------------------|-------------------------------------------------|
| **Native (default)** | `sandbox.ProtocolNative`  | `https://api.<domain>`           | `https://<port>-<sandboxID>.<domain>`           |
| **Private**          | `sandbox.ProtocolPrivate` | `<scheme>://<domain>/kruise/api` | `<scheme>://<domain>/kruise/<sandboxID>/<port>` |

- **Native**: Subdomain-based routing, for standard E2B public cloud deployments
- **Private**: Path-prefix-based routing (`/kruise/...`) through a unified gateway, for private deployments or local
  port forwarding

#### Scheme

| Value                   | Use Case                                           |
|-------------------------|----------------------------------------------------|
| **`"https"` (default)** | Production / public network                        |
| **`"http"`**            | Local port forwarding, TLS-free intranet debugging |

#### 4 Typical Combinations

| Protocol | Scheme | Configuration                                          | Use Case                                |
|----------|--------|--------------------------------------------------------|-----------------------------------------|
| Native   | https  | (all defaults)                                         | Public SaaS / standard deployment       |
| Private  | https  | `WithProtocol(ProtocolPrivate)`                        | Private gateway proxy                   |
| Private  | http   | `WithProtocol(ProtocolPrivate)` + `WithScheme("http")` | Local port-forward debugging            |
| Native   | http   | `WithScheme("http")`                                   | Subdomain deployment without TLS (rare) |

#### ConnectionConfigOption List

Applied via `sandbox.NewConnectionConfig(opts...)` or embedded in `Create/Connect` calls via `WithConfig(...)`:

| Option                                | Description                                                                     |
|---------------------------------------|---------------------------------------------------------------------------------|
| `WithAPIKey(key string)`              | API Key, sent as `X-API-Key` header                                             |
| `WithAccessToken(token string)`       | OAuth2 Access Token (alternative to APIKey)                                     |
| `WithDomain(domain string)`           | Domain, default `e2b.app`                                                       |
| `WithScheme(scheme string)`           | URL scheme, default `https`                                                     |
| `WithProtocol(p Protocol)`            | Routing protocol, default `ProtocolNative`                                      |
| `WithAPIURL(url string)`              | **Highest priority**: overrides API base URL directly, bypasses Protocol/Domain |
| `WithSandboxBaseURL(url string)`      | **Highest priority**: overrides sandbox envd base URL directly                  |
| `WithRequestTimeout(d time.Duration)` | HTTP request timeout, default 60s                                               |
| `WithDebug(debug bool)`               | Debug mode: skips certain API calls                                             |

#### Environment Variable Fallback

| Variable           | Field                           |
|--------------------|---------------------------------|
| `E2B_API_KEY`      | APIKey                          |
| `E2B_ACCESS_TOKEN` | AccessToken                     |
| `E2B_DOMAIN`       | Domain                          |
| `E2B_SCHEME`       | Scheme                          |
| `E2B_PROTOCOL`     | Protocol (`native` / `private`) |
| `E2B_API_URL`      | APIURL                          |

#### Priority

`WithAPIURL` / `WithSandboxBaseURL` (explicit override) > `WithProtocol` + `WithDomain` composition > environment
variables > defaults

---

### envd/client Package: Runtime Client Config

The Runtime Client **does not involve Protocol** — only `Scheme` + `Domain` are needed to determine the envd
address (`<scheme>://<domain>`).

#### Option List

| Option                                | Description                                    |
|---------------------------------------|------------------------------------------------|
| `WithDomain(domain string)`           | envd domain, default `e2b.app`                 |
| `WithScheme(scheme string)`           | URL scheme, default `https`                    |
| `WithRuntimeToken(token string)`      | Runtime token, sent as `X-Access-Token` header |
| `WithEnvdPort(port int)`              | envd port, default `49983`                     |
| `WithAPIKey(apiKey string)`           | Optional API Key (when ingress validates it)   |
| `WithAuthHeader(header string)`       | Override default Authorization header          |
| `WithSandboxBaseURL(url string)`      | Fully override URL composition                 |
| `WithHeader(key, value string)`       | Add a single custom header                     |
| `WithHeaders(headers map)`            | Merge multiple custom headers                  |
| `WithRequestTimeout(d time.Duration)` | HTTP timeout, default 60s                      |

---

## Create / Connect Sandbox

### `Create(ctx, template, opts...) (*Sandbox, error)`

Creates a new sandbox from a template. Defaults to `"code-interpreter"` when template is empty.

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

Connects to an existing (including paused) sandbox. Automatically resumes if paused.

```go
sb, err := sandbox.Connect(ctx, "default--xxx-xxx",
sandbox.WithConfig(sandbox.WithAPIKey("xxx"), sandbox.WithDomain("example.com")),
)
```

### SandboxOption List

| Option                       | Description                                     |
|------------------------------|-------------------------------------------------|
| `WithConfig(opts...)`        | Embed a set of `ConnectionConfigOption`         |
| `WithTimeout(seconds int32)` | Sandbox lifetime timeout, default 300 seconds   |
| `WithMetadata(map)`          | Sandbox metadata                                |
| `WithEnvVars(map)`           | Environment variables injected into the sandbox |
| `WithAutoPause(bool)`        | Whether to enable auto-pause                    |
| `WithSecure(bool)`           | Enable secure mode                              |

### Sandbox Instance Methods

| Method                                 | Description                              |
|----------------------------------------|------------------------------------------|
| `SandboxID() string`                   | Returns the sandbox ID                   |
| `TemplateID() string`                  | Returns the template ID                  |
| `GetInfo(ctx) (*SandboxInfo, error)`   | Gets sandbox details                     |
| `SetTimeout(ctx, timeout int32) error` | Updates the timeout                      |
| `Pause(ctx) (string, error)`           | Pauses the sandbox                       |
| `Kill(ctx) (bool, error)`              | Destroys the sandbox                     |
| `Close(ctx) error`                     | Alias for `Kill`, convenient for `defer` |

`Sandbox` exposes two sub-modules:

- `sb.Commands` — command execution (`*Commands`)
- `sb.Files` — filesystem operations (`*Filesystem`)

---

## Sandbox Management API (SandboxApi)

`SandboxApi` is the low-level REST client that can be used independently without creating a `Sandbox` instance (e.g., to
list all sandboxes).

```go
api := sandbox.NewSandboxApi(sandbox.NewConnectionConfig(
sandbox.WithAPIKey("xxx"),
sandbox.WithDomain("example.com"),
))
```

| Method                                                                       | Description                                                 |
|------------------------------------------------------------------------------|-------------------------------------------------------------|
| `List(ctx) ([]SandboxInfo, error)`                                           | Lists all running sandboxes                                 |
| `GetInfo(ctx, sandboxID) (*SandboxInfo, error)`                              | Gets sandbox details; returns `not found` error on 404      |
| `Kill(ctx, sandboxID) (bool, error)`                                         | Destroys a sandbox; returns `true` directly in `Debug` mode |
| `SetTimeout(ctx, sandboxID, timeout int32) error`                            | Updates the timeout                                         |
| `CreateSandbox(ctx, opts CreateSandboxOpts) (*SandboxCreateResponse, error)` | Low-level create API                                        |
| `ConnectSandbox(ctx, sandboxID, timeout int32) (*client.Sandbox, error)`     | Low-level connect API (auto-resumes paused sandboxes)       |
| `Pause(ctx, sandboxID) (string, error)`                                      | Pauses a sandbox                                            |

### `SandboxInfo` Fields

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

## Command Execution (Commands)

Operate in-container processes via `sb.Commands` (sandbox mode) or `c.Commands` (direct mode). Backed by
envd's `Process` gRPC service.

### Methods

| Method                                                      | Description                                                                                         |
|-------------------------------------------------------------|-----------------------------------------------------------------------------------------------------|
| `Run(ctx, cmd, opts...) (*CommandResult, error)`            | **Foreground execution**: starts a command and waits for completion, returns stdout/stderr/exitCode |
| `Start(ctx, cmd, opts...) (*CommandHandle, error)`          | **Background start**: returns a handle, caller decides when to `Wait`                               |
| `List(ctx) ([]ProcessInfo, error)`                          | Lists all running processes                                                                         |
| `Kill(ctx, pid uint32) (bool, error)`                       | Sends SIGKILL to the given PID; returns `false, nil` if process doesn't exist                       |
| `SendStdin(ctx, pid uint32, data string) error`             | Writes data to a process's stdin                                                                    |
| `ConnectToProcess(ctx, pid uint32) (*CommandHandle, error)` | Reconnects to a running process, subscribing to its subsequent output                               |

### `RunOpts` Fields

```go
type RunOpts struct {
Envs       map[string]string // Process environment variables
Cwd        string            // Working directory
Stdin      bool // Whether to allow SendStdin
Background bool // Background execution (reserved)
OnStdout   func (string) // Streaming stdout callback (foreground only)
OnStderr   func (string) // Streaming stderr callback (foreground only)
}
```

> Commands are executed via `/bin/bash -l -c <cmd>`, preserving the login environment.

### `CommandHandle`

Returned by `Start` / `ConnectToProcess` for interaction or waiting:

| Method                                             | Description                                                              |
|----------------------------------------------------|--------------------------------------------------------------------------|
| `Pid() uint32`                                     | Returns the process PID                                                  |
| `Wait(onStdout, onStderr) (*CommandResult, error)` | Blocks until completion; non-zero exit code includes `*CommandExitError` |
| `Disconnect()`                                     | Disconnects from the stream **without killing** the process              |
| `Kill() bool`                                      | Kills the process                                                        |

### `CommandResult` / `CommandExitError`

```go
type CommandResult struct {
Stdout   string
Stderr   string
ExitCode int32
Error    string
}

// Returned when exit code is non-zero (alongside *CommandResult)
type CommandExitError struct {
Stdout, Stderr string
ExitCode       int32
ErrorMessage   string
}
```

### Example

```go
// Foreground execution with streaming output
res, err := sb.Commands.Run(ctx, "ls -la /tmp", envdclient.RunOpts{
Cwd:      "/tmp",
Envs:     map[string]string{"LANG": "C"},
OnStdout: func (line string) { fmt.Print(line) },
})

// Background start + manual Kill
h, _ := sb.Commands.Start(ctx, "sleep 60")
fmt.Println("pid =", h.Pid())
h.Kill()
```

---

## Filesystem

Operate in-container files via `sb.Files` (sandbox mode) or `c.Files` (direct mode). Wraps the official envd Filesystem
gRPC service.

### Methods

| Method                                              | Description                                                             |
|-----------------------------------------------------|-------------------------------------------------------------------------|
| `List(ctx, path, depth...) ([]EntryInfo, error)`    | Lists directory entries; `depth` defaults to 1                          |
| `Exists(ctx, path) (bool, error)`                   | Checks if a path exists (via `Stat`, returns false on 404)              |
| `GetInfo(ctx, path) (*EntryInfo, error)`            | Gets file/directory info                                                |
| `MakeDir(ctx, path) (bool, error)`                  | Creates a directory recursively; returns `false, nil` if already exists |
| `Rename(ctx, oldPath, newPath) (*EntryInfo, error)` | Renames/moves a file or directory                                       |
| `Remove(ctx, path) error`                           | Removes a file or directory                                             |

### `EntryInfo` Fields

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

### Example

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

## Full Examples

### sandbox Mode (Management Client)

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

### envd/client Mode (Runtime Client)

```go
package main

import (
	"context"
	"fmt"

	envdclient "github.com/openkruise/agents-api/e2b/envd/client"
)

func main() {
	ctx := context.Background()

	c := envdclient.New("your-sandbox-id",
		envdclient.WithDomain("your.domain.com"),
		envdclient.WithScheme("http"),
		envdclient.WithRuntimeToken("your-runtime-token"),
	)

	if res, err := c.Commands.Run(ctx, "uname -a"); err == nil {
		fmt.Println(res.Stdout)
	}

	c.Files.MakeDir(ctx, "/tmp/demo")
}
```

For more complete demos, see:

- [Management Client example](https://github.com/openkruise/agents-api/blob/master/e2b/example/sandbox/main.go)
- [Runtime Client example](https://github.com/openkruise/agents-api/blob/master/e2b/example/envd_client/main.go)
