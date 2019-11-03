package com.arpadfodor.android.paw_scanner.views


import android.os.Bundle
import androidx.fragment.app.Fragment
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup

import com.arpadfodor.android.paw_scanner.R

/**
 * Tips Fragment
 */
class TipsFragment : Fragment() {

    companion object {
        fun newInstance() = TipsFragment()
    }

    override fun onCreateView(
        inflater: LayoutInflater, container: ViewGroup?,
        savedInstanceState: Bundle?
    ): View? {
        // Inflate the layout for this fragment
        return inflater.inflate(R.layout.tips_fragment, container, false)
    }

}
