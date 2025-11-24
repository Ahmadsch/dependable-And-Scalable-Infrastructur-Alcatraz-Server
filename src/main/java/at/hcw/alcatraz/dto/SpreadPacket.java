package at.hcw.alcatraz.dto;

import at.hcw.alcatraz.spread.SpreadMsgType;

import java.util.Map;

public record SpreadPacket(SpreadMsgType type, Map<String, String> data) {
}
