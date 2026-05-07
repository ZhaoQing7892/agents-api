package sandbox

import (
	"fmt"
	"net/http"
	"os"
	"time"

	"github.com/openkruise/agents-api/e2b/client"
	envdclient "github.com/openkruise/agents-api/e2b/envd/client"
)

// Protocol defines the URL routing protocol for E2B services.
//
// Two protocols are supported:
//
//	Native (default):
//	  API URL:     https://api.<domain>
//	  Sandbox URL: https://<port>-<sandboxID>.<domain>
//
//	Private (gateway):
//	  API URL:     <scheme>://<domain>/kruise/api
//	  Sandbox URL: <scheme>://<domain>/kruise/<sandboxID>/<port>
type Protocol string

const (
	// ProtocolNative uses subdomain-based routing (original E2B protocol).
	ProtocolNative Protocol = "native"
	// ProtocolPrivate uses path-based routing through a gateway.
	ProtocolPrivate Protocol = "private"
)

const (
	defaultDomain         = "e2b.app"
	defaultScheme         = "https"
	defaultRequestTimeout = 60 * time.Second
	defaultSandboxTimeout = 300 // seconds
	defaultEnvdPort       = 49983
)

// ConnectionConfig stores the configuration for connecting to E2B services.
type ConnectionConfig struct {
	// APIKey is the E2B API key for authentication.
	APIKey string
	// AccessToken is the OAuth2 access token (alternative to APIKey).
	AccessToken string
	// Domain is the base domain for E2B services (default: "e2b.app").
	Domain string
	// Scheme is the URL scheme, "https" (default) or "http".
	Scheme string
	// Protocol determines the URL routing mode: ProtocolNative or ProtocolPrivate.
	Protocol Protocol
	// APIURL overrides the full API base URL. Highest priority, bypasses Protocol/Domain.
	APIURL string
	// SandboxBaseURL overrides the sandbox envd base URL pattern. Highest priority, bypasses Protocol/Domain.
	SandboxBaseURL string
	// Debug enables debug mode, skipping certain API calls.
	Debug bool
	// RequestTimeout is the timeout for HTTP requests.
	RequestTimeout time.Duration
	// EnvdPort is the port for the envd service inside the sandbox.
	EnvdPort int
	// Headers contains additional headers to send with sandbox requests.
	Headers map[string]string
}

// NewConnectionConfig creates a new ConnectionConfig with defaults and environment variable fallback.
func NewConnectionConfig(opts ...ConnectionConfigOption) *ConnectionConfig {
	config := &ConnectionConfig{
		Domain:         defaultDomain,
		Scheme:         defaultScheme,
		Protocol:       ProtocolNative,
		RequestTimeout: defaultRequestTimeout,
		EnvdPort:       defaultEnvdPort,
		Headers:        make(map[string]string),
	}

	// Apply environment variable defaults
	if apiKey := os.Getenv("E2B_API_KEY"); apiKey != "" {
		config.APIKey = apiKey
	}
	if accessToken := os.Getenv("E2B_ACCESS_TOKEN"); accessToken != "" {
		config.AccessToken = accessToken
	}
	if domain := os.Getenv("E2B_DOMAIN"); domain != "" {
		config.Domain = domain
	}
	if apiURL := os.Getenv("E2B_API_URL"); apiURL != "" {
		config.APIURL = apiURL
	}
	if scheme := os.Getenv("E2B_SCHEME"); scheme != "" {
		config.Scheme = scheme
	}
	if protocol := os.Getenv("E2B_PROTOCOL"); protocol != "" {
		config.Protocol = Protocol(protocol)
	}

	// Apply options
	for _, opt := range opts {
		opt(config)
	}

	return config
}

// ConnectionConfigOption is a functional option for ConnectionConfig.
type ConnectionConfigOption func(*ConnectionConfig)

// WithAPIKey sets the API key.
func WithAPIKey(apiKey string) ConnectionConfigOption {
	return func(c *ConnectionConfig) {
		c.APIKey = apiKey
	}
}

// WithAccessToken sets the access token.
func WithAccessToken(accessToken string) ConnectionConfigOption {
	return func(c *ConnectionConfig) {
		c.AccessToken = accessToken
	}
}

// WithDomain sets the domain.
func WithDomain(domain string) ConnectionConfigOption {
	return func(c *ConnectionConfig) {
		c.Domain = domain
	}
}

// WithDebug enables debug mode.
func WithDebug(debug bool) ConnectionConfigOption {
	return func(c *ConnectionConfig) {
		c.Debug = debug
	}
}

// WithRequestTimeout sets the request timeout.
func WithRequestTimeout(timeout time.Duration) ConnectionConfigOption {
	return func(c *ConnectionConfig) {
		c.RequestTimeout = timeout
	}
}

// WithScheme sets the URL scheme ("https" or "http").
func WithScheme(scheme string) ConnectionConfigOption {
	return func(c *ConnectionConfig) {
		c.Scheme = scheme
	}
}

// WithProtocol sets the URL routing protocol (ProtocolNative or ProtocolPrivate).
func WithProtocol(protocol Protocol) ConnectionConfigOption {
	return func(c *ConnectionConfig) {
		c.Protocol = protocol
	}
}

// WithAPIURL overrides the full API base URL. Highest priority, bypasses Protocol/Domain.
func WithAPIURL(apiURL string) ConnectionConfigOption {
	return func(c *ConnectionConfig) {
		c.APIURL = apiURL
	}
}

// WithSandboxBaseURL overrides the sandbox envd base URL. Highest priority, bypasses Protocol/Domain.
func WithSandboxBaseURL(sandboxBaseURL string) ConnectionConfigOption {
	return func(c *ConnectionConfig) {
		c.SandboxBaseURL = sandboxBaseURL
	}
}

// GetAPIURL returns the base API URL.
//
// Priority: APIURL (explicit override) > Protocol + Domain.
//
//	Native:  https://api.<domain>
//	Private: <scheme>://<domain>/kruise/api
func (c *ConnectionConfig) GetAPIURL() string {
	if c.APIURL != "" {
		return c.APIURL
	}
	scheme := c.getScheme()
	if c.Protocol == ProtocolPrivate {
		return fmt.Sprintf("%s://%s/kruise/api", scheme, c.Domain)
	}
	return fmt.Sprintf("%s://api.%s", scheme, c.Domain)
}

// GetSandboxURL returns the envd API URL for a given sandbox.
//
// Priority: SandboxBaseURL (explicit override) > Protocol + Domain.
//
//	Native:  https://<port>-<sandboxID>.<domain>
//	Private: <scheme>://<domain>/kruise/<sandboxID>/<port>
func (c *ConnectionConfig) GetSandboxURL(sandboxID string) string {
	if c.SandboxBaseURL != "" {
		return fmt.Sprintf("%s/%s", c.SandboxBaseURL, sandboxID)
	}
	scheme := c.getScheme()
	if c.Protocol == ProtocolPrivate {
		return fmt.Sprintf("%s://%s/kruise/%s/%d", scheme, c.Domain, sandboxID, c.EnvdPort)
	}
	return fmt.Sprintf("%s://%d-%s.%s", scheme, c.EnvdPort, sandboxID, c.Domain)
}

// getScheme returns the URL scheme, defaulting to "https".
func (c *ConnectionConfig) getScheme() string {
	if c.Scheme != "" {
		return c.Scheme
	}
	return defaultScheme
}

// GetSandboxHeaders returns headers required for sandbox communication.
func (c *ConnectionConfig) GetSandboxHeaders(sandboxID string) map[string]string {
	headers := map[string]string{
		//"Content-Type": "application/json",
	}
	if c.APIKey != "" {
		headers["X-API-Key"] = c.APIKey
	}
	headers["Authorization"] = "Basic cm9vdDo="
	headers["E2b-Sandbox-Id"] = sandboxID
	headers["E2b-Sandbox-Port"] = fmt.Sprintf("%d", c.EnvdPort)
	for k, v := range c.Headers {
		headers[k] = v
	}
	return headers
}

// toEnvdConfig converts this ConnectionConfig into an envdclient.Config used
// by the embedded data-plane client. The sandboxID is required so that the
// Protocol-aware sandbox URL can be pre-computed and passed as
// SandboxBaseURL — the envd client itself has no notion of Protocol.
func (c *ConnectionConfig) toEnvdConfig(sandboxID string) *envdclient.Config {
	cfg := &envdclient.Config{
		Domain:         c.Domain,
		Scheme:         c.Scheme,
		EnvdPort:       c.EnvdPort,
		APIKey:         c.APIKey,
		RequestTimeout: c.RequestTimeout,
	}

	// Pre-compute the full sandbox URL using the Protocol-aware logic so
	// the envd client can use it directly without knowing about Protocol.
	cfg.SandboxBaseURL = c.GetSandboxURL(sandboxID)

	if len(c.Headers) > 0 {
		cfg.Headers = make(map[string]string, len(c.Headers))
		for k, v := range c.Headers {
			cfg.Headers[k] = v
		}
	}
	return cfg
}

// NewAPIClient creates a new OpenAPI client configured for this connection.
func (c *ConnectionConfig) NewAPIClient() *client.APIClient {
	cfg := client.NewConfiguration()
	cfg.Servers = client.ServerConfigurations{
		{URL: c.GetAPIURL()},
	}
	cfg.HTTPClient = &http.Client{
		Timeout: c.RequestTimeout,
	}
	if c.APIKey != "" {
		cfg.DefaultHeader["X-API-Key"] = c.APIKey
	}
	return client.NewAPIClient(cfg)
}
