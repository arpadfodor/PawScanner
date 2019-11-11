package com.arpadfodor.android.paw_scanner.models.AI

import android.content.res.AssetManager
import java.io.BufferedReader
import java.io.IOException
import java.io.InputStreamReader
import java.util.ArrayList

object LabelsManager{

    private const val LABEL_PATH = "labels.txt"

    /**
     * Labels corresponding to the output of the vision model
     */
    var labels = arrayListOf<String>()

    /**
     * Reads label list from Assets
     */
    @Throws(IOException::class)
    fun loadLabelList(asset: AssetManager){

        val reader = BufferedReader(InputStreamReader(asset.open(getLabelPath())))

        while(true){
            val line = reader.readLine() ?: break
            labels.add(line)
        }
        reader.close()

    }

    fun getRawLabels(): ArrayList<String>{
        return labels
    }

    fun getFormattedLabels(): ArrayList<String>{

        val formattedLabels = arrayListOf<String>()

        for(label in labels){
            formattedLabels.add(label.substringAfter('-').replace('_', ' '))
        }

        return formattedLabels

    }

    fun getLabelPath(): String {
        return LABEL_PATH
    }

    /**
     * Get the total number of labels
     *
     * @return Int      Number of labels
     */
    fun getNumOfLabels(): Int {
        return labels.size
    }

}