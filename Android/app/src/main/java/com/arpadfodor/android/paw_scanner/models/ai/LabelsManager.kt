package com.arpadfodor.android.paw_scanner.models.ai

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

    /**
     * Returns labels in an array of raw label strings
     *
     * @return ArrayList<String>      List of raw names
     */
    fun getRawLabels(): ArrayList<String>{
        return labels
    }

    /**
     * Returns labels in an array of name strings
     *
     * @return ArrayList<String>      List of names
     */
    fun getFormattedLabels(): ArrayList<String>{

        val formattedLabels = arrayListOf<String>()

        for(label in labels){
            formattedLabels.add(label.substringAfter('-').replace('_', ' '))
        }

        return formattedLabels

    }

    /**
     * Returns labels in an array of Id-name pairs
     *
     * @return ArrayList<Pair<String, String>>      List of Pairs of Id, name
     */
    fun getIdWithNames(): ArrayList<Pair<String, String>>{

        val formattedLabels = arrayListOf<Pair<String, String>>()

        for(label in labels){

            val rawPairOfIdAndName = label.splitAtIndex(10)
            val newId = rawPairOfIdAndName.first.trimEnd('-')
            val newName = rawPairOfIdAndName.second.replace('_', ' ')

            val pairOfIdAndName = Pair(newId, newName)

            formattedLabels.add(pairOfIdAndName)

        }

        return formattedLabels

    }

    private fun getLabelPath(): String {
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

    /**
     * Split String into two parts at the index
     *
     * @return Pair<String, String>      Pair of Id, Name of the String
     */
    private fun String.splitAtIndex(index : Int) = take(index) to substring(index)

}