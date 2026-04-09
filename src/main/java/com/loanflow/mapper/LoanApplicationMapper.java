package com.loanflow.mapper;

import com.loanflow.dto.response.BorrowerApplicationResponse;
import com.loanflow.dto.response.LoanApplicationResponse;
import com.loanflow.dto.response.OfficerApplicationResponse;
import com.loanflow.entity.LoanApplication;
import org.mapstruct.*;
import java.util.List;

@Mapper(componentModel = "spring")
public interface LoanApplicationMapper {

    /**
     * reviewedBy is nullable — MapStruct null-safe chaining handles this.
     * If reviewedBy == null → reviewedByName = null (no NPE).
     *
     * monthlyIncome and existingMonthlyEmi are included so the officer
     * can see the borrower's declared income during review.
     */

    // used in BorrowerController
    @Mapping(target = "reviewedByName", source = "reviewedBy.name")
    BorrowerApplicationResponse toBorrowerResponse(LoanApplication application);
    List<BorrowerApplicationResponse> toBorrowerResponseList(List<LoanApplication> applications);

    // used in OfficerController
    @Mapping(target = "reviewedByName", source = "reviewedBy.name")
    OfficerApplicationResponse toOfficerResponse(LoanApplication application);
    List<OfficerApplicationResponse> toOfficerResponseList(List<LoanApplication> applications);

    @Mapping(target = "reviewedByName", source = "reviewedBy.name")
    LoanApplicationResponse toResponse(LoanApplication application);

    List<LoanApplicationResponse> toResponseList(List<LoanApplication> applications);
}
