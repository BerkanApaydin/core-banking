package com.bank.app.transfer.application.port.in;

import com.bank.app.transfer.application.dto.ReportCriteria;
import com.bank.app.transfer.application.dto.TransferReportResponse;

/**
 * Items plus whole-range totals in a single read (see
 * {@code GenerateTransferReportWithTotalsQueryImpl}).
 */
public interface GenerateTransferReportWithTotalsQuery {

    TransferReportResponse execute(ReportCriteria criteria);
}
