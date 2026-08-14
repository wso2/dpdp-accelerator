package com.openfgc.nomineeservice.web.dto;

/** Whether an active nomination exists for the queried (owner, nominee) pair. */
public record GateActiveResponse(boolean active) {
}
