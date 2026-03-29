//package com.loanflow.security;
//
//import com.loanflow.entity.user.User;
//import com.loanflow.exception.ResourceNotFoundException;
//import com.loanflow.exception.UnauthorizedAccessException;
//import com.loanflow.repository.UserRepository;
//import lombok.RequiredArgsConstructor;
//import lombok.extern.slf4j.Slf4j;
//import org.springframework.security.core.Authentication;
//import org.springframework.security.core.context.SecurityContextHolder;
//import org.springframework.security.core.userdetails.UserDetails;
//import org.springframework.stereotype.Component;
//
//import java.util.UUID;
//
//@Component
//@RequiredArgsConstructor
//@Slf4j
//public class SecurityUtils {
//
//    private final UserRepository userRepository;
//
//    /**
//     * Extracts the authenticated user's email from the SecurityContext
//     * and fetches the fully populated User entity from the database.
//     *
//     * @return The currently authenticated User entity
//     * @throws UnauthorizedAccessException if the user is not logged in
//     * @throws ResourceNotFoundException if the user exists in the token but was deleted from the DB
//     */
//    public User getCurrentUser() {
//        Authentication authentication = SecurityContextHolder.getContext().getAuthentication();
//
//        // 1. Check if an authentication context even exists
//        if (authentication == null || !authentication.isAuthenticated() || "anonymousUser".equals(authentication.getPrincipal())) {
//            log.warn("Attempted to access a secured resource without valid authentication.");
//            throw new UnauthorizedAccessException("You must be logged in to perform this action.");
//        }
//
//        // 2. Extract the email (username) from the Principal
//        String email;
//        Object principal = authentication.getPrincipal();
//
//        if (principal instanceof UserDetails) {
//            email = ((UserDetails) principal).getUsername();
//        } else {
//            email = principal.toString(); // Fallback if the principal is just a simple String
//        }
//
//        // 3. Load the user from the database
//        return userRepository.findByEmail(email)
//                .orElseThrow(() -> {
//                    log.error("Token valid, but user not found in database for email: {}", email);
//                    return new ResourceNotFoundException("User associated with this token no longer exists.");
//                });
//    }
//
//    /**
//     * Checks if the currently logged-in user is the owner of a specific resource.
//     * Used heavily in Controllers/Services to prevent Horizontal Privilege Escalation (IDOR attacks).
//     *
//     * @param resourceOwnerId The UUID of the user who owns the data being requested
//     * @return true if the current user owns the data, false otherwise
//     */
//    public boolean isOwner(UUID resourceOwnerId) {
//        if (resourceOwnerId == null) {
//            return false;
//        }
//        User currentUser = getCurrentUser();
//        return currentUser.getId().equals(resourceOwnerId);
//    }
//}