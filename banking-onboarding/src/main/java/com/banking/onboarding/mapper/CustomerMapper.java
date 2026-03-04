package com.banking.onboarding.mapper;

import com.banking.onboarding.domain.Address;
import com.banking.onboarding.domain.Customer;
import com.banking.onboarding.dto.CustomerDtos;
import org.mapstruct.*;

/**
 * MapStruct mapper — eliminates manual field-by-field mapping.
 * Spring component model means it's auto-injected as a bean.
 */
@Mapper(
    componentModel = "spring",
    nullValuePropertyMappingStrategy = NullValuePropertyMappingStrategy.IGNORE,
    unmappedTargetPolicy = ReportingPolicy.WARN
)
public interface CustomerMapper {

    @Mapping(target = "customerId",   ignore = true)
    @Mapping(target = "kycStatus",    constant = "PENDING")
    @Mapping(target = "onboardingStatus", constant = "INITIATED")
    @Mapping(target = "riskCategory", constant = "LOW")
    @Mapping(target = "documents",    ignore = true)
    @Mapping(target = "address",      source = "address")
    Customer toEntity(CustomerDtos.OnboardingRequest request);

    @Mapping(target = "fullName",         expression = "java(customer.getFullName())")
    @Mapping(target = "gender",           expression = "java(customer.getGender().name())")
    @Mapping(target = "kycStatus",        expression = "java(customer.getKycStatus().name())")
    @Mapping(target = "onboardingStatus", expression = "java(customer.getOnboardingStatus().name())")
    @Mapping(target = "riskCategory",     expression = "java(customer.getRiskCategory().name())")
    @Mapping(target = "idType",           expression = "java(customer.getIdType() != null ? customer.getIdType().name() : null)")
    @Mapping(target = "employmentType",   expression = "java(customer.getEmploymentType() != null ? customer.getEmploymentType().name() : null)")
    @Mapping(target = "address",          source = "address")
    CustomerDtos.CustomerResponse toResponse(Customer customer);

    @Mapping(target = "formatted", expression = "java(address.getFormatted())")
    CustomerDtos.AddressResponse toAddressResponse(Address address);

    Address toAddress(CustomerDtos.AddressRequest request);

    @Mapping(target = "fullName",         expression = "java(customer.getFullName())")
    @Mapping(target = "kycStatus",        expression = "java(customer.getKycStatus().name())")
    @Mapping(target = "onboardingStatus", expression = "java(customer.getOnboardingStatus().name())")
    CustomerDtos.CustomerSummary toSummary(Customer customer);
}
