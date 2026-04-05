package com.loanflow.event;

import com.loanflow.entity.EmiSchedule;
import com.loanflow.entity.Payment;
import lombok.AllArgsConstructor;
import lombok.Getter;

@Getter
@AllArgsConstructor
public class PaymentReceivedEvent {

    private final Payment payment;
    private final EmiSchedule emiSchedule;

}