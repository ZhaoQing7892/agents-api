"""
Unit tests for SandboxSetClient
"""

import pytest
import unittest
from unittest.mock import Mock, patch, MagicMock
from kubernetes.client.rest import ApiException

from agents_api_client import SandboxSetClient
from agents_api_client.exceptions import SandboxSetNotFoundException


class TestSandboxSetClient(unittest.TestCase):
    """Test cases for SandboxSetClient class"""

    def setUp(self):
        """Setup test fixtures before each test method."""
        # Mock Kubernetes configuration loading
        self.patcher_load_config = patch('kubernetes.config.load_kube_config')
        self.patcher_incluster_config = patch('kubernetes.config.load_incluster_config')
        self.patcher_custom_api = patch('kubernetes.client.CustomObjectsApi')

        self.mock_load_config = self.patcher_load_config.start()
        self.mock_incluster_config = self.patcher_incluster_config.start()
        self.mock_custom_api_class = self.patcher_custom_api.start()

        self.mock_custom_api = Mock()
        self.mock_custom_api_class.return_value = self.mock_custom_api

        self.client = SandboxSetClient(namespace="test-namespace")

    def tearDown(self):
        """Clean up after each test method."""
        self.patcher_load_config.stop()
        self.patcher_incluster_config.stop()
        self.patcher_custom_api.stop()

    def test_create_sandboxset(self):
        """Test creating a sandboxset."""
        # Arrange
        mock_response = {
            "apiVersion": "agents.kruise.io/v1alpha1",
            "kind": "SandboxSet",
            "metadata": {"name": "test-sandboxset", "namespace": "test-namespace"},
            "spec": {"replicas": 2}
        }
        self.mock_custom_api.create_namespaced_custom_object.return_value = mock_response

        sandboxset_body = {
            "apiVersion": "agents.kruise.io/v1alpha1",
            "kind": "SandboxSet",
            "metadata": {"name": "test-sandboxset"},
            "spec": {"replicas": 2}
        }

        # Act
        result = self.client.create_sandboxset(sandboxset_body)

        # Assert
        self.assertEqual(result, mock_response)
        self.mock_custom_api.create_namespaced_custom_object.assert_called_once_with(
            group="agents.kruise.io",
            version="v1alpha1",
            namespace="test-namespace",
            plural="sandboxsets",
            body=sandboxset_body
        )

    def test_get_sandboxset_success(self):
        """Test getting an existing sandboxset."""
        # Arrange
        mock_response = {
            "apiVersion": "agents.kruise.io/v1alpha1",
            "kind": "SandboxSet",
            "metadata": {"name": "existing-sandboxset", "namespace": "test-namespace"},
            "spec": {"replicas": 2},
            "status": {"replicas": 1}
        }
        self.mock_custom_api.get_namespaced_custom_object.return_value = mock_response

        # Act
        result = self.client.get_sandboxset("existing-sandboxset")

        # Assert
        self.assertEqual(result, mock_response)

    def test_get_sandboxset_not_found(self):
        """Test getting a non-existing sandboxset raises SandboxSetNotFoundException."""
        # Arrange
        api_exception = ApiException(status=404)
        self.mock_custom_api.get_namespaced_custom_object.side_effect = api_exception

        # Act & Assert
        with self.assertRaises(SandboxSetNotFoundException):
            self.client.get_sandboxset("non-existing-sandboxset")

    def test_get_sandboxset_with_namespace_override(self):
        """Test getting a sandboxset with namespace override."""
        # Arrange
        mock_response = {
            "apiVersion": "agents.kruise.io/v1alpha1",
            "kind": "SandboxSet",
            "metadata": {"name": "existing-sandboxset", "namespace": "other-namespace"},
            "spec": {"replicas": 2}
        }
        self.mock_custom_api.get_namespaced_custom_object.return_value = mock_response

        # Act
        result = self.client.get_sandboxset("existing-sandboxset", namespace="other-namespace")

        # Assert
        self.assertEqual(result, mock_response)
        self.mock_custom_api.get_namespaced_custom_object.assert_called_once_with(
            group="agents.kruise.io",
            version="v1alpha1",
            namespace="other-namespace",
            plural="sandboxsets",
            name="existing-sandboxset"
        )

    def test_update_sandboxset(self):
        """Test updating a sandboxset."""
        # Arrange
        mock_response = {
            "apiVersion": "agents.kruise.io/v1alpha1",
            "kind": "SandboxSet",
            "metadata": {"name": "updated-sandboxset", "namespace": "test-namespace"},
            "spec": {"replicas": 5}
        }
        self.mock_custom_api.patch_namespaced_custom_object.return_value = mock_response

        update_body = {
            "apiVersion": "agents.kruise.io/v1alpha1",
            "kind": "SandboxSet",
            "metadata": {"name": "updated-sandboxset"},
            "spec": {"replicas": 5}
        }

        # Act
        result = self.client.update_sandboxset("updated-sandboxset", update_body)

        # Assert
        self.assertEqual(result, mock_response)
        self.mock_custom_api.patch_namespaced_custom_object.assert_called_once_with(
            group="agents.kruise.io",
            version="v1alpha1",
            namespace="test-namespace",
            plural="sandboxsets",
            name="updated-sandboxset",
            body=update_body
        )

    def test_update_sandboxset_status(self):
        """Test updating a sandboxset status."""
        # Arrange
        mock_response = {
            "apiVersion": "agents.kruise.io/v1alpha1",
            "kind": "SandboxSet",
            "metadata": {"name": "test-sandboxset", "namespace": "test-namespace"},
            "spec": {"replicas": 2},
            "status": {"replicas": 2, "readyReplicas": 2}
        }
        self.mock_custom_api.patch_namespaced_custom_object_status.return_value = mock_response

        status_body = {
            "status": {
                "replicas": 2,
                "readyReplicas": 2
            }
        }

        # Act
        result = self.client.update_sandboxset_status("test-sandboxset", status_body)

        # Assert
        self.assertEqual(result, mock_response)
        self.mock_custom_api.patch_namespaced_custom_object_status.assert_called_once_with(
            group="agents.kruise.io",
            version="v1alpha1",
            namespace="test-namespace",
            plural="sandboxsets",
            name="test-sandboxset",
            body=status_body
        )

    def test_delete_sandboxset(self):
        """Test deleting a sandboxset."""
        # Arrange
        mock_response = {"status": "Success"}
        self.mock_custom_api.delete_namespaced_custom_object.return_value = mock_response

        # Act
        result = self.client.delete_sandboxset("test-sandboxset")

        # Assert
        self.assertEqual(result, mock_response)
        self.mock_custom_api.delete_namespaced_custom_object.assert_called_once_with(
            group="agents.kruise.io",
            version="v1alpha1",
            namespace="test-namespace",
            plural="sandboxsets",
            name="test-sandboxset",
            body=unittest.mock.ANY  # Use ANY to match any body parameter
        )

    def test_list_sandboxsets(self):
        """Test listing sandboxsets."""
        # Arrange
        mock_response = {
            "apiVersion": "agents.kruise.io/v1alpha1",
            "kind": "SandboxSetList",
            "items": [
                {
                    "apiVersion": "agents.kruise.io/v1alpha1",
                    "kind": "SandboxSet",
                    "metadata": {"name": "sandboxset-1", "namespace": "test-namespace"},
                    "spec": {"replicas": 2}
                },
                {
                    "apiVersion": "agents.kruise.io/v1alpha1",
                    "kind": "SandboxSet",
                    "metadata": {"name": "sandboxset-2", "namespace": "test-namespace"},
                    "spec": {"replicas": 3}
                }
            ]
        }
        self.mock_custom_api.list_namespaced_custom_object.return_value = mock_response

        # Act
        result = self.client.list_sandboxsets()

        # Assert
        self.assertEqual(result, mock_response)
        self.mock_custom_api.list_namespaced_custom_object.assert_called_once_with(
            group="agents.kruise.io",
            version="v1alpha1",
            namespace="test-namespace",
            plural="sandboxsets",
            label_selector=None,
            field_selector=None
        )

    def test_list_sandboxsets_with_filters(self):
        """Test listing sandboxsets with label and field selectors."""
        # Arrange
        mock_response = {
            "apiVersion": "agents.kruise.io/v1alpha1",
            "kind": "SandboxSetList",
            "items": []
        }
        self.mock_custom_api.list_namespaced_custom_object.return_value = mock_response

        # Act
        result = self.client.list_sandboxsets(
            label_selector="app=test",
            field_selector="metadata.name=test-sandboxset"
        )

        # Assert
        self.assertEqual(result, mock_response)
        self.mock_custom_api.list_namespaced_custom_object.assert_called_once_with(
            group="agents.kruise.io",
            version="v1alpha1",
            namespace="test-namespace",
            plural="sandboxsets",
            label_selector="app=test",
            field_selector="metadata.name=test-sandboxset"
        )

    def test_scale_sandboxset(self):
        """Test scaling a sandboxset."""
        # Arrange
        mock_response = {
            "apiVersion": "agents.kruise.io/v1alpha1",
            "kind": "SandboxSet",
            "metadata": {"name": "scaled-sandboxset", "namespace": "test-namespace"},
            "spec": {"replicas": 5}
        }
        self.mock_custom_api.patch_namespaced_custom_object.return_value = mock_response

        # Act
        result = self.client.scale_sandboxset("scaled-sandboxset", 5)

        # Assert
        self.assertEqual(result, mock_response)

        # Verify that the correct patch was applied
        called_args = self.mock_custom_api.patch_namespaced_custom_object.call_args
        self.assertEqual(called_args[1]["group"], "agents.kruise.io")
        self.assertEqual(called_args[1]["version"], "v1alpha1")
        self.assertEqual(called_args[1]["namespace"], "test-namespace")
        self.assertEqual(called_args[1]["plural"], "sandboxsets")
        self.assertEqual(called_args[1]["name"], "scaled-sandboxset")
        self.assertEqual(called_args[1]["body"]["spec"]["replicas"], 5)

    def test_get_sandboxset_replicas(self):
        """Test getting the current number of replicas for a sandboxset."""
        # Arrange
        mock_response = {
            "apiVersion": "agents.kruise.io/v1alpha1",
            "kind": "SandboxSet",
            "metadata": {"name": "test-sandboxset", "namespace": "test-namespace"},
            "spec": {"replicas": 3}
        }
        self.mock_custom_api.get_namespaced_custom_object.return_value = mock_response

        # Act
        result = self.client.get_sandboxset_replicas("test-sandboxset")

        # Assert
        self.assertEqual(result, 3)

    def test_get_sandboxset_replicas_default_zero(self):
        """Test getting replicas when spec doesn't have replicas key."""
        # Arrange
        mock_response = {
            "apiVersion": "agents.kruise.io/v1alpha1",
            "kind": "SandboxSet",
            "metadata": {"name": "test-sandboxset", "namespace": "test-namespace"},
            "spec": {}  # No replicas key
        }
        self.mock_custom_api.get_namespaced_custom_object.return_value = mock_response

        # Act
        result = self.client.get_sandboxset_replicas("test-sandboxset")

        # Assert
        self.assertEqual(result, 0)

    def test_patch_sandboxset(self):
        """Test patching a sandboxset."""
        # Arrange
        mock_response = {
            "apiVersion": "agents.kruise.io/v1alpha1",
            "kind": "SandboxSet",
            "metadata": {"name": "patched-sandboxset", "namespace": "test-namespace"},
            "spec": {"replicas": 4, "paused": True}
        }
        self.mock_custom_api.patch_namespaced_custom_object.return_value = mock_response

        patch_body = {
            "spec": {
                "paused": True
            }
        }

        # Act
        result = self.client.patch_sandboxset("patched-sandboxset", patch_body)

        # Assert
        self.assertEqual(result, mock_response)
        self.mock_custom_api.patch_namespaced_custom_object.assert_called_once_with(
            group="agents.kruise.io",
            version="v1alpha1",
            namespace="test-namespace",
            plural="sandboxsets",
            name="patched-sandboxset",
            body=patch_body,
            content_type='application/merge-patch+json'
        )

    def test_delete_collection_sandboxsets(self):
        """Test deleting a collection of sandboxsets."""
        # Arrange
        mock_response = {"status": "Success"}
        self.mock_custom_api.delete_collection_namespaced_custom_object.return_value = mock_response

        # Act
        result = self.client.delete_collection_sandboxsets(label_selector="app=test")

        # Assert
        self.assertEqual(result, mock_response)
        self.mock_custom_api.delete_collection_namespaced_custom_object.assert_called_once_with(
            group="agents.kruise.io",
            version="v1alpha1",
            namespace="test-namespace",
            plural="sandboxsets",
            label_selector="app=test",
            field_selector=None
        )
