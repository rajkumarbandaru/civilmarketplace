package com.civileng.marketplace.support.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;
import lombok.Data;

@Data
public class ReplyRequest {

    @NotBlank
    @Size(max = 4000)
    private String body;
}
