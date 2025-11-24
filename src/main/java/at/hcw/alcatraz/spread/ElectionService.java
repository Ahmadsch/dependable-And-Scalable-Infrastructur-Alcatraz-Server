package at.hcw.alcatraz.spread;

import org.springframework.stereotype.Component;

import java.util.List;

/**
 * Maintains local master state.
 * <p>
 * Master selection rule:
 * - Smallest logical node id in the current membership becomes master.
 * - All nodes apply the same rule after each membership update.
 * <p>
 * Concurrency model:
 * - All read/write operations are protected by the same intrinsic lock.
 * - evaluate() and resetMaster() update both fields in one atomic step.
 * - isMaster() and getCurrentMasterId() read the pair under the same lock.
 * - This prevents stale reads and prevents mixed states when both fields
 * are updated by the Spread membership thread while HTTP threads read them (Very unlikely to happens).
 */
@Component
public class ElectionService {

    private boolean masterFlag = false;


    /**
     * Logical id of the current master.
     */
    private String currentMasterId;

    /**
     * Applies the membership update and sets the master accordingly.
     *
     * @param sortedNodeIds ordered node ids
     * @param selfId        local node id
     */
    public synchronized void evaluate(List<String> sortedNodeIds, String selfId) {
        currentMasterId = sortedNodeIds.get(0);
        masterFlag = selfId.equals(currentMasterId);

        System.out.printf(
                "[Election] members=%s, masterId=%s, self=%s, isMaster=%s%n",
                sortedNodeIds, currentMasterId, selfId, masterFlag
        );
    }

    /**
     * Clears master information when the membership becomes empty.
     */
    public synchronized void resetMaster() {
        currentMasterId = null;
        masterFlag = false;
    }

    /**
     * @return true if this node is master
     */
    public synchronized boolean isMaster() {
        return masterFlag;
    }

    /**
     * @return id of the current master
     */
    public synchronized String getCurrentMasterId() {
        return currentMasterId;
    }
}
