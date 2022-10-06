package gov.cdc.prime.router.fhirengine.engine

import gov.cdc.prime.router.ActionLogger
import gov.cdc.prime.router.InvalidReportMessage
import gov.cdc.prime.router.Metadata
import gov.cdc.prime.router.Report
import gov.cdc.prime.router.SettingsProvider
import gov.cdc.prime.router.azure.ActionHistory
import gov.cdc.prime.router.azure.BlobAccess
import gov.cdc.prime.router.azure.DatabaseAccess
import gov.cdc.prime.router.azure.Event
import gov.cdc.prime.router.azure.QueueAccess
import gov.cdc.prime.router.azure.db.Tables
import gov.cdc.prime.router.azure.db.enums.TaskAction
import gov.cdc.prime.router.fhirengine.translation.hl7.FhirToHl7Converter
import gov.cdc.prime.router.fhirengine.utils.FhirTranscoder
import gov.cdc.prime.router.fhirengine.utils.HL7MessageRSHelpers

/**
 * Translate a full-ELR FHIR message into the formats needed by any receivers from the route step
 * [metadata] mockable metadata
 * [settings] mockable settings
 * [db] mockable database access
 * [blob] mockable blob storage
 * [queue] mockable azure queue
 */
class FHIRTranslator(
    metadata: Metadata = Metadata.getInstance(),
    settings: SettingsProvider = this.settingsProviderSingleton,
    db: DatabaseAccess = this.databaseAccessSingleton,
    blob: BlobAccess = BlobAccess(),
    queue: QueueAccess = QueueAccess,
) : FHIREngine(metadata, settings, db, blob, queue) {

    /**
     * Accepts a FHIR [message], parses it, and generates translated output files for each item in the destinations
     *  element.
     * [actionHistory] and [actionLogger] ensure all activities are logged.
     * [metadata] will usually be null; mocked metadata can be passed in for unit tests
     */
    override fun doWork(
        message: RawSubmission,
        actionLogger: ActionLogger,
        actionHistory: ActionHistory,
        metadata: Metadata?
    ) {
        logger.trace("Translating FHIR file for receivers.")
        try {
            // pull fhir document and parse FHIR document
            val bundle = FhirTranscoder.decode(message.downloadContent())

            // track input report
            actionHistory.trackExistingInputReport(message.reportId)

            // todo: iterate over each receiver, translating on a per-receiver basis - for phase 1, hard coded to CO
            val receivers = listOf("co-phd.elr")

            receivers.forEach { recName ->
                val receiver = settings.findReceiver(recName)!!
                // todo: get schema for receiver - for Phase 1 this is solely going to convert to HL7 and not do any
                //  receiver-specific transforms
                val converter = FhirToHl7Converter(
                    bundle, "ORU_R01-base",
                    "metadata/hl7_mapping/ORU_R01"
                )
                val hl7Message = converter.convert()
                val bodyBytes = hl7Message.encode().toByteArray()

                // get a Report from the hl7 message
                val (report, event, blobInfo) = HL7MessageRSHelpers.takeHL7GetReport(
                    Event.EventAction.BATCH,
                    bodyBytes,
                    message.reportId,
                    receiver,
                    metadata,
                    actionHistory
                )

                // insert batch task into Task table
                this.insertBatchTask(
                    report,
                    report.bodyFormat.toString(),
                    blobInfo.blobUrl,
                    event
                )

                // nullify the previous task next_action
                db.updateTask(
                    message.reportId,
                    TaskAction.none,
                    null,
                    null,
                    finishedField = Tables.TASK.TRANSLATED_AT,
                    null
                )
            }
        } catch (e: IllegalArgumentException) {
            logger.error(e)
            actionLogger.error(InvalidReportMessage(e.message ?: ""))
        }
    }

    /**
     * Inserts a 'batch' task into the task table for the [report] in question. This is just a pass-through function
     * but is present here for proper separation of layers and testing. This may need to be modified in the future.
     * The task will track the [report] in the [format] specified and knows it is located at [reportUrl].
     * [nextAction] specifies what is going to happen next for this report
     *
     */
    private fun insertBatchTask(
        report: Report,
        reportFormat: String,
        reportUrl: String,
        nextAction: Event
    ) {
        db.insertTask(report, reportFormat, reportUrl, nextAction, null)
    }
}