package gov.cdc.prime.router.azure

import com.fasterxml.jackson.databind.SerializationFeature
import com.fasterxml.jackson.datatype.jsr310.JavaTimeModule
import com.fasterxml.jackson.module.kotlin.jacksonObjectMapper
import gov.cdc.prime.router.Metadata
import gov.cdc.prime.router.Organization
import gov.cdc.prime.router.Receiver
import gov.cdc.prime.router.Sender
import gov.cdc.prime.router.SettingsProvider
import gov.cdc.prime.router.azure.db.enums.SettingType
import gov.cdc.prime.router.azure.db.tables.pojos.Setting
import gov.cdc.prime.router.messages.OrganizationMessage
import gov.cdc.prime.router.messages.ReceiverMessage
import gov.cdc.prime.router.messages.SenderMessage
import gov.cdc.prime.router.messages.SettingMessage
import gov.cdc.prime.router.messages.SettingMetadata
import org.jooq.JSONB
import java.time.OffsetDateTime

/**
 * Settings for Organization, Receivers, and Senders from the Azure Database.
 * Contains all business logic regarding settings as well as JSON serialization.
 */
class SettingsFacade(
    private val metadata: Metadata,
    private val db: DatabaseAccess = DatabaseAccess()
) : SettingsProvider {
    enum class AccessResult {
        SUCCESS,
        CREATED,
        NOT_FOUND,
        BAD_REQUEST
    }

    private val mapper = jacksonObjectMapper()

    init {
        // Format OffsetDateTime as an ISO string
        mapper.registerModule(JavaTimeModule())
        mapper.disable(SerializationFeature.WRITE_DATES_AS_TIMESTAMPS)
    }

    override val organizations: Collection<Organization>
        get() = findSettings(OrganizationMessage::class.java)

    override val senders: Collection<Sender>
        get() = findSettings(SenderMessage::class.java)

    override val receivers: Collection<Receiver>
        get() = findSettings(ReceiverMessage::class.java)

    override fun findOrganization(name: String): Organization? {
        return findSetting(name, OrganizationMessage::class.java)
    }

    override fun findReceiver(fullName: String): Receiver? {
        val pair = Receiver.parseFullName(fullName)
        return findSetting(pair.second, ReceiverMessage::class.java, pair.first)
    }

    override fun findSender(fullName: String): Sender? {
        val pair = Sender.parseFullName(fullName)
        return findSetting(pair.second, SenderMessage::class.java, pair.first)
    }

    override fun findOrganizationAndReceiver(fullName: String): Pair<Organization, Receiver>? {
        return findOrganizationAndReceiver(fullName, null)
    }

    fun <T : SettingMessage> findSettingAsJson(
        name: String,
        clazz: Class<T>,
        organizationName: String? = null,
    ): String? {
        val result = findSetting(name, clazz, organizationName) ?: return null
        return mapper.writeValueAsString(result)
    }

    fun getLastModified(): OffsetDateTime? {
        return db.fetchLastModified()
    }

    private fun <T : SettingMessage> findSetting(
        name: String,
        clazz: Class<T>,
        organizationName: String? = null
    ): T? {
        val setting = db.transactReturning { txn ->
            val settingType = settingTypeFromClass(clazz.name)
            if (organizationName != null)
                db.fetchSetting(settingType, name, organizationName, txn)
            else
                db.fetchSetting(settingType, name, parentId = null, txn)
        } ?: return null
        val result = mapper.readValue(setting.values.data(), clazz)
        result.meta = SettingMetadata(setting.version, setting.createdBy, setting.createdAt)
        return result
    }

    fun <T : SettingMessage> findSettingsAsJson(clazz: Class<T>): String {
        val list = findSettings(clazz)
        return mapper.writeValueAsString(list)
    }

    private fun <T : SettingMessage> findSettings(clazz: Class<T>): List<T> {
        val settingType = settingTypeFromClass(clazz.name)
        val settings = db.transactReturning { txn ->
            db.fetchSettings(settingType, txn)
        }
        return settings.map {
            val result = mapper.readValue(it.values.data(), clazz)
            result.meta = SettingMetadata(it.version, it.createdBy, it.createdAt)
            result
        }
    }

    fun <T : SettingMessage> findSettingsAsJson(organizationName: String, clazz: Class<T>): Pair<AccessResult, String> {
        val (result, settings, errorMessage) = db.transactReturning { txn ->
            val organization = db.fetchSetting(SettingType.ORGANIZATION, organizationName, null, txn)
                ?: return@transactReturning Triple(
                    AccessResult.NOT_FOUND, emptyList(), errorJson("Organization not found")
                )
            val settingType = settingTypeFromClass(clazz.name)
            val settings = db.fetchSettings(settingType, organization.settingId, txn)
            Triple(AccessResult.SUCCESS, settings, "")
        }
        return if (result == AccessResult.SUCCESS) {
            val settingsWithMeta = settings.map {
                val setting = mapper.readValue(it.values.data(), clazz)
                setting.meta = SettingMetadata(it.version, it.createdBy, it.createdAt)
                setting
            }
            val json = mapper.writeValueAsString(settingsWithMeta)
            Pair(result, json)
        } else {
            Pair(result, errorMessage)
        }
    }

    fun findOrganizationAndReceiver(fullName: String, txn: DataAccessTransaction?): Pair<Organization, Receiver>? {
        val (organizationName, receiverName) = Receiver.parseFullName(fullName)
        val (organizationSetting, receiverSetting) = db.fetchOrganizationAndSetting(
            SettingType.RECEIVER, receiverName, organizationName, txn
        ) ?: return null
        val receiver = mapper.readValue(receiverSetting.values.data(), ReceiverMessage::class.java)
        val organization = mapper.readValue(organizationSetting.values.data(), OrganizationMessage::class.java)
        return Pair(organization, receiver)
    }

    fun <T : SettingMessage> putSetting(
        name: String,
        json: String,
        claims: AuthenticatedClaims,
        clazz: Class<T>,
        organizationName: String? = null
    ): Pair<AccessResult, String> {
        return db.transactReturning { txn ->
            // Check that the orgName is valid (or null)
            val organizationId = organizationName?.let {
                val organization = db.fetchSetting(SettingType.ORGANIZATION, organizationName, null, txn)
                    ?: return@transactReturning Pair(AccessResult.BAD_REQUEST, errorJson("No organization match"))
                organization.settingId
            }
            // Check the payload
            val (valid, error, normalizedJson) = validateAndNormalize(json, clazz, name, organizationName)
            if (!valid)
                return@transactReturning Pair(AccessResult.BAD_REQUEST, errorJson(error ?: "validation error"))
            if (normalizedJson == null) error("Internal Error: validation error")

            // Find the current setting to see if this is a create or an update operation
            val settingType = settingTypeFromClass(clazz.name)
            val current = db.fetchSetting(settingType, name, organizationId, txn)
            val currentVersion = current?.version ?: db.findSettingVersion(settingType, name, organizationId, txn)

            // Form the new setting
            val settingMetadata = SettingMetadata(currentVersion + 1, claims.userName, OffsetDateTime.now())
            val setting = Setting(
                null, settingType, name, organizationId,
                normalizedJson, false, true,
                settingMetadata.version, settingMetadata.createdBy, settingMetadata.createdAt
            )

            // Now insert
            val (accessResult, resultMetadata) = when {
                current == null -> {
                    // No existing setting, just add to the new setting to the table
                    db.insertSetting(setting, txn)
                    Pair(AccessResult.CREATED, settingMetadata)
                }
                current.values == normalizedJson -> {
                    // Don't create a new version if the payload matches the current version
                    Pair(AccessResult.SUCCESS, SettingMetadata(current.version, current.createdBy, current.createdAt))
                }
                else -> {
                    // Update existing setting by deactivate the current setting and inserting a new version
                    db.deactivateSetting(current.settingId, txn)
                    val newId = db.insertSetting(setting, txn)
                    // If inserting an org, update all children settings to point to the new org
                    if (settingType == SettingType.ORGANIZATION)
                        db.updateOrganizationId(current.settingId, newId, txn)
                    Pair(AccessResult.SUCCESS, settingMetadata)
                }
            }

            val settingResult = mapper.readValue(setting.values.data(), clazz)
            settingResult.meta = SettingMetadata(
                resultMetadata.version,
                resultMetadata.createdBy,
                resultMetadata.createdAt
            )

            val outputJson = mapper.writeValueAsString(settingResult)
            Pair(accessResult, outputJson)
        }
    }

    /**
     * Make sure the input json is valid, consistent and normalized
     */
    private fun <T : SettingMessage> validateAndNormalize(
        json: String,
        clazz: Class<T>,
        name: String,
        organizationName: String? = null,
    ): Triple<Boolean, String?, JSONB?> {
        val input = try {
            mapper.readValue(json, clazz)
        } catch (ex: Exception) {
            null
        } ?: return Triple(false, "Could not parse JSON payload", null)
        if (input.name != name)
            return Triple(false, "Payload and path name do not match", null)
        if (input.organizationName != organizationName)
            return Triple(false, "Payload and path organization name do not match", null)
        input.consistencyErrorMessage(metadata) ?.let { return Triple(false, it, null) }
        val normalizedJson = JSONB.valueOf(mapper.writeValueAsString(input))
        return Triple(true, null, normalizedJson)
    }

    fun <T : SettingMessage> deleteSetting(
        name: String,
        claims: AuthenticatedClaims,
        clazz: Class<T>,
        organizationName: String? = null
    ): Pair<AccessResult, String> {
        return db.transactReturning { txn ->
            val settingType = settingTypeFromClass(clazz.name)
            val current = if (organizationName != null)
                db.fetchSetting(settingType, name, organizationName, txn)
            else
                db.fetchSetting(settingType, name, parentId = null, txn)
            if (current == null) return@transactReturning Pair(AccessResult.NOT_FOUND, errorJson("Item not found"))
            val settingMetadata = SettingMetadata(current.version + 1, claims.userName, OffsetDateTime.now())

            db.insertDeletedSettingAndChildren(current.settingId, settingMetadata, txn)
            db.deactivateSettingAndChildren(current.settingId, txn)

            val outputJson = mapper.writeValueAsString(settingMetadata)
            Pair(AccessResult.SUCCESS, outputJson)
        }
    }

    companion object {
        // The SettingAccess is heavy-weight object (because it contains a Jackson Mapper) so reuse it when possible
        val common: SettingsFacade by lazy {
            SettingsFacade(Metadata.getInstance(), DatabaseAccess())
        }

        private fun settingTypeFromClass(className: String): SettingType {
            return when (className) {
                OrganizationMessage::class.qualifiedName -> SettingType.ORGANIZATION
                ReceiverMessage::class.qualifiedName -> SettingType.RECEIVER
                SenderMessage::class.qualifiedName -> SettingType.SENDER
                else -> error("Internal Error: Unknown classname: $className")
            }
        }

        private fun errorJson(message: String): String {
            return """{"error": "$message"}"""
        }
    }
}
