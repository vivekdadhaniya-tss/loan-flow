package com.loanflow.repository;

import com.loanflow.entity.EmiSchedule;
import com.loanflow.entity.Loan;
import com.loanflow.entity.Payment;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.UUID;

@Repository
public interface PaymentRepository extends JpaRepository<Payment, UUID> {

    boolean existsByEmiSchedule(EmiSchedule emiSchedule);

    List<Payment> findByLoanOrderByPaidAtDesc(Loan loan);
}
