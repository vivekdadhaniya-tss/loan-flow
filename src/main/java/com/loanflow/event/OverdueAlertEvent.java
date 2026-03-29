package com.loanflow.event;

import com.loanflow.entity.OverdueTracker;
import lombok.*;

@Getter
@AllArgsConstructor
public class OverdueAlertEvent {
    private final OverdueTracker tracker;
}
