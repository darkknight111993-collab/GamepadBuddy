package com.gamepadbuddy.ui.controllers

import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import androidx.fragment.app.Fragment
import androidx.lifecycle.Observer
import com.gamepadbuddy.core.EventBus
import com.gamepadbuddy.databinding.FragmentControllersBinding
import com.gamepadbuddy.input.listConnectedGamepads

/**
 * Tab 2 — Tay cầm (file 11 #3): số tay cầm đang kết nối + log sự kiện (từ EventBus).
 */
class ControllersFragment : Fragment() {
    private var _binding: FragmentControllersBinding? = null
    private val binding get() = _binding!!
    private val logObserver = Observer<String> { binding.tvLog.text = it }

    override fun onCreateView(inflater: LayoutInflater, container: ViewGroup?, savedInstanceState: Bundle?): View {
        _binding = FragmentControllersBinding.inflate(inflater, container, false)
        return binding.root
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)
        binding.tvStatus.text = "${listConnectedGamepads().size} Gamepad(s) Connected"
    }

    override fun onResume() {
        super.onResume()
        EventBus.gamepadLog.observe(viewLifecycleOwner, logObserver)
    }

    override fun onPause() {
        EventBus.gamepadLog.removeObserver(logObserver)
        super.onPause()
    }

    override fun onDestroyView() { _binding = null; super.onDestroyView() }
}
