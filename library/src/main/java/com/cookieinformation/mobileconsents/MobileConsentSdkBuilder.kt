package com.cookieinformation.mobileconsents

import android.annotation.SuppressLint
import android.content.Context
import android.widget.Toast
import com.cookieinformation.mobileconsents.ConsentItem.Type
import com.cookieinformation.mobileconsents.adapter.moshi
import com.cookieinformation.mobileconsents.interfaces.CallFactory
import com.cookieinformation.mobileconsents.interfaces.SdkBuilder
import com.cookieinformation.mobileconsents.models.MobileConsentCredentials
import com.cookieinformation.mobileconsents.models.MobileConsentCustomUI
import com.cookieinformation.mobileconsents.networking.ConsentClient
import com.cookieinformation.mobileconsents.storage.ConsentStorage
import com.cookieinformation.mobileconsents.storage.MoshiFileHandler
import com.cookieinformation.mobileconsents.storage.Preferences
import com.cookieinformation.mobileconsents.system.getApplicationProperties
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.sync.Mutex
import okhttp3.Call
import okhttp3.HttpUrl.Companion.toHttpUrl
import okhttp3.OkHttpClient
import java.io.File
import java.lang.ref.WeakReference
import java.util.UUID

private const val storageFileName = "mobileconsents_storage.txt"

/**
 * Builder for SDK instances. The builder is implementation of Fluent Builder Pattern,
 * thus all parameters must be provided in a valid order. You can get instance of this builder
 * via [MobileConsentSdk.Builder] static function.
 */
internal class MobileConsentSdkBuilder constructor(
  private val context: Context
) : CallFactory, SdkBuilder {
  private var clientId: String? = null
  private var solutionId: String? = null
  private var clientSecret: String? = null

  override fun setClientCredentials(credentials: MobileConsentCredentials): CallFactory {
    clientId = credentials.clientId
    clientSecret = credentials.clientSecret
    solutionId = credentials.solutionId
    return this
  }

  override fun setMobileConsentCustomUI(customUI: MobileConsentCustomUI): CallFactory {
    return this
  }

  override fun build(): MobileConsentSdk {
    if(clientId == null || clientId.orEmpty().isEmpty()){
      Throwable("Please set a client id")
    }
    if(solutionId == null || solutionId.orEmpty().isEmpty()){
      Throwable("Please set a solution id")
    }
    if(clientSecret == null || clientSecret.orEmpty().isEmpty()){
      Throwable("Please set a client secret id")
    }

    val factory = getOkHttpClient(context)//OkHttpClient()

    val storageFile = File(context.filesDir, storageFileName)
    val preferences = Preferences(context.applicationContext)
    lateinit var consentClient: ConsentClient
    try {
      val uuid = UUID.fromString(solutionId)
      consentClient = ConsentClient(
        uuid,
        clientId = clientId.orEmpty(),
        clientSecret = clientSecret.orEmpty(),
        getUrl = BuildConfig.BASE_URL_CONSENT_SOLUTION.toHttpUrl(),
        postUrl = BuildConfig.BASE_URL_CONSENT.toHttpUrl(),
        callFactory = factory,
        moshi = moshi,
        preferences = preferences
      )
    } catch (e: IllegalArgumentException) {
      Toast.makeText(context.applicationContext, e.message.toString(), Toast.LENGTH_SHORT).show()
    }
    val consentStorage =
      ConsentStorage(context.applicationContext, Mutex, storageFile, MoshiFileHandler(moshi), getSaveConsentsMutableFlow(), Dispatchers.IO)
    return MobileConsentSdk(
      consentClient = consentClient,
      consentStorage = consentStorage,
      applicationProperties = context.getApplicationProperties(),
      dispatcher = Dispatchers.IO,
      saveConsentsFlow = consentStorage.saveConsentsFlow
    )
  }

  private companion object {
    /**
     *  Global Mutex, used for synchronization of writing data to the storage, shared across all SDK instances.
     */
    private val Mutex = Mutex()

    /**
     * Returns global flow for observing end emitting "save consents" events.
     */
    @SuppressLint("SyntheticAccessor")
    fun getSaveConsentsMutableFlow(): MutableSharedFlow<Map<Type, Boolean>> = synchronized(this) {
      var eventsEmitter = SaveConsentsMutableFlowReference.get()
      if (eventsEmitter == null) {
        eventsEmitter = MutableSharedFlow()
        SaveConsentsMutableFlowReference = WeakReference(eventsEmitter)
      }
      eventsEmitter
    }

    /**
     * Reference to global flow for observing end emitting "save consents" events, shared across all SDK instances.
     * Warning: Do not use this field directly. Use [getSaveConsentsMutableFlow].
     */
    private var SaveConsentsMutableFlowReference = WeakReference<MutableSharedFlow<Map<Type, Boolean>>>(null)
  }
}
