package dev.mintychochip.api;

/**
 * Outcome of a party operation.
 */
public enum PartyResult {
    SUCCESS,
    NOT_IN_PARTY,
    ALREADY_IN_PARTY,
    TARGET_IN_PARTY,
    SELF_INVITE,
    ALREADY_INVITED,
    PARTY_FULL,
    NOT_LEADER,
    NOT_A_MEMBER,
    SELF_KICK,
    NO_INVITE,
    INVALID_NAME
}
