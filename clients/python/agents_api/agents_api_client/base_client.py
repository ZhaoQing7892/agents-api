"""
Base client module for Agents API Python Client
"""

import time
import logging
from abc import ABC
from typing import Dict, Any, Optional
from kubernetes import client, config, watch

logger = logging.getLogger(__name__)


class BaseCustomResourceClient(ABC):
    """
    Abstract base class for custom resource clients.
    """

    def __init__(self, api_group: str, api_version: str, plural: str, namespace: str):
        """
        Initialize the base client.

        Args:
            api_group (str): API group for the custom resource
            api_version (str): API version for the custom resource
            plural (str): Plural form of the resource name
            namespace (str): Namespace for the resources
        """
        self.api_group = api_group
        self.api_version = api_version
        self.plural = plural
        self.namespace = namespace

        logger.info(f"Initializing {self.__class__.__name__} with namespace: {namespace}")

        # Load Kubernetes configuration
        try:
            config.load_incluster_config()
            logger.info("Loaded in-cluster Kubernetes configuration")
        except config.ConfigException:
            config.load_kube_config()
            logger.info("Loaded local Kubernetes configuration")

        self.custom_objects_api = client.CustomObjectsApi()

    def create_resource(self, body: Dict[str, Any], namespace: Optional[str] = None) -> Dict[str, Any]:
        """
        Create a new custom resource.

        Args:
            body (dict): The resource definition
            namespace (str, optional): The namespace to create the resource in.
                                     Defaults to the client's namespace.

        Returns:
            dict: The created resource

        Raises:
            ApiException: If the API call fails
        """
        ns = namespace or self.namespace
        return self.custom_objects_api.create_namespaced_custom_object(
            group=self.api_group,
            version=self.api_version,
            namespace=ns,
            plural=self.plural,
            body=body
        )

    def get_resource(self, name: str, namespace: Optional[str] = None) -> Dict[str, Any]:
        """
        Get a specific custom resource by name.

        Args:
            name (str): The name of the resource
            namespace (str, optional): The namespace of the resource.
                                     Defaults to the client's namespace.

        Returns:
            dict: The resource

        Raises:
            ApiException: If the API call fails
        """
        ns = namespace or self.namespace
        return self.custom_objects_api.get_namespaced_custom_object(
            group=self.api_group,
            version=self.api_version,
            namespace=ns,
            plural=self.plural,
            name=name
        )

    def update_resource(self, name: str, body: Dict[str, Any], namespace: Optional[str] = None) -> Dict[str, Any]:
        """
        Update an existing custom resource.

        Args:
            name (str): The name of the resource to update
            body (dict): The updated resource definition
            namespace (str, optional): The namespace of the resource.
                                     Defaults to the client's namespace.

        Returns:
            dict: The updated resource

        Raises:
            ApiException: If the API call fails
        """
        ns = namespace or self.namespace
        return self.custom_objects_api.patch_namespaced_custom_object(
            group=self.api_group,
            version=self.api_version,
            namespace=ns,
            plural=self.plural,
            name=name,
            body=body
        )

    def update_resource_status(self, name: str, body: Dict[str, Any], namespace: Optional[str] = None) -> Dict[str, Any]:
        """
        Update the status of a custom resource.

        Args:
            name (str): The name of the resource
            body (dict): The updated status portion of the resource
            namespace (str, optional): The namespace of the resource.
                                     Defaults to the client's namespace.

        Returns:
            dict: The updated resource with status

        Raises:
            ApiException: If the API call fails
        """
        ns = namespace or self.namespace
        return self.custom_objects_api.patch_namespaced_custom_object_status(
            group=self.api_group,
            version=self.api_version,
            namespace=ns,
            plural=self.plural,
            name=name,
            body=body
        )

    def delete_resource(self, name: str, namespace: Optional[str] = None,
                        grace_period_seconds: Optional[int] = None) -> Dict[str, Any]:
        """
        Delete a specific custom resource.

        Args:
            name (str): The name of the resource to delete
            namespace (str, optional): The namespace of the resource.
                                     Defaults to the client's namespace.
            grace_period_seconds (int, optional): Grace period for deletion in seconds

        Returns:
            dict: The deletion status

        Raises:
            ApiException: If the API call fails
        """
        ns = namespace or self.namespace
        body = client.V1DeleteOptions()
        if grace_period_seconds is not None:
            body.grace_period_seconds = grace_period_seconds

        return self.custom_objects_api.delete_namespaced_custom_object(
            group=self.api_group,
            version=self.api_version,
            namespace=ns,
            plural=self.plural,
            name=name,
            body=body
        )

    def list_resources(self, namespace: Optional[str] = None,
                       label_selector: Optional[str] = None,
                       field_selector: Optional[str] = None) -> Dict[str, Any]:
        """
        List all custom resources in a namespace.

        Args:
            namespace (str, optional): The namespace to list resources from.
                                     Defaults to the client's namespace.
            label_selector (str, optional): Label selector for filtering
            field_selector (str, optional): Field selector for filtering

        Returns:
            dict: A list of custom resources
        """
        ns = namespace or self.namespace
        return self.custom_objects_api.list_namespaced_custom_object(
            group=self.api_group,
            version=self.api_version,
            namespace=ns,
            plural=self.plural,
            label_selector=label_selector,
            field_selector=field_selector
        )

    def watch_resources(self, namespace: Optional[str] = None,
                        label_selector: Optional[str] = None,
                        field_selector: Optional[str] = None,
                        timeout_seconds: Optional[int] = None):
        """
        Watch for changes to custom resources.

        Args:
            namespace (str, optional): The namespace to watch resources in.
                                     Defaults to the client's namespace.
            label_selector (str, optional): Label selector for filtering
            field_selector (str, optional): Field selector for filtering
            timeout_seconds (int, optional): Timeout for the watch in seconds

        Yields:
            dict: Events related to custom resources
        """
        ns = namespace or self.namespace
        watcher = watch.Watch()

        for event in watcher.stream(
                self.custom_objects_api.list_namespaced_custom_object,
                group=self.api_group,
                version=self.api_version,
                namespace=ns,
                plural=self.plural,
                label_selector=label_selector,
                field_selector=field_selector,
                timeout_seconds=timeout_seconds
        ):
            yield event
            # Break if the event type is ERROR to prevent infinite loop
            if event.get("type") == "ERROR":
                break

    def patch_resource(self, name: str, body: Dict[str, Any],
                       namespace: Optional[str] = None,
                       content_type: str = 'application/merge-patch+json') -> Dict[str, Any]:
        """
        Patch a specific custom resource.

        Args:
            name (str): The name of the resource to patch
            body (dict): The patch body
            namespace (str, optional): The namespace of the resource.
                                     Defaults to the client's namespace.
            content_type (str): The content type for the patch operation
                               Defaults to 'application/merge-patch+json'

        Returns:
            dict: The patched resource

        Raises:
            ApiException: If the API call fails
        """
        ns = namespace or self.namespace
        return self.custom_objects_api.patch_namespaced_custom_object(
            group=self.api_group,
            version=self.api_version,
            namespace=ns,
            plural=self.plural,
            name=name,
            body=body,
            content_type=content_type
        )

    def delete_collection_resources(self, namespace: Optional[str] = None,
                                    label_selector: Optional[str] = None,
                                    field_selector: Optional[str] = None) -> Dict[str, Any]:
        """
        Delete a collection of custom resources.

        Args:
            namespace (str, optional): The namespace to delete resources from.
                                     Defaults to the client's namespace.
            label_selector (str, optional): Label selector for filtering
            field_selector (str, optional): Field selector for filtering

        Returns:
            dict: The deletion status
        """
        ns = namespace or self.namespace
        return self.custom_objects_api.delete_collection_namespaced_custom_object(
            group=self.api_group,
            version=self.api_version,
            namespace=ns,
            plural=self.plural,
            label_selector=label_selector,
            field_selector=field_selector
        )

    def wait_for_resource_condition(self, name: str, condition_type: str,
                                    condition_status: str = "True",
                                    timeout: int = 300,
                                    namespace: Optional[str] = None) -> Dict[str, Any]:
        """
        Wait for a custom resource to reach a specific condition status.

        Args:
            name (str): The name of the resource
            condition_type (str): The condition type to wait for
            condition_status (str): The expected status of the condition.
                                   Defaults to "True".
            timeout (int): Maximum time to wait in seconds. Defaults to 300.
            namespace (str, optional): The namespace of the resource.
                                     Defaults to the client's namespace.

        Returns:
            dict: The resource when condition is met

        Raises:
            TimeoutError: If the condition is not met within the timeout
        """
        ns = namespace or self.namespace
        end_time = time.time() + timeout

        for event in self.watch_resources(namespace=ns,
                                          field_selector=f"metadata.name={name}"):
            resource = event['object']

            # Check if the specific condition exists with the expected status
            if 'status' in resource and 'conditions' in resource['status']:
                for condition in resource['status']['conditions']:
                    if (condition.get('type') == condition_type and
                            condition.get('status') == condition_status):
                        return resource

            if time.time() > end_time:
                raise TimeoutError(f"Timeout waiting for condition {condition_type}={condition_status} "
                                   f"for resource {name} in namespace {ns}")

        raise TimeoutError(f"Unable to find resource {name} in namespace {ns}")
