package com.arpadfodor.android.paw_scanner.views

import android.os.Bundle
import androidx.fragment.app.Fragment
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import com.arpadfodor.android.paw_scanner.R

class BreedsFragment : Fragment() {

    companion object {
        fun newInstance() = BreedsFragment()
    }

    override fun onCreateView(
        inflater: LayoutInflater, container: ViewGroup?,
        savedInstanceState: Bundle?
    ): View? {
        // Inflate the layout for this fragment
        return inflater.inflate(R.layout.breeds_fragment, container, false)
    }

}
