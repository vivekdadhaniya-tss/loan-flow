package com.loanflow.repository;

import java.util.Optional;

import com.loanflow.entity.LoanApplication;
import com.loanflow.entity.user.User;
import com.loanflow.enums.ApplicationStatus;
import org.hibernate.validator.constraints.UUID;
import org.springframework.boot.context.properties.bind.BindResult;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import java.util.List;
import java.util.Collection;

public interface LoanApplicationRepository extends JpaRepository<LoanApplication , UUID> {

    @Query(value = "SELECT nextval('application_number_seq')", nativeQuery = true)
    Long getNextApplicationSequence();

    boolean existsByBorrowerAndStatusIn(User borrower, Collection<ApplicationStatus> statuses);

    Optional<LoanApplication> findByApplicationNumber(String applicationNumber);

    List<LoanApplication> findByBorrower(User borrower);

}
