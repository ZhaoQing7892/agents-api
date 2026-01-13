"""
SandboxSetClient for managing SandboxSet resources in Kubernetes.

This module provides a client for creating, updating, deleting, and managing
SandboxSet custom resources in a Kubernetes cluster.
"""

import time
import logging
from typing import Dict, Any, Optional
from kubernetes.client.rest import ApiException
from .exceptions import SandboxSetNotFoundException
from .constants import (
    SANDBOXSET_API_GROUP,
    SANDBOXSET_API_VERSION,
    SANDBOXSET_PLURAL,
    DEFAULT_NAMESPACE
)
from .base_client import BaseCustomResourceClient


logger = logging.getLogger(__name__)


class SandboxSetClient(BaseCustomResourceClient):
    """
    A client for managing SandboxSet custom resources in Kubernetes.

    Provides methods to perform CRUD operations on SandboxSet resources,
    including creation, retrieval, updating, deletion, and watching.
    """

    def __init__(self, namespace: str = DEFAULT_NAMESPACE):
        """
        Initialize the SandboxSetClient.

        Args:
            namespace (str): The namespace where SandboxSet resources will be managed.
                           Defaults to 'default'.
        """
        super().__init__(SANDBOXSET_API_GROUP, SANDBOXSET_API_VERSION, SANDBOXSET_PLURAL, namespace)

    def create_sandboxset(self, body: Dict[str, Any], namespace: Optional[str] = None) -> Dict[str, Any]:
        """
        Create a new SandboxSet resource.

        Args:
            body (dict): The SandboxSet resource definition
            namespace (str, optional): The namespace to create the resource in.
                                     Defaults to the client's namespace.

        Returns:
            dict: The created SandboxSet resource

        Raises:
            ApiException: If the API call fails
        """
        return self.create_resource(body, namespace)

    def get_sandboxset(self, name: str, namespace: Optional[str] = None) -> Dict[str, Any]:
        """
        Get a specific SandboxSet resource by name.

        Args:
            name (str): The name of the SandboxSet resource
            namespace (str, optional): The namespace of the resource.
                                     Defaults to the client's namespace.

        Returns:
            dict: The SandboxSet resource

        Raises:
            SandboxSetNotFoundException: If the SandboxSet is not found
            ApiException: If the API call fails
        """
        ns = namespace or self.namespace
        try:
            return self.get_resource(name, namespace)
        except ApiException as e:
            if e.status == 404:
                raise SandboxSetNotFoundException(f"SandboxSet '{name}' not found in namespace '{ns}'")
            raise

    def update_sandboxset(self, name: str, body: Dict[str, Any], namespace: Optional[str] = None) -> Dict[str, Any]:
        """
        Update an existing SandboxSet resource.

        Args:
            name (str): The name of the SandboxSet resource to update
            body (dict): The updated SandboxSet resource definition
            namespace (str, optional): The namespace of the resource.
                                     Defaults to the client's namespace.

        Returns:
            dict: The updated SandboxSet resource

        Raises:
            ApiException: If the API call fails
        """
        return self.update_resource(name, body, namespace)

    def update_sandboxset_status(self, name: str, body: Dict[str, Any], namespace: Optional[str] = None) -> Dict[str, Any]:
        """
        Update the status of a SandboxSet resource.

        Args:
            name (str): The name of the SandboxSet resource
            body (dict): The updated status portion of the resource
            namespace (str, optional): The namespace of the resource.
                                     Defaults to the client's namespace.

        Returns:
            dict: The updated SandboxSet resource with status

        Raises:
            ApiException: If the API call fails
        """
        return self.update_resource_status(name, body, namespace)

    def delete_sandboxset(self, name: str, namespace: Optional[str] = None,
                          grace_period_seconds: Optional[int] = None) -> Dict[str, Any]:
        """
        Delete a specific SandboxSet resource.

        Args:
            name (str): The name of the SandboxSet resource to delete
            namespace (str, optional): The namespace of the resource.
                                     Defaults to the client's namespace.
            grace_period_seconds (int, optional): Grace period for deletion in seconds

        Returns:
            dict: The deletion status

        Raises:
            ApiException: If the API call fails
        """
        return self.delete_resource(name, namespace, grace_period_seconds)

    def list_sandboxsets(self, namespace: Optional[str] = None,
                         label_selector: Optional[str] = None,
                         field_selector: Optional[str] = None) -> Dict[str, Any]:
        """
        List all SandboxSet resources in a namespace.

        Args:
            namespace (str, optional): The namespace to list resources from.
                                     Defaults to the client's namespace.
            label_selector (str, optional): Label selector for filtering
            field_selector (str, optional): Field selector for filtering

        Returns:
            dict: A list of SandboxSet resources
        """
        return self.list_resources(namespace, label_selector, field_selector)

    def watch_sandboxsets(self, namespace: Optional[str] = None,
                          label_selector: Optional[str] = None,
                          field_selector: Optional[str] = None,
                          timeout_seconds: Optional[int] = None):
        """
        Watch for changes to SandboxSet resources.

        Args:
            namespace (str, optional): The namespace to watch resources in.
                                     Defaults to the client's namespace.
            label_selector (str, optional): Label selector for filtering
            field_selector (str, optional): Field selector for filtering
            timeout_seconds (int, optional): Timeout for the watch in seconds

        Yields:
            dict: Events related to SandboxSet resources
        """
        yield from self.watch_resources(namespace, label_selector, field_selector, timeout_seconds)

    def patch_sandboxset(self, name: str, body: Dict[str, Any],
                         namespace: Optional[str] = None,
                         content_type: str = 'application/merge-patch+json') -> Dict[str, Any]:
        """
        Patch a specific SandboxSet resource.

        Args:
            name (str): The name of the SandboxSet resource to patch
            body (dict): The patch body
            namespace (str, optional): The namespace of the resource.
                                     Defaults to the client's namespace.
            content_type (str): The content type for the patch operation
                               Defaults to 'application/merge-patch+json'

        Returns:
            dict: The patched SandboxSet resource

        Raises:
            ApiException: If the API call fails
        """
        return self.patch_resource(name, body, namespace, content_type)

    def delete_collection_sandboxsets(self, namespace: Optional[str] = None,
                                      label_selector: Optional[str] = None,
                                      field_selector: Optional[str] = None) -> Dict[str, Any]:
        """
        Delete a collection of SandboxSet resources.

        Args:
            namespace (str, optional): The namespace to delete resources from.
                                     Defaults to the client's namespace.
            label_selector (str, optional): Label selector for filtering
            field_selector (str, optional): Field selector for filtering

        Returns:
            dict: The deletion status
        """
        return self.delete_collection_resources(namespace, label_selector, field_selector)

    def wait_for_sandboxset_condition(self, name: str, condition_type: str,
                                      condition_status: str = "True",
                                      timeout: int = 300,
                                      namespace: Optional[str] = None) -> Dict[str, Any]:
        """
        Wait for a SandboxSet resource to reach a specific condition status.

        Args:
            name (str): The name of the SandboxSet resource
            condition_type (str): The condition type to wait for
            condition_status (str): The expected status of the condition.
                                   Defaults to "True".
            timeout (int): Maximum time to wait in seconds. Defaults to 300.
            namespace (str, optional): The namespace of the resource.
                                     Defaults to the client's namespace.

        Returns:
            dict: The SandboxSet resource when condition is met

        Raises:
            TimeoutError: If the condition is not met within the timeout
        """
        return self.wait_for_resource_condition(name, condition_type, condition_status, timeout, namespace)

    def scale_sandboxset(self, name: str, replicas: int, namespace: Optional[str] = None) -> Dict[str, Any]:
        """
        Scale a SandboxSet to the specified number of replicas.

        Args:
            name (str): The name of the SandboxSet to scale
            replicas (int): The desired number of replicas
            namespace (str, optional): The namespace of the resource.
                                     Defaults to the client's namespace.

        Returns:
            dict: The updated SandboxSet resource
        """
        ns = namespace or self.namespace
        # Create patch to update the replicas
        patch_body = {
            "spec": {
                "replicas": replicas
            }
        }

        return self.custom_objects_api.patch_namespaced_custom_object(
            group=self.api_group,
            version=self.api_version,
            namespace=ns,
            plural=self.plural,
            name=name,
            body=patch_body
        )

    def get_sandboxset_replicas(self, name: str, namespace: Optional[str] = None) -> int:
        """
        Get the current number of replicas for a SandboxSet.

        Args:
            name (str): The name of the SandboxSet
            namespace (str, optional): The namespace of the resource.
                                     Defaults to the client's namespace.

        Returns:
            int: The current number of replicas
        """
        sandboxset = self.get_sandboxset(name, namespace)
        spec = sandboxset.get('spec', {})
        return spec.get('replicas', 0)
