package com.cookieinformation.mobileconsents.storage

import android.content.Context
import com.cookieinformation.mobileconsents.ConsentItem.Type
import com.cookieinformation.mobileconsents.ProcessingPurpose
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.asSharedFlow
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.withContext
import okio.sink
import okio.source
import java.io.File
import java.io.IOException
import java.util.UUID

private const val userIdKey = "user_id_key"
private const val scratchFileSuffix = ".tmp"

/**
 * Storage responsible for saving generated [UUID] of user and all consent choices.
 * Data is stored in text file inside of App internal memory. All data is stored as Map<String, String> and
 * serialized to JSON. For serialization Moshi library is being used [MoshiFileHandler]
 * @param mutex used for synchronization of write operations.
 * @param file storage's text file.
 * @param fileHandler used for serialization.
 * @param saveConsentsMutableFlow MutableSharedFlow for emitting "save consents" events.
 * @param dispatcher coroutine IO dispatcher.
 */
@Suppress("LongParameterList")
internal class ConsentStorage(
  private val applicationContext: Context,
  private val mutex: Mutex,
  private val file: File,
  private val fileHandler: MoshiFileHandler,
  private val saveConsentsMutableFlow: MutableSharedFlow<Map<Type, Boolean>>,
  private val dispatcher: CoroutineDispatcher
) {

  val consentPreferences = ConsentPreferences(applicationContext)

  /**
   * SharedFlow for observing "save consents" events.
   */
  val saveConsentsFlow = saveConsentsMutableFlow.asSharedFlow()

  init {
    createStorageFile(file)
  }

  /**
   * Store list of consent choices ([ProcessingPurpose]) as Map of UUIDs and Booleans.
   */
  suspend fun storeConsentChoices(purposes: List<ProcessingPurpose>) {
    val writtenValues = writeValues(purposes.associate { it.type.name to it.consentGiven.toString() })
    saveConsentsMutableFlow.emit(writtenValues.toConsents())
    writtenValues.toConsents().toMap().forEach {
      consentPreferences.sharedPreferences().edit().putBoolean(it.key.name, it.value).commit()
    }
  }

  /**
   * Obtain user id necessary for posting consents. If there is no user id stored it is generated, it should only happen
   * once per App's installation.
   */
  suspend fun getUserId(): UUID {
    val userId = readValue(userIdKey)
    return if (userId == null) {
      val newUserId = UUID.randomUUID()
      writeValues(mapOf(userIdKey to newUserId.toString()))
      newUserId
    } else {
      UUID.fromString(userId)
    }
  }

  suspend fun getConsentChoice(consentId: UUID): Boolean {
    val value = readValue(consentId.toString())

    return value.toBoolean()
  }

  /**
   * Get all of stored consent choices. User id is filtered out.
   */
  fun getAllConsentChoices(): Map<Type, Boolean> = consentPreferences.getAllConsentChoices()

  /**
   * Maps key and value read from file to consents map
   */
  private fun Map<String, String>.toConsents(): Map<Type, Boolean> =
    filterKeys { it != userIdKey }
      .entries.associate {
        Type.findTypeByValue(it.key) to it.value.toBoolean()
      }

  /**
   * Write new values to storage. Old data is copied from storage file to scratch file, along with new data.
   * If any of new data has same key as old one, the old one will be overwritten with new value. After successful
   * copying the scratch file is renamed to the storage file. Function is synchronized via mutex shared across all
   * SDK instances.
   */
  private suspend fun writeValues(values: Map<String, String>) = withContext(dispatcher) {
    mutex.withLock {
      val scratchFile = File(file.path + scratchFileSuffix)
      try {
        val data = readAll() + values

        scratchFile.sink().use { sink ->
          fileHandler.writeTo(sink, data)
        }

        if (!scratchFile.renameTo(file)) {
          throw IOException("$scratchFile could not be renamed to $file")
        }
        data
      } catch (e: IOException) {
        scratchFile.delete()
        throw e
      }
    }
  }

  private suspend fun readValue(key: String): String? {
    val data = readAll()
    return data[key]
  }

  private suspend fun readAll(): Map<String, String> = withContext(dispatcher) {
    file.source().use(fileHandler::readFrom)
  }

  /**
   * Create storage file and its parent directories if needed.
   */
  private fun createStorageFile(file: File) {
    file.parentFile?.mkdirs()
    if (!file.exists()) {
      file.createNewFile()
    }
  }
}
