package com.civileng.marketplace.support.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;
import lombok.Data;

@Data
public class CreateTicketRequest {

    @NotBlank
    @Size(max = 200)
    private String subject;

    @NotBlank
    @Size(max = 4000)
    private String description;

    @Size(max = 50)
    private String category;

    /** Optional; defaults to MEDIUM in the service layer. */
    private String priority;
}
