package e2e

import (
	"context"
	"fmt"
	"time"

	agentsv1alpha1 "github.com/openkruise/agents-api/agents/v1alpha1"
	corev1 "k8s.io/api/core/v1"
	metav1 "k8s.io/apimachinery/pkg/apis/meta/v1"
	"k8s.io/apimachinery/pkg/types"

	. "github.com/onsi/ginkgo/v2"
	. "github.com/onsi/gomega"
)

var _ = Describe("SandboxSet", func() {
	var (
		sandboxSet *agentsv1alpha1.SandboxSet
		ctx        = context.Background()
	)

	BeforeEach(func() {
		sandboxSet = &agentsv1alpha1.SandboxSet{
			ObjectMeta: metav1.ObjectMeta{
				Name:      fmt.Sprintf("test-sandboxset-%d", time.Now().UnixNano()),
				Namespace: Namespace,
				Labels: map[string]string{
					"app":        "e2e-test",
					"managed-by": "ginkgo",
				},
			},
			Spec: agentsv1alpha1.SandboxSetSpec{
				Replicas: int32(2),
				EmbeddedSandboxTemplate: agentsv1alpha1.EmbeddedSandboxTemplate{
					Template: &corev1.PodTemplateSpec{
						ObjectMeta: metav1.ObjectMeta{
							Labels: map[string]string{"app": "sandboxset-test"},
						},
						Spec: corev1.PodSpec{
							RestartPolicy: corev1.RestartPolicyNever,
							Containers: []corev1.Container{
								{
									Name:    "main",
									Image:   "busybox:latest",
									Command: []string{"sh", "-c", "sleep 3600"},
								},
							},
						},
					},
				},
			},
		}
	})

	AfterEach(func() {
		By("Cleaning up SandboxSet")
		_ = k8sClient.Delete(ctx, sandboxSet)
	})

	Context("creation and scale up", func() {
		It("should create SandboxSet and scale from 2 to 3 replicas", func() {
			By("Creating SandboxSet")
			Expect(k8sClient.Create(ctx, sandboxSet)).To(Succeed())

			By("Verifying the SandboxSet is created")
			Eventually(func() error {
				return k8sClient.Get(ctx, types.NamespacedName{
					Name:      sandboxSet.Name,
					Namespace: sandboxSet.Namespace,
				}, sandboxSet)
			}, time.Minute*10, time.Millisecond*500).Should(Succeed())

			By("Verifying the SandboxSet AvailableReplicas reaches 2")
			Eventually(func() int32 {
				_ = k8sClient.Get(ctx, types.NamespacedName{
					Name:      sandboxSet.Name,
					Namespace: sandboxSet.Namespace,
				}, sandboxSet)
				return sandboxSet.Status.AvailableReplicas
			}, time.Second*30, time.Millisecond*500).Should(Equal(int32(2)))

			By("Scaling up to 3 replicas")
			Expect(k8sClient.Get(ctx, types.NamespacedName{
				Name:      sandboxSet.Name,
				Namespace: sandboxSet.Namespace,
			}, sandboxSet)).To(Succeed())
			sandboxSet.Spec.Replicas = int32(3)
			Expect(k8sClient.Update(ctx, sandboxSet)).To(Succeed())

			By("Verifying the SandboxSet AvailableReplicas reaches 3")
			Eventually(func() int32 {
				_ = k8sClient.Get(ctx, types.NamespacedName{
					Name:      sandboxSet.Name,
					Namespace: sandboxSet.Namespace,
				}, sandboxSet)
				return sandboxSet.Status.AvailableReplicas
			}, time.Second*30, time.Millisecond*500).Should(Equal(int32(3)))
		})
	})
})
