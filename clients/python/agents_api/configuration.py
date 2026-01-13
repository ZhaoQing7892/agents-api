"""
Configuration module for Agents API Python Client
"""

from kubernetes.client.configuration import Configuration as K8sConfiguration
from agents_api_client import DEFAULT_TIMEOUT, REQUEST_TIMEOUT, RETRY_COUNT


class Configuration(K8sConfiguration):
    """
    Configuration class for Agents API client.
    Inherits from Kubernetes client configuration.
    """

    def __init__(self):
        super().__init__()
        # Add specific configuration for Agents API
        self.default_timeout = DEFAULT_TIMEOUT
        self.request_timeout = REQUEST_TIMEOUT
        self.retry_count = RETRY_COUNT

    @classmethod
    def get_default_copy(cls):
        """
        Get a copy of the default configuration.

        Returns:
            Configuration: A copy of the default configuration
        """
        return cls()
