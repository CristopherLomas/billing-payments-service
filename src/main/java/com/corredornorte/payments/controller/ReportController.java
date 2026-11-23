package com.corredornorte.payments.controller;

import com.corredornorte.payments.dto.ReconciliationResponse;
import com.corredornorte.payments.dto.RevenueReportResponse;
import com.corredornorte.payments.service.ReportService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.Parameter;
import io.swagger.v3.oas.annotations.media.Content;
import io.swagger.v3.oas.annotations.media.Schema;
import io.swagger.v3.oas.annotations.responses.ApiResponse;
import io.swagger.v3.oas.annotations.responses.ApiResponses;
import io.swagger.v3.oas.annotations.tags.Tag;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.format.annotation.DateTimeFormat;
import org.springframework.http.ProblemDetail;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.time.LocalDate;
import java.time.OffsetDateTime;

@Slf4j
@RestController
@RequestMapping("/api/v1/admin/payments")
@RequiredArgsConstructor
@Tag(name = "Reportes y Conciliación", description = "Endpoints de reportería financiera y cuadre de caja para Administradores")
public class ReportController {

    private final ReportService reportService;

    @GetMapping("/reports/revenue")
    @Operation(summary = "Reporte de recaudación financiera", description = "Genera el consolidado de ingresos en rango de fechas con desglose por método y estado.")
    @ApiResponses({
            @ApiResponse(responseCode = "200", description = "Reporte generado exitosamente",
                    content = @Content(schema = @Schema(implementation = RevenueReportResponse.class))),
            @ApiResponse(responseCode = "400", description = "Rango de fechas inválido",
                    content = @Content(schema = @Schema(implementation = ProblemDetail.class)))
    })
    public ResponseEntity<RevenueReportResponse> getRevenueReport(
            @Parameter(description = "Fecha y hora inicial en formato ISO-8601 (ej. 2026-11-01T00:00:00Z)")
            @RequestParam @DateTimeFormat(iso = DateTimeFormat.ISO.DATE_TIME) OffsetDateTime from,
            @Parameter(description = "Fecha y hora final en formato ISO-8601 (ej. 2026-11-30T23:59:59Z)")
            @RequestParam @DateTimeFormat(iso = DateTimeFormat.ISO.DATE_TIME) OffsetDateTime to) {

        RevenueReportResponse report = reportService.getRevenueReport(from, to);
        return ResponseEntity.ok(report);
    }

    @GetMapping("/reconciliation")
    @Operation(summary = "Reporte de conciliación diaria", description = "Devuelve el cuadre de caja del día con lista de transacciones y sumatorias por método.")
    @ApiResponses({
            @ApiResponse(responseCode = "200", description = "Reporte de conciliación obtenido",
                    content = @Content(schema = @Schema(implementation = ReconciliationResponse.class))),
            @ApiResponse(responseCode = "400", description = "Parámetro de fecha mal formado",
                    content = @Content(schema = @Schema(implementation = ProblemDetail.class)))
    })
    public ResponseEntity<ReconciliationResponse> getReconciliationReport(
            @Parameter(description = "Fecha a conciliar en formato YYYY-MM-DD (por defecto hoy en UTC)")
            @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate date) {

        ReconciliationResponse reconciliation = reportService.getReconciliationReport(date);
        return ResponseEntity.ok(reconciliation);
    }
}
