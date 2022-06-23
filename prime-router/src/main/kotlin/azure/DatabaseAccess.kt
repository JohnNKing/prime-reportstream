package gov.cdc.prime.router.azure

import com.zaxxer.hikari.HikariConfig
import com.zaxxer.hikari.HikariDataSource
import gov.cdc.prime.router.Organization
import gov.cdc.prime.router.Report
import gov.cdc.prime.router.ReportId
import gov.cdc.prime.router.azure.db.Routines
import gov.cdc.prime.router.azure.db.Tables
import gov.cdc.prime.router.azure.db.Tables.ACTION
import gov.cdc.prime.router.azure.db.Tables.COVID_RESULT_METADATA
import gov.cdc.prime.router.azure.db.Tables.EMAIL_SCHEDULE
import gov.cdc.prime.router.azure.db.Tables.ITEM_LINEAGE
import gov.cdc.prime.router.azure.db.Tables.JTI_CACHE
import gov.cdc.prime.router.azure.db.Tables.RECEIVER_CONNECTION_CHECK_RESULTS
import gov.cdc.prime.router.azure.db.Tables.REPORT_FACILITIES
import gov.cdc.prime.router.azure.db.Tables.REPORT_LINEAGE
import gov.cdc.prime.router.azure.db.Tables.SETTING
import gov.cdc.prime.router.azure.db.Tables.TASK
import gov.cdc.prime.router.azure.db.enums.SettingType
import gov.cdc.prime.router.azure.db.enums.TaskAction
import gov.cdc.prime.router.azure.db.tables.ReportFile.REPORT_FILE
import gov.cdc.prime.router.azure.db.tables.pojos.Action
import gov.cdc.prime.router.azure.db.tables.pojos.CovidResultMetadata
import gov.cdc.prime.router.azure.db.tables.pojos.ItemLineage
import gov.cdc.prime.router.azure.db.tables.pojos.JtiCache
import gov.cdc.prime.router.azure.db.tables.pojos.ListSendFailures
import gov.cdc.prime.router.azure.db.tables.pojos.ReportFile
import gov.cdc.prime.router.azure.db.tables.pojos.SenderItems
import gov.cdc.prime.router.azure.db.tables.pojos.Setting
import gov.cdc.prime.router.azure.db.tables.pojos.Task
import gov.cdc.prime.router.azure.db.tables.records.CovidResultMetadataRecord
import gov.cdc.prime.router.azure.db.tables.records.TaskRecord
import gov.cdc.prime.router.common.Environment
import org.apache.logging.log4j.kotlin.Logging
import org.flywaydb.core.Flyway
import org.jooq.Configuration
import org.jooq.DSLContext
import org.jooq.Field
import org.jooq.JSON
import org.jooq.SQLDialect
import org.jooq.impl.DSL
import org.jooq.impl.DSL.inline
import org.postgresql.Driver
import java.sql.Connection
import java.sql.DriverManager
import java.time.OffsetDateTime
import java.util.UUID
import javax.sql.DataSource

const val databaseVariable = "POSTGRES_URL"
const val userVariable = "POSTGRES_USER"
const val passwordVariable = "POSTGRES_PASSWORD"

// general max length of free from metadata strings since jooq/postgres
// does not truncate values when persisting to the database
const val METADATA_MAX_LENGTH = 512

typealias DataAccessTransaction = Configuration

/**
 * A data access layer for the database. The idea or abstraction is CRUD on tables in the database.
 * The interface uses the POJO abstractions of the database tables.
 *
 * The companion object does the connection pooling and settings.
 */
class DatabaseAccess(private val create: DSLContext) : Logging {
    constructor(
        dataSource: DataSource = commonDataSource
    ) : this(DSL.using(dataSource, SQLDialect.POSTGRES))
    constructor(connection: Connection) : this(DSL.using(connection, SQLDialect.POSTGRES))

    fun checkConnection() {
        create.selectFrom(REPORT_FILE).where(REPORT_FILE.REPORT_ID.eq(UUID.randomUUID())).fetch()
    }

    /** Make the other calls in the context of a SQL transaction */
    fun transact(block: (txn: DataAccessTransaction) -> Unit) {
        create.transaction { txn: Configuration -> block(txn) }
    }

    /** Make the other calls in the context of a SQL transaction, returning a result */
    fun <T> transactReturning(block: (txn: DataAccessTransaction) -> T): T {
        return create.transactionResult { txn: Configuration -> block(txn) }
    }

    /*
     * Task queries
     */

    /** Fetch a task record and lock it so other connections can grab it */
    fun fetchAndLockTask(reportId: ReportId, txn: DataAccessTransaction): Task {
        return DSL.using(txn)
            .selectFrom(TASK)
            .where(TASK.REPORT_ID.eq(reportId))
            .forUpdate()
            .fetchOne()
            ?.into(Task::class.java)
            ?: error("Could not find $reportId that matches a task")
    }

    /**
     * Fetch multiple task records and lock them so other connections cannot grab them.
     * If the [at] time is not null, do an exact match against next_action_at. TODO remove this option - not used.
     * If the [at] time is null, limit query to when next_action_at is after [backstopTime].
     *
     * BatchFunction's job in life is operating on a timer, so it only operates on Tasks with nonnull next_action_at.
     *
     * Note that this uses TASK.next_action_at as the backstopTime comparison, rather than TASK.created_at.
     * This allows us to manually recover from failures by simply setting a TASK's next_action_at in the future.
     * This also avoids "losing" batch tasks if a receiver's [Receiver.Timing.numberPerDay] changes while there is
     * work-in-progress waiting to be batch.  For example, we might change the setting from once a day to once an hour.
     */
    fun fetchAndLockBatchTasksForOneReceiver(
        at: OffsetDateTime?,
        receiverFullName: String,
        limit: Int,
        backstopTime: OffsetDateTime?,
        txn: DataAccessTransaction
    ): List<Task> {
        val cond =
            if (at == null) {
                TASK.RECEIVER_NAME.eq(receiverFullName)
                    .and(TASK.NEXT_ACTION.eq(TaskAction.batch))
                    .and(TASK.NEXT_ACTION_AT.greaterOrEqual(backstopTime))
            } else {
                TASK.RECEIVER_NAME
                    .eq(receiverFullName)
                    .and(TASK.NEXT_ACTION.eq(TaskAction.batch))
                    .and(TASK.NEXT_ACTION_AT.eq(at))
            }
        return DSL.using(txn)
            .selectFrom(TASK)
            .where(cond)
            .orderBy(TASK.CREATED_AT)
            .limit(limit)
            .forUpdate()
            .skipLocked() // Allows the same query to run in parallel. Otherwise, the query would lock the table.
            .fetch()
            .into(Task::class.java)
    }

    /**
     * Returns true if there is already a record from the last 7 days
     * in the item_lineage table that matches the passed in [itemHash]
     * @return true if item is duplicate
     */
    fun isDuplicateItem(
        itemHash: String,
        txn: DataAccessTransaction? = null
    ): Boolean {
        val ctx = if (txn != null) DSL.using(txn) else create
        val weekAgo = OffsetDateTime.now().minusDays(7)
        return ctx
            .fetchExists(
                ctx.selectFrom(ITEM_LINEAGE)
                    .where(ITEM_LINEAGE.ITEM_HASH.eq(itemHash))
                    .and(ITEM_LINEAGE.CREATED_AT.greaterOrEqual(weekAgo))
            )
    }

    /**
     * Get the number of outstanding actions to batch for a specific receiver
     */
    fun fetchNumReportsNeedingBatch(
        receiverFullName: String,
        backstopTime: OffsetDateTime,
        txn: DataAccessTransaction?
    ): Int {
        val ctx = if (txn != null) DSL.using(txn) else create
        return ctx
            .select(TASK.asterisk())
            .from(TASK)
            .where(TASK.NEXT_ACTION.eq(TaskAction.batch))
            .and(TASK.RECEIVER_NAME.eq(receiverFullName))
            .and(TASK.NEXT_ACTION_AT.greaterOrEqual(backstopTime))
            .count()
    }

    /**
     * Given a receiver, finds out if the receiver (identified by [receiverOrg] and [receiverSvc]) had at least
     * one 'send' action within after the passed in [checkTime].
     */
    fun checkRecentlySent(
        receiverOrg: String,
        receiverSvc: String,
        checkTime: OffsetDateTime,
        txn: DataAccessTransaction?
    ): Boolean {
        val ctx = if (txn != null) DSL.using(txn) else create
        return ctx
            .select(ACTION.asterisk())
            .from(ACTION)
            .join(Tables.REPORT_FILE)
            .on(ACTION.ACTION_ID.eq(Tables.REPORT_FILE.ACTION_ID))
            .where(Tables.REPORT_FILE.RECEIVING_ORG.eq(receiverOrg))
            .and(Tables.REPORT_FILE.RECEIVING_ORG_SVC.eq(receiverSvc))
            .and(ACTION.CREATED_AT.greaterOrEqual(checkTime))
            .and(ACTION.ACTION_NAME.eq(TaskAction.send))
            .count() > 0
    }

    fun fetchTask(reportId: ReportId): Task {
        return create.selectFrom(TASK)
            .where(TASK.REPORT_ID.eq(reportId))
            .fetchOne()
            ?.into(Task::class.java)
            ?: error("Could not find $reportId that matches a task")
    }

    /** Take a report and put into the database after already serializing the body of the report */
    fun insertTask(
        report: Report,
        bodyFormat: String,
        bodyUrl: String,
        nextAction: Event,
        txn: DataAccessTransaction? = null,
    ) {
        fun insert(txn: Configuration) {
            val task = createTaskRecord(report, bodyFormat, bodyUrl, nextAction)
            DSL.using(txn).executeInsert(task)
        }

        if (txn != null) {
            insert(txn)
        } else {
            create.transaction { innerTxn -> insert(innerTxn) }
        }
    }

    fun updateTask(
        reportId: ReportId,
        nextAction: TaskAction,
        nextActionAt: OffsetDateTime?,
        retryToken: String?,
        finishedField: Field<OffsetDateTime>,
        txn: DataAccessTransaction?
    ) {
        val ctx = if (txn != null) DSL.using(txn) else create
        ctx.update(TASK)
            .set(TASK.NEXT_ACTION, nextAction)
            .set(TASK.NEXT_ACTION_AT, nextActionAt)
            .set(TASK.RETRY_TOKEN, if (retryToken != null) JSON.valueOf(retryToken) else null)
            .set(finishedField, OffsetDateTime.now())
            .where(TASK.REPORT_ID.eq(reportId))
            .execute()
    }

    /*
     * ActionHistory queries
     */

    /** You should include org as a search criteria to enforce authorization to get that report. */
    fun fetchReportFile(
        reportId: ReportId,
        org: Organization? = null,
        txn: DataAccessTransaction? = null
    ): ReportFile {
        val ctx = if (txn != null) DSL.using(txn) else create
        val cond =
            if (org == null) {
                Tables.REPORT_FILE.REPORT_ID.eq(reportId)
            } else {
                Tables.REPORT_FILE
                    .REPORT_ID
                    .eq(reportId)
                    .and(Tables.REPORT_FILE.RECEIVING_ORG.eq(org.name))
            }
        return ctx.selectFrom(Tables.REPORT_FILE)
            .where(cond)
            .fetchOne()
            ?.into(ReportFile::class.java)
            ?: error(
                "Could not find $reportId in REPORT_FILE" +
                    if (org != null) {
                        " associated with organization ${org.name}"
                    } else ""
            )
    }

    /**
     * Fetch a report_file row based on the passed blob file name.
     */
    fun fetchReportFileByBlobURL(
        fileName: String,
        txn: DataAccessTransaction? = null
    ): ReportFile? {
        val ctx = if (txn != null) DSL.using(txn) else create
        return ctx.selectFrom(Tables.REPORT_FILE)
            .where(REPORT_FILE.BODY_URL.like("%$fileName"))
            .fetchOneInto(ReportFile::class.java)
    }

    /**
     * Fetch a set of report_file rows based on the passed in list of [reportIds].
     */
    fun fetchReportFileByIds(
        reportIds: List<ReportId>,
        txn: DataAccessTransaction? = null
    ): List<ReportFile> {
        val ctx = if (txn != null) DSL.using(txn) else create
        return ctx.selectFrom(Tables.REPORT_FILE)
            .where(REPORT_FILE.REPORT_ID.`in`(reportIds))
            .fetchInto(ReportFile::class.java)
    }

    fun fetchAllInternalReports(
        createdDateTime: OffsetDateTime? = null,
        txn: DataAccessTransaction? = null
    ): List<ReportFile> {
        val createdDt = createdDateTime ?: OffsetDateTime.now().minusDays(30)
        val ctx = if (txn != null) DSL.using(txn) else create
        val cond =
            Tables.REPORT_FILE
                .SENDING_ORG
                .isNotNull
                .and(Tables.REPORT_FILE.BODY_FORMAT.eq("INTERNAL"))
                .and(Tables.REPORT_FILE.CREATED_AT.ge(createdDt))
        return ctx.selectFrom(Tables.REPORT_FILE).where(cond).fetchArray().map {
            it.into(ReportFile::class.java)
        }
    }

    fun fetchSenderItems(
        receiverReportId: ReportId,
        receiverReportIndex: Int,
        limit: Int,
        txn: DataAccessTransaction? = null
    ): List<SenderItems> {
        val ctx = if (txn != null) DSL.using(txn) else create
        return ctx
            .selectFrom(Routines.senderItems(receiverReportId, receiverReportIndex, limit))
            .fetchInto(SenderItems::class.java)
    }

    fun fetchSingleMetadata(
        messageID: String,
        txn: DataAccessTransaction? = null
    ): CovidResultMetadata? {
        val ctx = if (txn != null) DSL.using(txn) else create
        return ctx.selectFrom(Tables.COVID_RESULT_METADATA)
            .where(Tables.COVID_RESULT_METADATA.MESSAGE_ID.eq(messageID.toString()))
            .fetchOne()
            ?.into(CovidResultMetadata::class.java)
    }

    /** Returns null if report has no item-level lineage info tracked. */
    fun fetchItemLineagesForReport(
        reportId: ReportId,
        itemCount: Int,
        txn: DataAccessTransaction? = null
    ): List<ItemLineage>? {
        val ctx = if (txn != null) DSL.using(txn) else create
        val itemLineages =
            ctx.selectFrom(Tables.ITEM_LINEAGE)
                .where(Tables.ITEM_LINEAGE.CHILD_REPORT_ID.eq(reportId))
                .orderBy(
                    Tables.ITEM_LINEAGE.CHILD_INDEX
                ) // todo Don't know if this will be too slow?  Use a map in mem?
                .fetch()
                .into(ItemLineage::class.java)
                .toList()
        // sanity check.  If there are lineages, every record up to itemCount should have at least
        // one lineage.
        // OK to have more than one lineage.  Eg, a merge.
        if (itemLineages.isEmpty()) {
            return null
        } else {
            if (itemLineages.size < itemCount)
                error(
                    "For $reportId, must have at least $itemCount item lineages. There were ${itemLineages.size}"
                )
            val uniqueIndexCount = itemLineages.map { it.childIndex }.toSet().size
            if (uniqueIndexCount != itemCount)
                error(
                    "For report $reportId, expected $itemCount unique indexes; there were $uniqueIndexCount"
                )
        }
        return itemLineages
    }

    /**
     * Fetch an action for a given [reportId].
     * @param txn an optional database transaction
     * @return an Action, or null if no such reportId exists.
     */
    fun fetchActionForReportId(
        reportId: UUID,
        txn: DataAccessTransaction? = null
    ): Action? {
        val ctx = if (txn != null) DSL.using(txn) else create
        return ctx.select(ACTION.asterisk())
            .from(ACTION)
            .join(REPORT_FILE)
            .on(REPORT_FILE.ACTION_ID.eq(ACTION.ACTION_ID))
            .where(REPORT_FILE.REPORT_ID.eq(reportId))
            .fetchOne()
            ?.into(Action::class.java)
    }

    /**
     * Fetch a single action obj for a given [actionId].
     * @param txn an optional database transaction
     * @return an Action.  Returns null if no such action exists.
     */
    fun fetchAction(
        actionId: Long,
        txn: DataAccessTransaction? = null
    ): Action? {
        val ctx = if (txn != null) DSL.using(txn) else create
        return ctx.selectFrom(ACTION)
            .where(ACTION.ACTION_ID.eq(actionId))
            .fetchOne()
            ?.into(Action::class.java)
    }

    fun fetchDownloadableReportFiles(
        since: OffsetDateTime?,
        orgName: String,
        txn: DataAccessTransaction? = null,
    ): List<ReportFile> {
        val ctx = if (txn != null) DSL.using(txn) else create
        val cond =
            if (since == null) {
                Tables.REPORT_FILE
                    .RECEIVING_ORG
                    .eq(orgName)
                    .and(Tables.REPORT_FILE.NEXT_ACTION.eq(TaskAction.send))
            } else {
                Tables.REPORT_FILE
                    .RECEIVING_ORG
                    .eq(orgName)
                    .and(Tables.REPORT_FILE.NEXT_ACTION.eq(TaskAction.send))
                    .and(Tables.REPORT_FILE.CREATED_AT.ge(since))
            }

        return ctx.selectFrom(Tables.REPORT_FILE)
            .where(cond)
            .fetch()
            .into(ReportFile::class.java)
            .toList()
    }

    fun fetchChildReports(
        parentReportId: UUID,
        txn: DataAccessTransaction? = null,
    ): List<ReportId> {
        val ctx = if (txn != null) DSL.using(txn) else create
        return ctx.select(REPORT_LINEAGE.CHILD_REPORT_ID)
            .from(REPORT_LINEAGE)
            .where(REPORT_LINEAGE.PARENT_REPORT_ID.eq(parentReportId))
            .fetch()
            .into(ReportId::class.java)
            .toList()
    }

    /** Settings queries */
    fun fetchSetting(
        type: SettingType,
        name: String,
        parentId: Int?,
        txn: DataAccessTransaction
    ): Setting? {
        return DSL.using(txn)
            .selectFrom(SETTING)
            .where(
                SETTING.IS_ACTIVE.isTrue,
                SETTING.TYPE.eq(type),
                SETTING.NAME.eq(name),
                if (parentId != null) SETTING.ORGANIZATION_ID.eq(parentId)
                else SETTING.ORGANIZATION_ID.isNull
            )
            .fetchOne()
            ?.into(Setting::class.java)
    }

    fun fetchSetting(
        type: SettingType,
        name: String,
        organizationName: String,
        txn: DataAccessTransaction
    ): Setting? {
        val org = SETTING.`as`("org")
        val item = SETTING.`as`("item")
        return DSL.using(txn)
            .select(item.asterisk())
            .from(item)
            .join(org)
            .on(item.ORGANIZATION_ID.eq(org.SETTING_ID))
            .where(
                item.IS_ACTIVE.isTrue,
                item.TYPE.eq(type),
                item.NAME.eq(name),
                org.IS_ACTIVE.isTrue,
                org.TYPE.eq(SettingType.ORGANIZATION),
                org.ORGANIZATION_ID.isNull,
                org.NAME.eq(organizationName),
            )
            .fetchOne()
            ?.into(Setting::class.java)
    }

    /**
     * Fetch both the item and the organization of the item at the same time to optimize db queries
     */
    fun fetchOrganizationAndSetting(
        type: SettingType,
        name: String,
        organizationName: String,
        txn: DataAccessTransaction? = null
    ): Pair<Setting, Setting>? {
        val org = SETTING.`as`("org")
        val item = SETTING.`as`("item")
        val ctx = if (txn != null) DSL.using(txn) else create
        val result =
            ctx.select(item.asterisk(), org.asterisk())
                .from(item)
                .join(org)
                .on(item.ORGANIZATION_ID.eq(org.SETTING_ID))
                .where(
                    item.IS_ACTIVE.isTrue,
                    item.TYPE.eq(type),
                    item.NAME.eq(name),
                    org.IS_ACTIVE.isTrue,
                    org.TYPE.eq(SettingType.ORGANIZATION),
                    org.ORGANIZATION_ID.isNull,
                    org.NAME.eq(organizationName),
                )
                .fetchOne()
                ?: return null

        val itemSetting =
            Setting(
                result.get(item.SETTING_ID),
                result.get(item.TYPE),
                result.get(item.NAME),
                result.get(item.ORGANIZATION_ID),
                result.get(item.VALUES),
                result.get(item.IS_DELETED),
                result.get(item.IS_ACTIVE),
                result.get(item.VERSION),
                result.get(item.CREATED_BY),
                result.get(item.CREATED_AT)
            )
        val orgSetting =
            Setting(
                result.get(org.SETTING_ID),
                result.get(org.TYPE),
                result.get(org.NAME),
                result.get(org.ORGANIZATION_ID),
                result.get(org.VALUES),
                result.get(org.IS_DELETED),
                result.get(org.IS_ACTIVE),
                result.get(org.VERSION),
                result.get(org.CREATED_BY),
                result.get(org.CREATED_AT)
            )
        return Pair(orgSetting, itemSetting)
    }

    fun fetchSettings(type: SettingType, txn: DataAccessTransaction): List<Setting> {
        return DSL.using(txn)
            .selectFrom(SETTING)
            .where(SETTING.IS_ACTIVE.isTrue, SETTING.TYPE.eq(type))
            .orderBy(SETTING.SETTING_ID)
            .fetch()
            .into(Setting::class.java)
    }

    fun fetchSettings(
        type: SettingType,
        organizationId: Int,
        txn: DataAccessTransaction
    ): List<Setting> {
        return DSL.using(txn)
            .select()
            .from(SETTING)
            .where(
                SETTING.IS_ACTIVE.isTrue,
                SETTING.TYPE.eq(type),
                SETTING.ORGANIZATION_ID.eq(organizationId)
            )
            .orderBy(SETTING.SETTING_ID)
            .fetch()
            .into(Setting::class.java)
    }

    fun insertSetting(setting: Setting, txn: DataAccessTransaction): Int {
        return DSL.using(txn)
            .insertInto(SETTING)
            .set(SETTING.SETTING_ID, DSL.defaultValue(SETTING.SETTING_ID))
            .set(SETTING.TYPE, setting.type)
            .set(SETTING.ORGANIZATION_ID, setting.organizationId)
            .set(SETTING.NAME, setting.name)
            .set(SETTING.IS_ACTIVE, setting.isActive)
            .set(SETTING.IS_DELETED, setting.isDeleted)
            .set(SETTING.VALUES, setting.values)
            .set(SETTING.VERSION, setting.version)
            .set(SETTING.CREATED_AT, setting.createdAt)
            .set(SETTING.CREATED_BY, setting.createdBy)
            .returningResult(SETTING.SETTING_ID)
            .fetchOne()
            ?.value1()
            ?: error("Fetch error")
    }

    fun updateOrganizationId(
        currentOrganizationId: Int,
        newOrganizationId: Int,
        txn: DataAccessTransaction
    ) {
        DSL.using(txn)
            .update(SETTING)
            .set(SETTING.ORGANIZATION_ID, newOrganizationId)
            .where(SETTING.ORGANIZATION_ID.eq(currentOrganizationId), SETTING.IS_ACTIVE.isTrue)
            .execute()
    }

    /** search for a setting and it children, insert a deleted setting for those found */
    fun insertDeletedSettingAndChildren(
        settingId: Int,
        createdBy: String,
        createdAt: OffsetDateTime,
        txn: DataAccessTransaction
    ) {
        DSL.using(txn)
            .insertInto(
                SETTING,
                SETTING.TYPE,
                SETTING.ORGANIZATION_ID,
                SETTING.NAME,
                SETTING.VALUES,
                SETTING.IS_DELETED,
                SETTING.IS_ACTIVE,
                SETTING.VERSION,
                SETTING.CREATED_BY,
                SETTING.CREATED_AT
            )
            .select(
                DSL.select(
                    SETTING.TYPE,
                    SETTING.ORGANIZATION_ID,
                    SETTING.NAME,
                    SETTING.VALUES,
                    DSL.value(true, SETTING.IS_DELETED),
                    DSL.value(false, SETTING.IS_ACTIVE),
                    SETTING.VERSION.plus(1),
                    DSL.value(createdBy, SETTING.CREATED_BY),
                    DSL.value(createdAt, SETTING.CREATED_AT)
                )
                    .from(SETTING)
                    .where(
                        SETTING.SETTING_ID
                            .eq(settingId)
                            .or(SETTING.ORGANIZATION_ID.eq(settingId)),
                        SETTING.IS_ACTIVE.isTrue
                    )
            )
            .execute()
    }

    fun deactivateSetting(settingId: Int, txn: DataAccessTransaction) {
        DSL.using(txn)
            .update(SETTING)
            .set(SETTING.IS_ACTIVE, false)
            .where(SETTING.SETTING_ID.eq(settingId))
            .execute()
    }

    fun deactivateSettingAndChildren(settingId: Int, txn: DataAccessTransaction) {
        DSL.using(txn)
            .update(SETTING)
            .set(SETTING.IS_ACTIVE, false)
            .where(
                SETTING.SETTING_ID.eq(settingId).or(SETTING.ORGANIZATION_ID.eq(settingId)),
                SETTING.IS_ACTIVE.isTrue
            )
            .execute()
    }

    /**
     * Find the current setting version looking through inactive and active settings. Return -1 no
     * setting is found.
     */
    fun findSettingVersion(
        type: SettingType,
        name: String,
        organizationId: Int?,
        txn: DataAccessTransaction
    ): Int {
        return DSL.using(txn)
            .select(DSL.max(SETTING.VERSION))
            .from(SETTING)
            .where(
                SETTING.TYPE.eq(type),
                SETTING.NAME.eq(name),
                if (organizationId == null) SETTING.ORGANIZATION_ID.isNull
                else SETTING.ORGANIZATION_ID.eq(organizationId)
            )
            .fetchOne()
            ?.getValue(DSL.max(SETTING.VERSION))
            ?: -1
    }

    fun insertJti(jti: String, expiresAt: OffsetDateTime? = null, txn: DataAccessTransaction) {
        val jtiCache = JtiCache()
        jtiCache.jti = jti
        jtiCache.expiresAt = expiresAt
        DSL.using(txn).newRecord(JTI_CACHE, jtiCache).store()
    }

    fun deleteExpiredJtis(txn: DataAccessTransaction) {
        DSL.using(txn)
            .deleteFrom(JTI_CACHE)
            .where(JTI_CACHE.EXPIRES_AT.lt(OffsetDateTime.now()))
            .execute()
    }

    fun fetchJti(jti: String, txn: DataAccessTransaction): JtiCache? {
        return DSL.using(txn)
            .selectFrom(JTI_CACHE)
            .where(JTI_CACHE.JTI.eq(jti))
            .fetchOne()
            ?.into(JtiCache::class.java)
    }

    /** EmailSchedule queries */
    fun fetchEmailSchedules(txn: DataAccessTransaction? = null): List<String> {

        val ctx = if (txn != null) DSL.using(txn) else create
        return ctx.select(EMAIL_SCHEDULE.VALUES)
            .from(EMAIL_SCHEDULE)
            .where(EMAIL_SCHEDULE.IS_ACTIVE.eq(true))
            .fetch()
            .into(String::class.java)
    }

    fun insertEmailSchedule(body: String?, user: String, txn: DataAccessTransaction? = null): Int? {
        val ctx = if (txn != null) DSL.using(txn) else create
        return ctx.insertInto(EMAIL_SCHEDULE)
            .set(
                EMAIL_SCHEDULE.EMAIL_SCHEDULE_ID,
                DSL.defaultValue(EMAIL_SCHEDULE.EMAIL_SCHEDULE_ID)
            )
            .set(EMAIL_SCHEDULE.VALUES, JSON.valueOf(body))
            .set(EMAIL_SCHEDULE.IS_ACTIVE, true)
            .set(EMAIL_SCHEDULE.VERSION, 1)
            .set(EMAIL_SCHEDULE.CREATED_BY, user)
            .set(EMAIL_SCHEDULE.CREATED_AT, OffsetDateTime.now())
            .returningResult(EMAIL_SCHEDULE.EMAIL_SCHEDULE_ID)
            .fetchOne()
            ?.into(Int::class.java)
    }

    fun deleteEmailSchedule(id: Int, txn: DataAccessTransaction? = null) {
        val ctx = if (txn != null) DSL.using(txn) else create
        ctx.update(EMAIL_SCHEDULE)
            .set(EMAIL_SCHEDULE.IS_ACTIVE, false)
            .where(EMAIL_SCHEDULE.EMAIL_SCHEDULE_ID.eq(id))
            .execute()
    }

    fun getFacilitiesForDownloadableReport(
        reportId: ReportId,
        txn: DataAccessTransaction? = null
    ): List<Facility> {
        val ctx = if (txn != null) DSL.using(txn) else create
        val result =
            ctx.select(
                REPORT_FACILITIES.TESTING_LAB_NAME,
                REPORT_FACILITIES.TESTING_LAB_CITY,
                REPORT_FACILITIES.TESTING_LAB_STATE,
                REPORT_FACILITIES.TESTING_LAB_CLIA,
                REPORT_FACILITIES.POSITIVE,
                REPORT_FACILITIES.COUNT_RECORDS
            )
                .from(REPORT_FACILITIES(reportId))
                .fetch()

        return result.map {
            Facility.Builder(
                facility = it.get(COVID_RESULT_METADATA.TESTING_LAB_NAME),
                CLIA = it.get(COVID_RESULT_METADATA.TESTING_LAB_CLIA),
                total = it.get(REPORT_FACILITIES.COUNT_RECORDS),
                positive = it.get(REPORT_FACILITIES.POSITIVE),
                location =
                if (it.get(COVID_RESULT_METADATA.TESTING_LAB_CITY)
                    .isNullOrBlank()
                )
                    it.get(COVID_RESULT_METADATA.TESTING_LAB_STATE)
                else
                    "${it.get(COVID_RESULT_METADATA.TESTING_LAB_CITY)}, " +
                        it.get(COVID_RESULT_METADATA.TESTING_LAB_STATE)
            )
                .build()
        }
    }

    fun deleteTestDataForReportId(reportId: UUID, txn: DataAccessTransaction) {
        DSL.using(txn)
            .deleteFrom(COVID_RESULT_METADATA)
            .where(COVID_RESULT_METADATA.REPORT_ID.eq(reportId))
            .execute()
    }

    fun checkReportExists(reportId: ReportId, txn: DataAccessTransaction): Boolean {
        // this is how you do a select 1 from ... in jooq
        return (
            DSL.using(txn)
                .select(inline(1))
                .from(REPORT_FILE)
                .where(REPORT_FILE.REPORT_ID.eq(reportId))
                .count()
            ) > 0
    }

    /** Fetch the newest CreatedAt timestamp, active or deleted. */
    fun fetchLastModified(txn: DataAccessTransaction? = null): OffsetDateTime? {
        val ctx = if (txn != null) DSL.using(txn) else create
        return ctx.select(DSL.max(SETTING.CREATED_AT))
            .from(SETTING)
            .fetchOne()
            ?.getValue(DSL.max(SETTING.CREATED_AT))
    }

    /**
     * Saves the connection check result to the db
     */
    fun saveRemoteConnectionCheck(
        txn: DataAccessTransaction? = null,
        connectionCheck: CheckFunction.RemoteConnectionCheck
    ) {
        val ctx = if (txn != null) DSL.using(txn) else create
        val initiatedOn = connectionCheck.initiatedOn.atOffset(Environment.rsTimeZone)
        val completedOn = connectionCheck.completedAt.atOffset(Environment.rsTimeZone)
        ctx.insertInto(RECEIVER_CONNECTION_CHECK_RESULTS)
            .set(RECEIVER_CONNECTION_CHECK_RESULTS.ORGANIZATION_ID, connectionCheck.organizationId)
            .set(RECEIVER_CONNECTION_CHECK_RESULTS.RECEIVER_ID, connectionCheck.receiverId)
            .set(RECEIVER_CONNECTION_CHECK_RESULTS.CONNECTION_CHECK_RESULT, connectionCheck.checkResult)
            .set(RECEIVER_CONNECTION_CHECK_RESULTS.CONNECTION_CHECK_SUCCESSFUL, connectionCheck.checkSuccessful)
            .set(RECEIVER_CONNECTION_CHECK_RESULTS.CONNECTION_CHECK_STARTED_AT, initiatedOn)
            .set(RECEIVER_CONNECTION_CHECK_RESULTS.CONNECTION_CHECK_COMPLETED_AT, completedOn)
            .execute()
    }

    fun refreshMaterializedViews(tableName: String, txn: DataAccessTransaction? = null) {
        val ctx = if (txn != null) DSL.using(txn) else create
        Routines.refreshMaterializedViews(ctx.configuration(), tableName)
    }

    /**
     * Calls the "Last Mile" stored procedure
     * Returns all send_errors in the past daysBackSpan.
     * Nothing found returns empty Result
     */
    fun fetchSendFailures(
        daysBackSpan: Int = 30,
        txn: DataAccessTransaction? = null
    ): List<ListSendFailures> {
        val ctx = if (txn != null) DSL.using(txn) else create
        return ctx
            .selectFrom(Routines.listSendFailures(daysBackSpan))
            .fetchInto(ListSendFailures::class.java)
    }

    /** Common companion object */
    companion object {
        /** Global var. Set to false prior to the lazy init, to prevent flyway migrations */
        var isFlywayMigrationOK = true

        /**
         * Create a connection pool
         *
         * Dev Note: Why a connection pool with a "stateless" Azure function? The reason is that
         * Azure functions are actually deployed in a web server. That is functions amortize startup
         * costs by reusing an existing process for a function invocation. Hence, a connection pool
         * is a win in latency after the first initialization.
         */
        private val hikariDataSource: HikariDataSource by lazy {
            DriverManager.registerDriver(Driver())

            val password = System.getenv(passwordVariable)
            val user = System.getenv(userVariable)
            val databaseUrl = System.getenv(databaseVariable)
            val config = HikariConfig()
            config.jdbcUrl = databaseUrl
            config.username = user
            config.password = password
            config.addDataSourceProperty(
                "dataSourceClassName",
                "org.postgresql.ds.PGSimpleDataSource"
            )
            config.addDataSourceProperty("cachePrepStmts", "true")
            config.addDataSourceProperty("prepStmtCacheSize", "250")
            config.addDataSourceProperty("prepStmtCacheSqlLimit", "2048")
            config.addDataSourceProperty(
                "connectionTimeout",
                "60000"
            ) // Default is 30000 (30 seconds)

            // See this info why these are a good value
            //  https://github.com/brettwooldridge/HikariCP/wiki/About-Pool-Sizing
            config.minimumIdle = 2
            config.maximumPoolSize = 25
            // This strongly recommended to be set "be several seconds shorter than any database or
            // infrastructure
            // imposed connection time limit". Not sure what value is but have observed that
            // connection are closed
            // after about 10 minutes
            config.maxLifetime = 180000
            val dataSource = HikariDataSource(config)

            val flyway = Flyway.configure().dataSource(dataSource).load()
            if (isFlywayMigrationOK) {
                flyway.migrate()
            }

            dataSource
        }

        val commonDataSource: DataSource
            get() = hikariDataSource

        fun createTaskRecord(
            report: Report,
            bodyFormat: String,
            bodyUrl: String,
            nextAction: Event,
        ): TaskRecord {
            return TaskRecord(
                report.id,
                nextAction.eventAction.toTaskAction(),
                nextAction.at,
                report.schema.name,
                report.destination?.fullName ?: "",
                report.itemCount,
                bodyFormat,
                bodyUrl,
                report.createdDateTime,
                null,
                null,
                null,
                null,
                null,
                null,
                null
            )
        }

        fun createTask(
            report: Report,
            bodyFormat: String,
            bodyUrl: String,
            nextAction: Event,
        ): Task {
            return Task(
                report.id,
                nextAction.eventAction.toTaskAction(),
                nextAction.at,
                report.schema.name,
                report.destination?.fullName ?: "",
                report.itemCount,
                bodyFormat,
                bodyUrl,
                report.createdDateTime,
                null,
                null,
                null,
                null,
                null,
                null,
                null
            )
        }

        /**
         * Saves metadata to database. Since jooq/postgres does not truncate data that is too long, any
         * columns that can be of varying length are truncated so the batch transaction is not lost. All
         * of these columns have been normalized to METADATA_MAX_LENGTH out of convenience.
         * @param testData : the report meta data to persist
         * @param txn : the database transaction to use for this insert/update
         */
        fun saveTestData(testData: List<CovidResultMetadata>, txn: DataAccessTransaction) {
            DSL.using(txn)
                .batchInsert(
                    testData.map { td ->
                        CovidResultMetadataRecord().also { record ->
                            record.messageId = td.messageId?.take(METADATA_MAX_LENGTH)
                            record.previousMessageId = td.previousMessageId?.take(METADATA_MAX_LENGTH)
                            record.reportId = td.reportId
                            record.reportIndex = td.reportIndex
                            record.orderingProviderName =
                                td.orderingProviderName?.take(METADATA_MAX_LENGTH)
                            record.orderingProviderCounty =
                                td.orderingProviderCounty?.take(METADATA_MAX_LENGTH)
                            record.orderingProviderId =
                                td.orderingProviderId?.take(METADATA_MAX_LENGTH)
                            record.orderingProviderPostalCode = td.orderingProviderPostalCode
                            record.orderingProviderState =
                                td.orderingProviderState?.take(METADATA_MAX_LENGTH)
                            record.orderingFacilityCity =
                                td.orderingFacilityCity?.take(METADATA_MAX_LENGTH)
                            record.orderingFacilityCounty =
                                td.orderingFacilityCounty?.take(METADATA_MAX_LENGTH)
                            record.orderingFacilityName =
                                td.orderingFacilityName?.take(METADATA_MAX_LENGTH)
                            record.orderingFacilityPostalCode = td.orderingFacilityPostalCode
                            record.orderingFacilityState =
                                td.orderingFacilityState?.take(METADATA_MAX_LENGTH)
                            record.organizationName =
                                td.organizationName?.take(METADATA_MAX_LENGTH)
                            record.testResult = td.testResult?.take(METADATA_MAX_LENGTH)
                            record.testResultCode = td.testResultCode
                            record.equipmentModel = td.equipmentModel?.take(METADATA_MAX_LENGTH)
                            record.specimenCollectionDateTime = td.specimenCollectionDateTime
                            record.testingLabCity = td.testingLabCity?.take(METADATA_MAX_LENGTH)
                            record.testingLabClia = td.testingLabClia
                            record.testingLabCounty =
                                td.testingLabCounty?.take(METADATA_MAX_LENGTH)
                            record.testingLabName = td.testingLabName?.take(METADATA_MAX_LENGTH)
                            record.testingLabPostalCode = td.testingLabPostalCode
                            record.testingLabState =
                                td.testingLabState?.take(METADATA_MAX_LENGTH)
                            record.patientAge = td.patientAge
                            record.patientCounty = td.patientCounty?.take(METADATA_MAX_LENGTH)
                            record.patientCountry = td.patientCountry?.take(METADATA_MAX_LENGTH)
                            record.patientEthnicity = td.patientEthnicity
                            record.patientEthnicityCode = td.patientEthnicityCode
                            record.patientGender = td.patientGender
                            record.patientGenderCode = td.patientGenderCode
                            record.patientPostalCode = td.patientPostalCode
                            record.patientRace = td.patientRace
                            record.patientRaceCode = td.patientRaceCode
                            record.patientState = td.patientState?.take(METADATA_MAX_LENGTH)
                            record.siteOfCare = td.siteOfCare?.take(METADATA_MAX_LENGTH)
                            record.senderId = td.senderId?.take(METADATA_MAX_LENGTH)
                            record.testKitNameId = td.testKitNameId?.take(METADATA_MAX_LENGTH)
                            record.testPerformedLoincCode = td.testPerformedLoincCode?.take(METADATA_MAX_LENGTH)
                        }
                    }
                )
                .execute()
        }
    }
}