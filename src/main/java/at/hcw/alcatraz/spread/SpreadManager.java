package at.hcw.alcatraz.spread;

import at.hcw.alcatraz.dto.SpreadPacket;
import at.hcw.alcatraz.dao.PlayerRegistry;
import jakarta.annotation.PostConstruct;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;
import spread.MembershipInfo;
import spread.SpreadGroup;

import java.util.Arrays;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * Central handler for all Spread events.
 *
 * Responsibilities:
 * - Process regular messages (UPDATE, RESET, START) and apply them to PlayerRegistry.
 * - Process membership updates and delegate master selection to ElectionService.
 * - Trigger replication of player state when this node is master.
 * - Resolve ports of cluster nodes for redirect logic.
 */
@Component
public class SpreadManager implements SpreadMessageHandler {

    private final SpreadConnectionService spread;
    private final ElectionService election;
    private final PlayerRegistry registry;
    private final Map<String, Integer> nodePorts;

    public SpreadManager(SpreadConnectionService spread,
                         ElectionService election,
                         PlayerRegistry registry,
                         @Value("${cluster.nodes}") String clusterNodes) {
        this.spread = spread;
        this.election = election;
        this.registry = registry;
        this.nodePorts = loadPorts(clusterNodes);
    }

    /**
     * Initializes Spread connection and registers this class as message handler.
     */
    @PostConstruct
    public void setup() throws Exception {
        spread.init(this);
    }

    /**
     * Parses "node1:8081,node2:8082,..." into a lookup table.
     *
     * @param cfg configuration string
     * @return map nodeId → httpPort
     */
    private Map<String, Integer> loadPorts(String cfg) {
        Map<String, Integer> out = new HashMap<>();
        for (String part : cfg.split(",")) {
            String[] p = part.split(":");
            out.put(p[0].trim(), Integer.parseInt(p[1].trim()));
        }
        return out;
    }

    // ================= REGULAR MESSAGES =================

    /**
     * Applies incoming application messages to local state.
     *
     * UPDATE: replace PlayerRegistry
     * RESET:  clear all players and reset started flag
     * START:  set started flag
     *
     * @param packet decoded Spread packet
     * @param sender sender group
     */
    @Override
    public void onRegularMessage(SpreadPacket packet, SpreadGroup sender) {
        switch (packet.type()) {
            case UPDATE -> {
                registry.replaceAll(packet.data());
                System.out.println("[Spread] Player registry updated from Master: " + sender);
            }
            case RESET -> {
                registry.reset();
                System.out.println("[Spread] Lobby reset received from Master: " + sender);
            }
            case START -> {
                registry.markStarted();
                System.out.println("[Spread] Game start signal received from Master: " + sender);
            }
            default -> System.err.println("[Spread] Unknown packet type: " + packet.type());
        }
    }

    // ================= MEMBERSHIP =================

    /**
     * Processes membership changes and updates master selection.
     *
     * Steps:
     * - Extract logical node ids from SpreadGroup strings.
     * - Sort ids to enforce deterministic master selection.
     * - If this node is master and a join event occurs, send state snapshot.
     * - Forward sorted membership list to ElectionService.
     *
     * @param info Spread membership information
     */
    @Override
    public void onMembership(MembershipInfo info) {

        var arr = info.getMembers();
        if (arr == null || arr.length == 0) {
            election.resetMaster();
            return;
        }

        // Map to logical ids: node1, node2, ...
        List<String> ids = Arrays.stream(arr)
                .map(SpreadGroup::toString)  // "#node1#localhost"
                .map(this::extractId)        // -> "node1"
                .sorted()
                .toList();

        String selfId = spread.getNodeId();

        if (election.isMaster() && info.isCausedByJoin()) {
            System.out.println("[Spread] snapshot handover to new joining node. Name: " + info.getJoined().toString());

            replicate();

            if (registry.isStarted()) {
                broadcastStart();
            }
        }

        election.evaluate(ids, selfId);
    }

    // ================= PUBLIC API =================

    /**
     * @return true if this node is master.
     */
    public boolean isMaster() {
        return election.isMaster();
    }

    /**
     * @return current master node id.
     */
    public String getCurrentMasterId() {
        return election.getCurrentMasterId();
    }

    /**
     * @return port of the node that currently holds master role.
     */
    public int getMasterPort() {
        return nodePorts.get(election.getCurrentMasterId());
    }

    /**
     * Broadcasts local PlayerRegistry snapshot to all nodes.
     */
    public void replicate() {
        spread.send(new SpreadPacket(SpreadMsgType.UPDATE, registry.snapshot()));
    }

    /**
     * Broadcasts game start flag to all nodes.
     */
    public void broadcastStart() {
        spread.send(new SpreadPacket(SpreadMsgType.START, null));
    }

    /**
     * Broadcasts a lobby reset to all nodes.
     */
    public void broadcastReset() {
        spread.send(new SpreadPacket(SpreadMsgType.RESET, Map.of()));
    }

    /**
     * Extracts the logical node id from a Spread group string.
     *
     * @param raw raw SpreadGroup string
     * @return extracted node id
     */
    private String extractId(String raw) {
        String[] parts = raw.split("#");
        for (String p : parts) {
            if (p.startsWith("node")) {
                return p;
            }
        }
        return raw;
    }

}
