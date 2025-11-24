package at.hcw.alcatraz.spread;

import at.hcw.alcatraz.dto.SpreadPacket;
import com.fasterxml.jackson.databind.ObjectMapper;
import lombok.Getter;
import lombok.Setter;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;
import spread.*;

import java.net.InetAddress;

/**
 * Manages the connection to a Spread daemon.
 *
 * Responsibilities:
 * - Connect to the daemon using the configured host, port and node id.
 * - Join the application group.
 * - Forward incoming regular and membership messages to a handler.
 * - Serialize and send application packets.
 *
 * Spread events:
 * - regularMessageReceived → application payload
 * - membershipMessageReceived → group membership changes
 */
@Component
public class SpreadConnectionService implements AdvancedMessageListener {

    private final ObjectMapper mapper = new ObjectMapper();
    private final SpreadConnection connection = new SpreadConnection();

    @Value("${spread.host}")
    private String host;

    @Value("${spread.port}")
    private int port;

    @Value("${spread.group}")
    private String groupName;

    @Getter
    @Value("${spread.node-id}")
    private String nodeId;

    @Setter
    private SpreadMessageHandler handler;

    /**
     * Initializes the Spread connection.
     *
     * Steps:
     * - Register listener.
     * - Connect to daemon.
     * - Join the configured group.
     *
     * @param handler callback for message and membership events
     */
    public void init(SpreadMessageHandler handler) throws Exception {
        this.handler = handler;
        connection.add(this);
        connection.connect(InetAddress.getByName(host), port, nodeId, false, true);
        SpreadGroup group = new SpreadGroup();
        group.join(connection, groupName);
        System.out.println("[Spread] Joined group '" + groupName + "' as " + nodeId);
    }

    /**
     * Sends a packet to the group.
     *
     * JSON encoding and multicast are handled here.
     * Self-discard is enabled so the sender does not receive its own message.
     *
     * @param packet payload to broadcast
     */
    public void send(SpreadPacket packet) {
        try {
            byte[] json = mapper.writeValueAsBytes(packet);
            SpreadMessage msg = new SpreadMessage();
            msg.setReliable();
            msg.addGroup(groupName);
            msg.setData(json);
            msg.setSelfDiscard(true);
            connection.multicast(msg);
        } catch (Exception e) {
            System.err.println("[Spread] Send failed: " + e.getMessage());
            e.printStackTrace();
        }
    }

    /**
     * Handles incoming application messages.
     * JSON is decoded and forwarded to the handler.
     */
    @Override
    public void regularMessageReceived(SpreadMessage msg) {
        if (handler == null)
            return;

        try {
            SpreadPacket packet = mapper.readValue(msg.getData(), SpreadPacket.class);
            handler.onRegularMessage(packet, msg.getSender());
        } catch (Exception e) {
            System.err.println("[Spread] JSON parse failed: " + e.getMessage());
            e.printStackTrace();
        }
    }

    /**
     * Handles incoming membership updates.
     * The handler receives the raw MembershipInfo from Spread.
     */
    @Override
    public void membershipMessageReceived(SpreadMessage msg) {
        if (handler == null)
            return;

        handler.onMembership(msg.getMembershipInfo());
    }
}
