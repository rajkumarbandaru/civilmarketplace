package com.civileng.marketplace.procurement.dto;

/** Who is calling, from the gateway's signed identity headers. */
public record Actor(Long userId, String email) {

    public Actor {
        email = email == null ? null : email.trim().toLowerCase();
    }
}
