//package com.loanflow.service.impl;
//
//import com.loanflow.dto.request.LoginRequest;
//import com.loanflow.dto.request.RegisterRequest;
//import com.loanflow.dto.response.AuthResponse;
//import com.loanflow.entity.user.Admin;
//import com.loanflow.entity.user.Borrower;
//import com.loanflow.entity.user.LoanOfficer;
//import com.loanflow.entity.user.User;
//import com.loanflow.exception.BusinessRuleException;
//import com.loanflow.repository.UserRepository;
//import com.loanflow.security.JwtTokenProvider;
//import com.loanflow.service.AuthService;
//import lombok.RequiredArgsConstructor;
//import org.springframework.stereotype.Service;
//import org.springframework.transaction.annotation.Transactional;
//
//@Service
//@RequiredArgsConstructor
//public class AuthServiceImpl implements AuthService{
//
//    private final UserRepository userRepository;
//    private final PasswordEncoder        passwordEncoder;
//    private final AuthenticationManager  authenticationManager;
//    private final JwtTokenProvider jwtTokenProvider;
//
//    @Transactional
//    public AuthResponse register(RegisterRequest req) {
//        if (userRepository.existsByEmail(req.getEmail())) {
//            throw new BusinessRuleException(
//                    "Email already registered: " + req.getEmail());
//        }
//
//        // Create correct subtype — JPA routes to the right table
//        User user = switch (req.getRole()) {
//            case BORROWER -> {
//                Borrower b = new Borrower();
//                b.setMonthlyIncome(req.getMonthlyIncome());
//                b.setPanNumber(req.getPanNumber());
//                b.setOccupation(req.getOccupation());
//                yield b;
//            }
//            case LOAN_OFFICER -> {
//                LoanOfficer o = new LoanOfficer();
//                o.setEmployeeId(req.getEmployeeId());
//                o.setDesignation(req.getDesignation());
////                o.setMaxApprovalLimit(req.getMaxApprovalLimit());
//                yield o;
//            }
//            case ADMIN -> {
//                Admin a = new Admin();
//                a.setAccessLevel(req.getAccessLevel());
//                yield a;
//            }
//        };
//
//        user.setName(req.getName());
//        user.setEmail(req.getEmail());
//        user.setPassword(passwordEncoder.encode(req.getPassword()));
//        user.setPhone(req.getPhone());
//
//        User saved = userRepository.save(user);
//        String token = jwtTokenProvider.generateTokenForUser(saved);
//
//        return AuthResponse.builder()
//                .token(token)
//                .role(saved.getRole())
//                .name(saved.getName())
//                .email(saved.getEmail())
//                .build();
//    }
//
//    public AuthResponse login(LoginRequest req) {
//        Authentication auth = authenticationManager.authenticate(
//                new UsernamePasswordAuthenticationToken(
//                        req.getEmail(), req.getPassword()));
//
//        User user = userRepository.findByEmail(req.getEmail())
//                .orElseThrow();
//
//        return AuthResponse.builder()
//                .token(jwtTokenProvider.generateToken(auth))
//                .role(user.getRole())
//                .name(user.getName())
//                .email(user.getEmail())
//                .build();
//    }
//}
