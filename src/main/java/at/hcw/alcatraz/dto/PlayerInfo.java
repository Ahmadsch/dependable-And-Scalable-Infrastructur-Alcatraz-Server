package at.hcw.alcatraz.dto;

import io.swagger.v3.oas.annotations.media.Schema;
import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

@Schema(name = "PlayerInfo")
@Data
@AllArgsConstructor
@NoArgsConstructor
public class PlayerInfo {

    @Schema(description = "Player name. It should be unique", example = "Alice")
    private String playerName;

    @Schema(description = "Callback URL. It should be unique for each player registered", example = "http://localhost:9001")
    private String callbackUrl;
}
