# client-java

## Generate
[Guide to generate Java codes from CustomResourceDefinition](/clients/java/docs/generate-model-from-crd.md)

## Usage

Add this dependency to your project's POM:

```xml
<dependency>
    <groupId>io.openkruise</groupId>
    <artifactId>agents-client-java</artifactId>
    <version>0.1.0</version>
    <scope>compile</scope>
</dependency>
```

**Note that this package has not been uploaded to the maven official repository. Currently, you should manually download this repo and package it to use.**

You should also add the dependency of Kubernetes official java SDK:

```xml
<dependency>
    <groupId>io.kubernetes</groupId>
    <artifactId>client-java</artifactId>
    <version>19.0.0</version>
</dependency>
```

### Manually package

At first generate the JAR by executing:

    mvn package

Then manually install the following JARs:

* target/client-java-0.1.0.jar
* target/lib/*.jar

## Getting Started

You have to use `ApiClient` and `CustomObjectsApi` in `io.kubernetes:client-java` package.
The only thing you should import from `io.openkruise:client-java` is `io.openkruise.agents.client.models.*`.

```java
import io.kubernetes.client.openapi.ApiClient;
import io.kubernetes.client.openapi.ApiException;
import io.kubernetes.client.openapi.Configuration;
import io.kubernetes.client.openapi.apis.CustomObjectsApi;
import io.kubernetes.client.openapi.models.*;
import io.kubernetes.client.util.Config;

public class MyExample {

    // generate this client from a kubeconfig file or something else
    private CustomObjectsApi api;
    
    private static final String GROUP = "agents.kruise.io";
    private static final String VERSION = "v1alpha1";
    private static final String PLURAL = "sandboxes";

    public MyExample() throws Exception {
        ApiClient client = Config.defaultClient();
        Configuration.setDefaultApiClient(client);
        this.api = new CustomObjectsApi();
    }

    public String createSandbox(String namespace, String name, String image) throws ApiException {
        try {
            V1alpha1Sandbox sandbox = createSandboxObject(name, image);

            Object result = api.createNamespacedCustomObject(
                GROUP, VERSION, namespace, PLURAL, sandbox, null, null, null
            );
            return name;
        } catch (ApiException e) {
            e.printStackTrace();
            return null;
        }
    }

    public void getSandbox(String name, String namespace) {
        try {
            Object result = api.getNamespacedCustomObject(
                GROUP, VERSION, namespace, PLURAL, name
            );
        } catch (ApiException e) {

        }
    }

    public void deleteSandbox(String name, String namespace) {
        try {
            api.deleteNamespacedCustomObject(
                GROUP, VERSION, namespace, PLURAL, name, null, null, null, null, null
            );
        } catch (ApiException e) {
            
        }
    }
    private V1alpha1Sandbox createSandboxObject(String name, String image) {
        V1alpha1Sandbox sandbox = new V1alpha1Sandbox();
        sandbox.setApiVersion(GROUP + "/" + VERSION);
        sandbox.setKind("Sandbox");

        // build metadata
        V1ObjectMeta metadata = new V1ObjectMeta();
        metadata.setName(name);
        metadata.setNamespace(NAMESPACE);
        Map<String, String> labels = new HashMap<>();
        labels.put("app", "sandbox-example");
        labels.put("managed-by", "java-client");
        metadata.setLabels(labels);
        sandbox.setMetadata(metadata);

        // build spec
        V1alpha1SandboxSpec spec = new V1alpha1SandboxSpec();

        // build PodTemplateSpec
        V1PodTemplateSpec templateSpec = new V1PodTemplateSpec();
        V1ObjectMeta templateMetadata = new V1ObjectMeta();
        templateMetadata.setLabels(Collections.singletonMap("app", "sandbox-container"));
        templateSpec.setMetadata(templateMetadata);

        // build PodSpec
        V1PodSpec podSpec = new V1PodSpec();
        podSpec.setRestartPolicy("Never");

        // container
        V1Container container = new V1Container();
        container.setName("sandbox-container");
        container.setImage(image);

        // port
        V1ContainerPort port = new V1ContainerPort();
        port.setName("http");
        port.setContainerPort(80);
        container.addPortsItem(port);

        podSpec.addContainersItem(container);
        templateSpec.setSpec(podSpec);

        spec.setTemplate(templateSpec);
        sandbox.setSpec(spec);

        return sandbox;
    }
}

```
