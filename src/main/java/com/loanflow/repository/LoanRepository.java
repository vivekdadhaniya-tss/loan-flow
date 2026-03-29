package com.loanflow.repository;

import com.loanflow.entity.Loan;
import com.loanflow.entity.user.User;
import com.loanflow.enums.LoanStatus;
import java.util.UUID;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import java.math.BigDecimal;
import java.util.Optional;
import java.util.List;


public interface LoanRepository extends JpaRepository<Loan , UUID> {
    @Query(value = "SELECT nextval('loan_number_seq')", nativeQuery = true)
    Long getNextLoanSequence();

    Long countByBorrowerAndStatus(User Browser, LoanStatus status);

    List<Loan> findByBorrowerAndStatus(User Borrower, LoanStatus status);

    List<Loan> findByBorrowerOrderByCreatedAtDesc(User Borrower);

    @Query("""
            SELECT SUM(l.monthlyEmi) FROM Loan l
            WHERE l.borrower.id = :borrowerId
            AND l.status = 'ACTIVE'
            """)
    Optional<BigDecimal> sumActiveMonthlyEmiByBorrower(@Param("borrowerId") UUID borrowerId);
    // Sum of monthly EMI across all ACTIVE loans for this borrower
    // Used by DtiCalculationService as Internal EMI

}
