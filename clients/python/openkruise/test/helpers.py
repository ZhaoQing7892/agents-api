"""Shared constants and helper functions for e2e tests."""

import time
from kubernetes import client

# Constants
GROUP = "agents.kruise.io"
VERSION = "v1alpha1"
NAMESPACE = "default"
TIMEOUT_LONG = 120
TIMEOUT_SHORT = 30
POLL_INTERVAL = 1


def wait_for_condition(get_func, condition_func, timeout=TIMEOUT_LONG, interval=POLL_INTERVAL):
    """
    Wait for a resource to meet a certain condition.

    Args:
        get_func: Function that returns the current resource state (dict)
        condition_func: Function that takes the resource dict and returns True when condition is met
        timeout: Maximum time to wait in seconds
        interval: Polling interval in seconds

    Returns:
        The resource dict when condition is met

    Raises:
        TimeoutError: If condition is not met within timeout
    """
    start_time = time.time()
    while time.time() - start_time < timeout:
        try:
            resource = get_func()
            if condition_func(resource):
                return resource
        except Exception:
            pass
        time.sleep(interval)
    raise TimeoutError(f"Condition not met within {timeout} seconds")


def wait_for_deletion(get_func, timeout=TIMEOUT_SHORT, interval=POLL_INTERVAL):
    """
    Wait for a resource to be deleted.

    Args:
        get_func: Function that returns the current resource state (dict)
        timeout: Maximum time to wait in seconds
        interval: Polling interval in seconds

    Raises:
        TimeoutError: If resource still exists after timeout
    """
    start_time = time.time()
    while time.time() - start_time < timeout:
        try:
            get_func()
        except client.exceptions.ApiException as e:
            if e.status == 404:
                return
        except Exception:
            return
        time.sleep(interval)
    raise TimeoutError(f"Resource not deleted within {timeout} seconds")
