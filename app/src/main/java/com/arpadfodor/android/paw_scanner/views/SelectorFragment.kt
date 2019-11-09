package com.arpadfodor.android.paw_scanner.views

import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.ArrayAdapter
import android.widget.ListView
import androidx.core.view.get
import androidx.fragment.app.Fragment
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

        val breedsAvailable = viewModel.labels

        val adapter = ArrayAdapter<String>(this.context!!, R.layout.breed_listview_row, R.id.tvBreedName, breedsAvailable)
        listView.adapter = adapter

        listView.setOnItemClickListener{ parent, view, position, id ->
            viewModel.setBreedNameAndLoad(adapter.getItem(position)?:"")
            fragmentManager?.beginTransaction()?.remove(this)?.commit()
        }

    }

}