//go:build e2e
// +build e2e

package e2e

import (
	"context"
	"testing"

	agentsv1alpha1 "github.com/openkruise/agents-api/agents/v1alpha1"
	metav1 "k8s.io/apimachinery/pkg/apis/meta/v1"
)

// TestSandboxSetCRUD tests Create, Get, List, Update, Delete for SandboxSet
func TestSandboxSetCRUD(t *testing.T) {
	ctx, cancel := context.WithTimeout(context.Background(), testTimeout)
	defer cancel()

	clientset := getClientset(t)
	sandboxSetClient := clientset.AgentsV1alpha1().SandboxSets(testNamespace)

	name := generateTestName("test-sandboxset")

	// Create
	t.Logf("Creating SandboxSet: %s", name)
	replicas := int32(1)
	sandboxSet := &agentsv1alpha1.SandboxSet{
		ObjectMeta: metav1.ObjectMeta{
			Name:      name,
			Namespace: testNamespace,
			Labels: map[string]string{
				"app":        "e2e-test",
				"managed-by": "go-sdk",
			},
		},
		Spec: agentsv1alpha1.SandboxSetSpec{
			Replicas: replicas,
			EmbeddedSandboxTemplate: agentsv1alpha1.EmbeddedSandboxTemplate{
				Template: basePodTemplateSpec(map[string]string{"app": "sandboxset-test"}),
			},
		},
	}

	created, err := sandboxSetClient.Create(ctx, sandboxSet, metav1.CreateOptions{})
	if err != nil {
		t.Fatalf("Failed to create SandboxSet: %v", err)
	}
	t.Logf("SandboxSet created: %s", created.Name)

	defer func() {
		t.Logf("Cleaning up SandboxSet: %s", name)
		_ = sandboxSetClient.Delete(ctx, name, metav1.DeleteOptions{})
	}()

	// Get
	got, err := sandboxSetClient.Get(ctx, name, metav1.GetOptions{})
	if err != nil {
		t.Fatalf("Failed to get SandboxSet: %v", err)
	}
	if got.Spec.Replicas != 1 {
		t.Fatalf("Expected replicas=1, got %d", got.Spec.Replicas)
	}
	t.Logf("Get SandboxSet OK: %s, replicas=%d", got.Name, got.Spec.Replicas)

	// List
	list, err := sandboxSetClient.List(ctx, metav1.ListOptions{
		LabelSelector: "app=e2e-test",
	})
	if err != nil {
		t.Fatalf("Failed to list SandboxSets: %v", err)
	}
	if len(list.Items) < 1 {
		t.Fatalf("Expected at least 1 SandboxSet, got %d", len(list.Items))
	}
	t.Logf("List SandboxSets OK: found %d items", len(list.Items))

	// Update
	t.Log("Updating SandboxSet...")
	got.Labels["updated"] = "true"
	updated, err := sandboxSetClient.Update(ctx, got, metav1.UpdateOptions{})
	if err != nil {
		t.Fatalf("Failed to update SandboxSet: %v", err)
	}
	if updated.Labels["updated"] != "true" {
		t.Fatalf("Expected label updated=true after update")
	}
	t.Logf("Update SandboxSet OK: %s", updated.Name)

	// Delete
	t.Log("Deleting SandboxSet...")
	err = sandboxSetClient.Delete(ctx, name, metav1.DeleteOptions{})
	if err != nil {
		t.Fatalf("Failed to delete SandboxSet: %v", err)
	}
	t.Logf("Delete SandboxSet OK: %s", name)

	// Verify deletion
	_, err = sandboxSetClient.Get(ctx, name, metav1.GetOptions{})
	if err == nil {
		t.Fatalf("SandboxSet should be deleted but still exists")
	}
	t.Log("Verified SandboxSet deleted successfully")
}
