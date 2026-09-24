package com.civileng.marketplace.auth.dto;

import lombok.Data;

import java.util.List;

/** The refresh tokens this device holds: the tab's own, and the remembered one if any. */
@Data
public class LogoutRequest {
    private List<String> refreshTokens;
}
