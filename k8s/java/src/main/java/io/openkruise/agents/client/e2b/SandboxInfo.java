package io.openkruise.agents.client.e2b;

import io.openkruise.agents.client.e2b.api.models.SandboxDetail;
import io.openkruise.agents.client.e2b.api.models.SandboxLifecycle;
import io.openkruise.agents.client.e2b.api.models.SandboxNetworkConfig;
import io.openkruise.agents.client.e2b.api.models.SandboxVolumeMount;
import io.openkruise.agents.client.e2b.api.models.SandboxesGet200ResponseInner;

import java.time.OffsetDateTime;
import java.util.List;
import java.util.Map;
import java.util.Objects;

/**
 * Sandbox information, used uniformly for list() and getInfo() return values.
 * Fields unique to detail are null when returned by list().
 */
public class SandboxInfo {

    private String sandboxID;
    private String templateID;
    private String alias;
    private String clientID;
    private OffsetDateTime startedAt;
    private OffsetDateTime endAt;
    private Integer cpuCount;
    private Integer memoryMB;
    private Integer diskSizeMB;
    private String envdVersion;
    private Map<String, String> metadata;
    private String state;
    private List<SandboxVolumeMount> volumeMounts;

    private String envdAccessToken;
    private String domain;
    private Boolean allowInternetAccess;
    private SandboxNetworkConfig network;
    private SandboxLifecycle lifecycle;

    public SandboxInfo() {
    }

    static SandboxInfo fromListResponse(SandboxesGet200ResponseInner sb) {
        return fillCommon(new SandboxInfo(),
            sb.getSandboxID(), sb.getTemplateID(), sb.getAlias(), sb.getClientID(),
            sb.getStartedAt(), sb.getEndAt(), sb.getCpuCount(), sb.getMemoryMB(),
            sb.getDiskSizeMB(), sb.getEnvdVersion(),
            sb.getState() != null ? sb.getState().getValue() : null,
            sb.getMetadata(), sb.getVolumeMounts());
    }

    static SandboxInfo fromDetail(SandboxDetail resp) {
        SandboxInfo info = fillCommon(new SandboxInfo(),
            resp.getSandboxID(), resp.getTemplateID(), resp.getAlias(), resp.getClientID(),
            resp.getStartedAt(), resp.getEndAt(), resp.getCpuCount(), resp.getMemoryMB(),
            resp.getDiskSizeMB(), resp.getEnvdVersion(),
            resp.getState() != null ? resp.getState().getValue() : null,
            resp.getMetadata(), resp.getVolumeMounts());
        info.envdAccessToken = resp.getEnvdAccessToken();
        info.domain = resp.getDomain();
        info.allowInternetAccess = resp.getAllowInternetAccess();
        info.network = resp.getNetwork();
        info.lifecycle = resp.getLifecycle();
        return info;
    }

    private static SandboxInfo fillCommon(SandboxInfo info,
        String sandboxID, String templateID, String alias, String clientID,
        OffsetDateTime startedAt, OffsetDateTime endAt,
        Integer cpuCount, Integer memoryMB, Integer diskSizeMB,
        String envdVersion, String state,
        Map<String, String> metadata, List<SandboxVolumeMount> volumeMounts) {
        info.sandboxID = sandboxID;
        info.templateID = templateID;
        info.alias = alias;
        info.clientID = clientID;
        info.startedAt = startedAt;
        info.endAt = endAt;
        info.cpuCount = cpuCount;
        info.memoryMB = memoryMB;
        info.diskSizeMB = diskSizeMB;
        info.envdVersion = envdVersion;
        info.state = state;
        info.metadata = metadata;
        info.volumeMounts = volumeMounts;
        return info;
    }

    public String getSandboxID() {return sandboxID;}

    public void setSandboxID(String sandboxID) {this.sandboxID = sandboxID;}

    public String getTemplateID() {return templateID;}

    public void setTemplateID(String templateID) {this.templateID = templateID;}

    public String getAlias() {return alias;}

    public void setAlias(String alias) {this.alias = alias;}

    public String getClientID() {return clientID;}

    public void setClientID(String clientID) {this.clientID = clientID;}

    public OffsetDateTime getStartedAt() {return startedAt;}

    public void setStartedAt(OffsetDateTime startedAt) {this.startedAt = startedAt;}

    public OffsetDateTime getEndAt() {return endAt;}

    public void setEndAt(OffsetDateTime endAt) {this.endAt = endAt;}

    public Integer getCpuCount() {return cpuCount;}

    public void setCpuCount(Integer cpuCount) {this.cpuCount = cpuCount;}

    public Integer getMemoryMB() {return memoryMB;}

    public void setMemoryMB(Integer memoryMB) {this.memoryMB = memoryMB;}

    public Integer getDiskSizeMB() {return diskSizeMB;}

    public void setDiskSizeMB(Integer diskSizeMB) {this.diskSizeMB = diskSizeMB;}

    public String getEnvdVersion() {return envdVersion;}

    public void setEnvdVersion(String envdVersion) {this.envdVersion = envdVersion;}

    public Map<String, String> getMetadata() {return metadata;}

    public void setMetadata(Map<String, String> metadata) {this.metadata = metadata;}

    public String getState() {return state;}

    public void setState(String state) {this.state = state;}

    public List<SandboxVolumeMount> getVolumeMounts() {return volumeMounts;}

    public void setVolumeMounts(List<SandboxVolumeMount> volumeMounts) {this.volumeMounts = volumeMounts;}

    public String getEnvdAccessToken() {return envdAccessToken;}

    public void setEnvdAccessToken(String envdAccessToken) {this.envdAccessToken = envdAccessToken;}

    public String getDomain() {return domain;}

    public void setDomain(String domain) {this.domain = domain;}

    public Boolean getAllowInternetAccess() {return allowInternetAccess;}

    public void setAllowInternetAccess(Boolean allowInternetAccess) {this.allowInternetAccess = allowInternetAccess;}

    public SandboxNetworkConfig getNetwork() {return network;}

    public void setNetwork(SandboxNetworkConfig network) {this.network = network;}

    public SandboxLifecycle getLifecycle() {return lifecycle;}

    public void setLifecycle(SandboxLifecycle lifecycle) {this.lifecycle = lifecycle;}

    @Override
    public boolean equals(Object o) {
        if (this == o) {return true;}
        if (o == null || getClass() != o.getClass()) {return false;}
        SandboxInfo that = (SandboxInfo)o;
        return Objects.equals(sandboxID, that.sandboxID);
    }

    @Override
    public int hashCode() {
        return Objects.hash(sandboxID);
    }

    @Override
    public String toString() {
        return "SandboxInfo{" +
            "sandboxID='" + sandboxID + '\'' +
            ", templateID='" + templateID + '\'' +
            ", alias='" + alias + '\'' +
            ", state='" + state + '\'' +
            ", cpuCount=" + cpuCount +
            ", memoryMB=" + memoryMB +
            ", diskSizeMB=" + diskSizeMB +
            '}';
    }
}
