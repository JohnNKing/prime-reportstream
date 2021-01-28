package gov.cdc.prime.router.transport

import com.microsoft.azure.functions.ExecutionContext
import gov.cdc.prime.router.OrganizationService
import gov.cdc.prime.router.ReportId
import gov.cdc.prime.router.TransportType
import gov.cdc.prime.router.azure.ActionHistory

interface ITransport {
    /**
     * Send the content on the specific transport. Return retry information, if needed. Null, if not.
     *
     * @param orgService obj representing the organization and service to send to.
     * @param transportType the type of the transport (should always match the class)
     * @param contents being sent
     * @param inputReportId ID of the report input.
     * @param retryItems the retry items from the last effort, if it was unsuccessful
     * @return null, if successful. RetryItems if not successful.
     */
    fun send(
        orgService: OrganizationService,
        transportType: TransportType,
        contents: ByteArray,
        inputReportId: ReportId,
        sentReportId: ReportId,
        retryItems: RetryItems?,
        context: ExecutionContext,
        actionHistory: ActionHistory,
    ): RetryItems?
}