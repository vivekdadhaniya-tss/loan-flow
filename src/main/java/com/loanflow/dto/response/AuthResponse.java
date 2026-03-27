package com.loanflow.dto.response;

import com.loanflow.enums.Role;
import lombok.*;

@Getter @Setter @NoArgsConstructor @AllArgsConstructor @Builder
public class AuthResponse {

    private String token;

    private Role role;

    private String name;

    private String email;
}
