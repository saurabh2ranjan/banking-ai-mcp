package com.banking.payment.mapper;

import com.banking.payment.domain.Payment;
import com.banking.payment.dto.PaymentDtos;
import org.mapstruct.*;

@Mapper(
    componentModel = "spring",
    nullValuePropertyMappingStrategy = NullValuePropertyMappingStrategy.IGNORE,
    unmappedTargetPolicy = ReportingPolicy.WARN
)
public interface PaymentMapper {

    @Mapping(target = "paymentType",    expression = "java(payment.getPaymentType().name())")
    @Mapping(target = "status",         expression = "java(payment.getStatus().name())")
    @Mapping(target = "fraudRiskLevel", expression = "java(payment.getFraudRiskLevel() != null ? payment.getFraudRiskLevel().name() : null)")
    PaymentDtos.PaymentResponse toResponse(Payment payment);

    @Mapping(target = "paymentType", expression = "java(payment.getPaymentType().name())")
    @Mapping(target = "status",      expression = "java(payment.getStatus().name())")
    PaymentDtos.PaymentSummary toSummary(Payment payment);
}
