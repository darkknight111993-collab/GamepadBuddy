package com.gamepadbuddy.ui.home

import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.ArrayAdapter
import android.widget.ImageView
import android.widget.TextView
import androidx.appcompat.content.res.AppCompatResources
import androidx.fragment.app.Fragment
import com.gamepadbuddy.R
import com.gamepadbuddy.databinding.FragmentHomeBinding
import com.gamepadbuddy.profile.Profile
import com.gamepadbuddy.profile.ProfileRepository

/**
 * Tab 1 — Trang chủ (file 11 #2): danh sách game đã cấu hình + nút thêm game (BottomSheet).
 */
class HomeFragment : Fragment() {
    private var _binding: FragmentHomeBinding? = null
    private val binding get() = _binding!!
    private val repo by lazy { ProfileRepository(requireContext()) }

    override fun onCreateView(inflater: LayoutInflater, container: ViewGroup?, savedInstanceState: Bundle?): View {
        _binding = FragmentHomeBinding.inflate(inflater, container, false)
        return binding.root
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)
        binding.btnAddGame.setOnClickListener {
            AddGameBottomSheet { refresh() }.show(parentFragmentManager, "add")
        }
        binding.lvGames.setOnItemClickListener { _, _, pos, _ ->
            val profile = binding.lvGames.adapter?.getItem(pos) as? Profile
                ?: return@setOnItemClickListener
            LaunchpadBottomSheet(profile).show(parentFragmentManager, "launch")
        }
        refresh()
    }

    fun refresh() {
        val pm = requireContext().packageManager
        val profiles = repo.getAll()
        val adapter = object : ArrayAdapter<Profile>(requireContext(), 0, profiles) {
            override fun getView(position: Int, convertView: View?, parent: ViewGroup): View {
                val row = convertView ?: LayoutInflater.from(parent.context)
                    .inflate(R.layout.row_app_picker, parent, false)
                val profile = getItem(position) ?: return@getView row
                // Icon app (fallback icon mặc định nếu package đã gỡ).
                val icon = runCatching { pm.getApplicationIcon(profile.packageName) }
                    .getOrNull() ?: AppCompatResources.getDrawable(requireContext(), android.R.drawable.sym_def_app_icon)
                row.findViewById<ImageView>(R.id.ivIcon).setImageDrawable(icon)
                // Tên thật của app; nếu chưa cài thì lấy tên profile.
                val label = runCatching {
                    pm.getApplicationLabel(pm.getApplicationInfo(profile.packageName, 0)).toString()
                }.getOrNull() ?: profile.name
                row.findViewById<TextView>(R.id.tvLabel).text = label
                return row
            }
        }
        binding.lvGames.adapter = adapter
    }

    override fun onDestroyView() { _binding = null; super.onDestroyView() }
}
