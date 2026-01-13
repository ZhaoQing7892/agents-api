"""
Custom exceptions for Agents API Python Client
"""


class AgentsAPIException(Exception):
    """
    Base exception for Agents API client.
    """
    pass


class SandboxNotFoundException(AgentsAPIException):
    """
    Raised when a Sandbox resource is not found.
    """
    pass


class SandboxSetNotFoundException(AgentsAPIException):
    """
    Raised when a SandboxSet resource is not found.
    """
    pass


class ValidationException(AgentsAPIException):
    """
    Raised when validation fails.
    """
    pass


class TimeoutException(AgentsAPIException):
    """
    Raised when an operation times out.
    """
    pass


class PermissionDeniedException(AgentsAPIException):
    """
    Raised when access to a resource is denied.
    """
    pass
