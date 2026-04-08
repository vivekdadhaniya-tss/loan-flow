package com.loanflow.mapper;

import com.loanflow.dto.response.BorrowerOverdueResponse;
import com.loanflow.dto.response.OverdueTrackerResponse;
import com.loanflow.entity.OverdueTracker;
import org.mapstruct.*;
import java.util.List;

@Mapper(componentModel = "spring")
public interface OverdueTrackerMapper {

    @Mapping(target = "loanNumber", source = "loan.loanNumber")
    @Mapping(target = "installmentNumber", source = "emiSchedule.installmentNumber")
    @Mapping(target = "borrowerName", expression = "java(tracker.getBorrower().getName())")
    @Mapping(target = "borrowerEmail", source = "borrower.email")
    @Mapping(target = "penaltySettled", expression = "java(tracker.getPenaltyStatus() == com.loanflow.enums.PenaltyStatus.SETTLED)")
    OverdueTrackerResponse toResponse(OverdueTracker tracker);

    @Mapping(target = "loanNumber", source = "loan.loanNumber")
    @Mapping(target = "installmentNumber", source = "emiSchedule.installmentNumber")
    // Calculate the total penalty amount by adding fixed amount and the percentage charge
    @Mapping(target = "totalPenaltyAmount", expression = "java(tracker.getFixedPenaltyAmount().add(tracker.getPenaltyCharge()))")
    @Mapping(target = "penaltyStatus", source = "penaltyStatus")
    BorrowerOverdueResponse toBorrowerResponse(OverdueTracker tracker);

    List<OverdueTrackerResponse> toResponseList(List<OverdueTracker> trackers);
}

