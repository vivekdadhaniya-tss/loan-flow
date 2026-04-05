package com.loanflow.service;

import com.loanflow.dto.response.BorrowerOverdueResponse;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;

public interface OverdueMonitorService {

    @Transactional(readOnly = true)
    List<BorrowerOverdueResponse> getMyOverdues(Long id);

    // Daily: marks PENDING EMIs past due date as OVERDUE + applies penalty
    void scanAndMarkOverdue();

    // Daily: transitions DEFAULTED loans (180+ days) to WRITTEN_OFF
    void scanAndMarkWrittenOff();
}
