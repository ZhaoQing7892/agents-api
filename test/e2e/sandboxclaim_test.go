package e2e

import (
	"context"
	"fmt"
	"time"

	agentsv1alpha1 "github.com/openkruise/agents-api/agents/v1alpha1"
	corev1 "k8s.io/api/core/v1"
	metav1 "k8s.io/apimachinery/pkg/apis/meta/v1"
	"k8s.io/apimachinery/pkg/types"
	"k8s.io/utils/ptr"

	. "github.com/onsi/ginkgo/v2"
	. "github.com/onsi/gomega"
)

var _ = Describe("SandboxClaim", func() {
	var (
		ctx          = context.Background()
		sandboxSet   *agentsv1alpha1.SandboxSet
		sandboxClaim *agentsv1alpha1.SandboxClaim
	)

	Context("basic claim flow", func() {
		BeforeEach(func() {
			By("Creating SandboxSet pool")
			sandboxSet = &agentsv1alpha1.SandboxSet{
				ObjectMeta: metav1.ObjectMeta{
					Name:      fmt.Sprintf("test-pool-%d", time.Now().UnixNano()),
					Namespace: Namespace,
					Labels: map[string]string{
						"app":        "e2e-test",
						"managed-by": "ginkgo",
					},
				},
				Spec: agentsv1alpha1.SandboxSetSpec{
					Replicas: int32(3),
					EmbeddedSandboxTemplate: agentsv1alpha1.EmbeddedSandboxTemplate{
						Template: &corev1.PodTemplateSpec{
							ObjectMeta: metav1.ObjectMeta{
								Labels: map[string]string{"app": "pool-test"},
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
			Expect(k8sClient.Create(ctx, sandboxSet)).To(Succeed())

			By("Waiting for pool to be ready")
			Eventually(func() int32 {
				_ = k8sClient.Get(ctx, types.NamespacedName{
					Name:      sandboxSet.Name,
					Namespace: sandboxSet.Namespace,
				}, sandboxSet)
				return sandboxSet.Status.AvailableReplicas
			}, time.Minute*2, time.Second).Should(Equal(int32(3)))
		})

		AfterEach(func() {
			By("Cleaning up SandboxClaim")
			if sandboxClaim != nil {
				_ = k8sClient.Delete(ctx, sandboxClaim)
			}
			By("Cleaning up SandboxSet pool")
			if sandboxSet != nil {
				_ = k8sClient.Delete(ctx, sandboxSet)
			}
		})

		It("should create SandboxClaim and verify completion", func() {
			By("Creating SandboxClaim")
			sandboxClaim = &agentsv1alpha1.SandboxClaim{
				ObjectMeta: metav1.ObjectMeta{
					Name:      fmt.Sprintf("test-sandboxclaim-%d", time.Now().UnixNano()),
					Namespace: Namespace,
					Labels: map[string]string{
						"app":        "e2e-test",
						"managed-by": "ginkgo",
					},
				},
				Spec: agentsv1alpha1.SandboxClaimSpec{
					TemplateName: sandboxSet.Name,
					Replicas:     ptr.To(int32(1)),
				},
			}
			Expect(k8sClient.Create(ctx, sandboxClaim)).To(Succeed())

			By("Verifying the claim transitions to Completed phase")
			Eventually(func() agentsv1alpha1.SandboxClaimPhase {
				_ = k8sClient.Get(ctx, types.NamespacedName{
					Name:      sandboxClaim.Name,
					Namespace: sandboxClaim.Namespace,
				}, sandboxClaim)
				return sandboxClaim.Status.Phase
			}, time.Minute, time.Second).Should(Equal(agentsv1alpha1.SandboxClaimPhaseCompleted))

			By("Verifying claimedReplicas equals 1")
			Expect(sandboxClaim.Status.ClaimedReplicas).To(Equal(int32(1)))
		})
	})
})
