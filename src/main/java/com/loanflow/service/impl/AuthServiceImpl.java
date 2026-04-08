package com.loanflow.service.impl;

import com.loanflow.dto.request.LoginRequest;
import com.loanflow.dto.request.RegisterRequest;
import com.loanflow.dto.response.AuthResponse;
import com.loanflow.entity.Address;
import com.loanflow.entity.user.Admin;
import com.loanflow.entity.user.Borrower;
import com.loanflow.entity.user.LoanOfficer;
import com.loanflow.entity.user.User;
import com.loanflow.exception.BusinessRuleException;
import com.loanflow.exception.ResourceNotFoundException;
import com.loanflow.repository.LoanOfficerRepository;
import com.loanflow.repository.UserRepository;
import com.loanflow.security.JwtTokenProvider;
import com.loanflow.service.AuthService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.security.authentication.AuthenticationManager;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.Authentication;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.*;
import java.time.format.DateTimeFormatter;

@Service
@RequiredArgsConstructor
@Slf4j
public class AuthServiceImpl implements AuthService {

    private final UserRepository userRepository;
    private final LoanOfficerRepository loanOfficerRepository;
    private final PasswordEncoder passwordEncoder;
    private final AuthenticationManager authenticationManager;
    private final JwtTokenProvider jwtTokenProvider;

    @Override
    @Transactional
    public AuthResponse register(RegisterRequest req) {

        log.debug("Processing registration for email: {}", req.getEmail());

        String email = req.getEmail().trim().toLowerCase();

        if (userRepository.existsByEmail(email)) {
            throw new BusinessRuleException("Email already registered: " + email);
        }

        User user = switch (req.getRole()) {

            case BORROWER -> {
                if (req.getDateOfBirth() == null) {
                    throw new BusinessRuleException("Date of birth is required for borrower.");
                }

                int age = Period.between(req.getDateOfBirth(), LocalDate.now()).getYears();
                if (age < 18 || age > 60) {
                    throw new BusinessRuleException("Borrower must be between 18 and 60 years old.");
                }

                if (req.getMonthlyIncome() == null || req.getMonthlyIncome().doubleValue() <= 0) {
                    throw new BusinessRuleException("Monthly income must be greater than 0.");
                }

                if (req.getPanNumber() == null || req.getPanNumber().isBlank()) {
                    throw new BusinessRuleException("PAN number is required for borrower.");
                }

                Borrower b = new Borrower();
                b.setMonthlyIncome(req.getMonthlyIncome());
                b.setPanNumber(req.getPanNumber().trim().toUpperCase());
                b.setOccupation(req.getOccupation().trim());
                b.setDateOfBirth(req.getDateOfBirth());

                if (req.getAddress() != null) {
                    Address address = new Address();
                    address.setFlatNo(req.getAddress().getFlatNo());
                    address.setArea(req.getAddress().getArea());
                    address.setCity(req.getAddress().getCity());
                    address.setState(req.getAddress().getState());
                    address.setPincode(req.getAddress().getPincode());

                    address.setBorrower(b);
                    b.setAddress(address);
                }

                yield b;
            }

            case LOAN_OFFICER -> {
                LoanOfficer o = new LoanOfficer();
                o.setEmployeeId(generateEmployeeId());
                o.setDesignation(req.getDesignation().trim());
                yield o;
            }

            case ADMIN -> {
                if (req.getAccessLevel() == null) {
                    throw new BusinessRuleException("Access level is required for admin.");
                }

                Admin a = new Admin();
                a.setAccessLevel(req.getAccessLevel());
                yield a;
            }

            default -> throw new BusinessRuleException("Cannot register user with role: " + req.getRole());
        };

        user.setName(req.getName().trim());
        user.setEmail(email);
        user.setPassword(passwordEncoder.encode(req.getPassword()));
        user.setPhone(req.getPhone().trim());
        user.setRole(req.getRole());
        user.setActive(true);
        user.setDeleted(false);

        User saved = userRepository.save(user);

        log.info("Successfully registered new user with email: {} and role: {}", saved.getEmail(), saved.getRole());

        return AuthResponse.builder()
                .role(saved.getRole())
                .build();
    }

    @Override
    public AuthResponse login(LoginRequest req) {

        log.debug("Processing login for email: {}", req.getEmail());

        Authentication auth = authenticationManager.authenticate(
                new UsernamePasswordAuthenticationToken(
                        req.getEmail(),
                        req.getPassword())
        );

        User user = userRepository.findByEmail(req.getEmail())
                .orElseThrow(() -> new ResourceNotFoundException("User not found: " + req.getEmail()));

        String token = jwtTokenProvider.generateToken(auth);

        log.info("Successfully logged in user: {}", user.getEmail());

        return AuthResponse.builder()
                .token(token)
                .role(user.getRole())
                .build();
    }

    private String generateEmployeeId() {
        Long seqVal = loanOfficerRepository.getNextEmployeeIdSequence();

        if (seqVal == null)
            throw new IllegalStateException("Failed to retrieve next value from employee_id_seq: sequence returned null");

        String datePrefix = LocalDate.now().format(DateTimeFormatter.BASIC_ISO_DATE);
        return String.format("EMP-%s-%06d", datePrefix, seqVal);
    }
}