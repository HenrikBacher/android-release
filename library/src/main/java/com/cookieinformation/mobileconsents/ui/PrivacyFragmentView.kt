package com.cookieinformation.mobileconsents.ui

import android.content.Context
import android.util.AttributeSet
import android.view.View
import android.widget.Button
import android.widget.FrameLayout
import androidx.annotation.MainThread
import androidx.appcompat.widget.Toolbar
import androidx.recyclerview.widget.RecyclerView
import androidx.recyclerview.widget.SimpleItemAnimator
import com.cookieinformation.mobileconsents.R
import com.cookieinformation.mobileconsents.ui.PrivacyFragmentView.IntentListener2
import java.util.UUID

/**
 * The Privacy Center view implementation. The view is used in [BasePrivacyCenterFragment] and should not be used directly
 * (except for ex. capturing events for analytics by [PrivacyFragmentView.IntentListener2]).
 */
public class PrivacyFragmentView @JvmOverloads constructor(
  context: Context,
  attrs: AttributeSet? = null,
  defStyleAttr: Int = 0,
  defStyleRes: Int = 0
) : FrameLayout(context, attrs, defStyleAttr, defStyleRes),
  ConsentSolutionView<PrivacyFragmentViewData, IntentListener2> {

  /**
   * A listener for events that can be triggered by the user.
   */
  @MainThread
  public interface IntentListener2 {

    /**
     * Called when the user wants to change the choice for the consent.
     *
     * @param id [UUID] of the consents.
     * @param accepted user's choice.
     */
    public fun onPrivacyCenterChoiceChanged(id: UUID, accepted: Boolean)

    /**
     * Called when the user wants toggle visibility of the details information.
     *
     * @param id [UUID] of the information.
     */
    public fun onPrivacyCenterDetailsToggle(id: UUID)

    /**
     * Called when the user accepts selected consents. It is called only if all required consents are chosen by the user.
     */
    public fun onPrivacyCenterAcceptClicked()

    /**
     * Called when the user wants to close the view.
     */
    public fun onPrivacyCenterDismissRequest()
  }

  private val intentListeners = mutableSetOf<IntentListener2>()
  private val consentListAdapter = PrivacyFragmentListAdapter(::onDetailsToggle, ::onChoiceChanged)

  private val contentView: View
  private val progressBar: View

  init {
    inflate(context, R.layout.mobileconsents_privacy_center, this)
    contentView = findViewById(R.id.mobileconsents_privacy_center_layout)
    contentView.visibility = View.GONE

    inflate(context, R.layout.mobileconsents_progressbar, this)
    progressBar = findViewById(R.id.mobileconsents_progressbar_layout)
    progressBar.visibility = View.VISIBLE

    contentView.findViewById<RecyclerView>(R.id.mobileconsents_privacy_center_list).apply {
      setHasFixedSize(true)
      (itemAnimator as SimpleItemAnimator).supportsChangeAnimations = false
      adapter = consentListAdapter
    }

    contentView.findViewById<Toolbar>(R.id.mobileconsents_privacy_center_toolbar).apply {
      setNavigationOnClickListener {
        onDismissRequest()
      }
    }

    findViewById<Button>(R.id.mobileconsents_privacy_center_btn_accept).setOnClickListener { onAcceptClicked() }
  }

  private fun onAcceptClicked() {
    for (listener in intentListeners) {
      listener.onPrivacyCenterAcceptClicked()
    }
  }

  private fun onChoiceChanged(id: UUID, accepted: Boolean) {
    for (listener in intentListeners) {
      listener.onPrivacyCenterChoiceChanged(id, accepted)
    }
  }

  private fun onDetailsToggle(id: UUID) {
    for (listener in intentListeners) {
      listener.onPrivacyCenterDetailsToggle(id)
    }
  }

  private fun onDismissRequest() {
    for (listener in intentListeners) {
      listener.onPrivacyCenterDismissRequest()
    }
  }

  public override fun addIntentListener(listener: IntentListener2) {
    require(!intentListeners.contains(listener))
    intentListeners.add(listener)
  }

  public override fun removeIntentListener(listener: IntentListener2) {
    require(intentListeners.contains(listener))
    intentListeners.remove(listener)
  }

  override fun showProgressBar() {
    progressBar.visibility = View.VISIBLE
  }

  override fun hideProgressBar() {
    progressBar.visibility = View.GONE
  }

  override fun showViewData(data: PrivacyFragmentViewData) {
    findViewById<Toolbar>(R.id.mobileconsents_privacy_center_toolbar).apply {
      title = data.title
    }
    findViewById<Button>(R.id.mobileconsents_privacy_center_btn_accept).apply {
      text = data.acceptButtonText
      isEnabled = data.acceptButtonEnabled
    }
    consentListAdapter.submitList(data.items)

    contentView.visibility = View.VISIBLE
  }

  override fun hideViewData() {
    contentView.visibility = View.GONE
  }

  override fun showRetryDialog(onRetry: () -> Unit, onDismiss: () -> Unit, title: String, message: String) {
    // postDelayed is workaround for: If view is embedded in a DialogFragment, the below dialog is shown under the DialogFragment.
    postDelayed({ createRetryDialog(context, onRetry, onDismiss, title, message).show() }, 0)
  }

  override fun showErrorDialog(onDismiss: () -> Unit) {
    // postDelayed is workaround for: If view is embedded in a DialogFragment, the below dialog is shown under the DialogFragment.
    postDelayed({ createErrorDialog(context, onDismiss).show() }, 0)
  }
}
