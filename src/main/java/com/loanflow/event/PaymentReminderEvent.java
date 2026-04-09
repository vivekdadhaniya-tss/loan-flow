package com.loanflow.event;

import com.loanflow.entity.EmiSchedule;
import lombok.AllArgsConstructor;
import lombok.Getter;

@Getter
@AllArgsConstructor
public class PaymentReminderEvent {

    private final EmiSchedule emiSchedule;

}