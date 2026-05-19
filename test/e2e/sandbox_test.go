package e2e

import (
	"context"
	"fmt"
	"time"

	. "github.com/onsi/ginkgo/v2"
	. "github.com/onsi/gomega"
	agentsv1alpha1 "github.com/openkruise/agents-api/agents/v1alpha1"
	corev1 "k8s.io/api/core/v1"
	metav1 "k8s.io/apimachinery/pkg/apis/meta/v1"
	"k8s.io/apimachinery/pkg/types"
	"sigs.k8s.io/controller-runtime/pkg/client"
)

var _ = Describe("Sandbox", func() {
	var (
		sandbox *agentsv1alpha1.Sandbox
		ctx     = context.Background()
	)

	BeforeEach(func() {
		sandbox = &agentsv1alpha1.Sandbox{
			ObjectMeta: metav1.ObjectMeta{
				Name:      fmt.Sprintf("test-sandbox-%d", time.Now().UnixNano()),
				Namespace: Namespace,
				Labels: map[string]string{
					"app":        "e2e-test",
					"managed-by": "ginkgo",
				},
			},
			Spec: agentsv1alpha1.SandboxSpec{
				EmbeddedSandboxTemplate: agentsv1alpha1.EmbeddedSandboxTemplate{
					Template: &corev1.PodTemplateSpec{
						ObjectMeta: metav1.ObjectMeta{
							Labels: map[string]string{"app": "sandbox-test"},
						},
						Spec: corev1.PodSpec{
							RestartPolicy: corev1.RestartPolicyNever,
							Containers: []corev1.Container{
								{
									Name:  "main",
									Image: "busybox:latest",
									Command: []string{
										"sh", "-c", "sleep 3600",
									},
								},
							},
						},
					},
				},
			},
		}
	})

	AfterEach(func() {
		By("Cleaning up Sandbox")
		_ = k8sClient.Delete(ctx, sandbox)
	})

	Context("CRUD operations", func() {
		It("should create and verify sandbox", func() {
			By("Creating Sandbox")
			Expect(k8sClient.Create(ctx, sandbox)).To(Succeed())

			By("Verifying the sandbox is created")
			Eventually(func() error {
				return k8sClient.Get(ctx, types.NamespacedName{
					Name:      sandbox.Name,
					Namespace: sandbox.Namespace,
				}, sandbox)
			}, time.Minute*10, time.Millisecond*500).Should(Succeed())

			By("Verifying phase transitions to Running")
			Eventually(func() agentsv1alpha1.SandboxPhase {
				_ = k8sClient.Get(ctx, types.NamespacedName{
					Name:      sandbox.Name,
					Namespace: sandbox.Namespace,
				}, sandbox)
				return sandbox.Status.Phase
			}, time.Second*90, time.Millisecond*500).Should(Equal(agentsv1alpha1.SandboxRunning))
		})
	})

	Context("update and delete", func() {
		It("should update labels and delete", func() {
			By("Creating Sandbox")
			Expect(k8sClient.Create(ctx, sandbox)).To(Succeed())

			By("Verifying the sandbox is created")
			Eventually(func() error {
				return k8sClient.Get(ctx, types.NamespacedName{
					Name:      sandbox.Name,
					Namespace: sandbox.Namespace,
				}, sandbox)
			}, time.Minute*10, time.Millisecond*500).Should(Succeed())

			By("Updating labels")
			Eventually(func() error {
				if err := k8sClient.Get(ctx, types.NamespacedName{
					Name:      sandbox.Name,
					Namespace: sandbox.Namespace,
				}, sandbox); err != nil {
					return err
				}
				sandbox.Labels["updated"] = "true"
				return k8sClient.Update(ctx, sandbox)
			}, time.Second*10, time.Millisecond*500).Should(Succeed())

			By("Listing Sandboxes by label")
			list := &agentsv1alpha1.SandboxList{}
			Eventually(func() int {
				err := k8sClient.List(ctx, list, client.InNamespace(Namespace), client.MatchingLabels{"app": "e2e-test"})
				if err != nil {
					return 0
				}
				return len(list.Items)
			}, time.Second*10, time.Millisecond*500).Should(BeNumerically(">=", 1))

			By("Deleting Sandbox")
			Expect(k8sClient.Delete(ctx, sandbox)).To(Succeed())

			By("Verifying deletion")
			Eventually(func() bool {
				err := k8sClient.Get(ctx, types.NamespacedName{
					Name:      sandbox.Name,
					Namespace: sandbox.Namespace,
				}, &agentsv1alpha1.Sandbox{})
				return err != nil
			}, time.Second*30, time.Second).Should(BeTrue())
		})
	})
})
