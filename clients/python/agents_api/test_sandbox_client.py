"""
Unit tests for SandboxClient
"""

import pytest
import unittest
from unittest.mock import Mock, patch
from agents_api_client import SandboxClient
from agents_api_client.exceptions import SandboxNotFoundException


class TestSandboxClient(unittest.TestCase):
    """Test cases for SandboxClient class"""

    def setUp(self):
        """Setup test fixtures before each test method."""
        self.patcher_load_config = patch('kubernetes.config.load_kube_config')
        self.patcher_custom_api = patch('kubernetes.client.CustomObjectsApi')

        self.mock_load_config = self.patcher_load_config.start()
        self.mock_custom_api_class = self.patcher_custom_api.start()

        self.mock_custom_api = Mock()
        self.mock_custom_api_class.return_value = self.mock_custom_api

        self.client = SandboxClient(namespace="test-namespace")

    def tearDown(self):
        """Clean up after each test method."""
        self.patcher_load_config.stop()
        self.patcher_custom_api.stop()

    def test_create_sandbox(self):
        """Test creating a sandbox."""
        # Arrange
        mock_response = {"metadata": {"name": "test-sandbox"}}
        self.mock_custom_api.create_namespaced_custom_object.return_value = mock_response

        sandbox_body = {
            "apiVersion": "agents.kruise.io/v1alpha1",
            "kind": "Sandbox",
            "metadata": {"name": "test-sandbox"},
            "spec": {}
        }

        # Act
        result = self.client.create_sandbox(sandbox_body)

        # Assert
        self.assertEqual(result, mock_response)
        self.mock_custom_api.create_namespaced_custom_object.assert_called_once_with(
            group="agents.kruise.io",
            version="v1alpha1",
            namespace="test-namespace",
            plural="sandboxes",
            body=sandbox_body
        )

    def test_get_sandbox_success(self):
        """Test getting an existing sandbox."""
        # Arrange
        mock_response = {
            "metadata": {"name": "existing-sandbox"},
            "spec": {},
            "status": {}
        }
        self.mock_custom_api.get_namespaced_custom_object.return_value = mock_response

        # Act
        result = self.client.get_sandbox("existing-sandbox")

        # Assert
        self.assertEqual(result, mock_response)

    def test_get_sandbox_not_found(self):
        """Test getting a non-existing sandbox raises exception."""
        from kubernetes.client.rest import ApiException
        self.mock_custom_api.get_namespaced_custom_object.side_effect = ApiException(status=404)

        # Act & Assert
        with self.assertRaises(SandboxNotFoundException):
            self.client.get_sandbox("non-existing-sandbox")

    def test_update_sandbox(self):
        """Test updating a sandbox."""
        # Arrange
        mock_response = {"metadata": {"name": "updated-sandbox"}}
        self.mock_custom_api.patch_namespaced_custom_object.return_value = mock_response

        update_body = {"spec": {"paused": True}}

        # Act
        result = self.client.update_sandbox("test-sandbox", update_body)

        # Assert
        self.assertEqual(result, mock_response)
        self.mock_custom_api.patch_namespaced_custom_object.assert_called_once_with(
            group="agents.kruise.io",
            version="v1alpha1",
            namespace="test-namespace",
            plural="sandboxes",
            name="test-sandbox",
            body=update_body
        )

    def test_delete_sandbox(self):
        """Test deleting a sandbox."""
        # Arrange
        mock_response = {"status": "Success"}
        self.mock_custom_api.delete_namespaced_custom_object.return_value = mock_response

        # Act
        result = self.client.delete_sandbox("test-sandbox")

        # Assert
        self.assertEqual(result, mock_response)
        self.mock_custom_api.delete_namespaced_custom_object.assert_called_once()
