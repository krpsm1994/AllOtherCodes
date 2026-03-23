package com.example.stocksmonitor

import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import androidx.fragment.app.Fragment
import androidx.recyclerview.widget.LinearLayoutManager
import com.google.android.material.snackbar.Snackbar
import com.example.stocksmonitor.databinding.FragmentPortfolioBinding

class PortfolioFragment : Fragment() {

    private lateinit var binding: FragmentPortfolioBinding
    private lateinit var portfolioManager: PortfolioManager
    private lateinit var adapter: PortfolioAdapter
    private var allHoldings = listOf<Holding>()
    private var currentFilter = "all"

    override fun onCreateView(
        inflater: LayoutInflater,
        container: ViewGroup?,
        savedInstanceState: Bundle?
    ): View {
        binding = FragmentPortfolioBinding.inflate(inflater, container, false)
        return binding.root
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)

        portfolioManager = PortfolioManager(requireContext())
        
        setupRecyclerView()
        setupFilterButtons()
        setupSyncButton()
        loadPortfolio()
    }

    private fun setupRecyclerView() {
        adapter = PortfolioAdapter(emptyList()) { holding ->
            showHoldingDetails(holding)
        }
        
        binding.holdingsRecyclerView.apply {
            layoutManager = LinearLayoutManager(requireContext())
            adapter = this@PortfolioFragment.adapter
        }
    }

    private fun setupFilterButtons() {
        binding.tabAll.setOnClickListener {
            setActiveFilter("all")
            updateFilteredHoldings()
        }

        binding.tabKiteLong.setOnClickListener {
            setActiveFilter("kite_long")
            updateFilteredHoldings()
        }

        binding.tabKiteShort.setOnClickListener {
            setActiveFilter("kite_short")
            updateFilteredHoldings()
        }

        binding.tabAngel.setOnClickListener {
            setActiveFilter("angel")
            updateFilteredHoldings()
        }
    }

    private fun setActiveFilter(filter: String) {
        currentFilter = filter
        
        // Update button styles
        binding.tabAll.isSelected = filter == "all"
        binding.tabKiteLong.isSelected = filter == "kite_long"
        binding.tabKiteShort.isSelected = filter == "kite_short"
        binding.tabAngel.isSelected = filter == "angel"
        
        // Update button backgrounds
        binding.tabAll.setBackgroundResource(
            if (filter == "all") android.R.drawable.btn_default else android.R.drawable.btn_default
        )
        binding.tabKiteLong.setBackgroundResource(
            if (filter == "kite_long") android.R.drawable.btn_default else android.R.drawable.btn_default
        )
        binding.tabKiteShort.setBackgroundResource(
            if (filter == "kite_short") android.R.drawable.btn_default else android.R.drawable.btn_default
        )
        binding.tabAngel.setBackgroundResource(
            if (filter == "angel") android.R.drawable.btn_default else android.R.drawable.btn_default
        )
    }

    private fun setupSyncButton() {
        binding.syncButton.setOnClickListener {
            syncPortfolio()
        }
    }

    private fun loadPortfolio() {
        allHoldings = portfolioManager.getHoldings()
        updateFilteredHoldings()
        updatePortfolioSummary()
    }

    private fun updateFilteredHoldings() {
        val filtered = when (currentFilter) {
            "kite_long" -> allHoldings.filter { it.source == "kite" && it.holdingType == "long" }
            "kite_short" -> allHoldings.filter { it.source == "kite" && it.holdingType == "short" }
            "angel" -> allHoldings.filter { it.source == "angel" }
            else -> allHoldings
        }

        if (filtered.isEmpty()) {
            binding.emptyStateLayout.visibility = View.VISIBLE
            binding.holdingsRecyclerView.visibility = View.GONE
        } else {
            binding.emptyStateLayout.visibility = View.GONE
            binding.holdingsRecyclerView.visibility = View.VISIBLE
            adapter.updateHoldings(filtered)
        }
    }

    private fun updatePortfolioSummary() {
        val (totalValue, totalInvested, totalPnL) = portfolioManager.getPortfolioSummary()
        
        binding.totalInvestedText.text = "₹${String.format("%.2f", totalInvested)}"
        binding.currentValueText.text = "₹${String.format("%.2f", totalValue)}"
        binding.totalPnlText.text = "₹${String.format("%.2f", totalPnL)}"
        
        // Color P&L based on positive/negative
        binding.totalPnlText.setTextColor(
            if (totalPnL >= 0) 
                android.graphics.Color.GREEN 
            else 
                android.graphics.Color.RED
        )
    }

    private fun syncPortfolio() {
        binding.loadingProgress.visibility = View.VISIBLE
        binding.syncButton.isEnabled = false

        var completedCalls = 0
        var totalCalls = 3
        val allFetchedHoldings = mutableListOf<Holding>()
        var hasError = false

        // Get credentials from parent activity (MainActivity)
        val parentActivity = requireActivity() as? MainActivity
        if (parentActivity == null) {
            Snackbar.make(binding.root, "Unable to access app context", Snackbar.LENGTH_LONG).show()
            binding.loadingProgress.visibility = View.GONE
            binding.syncButton.isEnabled = true
            return
        }

        // Fetch Kite Holdings
        val kiteAccessToken = parentActivity.getKiteAccessToken()
        if (kiteAccessToken != null) {
            portfolioManager.fetchKiteHoldings(
                "7mov9qt27tpmk2ft",  // KITE_API_KEY constant
                kiteAccessToken
            ) { holdings, error ->
                if (error == null) {
                    allFetchedHoldings.addAll(holdings)
                    Logger.d("PortfolioFragment", "Fetched ${holdings.size} Kite holdings")
                } else {
                    Logger.e("PortfolioFragment", "Error fetching Kite holdings: $error")
                }
                completedCalls++
                checkIfAllComplete(completedCalls, totalCalls, allFetchedHoldings)
            }
        } else {
            completedCalls++
            checkIfAllComplete(completedCalls, totalCalls, allFetchedHoldings)
        }

        // Fetch Kite Positions
        if (kiteAccessToken != null) {
            portfolioManager.fetchKitePositions(
                "7mov9qt27tpmk2ft",  // KITE_API_KEY constant
                kiteAccessToken
            ) { positions, error ->
                if (error == null) {
                    allFetchedHoldings.addAll(positions)
                    Logger.d("PortfolioFragment", "Fetched ${positions.size} Kite positions")
                } else {
                    Logger.e("PortfolioFragment", "Error fetching Kite positions: $error")
                }
                completedCalls++
                checkIfAllComplete(completedCalls, totalCalls, allFetchedHoldings)
            }
        } else {
            completedCalls++
            checkIfAllComplete(completedCalls, totalCalls, allFetchedHoldings)
        }

        // Fetch AngelOne Holdings
        val angelJwtToken = parentActivity.getAngelJwtToken()
        val angelClientId = parentActivity.getAngelClientId()
        if (angelJwtToken != null && angelClientId != null) {
            portfolioManager.fetchAngelHoldings(
                angelJwtToken,
                "GoqzPhth",  // AngelOne Portfolio API Key
                angelClientId
            ) { holdings, error ->
                if (error == null) {
                    allFetchedHoldings.addAll(holdings)
                    Logger.d("PortfolioFragment", "Fetched ${holdings.size} AngelOne holdings")
                } else {
                    Logger.e("PortfolioFragment", "Error fetching AngelOne holdings: $error")
                }
                completedCalls++
                checkIfAllComplete(completedCalls, totalCalls, allFetchedHoldings)
            }
        } else {
            completedCalls++
            checkIfAllComplete(completedCalls, totalCalls, allFetchedHoldings)
        }
    }

    private fun checkIfAllComplete(completed: Int, total: Int, holdings: List<Holding>) {
        if (completed >= total) {
            requireActivity().runOnUiThread {
                binding.loadingProgress.visibility = View.GONE
                binding.syncButton.isEnabled = true

                if (holdings.isNotEmpty()) {
                    // Combine duplicate holdings for the same symbol
                    val combinedHoldings = portfolioManager.combineHoldings(holdings)
                    Logger.d("PortfolioFragment", "Combined ${holdings.size} holdings into ${combinedHoldings.size}")
                    
                    portfolioManager.saveHoldings(combinedHoldings)
                    allHoldings = combinedHoldings
                    updateFilteredHoldings()
                    updatePortfolioSummary()
                    Snackbar.make(binding.root, "Portfolio synced! Found ${combinedHoldings.size} unique holdings", Snackbar.LENGTH_LONG).show()
                } else {
                    Snackbar.make(binding.root, "No holdings found. Please ensure you're logged in.", Snackbar.LENGTH_LONG).show()
                }
            }
        }
    }

    private fun showHoldingDetails(holding: Holding) {
        val details = buildString {
            appendLine("Symbol: ${holding.symbol}")
            appendLine("ISIN: ${holding.isin}")
            appendLine("Quantity: ${holding.qty}")
            appendLine("Average Price: ₹${String.format("%.2f", holding.avgPrice)}")
            appendLine("Current Price (LTP): ₹${String.format("%.2f", holding.ltp)}")
            appendLine("Current Value: ₹${String.format("%.2f", holding.currentValue)}")
            appendLine("Invested: ₹${String.format("%.2f", holding.qty * holding.avgPrice)}")
            appendLine("P&L: ₹${String.format("%.2f", holding.pnl)}")
            appendLine("P&L %: ${String.format("%.2f", holding.pnlPercent)}%")
            appendLine("Source: ${holding.source.uppercase()}")
            appendLine("Type: ${holding.holdingType.uppercase()}")
        }

        android.app.AlertDialog.Builder(requireContext())
            .setTitle("${holding.symbol} - Holdings Detail")
            .setMessage(details)
            .setPositiveButton("OK", null)
            .show()
    }

    override fun onResume() {
        super.onResume()
        // Refresh portfolio on resume
        loadPortfolio()
    }
}
