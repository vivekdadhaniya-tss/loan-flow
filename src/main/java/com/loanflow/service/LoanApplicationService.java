package com.loanflow.service;

import com.loanflow.dto.request.LoanApplicationRequest;
import com.loanflow.dto.response.BorrowerApplicationResponse;
//import com.loanflow.dto.response.LoanApplicationResponse;
import com.loanflow.dto.response.OfficerApplicationResponse;
import com.loanflow.entity.user.User;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;

public interface LoanApplicationService {
    @Transactional
    BorrowerApplicationResponse apply(LoanApplicationRequest request, User borrower);

    @Transactional
    void cancelApplication(String applicationNumber, User borrower);

    //  READ — officer pending queue
    @Transactional(readOnly = true)
    List<OfficerApplicationResponse> getPendingApplications();

    @Transactional(readOnly = true)
    List<BorrowerApplicationResponse> getMyApplications(Long borrowerId);
}
