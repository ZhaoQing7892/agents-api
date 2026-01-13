"""
Constants for Agents API Python Client
"""

# API Group and Version for Sandbox
SANDBOX_API_GROUP = "agents.kruise.io"
SANDBOX_API_VERSION = "v1alpha1"
SANDBOX_PLURAL = "sandboxes"

# API Group and Version for SandboxSet
SANDBOXSET_API_GROUP = "agents.kruise.io"
SANDBOXSET_API_VERSION = "v1alpha1"
SANDBOXSET_PLURAL = "sandboxsets"

# Resource kinds
SANDBOX_KIND = "Sandbox"
SANDBOXSET_KIND = "SandboxSet"

# Common constants
DEFAULT_NAMESPACE = "default"
DEFAULT_TIMEOUT = 30
RETRY_COUNT = 3
REQUEST_TIMEOUT = 60
WATCH_TIMEOUT = 300
MAX_CONNECTION_POOL_SIZE = 10

# Condition types
CONDITION_READY = "Ready"
CONDITION_AVAILABLE = "Available"
CONDITION_PROGRESSING = "Progressing"
CONDITION_DEGRADED = "Degraded"