package com.banking.account.mapper;

import com.banking.account.domain.Account;
import com.banking.account.dto.AccountDtos;
import org.mapstruct.*;

@Mapper(
    componentModel = "spring",
    nullValuePropertyMappingStrategy = NullValuePropertyMappingStrategy.IGNORE,
    unmappedTargetPolicy = ReportingPolicy.WARN
)
public interface AccountMapper {

    @Mapping(target = "accountType",   expression = "java(account.getAccountType().name())")
    @Mapping(target = "status",        expression = "java(account.getStatus().name())")
    AccountDtos.AccountResponse toResponse(Account account);

    @Mapping(target = "accountType", expression = "java(account.getAccountType().name())")
    @Mapping(target = "status",      expression = "java(account.getStatus().name())")
    AccountDtos.AccountSummary toSummary(Account account);

    @Mapping(target = "status", expression = "java(account.getStatus().name())")
    AccountDtos.BalanceResponse toBalanceResponse(Account account);
}
