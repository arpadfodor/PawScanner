package com.arpadfodor.android.paw_scanner.views

import androidx.lifecycle.ViewModelProviders
import android.os.Bundle
import androidx.fragment.app.Fragment
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup

import com.arpadfodor.android.paw_scanner.R
import com.arpadfodor.android.paw_scanner.viewmodels.MainViewModel

class HistoryFragment : Fragment() {

    companion object {
        fun newInstance() = HistoryFragment()
    }

    private lateinit var viewModel: MainViewModel

    override fun onCreateView(
        inflater: LayoutInflater, container: ViewGroup?,
        savedInstanceState: Bundle?
    ): View? {
        return inflater.inflate(R.layout.history_fragment, container, false)
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {

        super.onViewCreated(view, savedInstanceState)

        activity?.let {
            /**
             *  create view model in activity scope
             */
            viewModel = ViewModelProviders.of(it).get(MainViewModel::class.java)
        }

        // TODO: Use the ViewModel
    }

}
