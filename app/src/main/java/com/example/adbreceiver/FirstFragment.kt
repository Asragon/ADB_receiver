package com.example.adbreceiver

import android.os.Bundle
import android.util.Log
import androidx.fragment.app.Fragment
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import androidx.navigation.fragment.findNavController
import com.example.adbreceiver.databinding.FragmentFirstBinding
import com.example.adbreceiver.ui.AppUiState

private const val TAG = "FirstFragment"

/**
 * A simple [Fragment] subclass as the default destination in the navigation.
 * Doubles as the "intro screen" for the adb_script_runner command demo: it renders
 * [AppUiState] (background color + label text) and reacts live when the debug-only
 * BroadcastReceiver updates it.
 */
class FirstFragment : Fragment() {

    private var _binding: FragmentFirstBinding? = null

    // This property is only valid between onCreateView and
    // onDestroyView.
    private val binding get() = _binding!!

    private val uiStateListener = { renderUiState() }

    override fun onCreateView(
        inflater: LayoutInflater, container: ViewGroup?,
        savedInstanceState: Bundle?
    ): View {
        Log.d(TAG, "onCreateView")

        _binding = FragmentFirstBinding.inflate(inflater, container, false)
        return binding.root

    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)
        Log.d(TAG, "onViewCreated")

        binding.buttonFirst.setOnClickListener {
            Log.d(TAG, "Next button clicked")
            findNavController().navigate(R.id.action_FirstFragment_to_SecondFragment)
        }
        binding.buttonResetBackground.setOnClickListener {
            Log.d(TAG, "Reset background button clicked")
            AppUiState.setBackgroundColor(android.graphics.Color.WHITE)
        }
        binding.buttonResetLabel.setOnClickListener {
            Log.d(TAG, "Reset label button clicked")
            AppUiState.reset(getString(R.string.default_label_text))
        }

        if (AppUiState.labelText.isEmpty()) {
            AppUiState.reset(getString(R.string.default_label_text))
        }
        AppUiState.addListener(uiStateListener)
        renderUiState()
    }

    private fun renderUiState() {
        val b = _binding ?: return
        Log.v(TAG, "Rendering UI state: label=\"${AppUiState.labelText}\"")
        b.rootContainer.setBackgroundColor(AppUiState.backgroundColor)
        b.textviewLabel.text = AppUiState.labelText
    }

    override fun onDestroyView() {
        Log.d(TAG, "onDestroyView")
        AppUiState.removeListener(uiStateListener)
        super.onDestroyView()
        _binding = null
    }
}
