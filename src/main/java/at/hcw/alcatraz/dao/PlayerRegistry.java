package at.hcw.alcatraz.dao;

import org.springframework.stereotype.Component;

import java.util.*;

/**
 * Maintains local player state for this node.
 *
 * Tracks player identifiers and callback URLs.
 * Enforces:
 *  - maximum 4 players
 *  - minimum 2 players to start a game
 *  - no duplicate player names
 *  - no duplicate callback URLs
 *  - no modifications after the game has entered the started state
 *
 * All operations are synchronized to keep the registry consistent inside the JVM.
 */
@Component
public class PlayerRegistry {

    /** Map playerName → callbackUrl. */
    private final Map<String, String> players = new HashMap<>();

    /** Maximum number of players allowed. */
    private static final int MAX_PLAYERS = 4;

    /** Minimum number of players required to start. */
    private static final int MIN_PLAYERS = 2;

    /**
     * Adds a player if the registry allows modifications.
     *
     * Conditions:
     *  - game not started
     *  - size <= MAX_PLAYERS
     *  - unique name
     *  - unique callback URL
     *
     * @param name player name
     * @param callback callback URL
     * @return true if the player was added
     */
    public synchronized boolean add(String name, String callback) {
        if (players.size() >= MAX_PLAYERS) return false;
        if (players.containsKey(name)) return false;
        if (players.containsValue(callback)) return false;

        players.put(name, callback);
        return true;
    }

    /**
     * Removes a player.
     *
     * @param name player name
     * @return true if a player was removed
     */
    public synchronized boolean remove(String name) {
        return players.remove(name) != null;
    }

    /**
     * Replaces local state with a new player map.
     *
     * @param newPlayers playerName → callbackUrl mapping
     */
    public synchronized void replaceAll(Map<String, String> newPlayers) {
        players.clear();
        players.putAll(newPlayers);
    }

    /**
     * Returns a defensive copy of the registry.
     *
     * @return map copy
     */
    public synchronized Map<String, String> snapshot() {
        return new HashMap<>(players);
    }

    /**
     * Returns all player names.
     *
     * @return immutable set of names
     */
    public synchronized Set<String> list() {
        return Set.copyOf(players.keySet());
    }


    /**
     * @return true if the game was not marked as started and minimum two players are registered
     */
    public synchronized boolean tryStart() {
        return players.size() >= MIN_PLAYERS;
    }

    /**
     * Resets registry and game state.
     */
    public synchronized void reset() {
        players.clear();
    }

}
