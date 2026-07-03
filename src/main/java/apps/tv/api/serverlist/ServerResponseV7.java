package apps.tv.api.serverlist;

import java.util.List;

public class ServerResponseV7 {
    private List<ServerV7> servers;
    private List<ServerV7> vip_servers;

    public List<ServerV7> getServers() {
        return servers;
    }

    public void setServers(List<ServerV7> servers) {
        this.servers = servers;
    }

    public List<ServerV7> getVipServers() {
        return vip_servers;
    }

    public void setVipServers(List<ServerV7> vip_servers) {
        this.vip_servers = vip_servers;
    }
}
