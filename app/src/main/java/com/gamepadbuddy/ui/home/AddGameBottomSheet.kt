package com.gamepadbuddy.ui.home

import android.content.Intent
import android.content.pm.PackageManager
import android.graphics.drawable.Drawable
import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.ArrayAdapter
import android.widget.ImageView
import android.widget.TextView
import com.google.android.material.bottomsheet.BottomSheetDialogFragment
import com.gamepadbuddy.R
import com.gamepadbuddy.databinding.SheetAddGameBinding
import com.gamepadbuddy.profile.Presets
import com.gamepadbuddy.profile.ProfileRepository

/**
 * File 11 #5 — Thêm game (BottomSheet): chọn app đã cài → tạo profile preset MOBA.
 */
class AddGameBottomSheet(private val onAdded: () -> Unit) : BottomSheetDialogFragment() {

    private var _binding: SheetAddGameBinding? = null
    private val binding get() = _binding!!

    private data class AppEntry(
        val label: String,
        val packageName: String,
        val icon: Drawable
    )

    override fun onCreateView(inflater: LayoutInflater, container: ViewGroup?, savedInstanceState: Bundle?): View {
        _binding = SheetAddGameBinding.inflate(inflater, container, false)
        return binding.root
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)
        val pm = requireContext().packageManager

        // Lấy danh sách app có thể launch. Trên Android 11+ cần khai báo <queries> trong Manifest.
        val apps = pm.queryIntentActivities(
            Intent(Intent.ACTION_MAIN).apply { addCategory(Intent.CATEGORY_LAUNCHER) }, 0
        ).map { it.activityInfo.applicationInfo }
            .distinctBy { it.packageName }
            .map {
                AppEntry(
                    label = pm.getApplicationLabel(it).toString(),
                    packageName = it.packageName,
                    icon = pm.getApplicationIcon(it)
                )
            }
            .sortedBy { it.label }

        val adapter = object : ArrayAdapter<AppEntry>(requireContext(), 0, apps) {
            override fun getView(position: Int, convertView: View?, parent: ViewGroup): View {
                val row = convertView ?: LayoutInflater.from(parent.context)
                    .inflate(R.layout.row_app_picker, parent, false)
                val item = getItem(position) ?: return@getView row
                row.findViewById<ImageView>(R.id.ivIcon).setImageDrawable(item.icon)
                row.findViewById<TextView>(R.id.tvLabel).text = item.label
                return row
            }
        }

        binding.lvApps.adapter = adapter
        binding.lvApps.setOnItemClickListener { _, _, pos, _ ->
            val pkg = apps.getOrNull(pos)?.packageName ?: return@setOnItemClickListener
            val profile = Presets.moba(requireContext()).copy(packageName = pkg, name = "MOBA - $pkg")
            ProfileRepository(requireContext()).save(profile)
            onAdded()
            dismiss()
        }
    }

    override fun onDestroyView() { _binding = null; super.onDestroyView() }
}
