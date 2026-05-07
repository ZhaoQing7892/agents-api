package client

import (
	"net/http"

	"github.com/openkruise/agents-api/e2b/envd/filesystem/filesystemconnect"
	"github.com/openkruise/agents-api/e2b/envd/process/processconnect"
)

// Client is a thin client that talks directly to the envd service of a single
// sandbox. It only exposes in-sandbox capabilities (Files, Commands) and is
// completely decoupled from the sandbox-management REST API.
//
// Construction is purely local; New does not perform any network call. The
// caller is expected to know the sandbox already exists and is reachable.
type Client struct {
	// Commands provides command execution in the sandbox.
	Commands *Commands
	// Files provides filesystem operations in the sandbox.
	Files *Filesystem

	sandboxID  string
	config     *Config
	envdURL    string
	httpClient *http.Client
}

// New constructs an envd Client for a known sandbox ID.
//
// Typical usage:
//
//	c := client.New("default--code-interpreter-xxx",
//	    client.WithDomain("e2b-demo-sg.example.com"),
//	    client.WithScheme("http"),
//	)
//	out, _ := c.Commands.Run(ctx, "ls /")
func New(sandboxID string, opts ...Option) *Client {
	cfg := NewConfig(opts...)
	return NewWithConfig(sandboxID, cfg)
}

// NewWithConfig is like New but takes a pre-built Config. Used by upstream
// packages (e.g. sandbox) that already have their own configuration object.
func NewWithConfig(sandboxID string, cfg *Config) *Client {
	if cfg == nil {
		cfg = NewConfig()
	}
	httpClient := &http.Client{Timeout: cfg.RequestTimeout}
	envdURL := cfg.SandboxURL(sandboxID)
	headers := cfg.SandboxHeaders(sandboxID)

	fsRPC := filesystemconnect.NewFilesystemClient(httpClient, envdURL)
	procRPC := processconnect.NewProcessClient(httpClient, envdURL)

	return &Client{
		Commands:   NewCommands(procRPC, headers),
		Files:      NewFilesystem(fsRPC, headers),
		sandboxID:  sandboxID,
		config:     cfg,
		envdURL:    envdURL,
		httpClient: httpClient,
	}
}

// SandboxID returns the sandbox identifier this client is bound to.
func (c *Client) SandboxID() string {
	return c.sandboxID
}

// EnvdURL returns the resolved envd base URL for the bound sandbox.
func (c *Client) EnvdURL() string {
	return c.envdURL
}

// Config returns the underlying configuration. The returned pointer must be
// treated as read-only; mutating it does not reconfigure the client.
func (c *Client) Config() *Config {
	return c.config
}
