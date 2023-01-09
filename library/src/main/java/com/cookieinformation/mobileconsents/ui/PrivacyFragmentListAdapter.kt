package com.cookieinformation.mobileconsents.ui

import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import androidx.recyclerview.widget.DiffUtil
import androidx.recyclerview.widget.ListAdapter
import androidx.recyclerview.widget.RecyclerView
import com.cookieinformation.mobileconsents.ConsentItem.Type
import com.cookieinformation.mobileconsents.R

/**
 * RecyclerView's adapter for [PrivacyFragmentPreferencesItem] item model.
 */
internal class PrivacyFragmentListAdapter(
  private val onConsentItemChoiceToggle: (Type, Boolean) -> Unit,
  private val sdkColor: Int?
) :
  ListAdapter<PrivacyFragmentPreferencesItem, PrivacyFragmentListAdapter.ItemViewHolder>(AdapterConsentItemDiffCallback()) {

  override fun onCreateViewHolder(parent: ViewGroup, viewType: Int) =
    PreferencesItemViewHolder(
      LayoutInflater.from(parent.context)
        .inflate(R.layout.mobileconsents_privacy_item_preferences, parent, false),
      onConsentItemChoiceToggle, sdkColor
    )

  override fun onBindViewHolder(holder: ItemViewHolder, position: Int) = holder.bind(getItem(position))

  abstract class ItemViewHolder(itemView: View) : RecyclerView.ViewHolder(itemView) {
    abstract fun bind(item: PrivacyFragmentPreferencesItem)
  }

  class PreferencesItemViewHolder(itemView: View, onConsentItemChoiceToggle: (Type, Boolean) -> Unit, sdkColor: Int?) :
    ItemViewHolder(itemView) {

    private val preferencesAdapter = PrivacyPreferencesListAdapter(
      R.layout.mobileconsents_privacy_item_preferences_item,
      onConsentItemChoiceToggle, sdkColor
    )

    init {
      itemView.findViewById<RecyclerView>(R.id.mobileconsents_privacy_preferences_list).apply {
        adapter = preferencesAdapter
      }
    }

    override fun bind(item: PrivacyFragmentPreferencesItem) {
      val sort = item.items.sortedBy { !it.required }
      preferencesAdapter.submitList(sort)
    }
  }

  class AdapterConsentItemDiffCallback : DiffUtil.ItemCallback<PrivacyFragmentPreferencesItem>() {

    override fun areItemsTheSame(oldItem: PrivacyFragmentPreferencesItem, newItem: PrivacyFragmentPreferencesItem) =
      oldItem.id == newItem.id

    override fun areContentsTheSame(oldItem: PrivacyFragmentPreferencesItem, newItem: PrivacyFragmentPreferencesItem) =
      oldItem == newItem
  }
}
