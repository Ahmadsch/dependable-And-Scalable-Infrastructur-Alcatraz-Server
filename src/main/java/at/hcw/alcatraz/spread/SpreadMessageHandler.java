package at.hcw.alcatraz.spread;


import at.hcw.alcatraz.dto.SpreadPacket;
import spread.MembershipInfo;
import spread.SpreadGroup;

public interface SpreadMessageHandler {
    void onRegularMessage(SpreadPacket payload, SpreadGroup sender);
    void onMembership(MembershipInfo info);
}
