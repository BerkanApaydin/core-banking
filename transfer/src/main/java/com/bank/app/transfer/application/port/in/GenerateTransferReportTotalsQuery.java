package com.bank.app.transfer.application.port.in;

import com.bank.app.transfer.application.dto.ReportCriteria;
import com.bank.app.transfer.application.dto.TransferReportTotalsResponse;

/**
 * Range-wide transfer totals (count + volume in SQL), backing
 * {@code GET /api/v1/transfers/report/totals}. Paging fields of
 * {@link ReportCriteria} are ignored; the range guards are identical to the
 * paginated report so the two endpoints can never disagree on the window.
 */
public interface GenerateTransferReportTotalsQuery {

    TransferReportTotalsResponse execute(ReportCriteria criteria);
}
