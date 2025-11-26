package org.jellyfin.androidtv.ui.jellyseerr

import android.os.Bundle
import android.view.View
import android.widget.Toast
import androidx.fragment.app.Fragment
import androidx.lifecycle.lifecycleScope
import androidx.recyclerview.widget.LinearLayoutManager
import kotlinx.coroutines.launch
import org.jellyfin.androidtv.R
import org.jellyfin.androidtv.databinding.FragmentJellyseerrRequestsBinding
import org.koin.android.ext.android.inject
import org.koin.androidx.viewmodel.ext.android.viewModel

class RequestsFragment : Fragment(R.layout.fragment_jellyseerr_requests) {
	private val viewModel: JellyseerrViewModel by viewModel()

	private var _binding: FragmentJellyseerrRequestsBinding? = null
	private val binding get() = _binding!!

	private lateinit var requestsAdapter: RequestsAdapter

	override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
		super.onViewCreated(view, savedInstanceState)
		_binding = FragmentJellyseerrRequestsBinding.bind(view)

		setupRecyclerView()
		setupUI()
		setupObservers()
		
		lifecycleScope.launch {
			kotlinx.coroutines.delay(1000)
			loadRequests()
		}
	}

	private fun setupRecyclerView() {
		requestsAdapter = RequestsAdapter()

		binding.requestsList.apply {
			adapter = requestsAdapter
			layoutManager = LinearLayoutManager(requireContext())
		}
	}

	private fun setupUI() {
		binding.refreshButton.setOnClickListener {
			loadRequests()
		}
	}

	private fun setupObservers() {
		lifecycleScope.launch {
			viewModel.loadingState.collect { state ->
				when (state) {
					is JellyseerrLoadingState.Loading -> {
						binding.loadingState.visibility = View.VISIBLE
						binding.emptyState.visibility = View.GONE
					}
					is JellyseerrLoadingState.Success -> {
						binding.loadingState.visibility = View.GONE
					}
					is JellyseerrLoadingState.Error -> {
						binding.loadingState.visibility = View.GONE
						binding.emptyState.visibility = View.VISIBLE
						Toast.makeText(
							requireContext(),
							"Error: ${state.message}",
							Toast.LENGTH_LONG
						).show()
					}
					else -> {}
				}
			}
		}

		lifecycleScope.launch {
			viewModel.userRequests.collect { requests ->
				requestsAdapter.submitList(requests)
				if (requests.isEmpty()) {
					binding.emptyState.visibility = View.VISIBLE
					binding.requestsList.visibility = View.GONE
				} else {
					binding.emptyState.visibility = View.GONE
					binding.requestsList.visibility = View.VISIBLE
				}
			}
		}

		lifecycleScope.launch {
			viewModel.isAvailable.collect { isAvailable ->
				binding.notConnectedWarning.visibility =
					if (isAvailable) View.GONE else View.VISIBLE
			}
		}
	}

	private fun loadRequests() {
		viewModel.loadRequests()
	}

	override fun onDestroyView() {
		_binding = null
		super.onDestroyView()
	}
}
