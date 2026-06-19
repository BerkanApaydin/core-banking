package com.bank.app.transfer.application.port.in;

import com.bank.app.transfer.application.dto.ReportCriteria;
import com.bank.app.transfer.application.dto.TransferReportResponse;

public interface GenerateTransferReportPort {
    TransferReportResponse execute(ReportCriteria criteria);
}
