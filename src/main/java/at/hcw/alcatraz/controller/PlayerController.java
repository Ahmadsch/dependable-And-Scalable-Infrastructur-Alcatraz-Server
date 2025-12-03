package at.hcw.alcatraz.controller;

import at.hcw.alcatraz.dao.PlayerRegistry;
import at.hcw.alcatraz.dto.PlayerInfo;
import at.hcw.alcatraz.spread.SpreadManager;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.Parameter;
import io.swagger.v3.oas.annotations.enums.ParameterIn;
import io.swagger.v3.oas.annotations.responses.ApiResponse;
import io.swagger.v3.oas.annotations.responses.ApiResponses;
import io.swagger.v3.oas.annotations.tags.Tag;
import org.springframework.http.*;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.client.RestTemplate;

import java.net.Inet4Address;
import java.net.InetAddress;
import java.net.NetworkInterface;
import java.net.URI;
import java.util.*;

@Tag(name = "Players")
@RestController
@RequestMapping("/players")
public class PlayerController {


    private final PlayerRegistry registry;
    private final SpreadManager spread;

    public PlayerController(PlayerRegistry registry, SpreadManager spread) {
        this.registry = registry;
        this.spread = spread;
    }

    // -------- redirect helper --------
    private ResponseEntity<Void> redirectToMaster(String path) {
        String master = spread.getCurrentMasterId();
        int port = spread.getMasterPort();
        String ip = getWifiIp();

        HttpHeaders headers = new HttpHeaders();
        headers.add("X-Master-Node", master);
        headers.setLocation(URI.create("http://" + ip + ":" + port + path));
        System.out.println("Redirecting to master " + master + " --> http://" + ip + ":" + port + path);
        return ResponseEntity.status(HttpStatus.TEMPORARY_REDIRECT)
                .headers(headers)
                .build();
    }

    public static String getWifiIp() {
        try {
            Enumeration<NetworkInterface> interfaces = NetworkInterface.getNetworkInterfaces();
            while (interfaces.hasMoreElements()) {
                NetworkInterface iface = interfaces.nextElement();

                String name = iface.getDisplayName().toLowerCase();
                if (!iface.isUp()) continue;
                if (iface.isLoopback()) continue;
                if (name.contains("virtual")) continue;
                if (name.contains("vmware")) continue;
                if (name.contains("hyper-v")) continue;
                if (name.contains("vbox")) continue;

                // Nur WLAN akzeptieren, Weil Client läuft auf einen anderen PC
                if (!name.contains("wi-fi") && !name.contains("wlan") && !name.contains("wireless"))
                    continue;

                Enumeration<InetAddress> addresses = iface.getInetAddresses();
                while (addresses.hasMoreElements()) {
                    InetAddress addr = addresses.nextElement();
                    if (addr instanceof Inet4Address) {
                        return addr.getHostAddress();
                    }
                }
            }
        } catch (Exception e) {
            throw new RuntimeException(e);
        }
        return null;
    }

    // -------- register --------
    @Operation(summary = "Register player")
    @ApiResponses({
            @ApiResponse(responseCode = "201", description = "Registered"),
            @ApiResponse(responseCode = "302", description = "Redirect"),
            @ApiResponse(responseCode = "409", description = "Registration failed")
    })
    @PostMapping("/register")
    public ResponseEntity<?> register(
            @io.swagger.v3.oas.annotations.parameters.RequestBody(
                    description = "Player data",
                    required = true) @RequestBody PlayerInfo req) {

        if (!spread.isMaster())
            return redirectToMaster("/players/register");

        String name = req.getPlayerName();
        String callback = req.getCallbackUrl();

        if (!registry.add(name, callback)) {
            return ResponseEntity.status(HttpStatus.CONFLICT).body("Registration failed");
        }

        spread.replicate();
        return ResponseEntity.status(HttpStatus.CREATED).body("Registered");
    }

    // -------- unregister --------
    @Operation(summary = "Unregister player")
    @ApiResponses({
            @ApiResponse(responseCode = "200", description = "Removed"),
            @ApiResponse(responseCode = "302", description = "Redirect"),
            @ApiResponse(responseCode = "404", description = "Not found")
    })
    @Parameter(
            name = "name",
            description = "Player name",
            required = true,
            in = ParameterIn.PATH
    )
    @DeleteMapping("/unregister/{name}")
    public ResponseEntity<?> unregister(@PathVariable String name) {
        if (!spread.isMaster())
            return redirectToMaster("/players/unregister/" + name);

        if (!registry.remove(name))
            return ResponseEntity.status(HttpStatus.NOT_FOUND).body("Not found");

        spread.replicate();
        return ResponseEntity.ok("Removed");
    }

    // -------- list --------
    @Operation(summary = "List players")
    @ApiResponses({
            @ApiResponse(responseCode = "200", description = "List"),
            @ApiResponse(responseCode = "302", description = "Redirect")
    })
    @GetMapping("/all")
    public ResponseEntity<?> list() {

        if (!spread.isMaster())
            return redirectToMaster("/players/all");

        return ResponseEntity.ok(registry.list());
    }

    // -------- start --------
    @Operation(summary = "Start game")
    @ApiResponses({
            @ApiResponse(responseCode = "200", description = "Started"),
            @ApiResponse(responseCode = "302", description = "Redirect"),
            @ApiResponse(responseCode = "400", description = "Not enough players"),
            @ApiResponse(responseCode = "409", description = "Already running"),
            @ApiResponse(responseCode = "503", description = "Client unreachable")
    })
    @PostMapping("/game/start")
    public ResponseEntity<?> start() {


        if (!spread.isMaster())
            return redirectToMaster("/players/game/start");

        if (!registry.tryStart())
            return ResponseEntity.status(HttpStatus.BAD_REQUEST)
                    .body("Game cannot be started. Either Game has been already started or not enough players.");

        Map<String, String> allPlayers = registry.snapshot();
        RestTemplate restTemplate = new RestTemplate();

        for (Map.Entry<String, String> entry : allPlayers.entrySet()) {
            String playerName = entry.getKey();
            String callbackUrl = entry.getValue();

            List<PlayerInfo> others = new ArrayList<>();
            for (Map.Entry<String, String> e : allPlayers.entrySet()) {
                if (!e.getKey().equals(playerName)) {
                    others.add(new PlayerInfo(e.getKey(), e.getValue()));
                }
            }

            try {
                HttpHeaders headers = new HttpHeaders();
                headers.setContentType(MediaType.APPLICATION_JSON);
                HttpEntity<List<PlayerInfo>> entity = new HttpEntity<>(others, headers);

                restTemplate.postForEntity(callbackUrl + "/start", entity, Void.class);

            } catch (Exception ex) {
                System.err.println("Failed to notify " + playerName + ": " + ex.getMessage());
                return ResponseEntity.status(HttpStatus.SERVICE_UNAVAILABLE)
                        .body("Game start aborted. Client '" + playerName + "' is unreachable.");
            }
        }
        registry.reset();
        spread.broadcastReset();

        return ResponseEntity.ok("Game started. All clients notified. Lobby Reset");
    }

    // -------- finish --------
    @Operation(summary = "Finish game")
    @ApiResponses({
            @ApiResponse(responseCode = "200", description = "Reset"),
            @ApiResponse(responseCode = "302", description = "Redirect"),
            @ApiResponse(responseCode = "400", description = "No active game")
    })
    @PostMapping("/game/finish")
    public ResponseEntity<?> finishGame() {


        if (!spread.isMaster())
            return redirectToMaster("/players/game/finish");

        registry.reset();
        spread.broadcastReset();

        return ResponseEntity.ok("Lobby reset");
    }
}
