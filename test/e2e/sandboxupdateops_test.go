//go:build e2e
// +build e2e

package e2e

import (
	"context"
	"encoding/json"
	"testing"

	agentsv1alpha1 "github.com/openkruise/agents-api/agents/v1alpha1"
	metav1 "k8s.io/apimachinery/pkg/apis/meta/v1"
	"k8s.io/apimachinery/pkg/runtime"
)

// TestSandboxUpdateOpsCRUD tests Create, Get, List, Update, Delete for SandboxUpdateOps
func TestSandboxUpdateOpsCRUD(t *testing.T) {
	ctx, cancel := context.WithTimeout(context.Background(), testTimeout)
	defer cancel()

	clientset := getClientset(t)
	sandboxUpdateOpsClient := clientset.AgentsV1alpha1().SandboxUpdateOps(testNamespace)

	name := generateTestName("test-sandboxupdateops")

	// Create patch data
	patchData := map[string]interface{}{
		"spec": map[string]interface{}{
			"template": map[string]interface{}{
				"spec": map[string]interface{}{
					"containers": []map[string]interface{}{
						{
							"name":  "main",
							"image": "busybox:1.36",
						},
					},
				},
			},
		},
	}
	patchJSON, err := json.Marshal(patchData)
	if err != nil {
		t.Fatalf("Failed to marshal patch data: %v", err)
	}

	// Create
	t.Logf("Creating SandboxUpdateOps: %s", name)
	sandboxUpdateOps := &agentsv1alpha1.SandboxUpdateOps{
		ObjectMeta: metav1.ObjectMeta{
			Name:      name,
			Namespace: testNamespace,
			Labels: map[string]string{
				"app":        "e2e-test",
				"managed-by": "go-sdk",
			},
		},
		Spec: agentsv1alpha1.SandboxUpdateOpsSpec{
			Selector: &metav1.LabelSelector{
				MatchLabels: map[string]string{
					"app": "sandbox-test",
				},
			},
			Patch: runtime.RawExtension{
				Raw: patchJSON,
			},
		},
	}

	created, err := sandboxUpdateOpsClient.Create(ctx, sandboxUpdateOps, metav1.CreateOptions{})
	if err != nil {
		t.Fatalf("Failed to create SandboxUpdateOps: %v", err)
	}
	t.Logf("SandboxUpdateOps created: %s", created.Name)

	defer func() {
		t.Logf("Cleaning up SandboxUpdateOps: %s", name)
		_ = sandboxUpdateOpsClient.Delete(ctx, name, metav1.DeleteOptions{})
	}()

	// Get
	got, err := sandboxUpdateOpsClient.Get(ctx, name, metav1.GetOptions{})
	if err != nil {
		t.Fatalf("Failed to get SandboxUpdateOps: %v", err)
	}
	if got.Spec.Selector == nil {
		t.Fatalf("Expected selector to be set")
	}
	t.Logf("Get SandboxUpdateOps OK: %s", got.Name)

	// List
	list, err := sandboxUpdateOpsClient.List(ctx, metav1.ListOptions{
		LabelSelector: "app=e2e-test",
	})
	if err != nil {
		t.Fatalf("Failed to list SandboxUpdateOps: %v", err)
	}
	if len(list.Items) < 1 {
		t.Fatalf("Expected at least 1 SandboxUpdateOps, got %d", len(list.Items))
	}
	t.Logf("List SandboxUpdateOps OK: found %d items", len(list.Items))

	// Update
	t.Log("Updating SandboxUpdateOps...")
	got.Labels["updated"] = "true"
	updated, err := sandboxUpdateOpsClient.Update(ctx, got, metav1.UpdateOptions{})
	if err != nil {
		t.Fatalf("Failed to update SandboxUpdateOps: %v", err)
	}
	if updated.Labels["updated"] != "true" {
		t.Fatalf("Expected label updated=true after update")
	}
	t.Logf("Update SandboxUpdateOps OK: %s", updated.Name)

	// Delete
	t.Log("Deleting SandboxUpdateOps...")
	err = sandboxUpdateOpsClient.Delete(ctx, name, metav1.DeleteOptions{})
	if err != nil {
		t.Fatalf("Failed to delete SandboxUpdateOps: %v", err)
	}
	t.Logf("Delete SandboxUpdateOps OK: %s", name)

	// Verify deletion
	_, err = sandboxUpdateOpsClient.Get(ctx, name, metav1.GetOptions{})
	if err == nil {
		t.Fatalf("SandboxUpdateOps should be deleted but still exists")
	}
	t.Log("Verified SandboxUpdateOps deleted successfully")
}
