//package com.loanflow.config;
//
//import com.loanflow.constants.SecurityConstants;
//import com.loanflow.security.*;
//import lombok.RequiredArgsConstructor;
//import org.springframework.context.annotation.*;
//import org.springframework.security.authentication.AuthenticationManager;
//import org.springframework.security.config.annotation.authentication.configuration.*;
//import org.springframework.security.config.annotation.method.configuration.*;
//import org.springframework.security.config.annotation.web.builders.HttpSecurity;
//import org.springframework.security.config.http.SessionCreationPolicy;
//import org.springframework.security.crypto.bcrypt.BCryptPasswordEncoder;
//import org.springframework.security.crypto.password.PasswordEncoder;
//import org.springframework.security.web.SecurityFilterChain;
//import org.springframework.security.web.authentication.UsernamePasswordAuthenticationFilter;
//
//@Configuration
//@EnableMethodSecurity
//@RequiredArgsConstructor
//public class SecurityConfig {
//
//    private final JwtAuthenticationFilter jwtFilter;
//    private final CustomUserDetailsService userDetailsService;
//
//    @Bean
//    public SecurityFilterChain filterChain(HttpSecurity http)
//            throws Exception {
//        return http
//                .csrf(csrf -> csrf.disable())
//                .sessionManagement(s -> s.sessionCreationPolicy(
//                        SessionCreationPolicy.STATELESS))
//                .authorizeHttpRequests(auth -> auth
//                        .requestMatchers(SecurityConstants.PUBLIC_URLS).permitAll()
//                        .anyRequest().authenticated())
//                .addFilterBefore(jwtFilter,
//                        UsernamePasswordAuthenticationFilter.class)
//                .build();
//    }
//
//    @Bean
//    public PasswordEncoder passwordEncoder() {
//        return new BCryptPasswordEncoder(12);
//    }
//
//    @Bean
//    public AuthenticationManager authenticationManager(
//            AuthenticationConfiguration config) throws Exception {
//        return config.getAuthenticationManager();
//    }
//}
