package movie.dto;

import java.math.BigDecimal;

import movie.service.ReportingService;

public record RevenueResponse(BigDecimal gross, BigDecimal refunded, BigDecimal net) {

    public static RevenueResponse from(ReportingService.Revenue revenue) {
        return new RevenueResponse(revenue.gross(), revenue.refunded(), revenue.net());
    }
}
