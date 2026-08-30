package com.gamepadbuddy.onboarding

import android.app.Activity
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import androidx.recyclerview.widget.RecyclerView
import com.gamepadbuddy.databinding.ItemOnboardingStepBinding

/**
 * Hiển thị từng bước onboarding: chỉ bước đang active (đã xong hết bước trước) mới xổ nút Enable.
 */
class StepListAdapter(
    private val steps: List<OnboardingStep>,
    private val onManualConfirm: (String) -> Unit
) : RecyclerView.Adapter<StepListAdapter.VH>() {

    private var states = List(steps.size) { false }

    fun refreshStates(newStates: List<Boolean>) {
        states = newStates
        notifyDataSetChanged()
    }

    class VH(val binding: ItemOnboardingStepBinding) : RecyclerView.ViewHolder(binding.root)

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): VH {
        val b = ItemOnboardingStepBinding.inflate(LayoutInflater.from(parent.context), parent, false)
        return VH(b)
    }

    override fun getItemCount() = steps.size

    override fun onBindViewHolder(holder: VH, position: Int) {
        val step = steps[position]
        val isDone = states[position]
        val isActive = !isDone && states.take(position).all { it }
        holder.binding.apply {
            tvTitle.text = step.title
            tvDesc.text = step.description
            tvIndex.text = (position + 1).toString()
            val ctx = root.context
            tvIndex.setBackgroundColor(
                if (isDone) ctx.getColor(android.R.color.holo_green_dark)
                else ctx.getColor(android.R.color.darker_gray)
            )
            root.alpha = if (isDone) 0.5f else 1f
            btnEnable.visibility = if (isActive) View.VISIBLE else View.GONE
            btnEnable.setOnClickListener { step.requestAction(root.context as Activity) }
            val canManual = step.manualConfirmable && !isDone && isActive
            btnConfirm.visibility = if (canManual) View.VISIBLE else View.GONE
            btnConfirm.setOnClickListener { onManualConfirm(step.id) }
        }
    }
}
