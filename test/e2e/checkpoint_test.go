//go:build e2e

package e2e

import (
	"context"
	"testing"

	agentsv1alpha1 "github.com/openkruise/agents-api/agents/v1alpha1"
	metav1 "k8s.io/apimachinery/pkg/apis/meta/v1"
)

// TestCheckpointCRUD tests Create, Get, List, Update, Delete for Checkpoint
func TestCheckpointCRUD(t *testing.T) {
	ctx, cancel := context.WithTimeout(context.Background(), testTimeout)
	defer cancel()

	clientset := getClientset(t)
	checkpointClient := clientset.AgentsV1alpha1().Checkpoints(testNamespace)

	name := generateTestName("test-checkpoint")

	// Create
	t.Logf("Creating Checkpoint: %s", name)
	sandboxName := "test-sandbox"
	podName := "test-pod"
	checkpoint := &agentsv1alpha1.Checkpoint{
		ObjectMeta: metav1.ObjectMeta{
			Name:      name,
			Namespace: testNamespace,
			Labels: map[string]string{
				"app":        "e2e-test",
				"managed-by": "go-sdk",
			},
		},
		Spec: agentsv1alpha1.CheckpointSpec{
			SandboxName: &sandboxName,
			PodName:     &podName,
		},
	}

	created, err := checkpointClient.Create(ctx, checkpoint, metav1.CreateOptions{})
	if err != nil {
		t.Fatalf("Failed to create Checkpoint: %v", err)
	}
	t.Logf("Checkpoint created: %s", created.Name)

	defer func() {
		t.Logf("Cleaning up Checkpoint: %s", name)
		_ = checkpointClient.Delete(ctx, name, metav1.DeleteOptions{})
	}()

	// Get
	got, err := checkpointClient.Get(ctx, name, metav1.GetOptions{})
	if err != nil {
		t.Fatalf("Failed to get Checkpoint: %v", err)
	}
	if got.Spec.SandboxName == nil || *got.Spec.SandboxName != sandboxName {
		t.Fatalf("Expected sandboxName=%s, got %v", sandboxName, got.Spec.SandboxName)
	}
	t.Logf("Get Checkpoint OK: %s, sandboxName=%s", got.Name, *got.Spec.SandboxName)

	// List
	list, err := checkpointClient.List(ctx, metav1.ListOptions{
		LabelSelector: "app=e2e-test",
	})
	if err != nil {
		t.Fatalf("Failed to list Checkpoints: %v", err)
	}
	if len(list.Items) < 1 {
		t.Fatalf("Expected at least 1 Checkpoint, got %d", len(list.Items))
	}
	t.Logf("List Checkpoints OK: found %d items", len(list.Items))

	// Update
	t.Log("Updating Checkpoint...")
	got.Labels["updated"] = "true"
	updated, err := checkpointClient.Update(ctx, got, metav1.UpdateOptions{})
	if err != nil {
		t.Fatalf("Failed to update Checkpoint: %v", err)
	}
	if updated.Labels["updated"] != "true" {
		t.Fatalf("Expected label updated=true after update")
	}
	t.Logf("Update Checkpoint OK: %s", updated.Name)

	// Delete
	t.Log("Deleting Checkpoint...")
	err = checkpointClient.Delete(ctx, name, metav1.DeleteOptions{})
	if err != nil {
		t.Fatalf("Failed to delete Checkpoint: %v", err)
	}
	t.Logf("Delete Checkpoint OK: %s", name)

	// Verify deletion
	_, err = checkpointClient.Get(ctx, name, metav1.GetOptions{})
	if err == nil {
		t.Fatalf("Checkpoint should be deleted but still exists")
	}
	t.Log("Verified Checkpoint deleted successfully")
}
