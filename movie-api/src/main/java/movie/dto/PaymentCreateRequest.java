package movie.dto;

import java.math.BigDecimal;

import jakarta.validation.constraints.DecimalMin;
import jakarta.validation.constraints.NotNull;

public record PaymentCreateRequest(@NotNull @DecimalMin("0.0") BigDecimal amount, String providerReference) {
}
