"""
SandboxClient for managing Sandbox resources in Kubernetes.

This module provides a client for creating, updating, deleting, and managing
Sandbox custom resources in a Kubernetes cluster.
"""

import logging
from typing import Dict, Any, Optional
from kubernetes.client.rest import ApiException
from .exceptions import SandboxNotFoundException
from .constants import (
    SANDBOX_API_GROUP,
    SANDBOX_API_VERSION,
    SANDBOX_PLURAL,
    DEFAULT_NAMESPACE
)
from .base_client import BaseCustomResourceClient


logger = logging.getLogger(__name__)


class SandboxClient(BaseCustomResourceClient):
    """
    A client for managing Sandbox custom resources in Kubernetes.

    Provides methods to perform CRUD operations on Sandbox resources,
    including creation, retrieval, updating, deletion, and watching.
    """

    def __init__(self, namespace: str = DEFAULT_NAMESPACE):
        """
        Initialize the SandboxClient.

        Args:
            namespace (str): The namespace where Sandbox resources will be managed.
                           Defaults to 'default'.
        """
        super().__init__(SANDBOX_API_GROUP, SANDBOX_API_VERSION, SANDBOX_PLURAL, namespace)

    def create_sandbox(self, body: Dict[str, Any], namespace: Optional[str] = None) -> Dict[str, Any]:
        """
        Create a new Sandbox resource.

        Args:
            body (dict): The Sandbox resource definition
            namespace (str, optional): The namespace to create the resource in.
                                     Defaults to the client's namespace.

        Returns:
            dict: The created Sandbox resource

        Raises:
            ApiException: If the API call fails
        """
        return self.create_resource(body, namespace)

    def get_sandbox(self, name: str, namespace: Optional[str] = None) -> Dict[str, Any]:
        """
        Get a specific Sandbox resource by name.

        Args:
            name (str): The name of the Sandbox resource
            namespace (str, optional): The namespace of the resource.
                                     Defaults to the client's namespace.

        Returns:
            dict: The Sandbox resource

        Raises:
            SandboxNotFoundException: If the Sandbox is not found
            ApiException: If the API call fails
        """
        ns = namespace or self.namespace
        try:
            return self.get_resource(name, namespace)
        except ApiException as e:
            if e.status == 404:
                raise SandboxNotFoundException(f"Sandbox '{name}' not found in namespace '{ns}'")
            raise

    def update_sandbox(self, name: str, body: Dict[str, Any], namespace: Optional[str] = None) -> Dict[str, Any]:
        """
        Update an existing Sandbox resource.

        Args:
            name (str): The name of the Sandbox resource to update
            body (dict): The updated Sandbox resource definition
            namespace (str, optional): The namespace of the resource.
                                     Defaults to the client's namespace.

        Returns:
            dict: The updated Sandbox resource

        Raises:
            ApiException: If the API call fails
        """
        return self.update_resource(name, body, namespace)

    def update_sandbox_status(self, name: str, body: Dict[str, Any], namespace: Optional[str] = None) -> Dict[str, Any]:
        """
        Update the status of a Sandbox resource.

        Args:
            name (str): The name of the Sandbox resource
            body (dict): The updated status portion of the resource
            namespace (str, optional): The namespace of the resource.
                                     Defaults to the client's namespace.

        Returns:
            dict: The updated Sandbox resource with status

        Raises:
            ApiException: If the API call fails
        """
        return self.update_resource_status(name, body, namespace)

    def delete_sandbox(self, name: str, namespace: Optional[str] = None,
                       grace_period_seconds: Optional[int] = None) -> Dict[str, Any]:
        """
        Delete a specific Sandbox resource.

        Args:
            name (str): The name of the Sandbox resource to delete
            namespace (str, optional): The namespace of the resource.
                                     Defaults to the client's namespace.
            grace_period_seconds (int, optional): Grace period for deletion in seconds

        Returns:
            dict: The deletion status

        Raises:
            ApiException: If the API call fails
        """
        return self.delete_resource(name, namespace, grace_period_seconds)

    def list_sandboxes(self, namespace: Optional[str] = None,
                       label_selector: Optional[str] = None,
                       field_selector: Optional[str] = None) -> Dict[str, Any]:
        """
        List all Sandbox resources in a namespace.

        Args:
            namespace (str, optional): The namespace to list resources from.
                                     Defaults to the client's namespace.
            label_selector (str, optional): Label selector for filtering
            field_selector (str, optional): Field selector for filtering

        Returns:
            dict: A list of Sandbox resources
        """
        return self.list_resources(namespace, label_selector, field_selector)

    def watch_sandboxes(self, namespace: Optional[str] = None,
                        label_selector: Optional[str] = None,
                        field_selector: Optional[str] = None,
                        timeout_seconds: Optional[int] = None):
        """
        Watch for changes to Sandbox resources.

        Args:
            namespace (str, optional): The namespace to watch resources in.
                                     Defaults to the client's namespace.
            label_selector (str, optional): Label selector for filtering
            field_selector (str, optional): Field selector for filtering
            timeout_seconds (int, optional): Timeout for the watch in seconds

        Yields:
            dict: Events related to Sandbox resources
        """
        yield from self.watch_resources(namespace, label_selector, field_selector, timeout_seconds)

    def patch_sandbox(self, name: str, body: Dict[str, Any],
                      namespace: Optional[str] = None,
                      content_type: str = 'application/merge-patch+json') -> Dict[str, Any]:
        """
        Patch a specific Sandbox resource.

        Args:
            name (str): The name of the Sandbox resource to patch
            body (dict): The patch body
            namespace (str, optional): The namespace of the resource.
                                     Defaults to the client's namespace.
            content_type (str): The content type for the patch operation
                               Defaults to 'application/merge-patch+json'

        Returns:
            dict: The patched Sandbox resource

        Raises:
            ApiException: If the API call fails
        """
        return self.patch_resource(name, body, namespace, content_type)

    def delete_collection_sandboxes(self, namespace: Optional[str] = None,
                                    label_selector: Optional[str] = None,
                                    field_selector: Optional[str] = None) -> Dict[str, Any]:
        """
        Delete a collection of Sandbox resources.

        Args:
            namespace (str, optional): The namespace to delete resources from.
                                     Defaults to the client's namespace.
            label_selector (str, optional): Label selector for filtering
            field_selector (str, optional): Field selector for filtering

        Returns:
            dict: The deletion status
        """
        return self.delete_collection_resources(namespace, label_selector, field_selector)

    def wait_for_sandbox_condition(self, name: str, condition_type: str,
                                   condition_status: str = "True",
                                   timeout: int = 300,
                                   namespace: Optional[str] = None) -> Dict[str, Any]:
        """
        Wait for a Sandbox resource to reach a specific condition status.

        Args:
            name (str): The name of the Sandbox resource
            condition_type (str): The condition type to wait for
            condition_status (str): The expected status of the condition.
                                   Defaults to "True".
            timeout (int): Maximum time to wait in seconds. Defaults to 300.
            namespace (str, optional): The namespace of the resource.
                                     Defaults to the client's namespace.

        Returns:
            dict: The Sandbox resource when condition is met

        Raises:
            TimeoutError: If the condition is not met within the timeout
        """
        return self.wait_for_resource_condition(name, condition_type, condition_status, timeout, namespace)
