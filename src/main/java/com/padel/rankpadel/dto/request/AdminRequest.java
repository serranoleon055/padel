package com.padel.rankpadel.dto.request;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

@Getter
@Setter
@AllArgsConstructor
@NoArgsConstructor
@Builder
public class AdminRequest {

    @NotBlank
    @Size(max = 80)
    private String username;

    @Size(min = 6, max = 120)
    private String password;
}
