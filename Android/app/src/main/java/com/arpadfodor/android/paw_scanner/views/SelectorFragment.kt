package com.arpadfodor.android.paw_scanner.views

import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.ArrayAdapter
import android.widget.ListView
import androidx.fragment.app.Fragment
import androidx.lifecycle.Observer
import androidx.lifecycle.ViewModelProviders
import com.arpadfodor.android.paw_scanner.R
import com.arpadfodor.android.paw_scanner.viewmodels.BreedViewModel

class SelectorFragment : Fragment(){

    companion object {
        fun newInstance() = SelectorFragment()
    }

    private lateinit var viewModel: BreedViewModel

    /*
    * List of the available breeds
    */
    private lateinit var listView: ListView

    override fun onCreateView(inflater: LayoutInflater, container: ViewGroup?, savedInstanceState: Bundle?): View? {
        // Inflate the layout for this fragment
        return inflater.inflate(R.layout.selector_fragment, container, false)
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {

        super.onViewCreated(view, savedInstanceState)

        listView = view.findViewById(R.id.available_breeds)

        activity?.let {
            /**
             *  create view model in activity scope
             */
            viewModel = ViewModelProviders.of(it).get(BreedViewModel::class.java)
        }
        subscribeToViewModel()

        val breedsAvailable = arrayListOf<String>()

        for(label in viewModel.labels){
            breedsAvailable.add(label.second)
        }

        val adapter = ArrayAdapter<String>(this.context!!, R.layout.listview_row, R.id.tvItemName, breedsAvailable)
        listView.adapter = adapter

        listView.setOnItemClickListener{ parent, view, position, id ->
            viewModel.setCurrentBreed(viewModel.labels[position])
        }

    }

    private fun subscribeToViewModel() {

        // Create the boolean observer which updates the UI in case of setting selector displayed flag to false
        val isSelectorDisplayedObserver = Observer<Boolean> { result ->

            if(result == false){
                fragmentManager?.beginTransaction()?.remove(this)?.commit()
            }

        }

        // Observe the LiveData
        viewModel.isSelectorDisplayed.observe(this, isSelectorDisplayedObserver)

    }

}