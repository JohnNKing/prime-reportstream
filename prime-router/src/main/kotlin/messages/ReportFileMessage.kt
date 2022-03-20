package gov.cdc.prime.router.messages

/**
 * This is the response to the sender-file API.
 * The message contains the content of a report that is kept in the service.
 * This message largely comes from the report_file DB table.
 *
 *  [reportId] is the UUID of the report.
 *  [schemaTopic] is the topic of the pipeline
 *  [schemaName] refers to the schema of the report
 *  [contentType] is the MIME content name. Usually,
 *  [content] is a JSON escaped string of the
 *  [origin] provides information about blob that the content came from
 *  [request] provides information about the request that created the message.
 */
class ReportFileMessage(
    val reportId: String,
    val schemaTopic: String,
    val schemaName: String,
    val contentType: String,
    val content: String,
    val origin: Origin? = null,
    val request: Request? = null,
) {
    data class Origin(
        val bodyUrl: String = "",
        val sendingOrg: String = "",
        val sendingOrgClient: String = "",
        val receivingOrg: String = "",
        val receivingOrgSvc: String = "",
        val indices: List<Int> = emptyList(),
        val createdAt: String = "",
    )

    data class Request(
        val reportId: String = "",
        val indices: List<Int> = emptyList()
    )
}