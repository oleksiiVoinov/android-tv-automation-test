package apps.tv.api.serverlist;

import java.util.List;

/**
 * A single entry of a v7 server's {@code services} array (V2 infrastructure support).
 */
public class ServiceV7 {

    private String id;
    private List<Integer> ports;
    private int timeout;
    private String inbound_ip;
    private String instance_id;

    public String getId() {
        return id;
    }

    public void setId(String id) {
        this.id = id;
    }

    public List<Integer> getPorts() {
        return ports;
    }

    public void setPorts(List<Integer> ports) {
        this.ports = ports;
    }

    public int getTimeout() {
        return timeout;
    }

    public void setTimeout(int timeout) {
        this.timeout = timeout;
    }

    public String getInboundIp() {
        return inbound_ip;
    }

    public void setInboundIp(String inbound_ip) {
        this.inbound_ip = inbound_ip;
    }

    public String getInstanceId() {
        return instance_id;
    }

    public void setInstanceId(String instance_id) {
        this.instance_id = instance_id;
    }

    public boolean isV2Instance() {
        return (inbound_ip != null && !inbound_ip.isBlank())
                || (instance_id != null && !instance_id.isBlank());
    }
}
