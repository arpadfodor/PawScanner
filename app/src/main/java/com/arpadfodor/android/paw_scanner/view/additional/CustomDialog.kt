package com.arpadfodor.android.paw_scanner.view.additional

import android.app.AlertDialog
import android.content.Context
import android.graphics.Color
import android.graphics.drawable.ColorDrawable
import android.graphics.drawable.Drawable
import android.view.LayoutInflater
import android.widget.ImageView
import android.widget.TextView
import com.arpadfodor.android.paw_scanner.R

/**
 * Dialog class of the app
 *
 * @param    context            Context of the parent where the dialog is shown
 * @param    title              Title of the dialog
 * @param    description        Description of the dialog
 * @param    image              Image shown on the dialog
 */
class CustomDialog(context: Context, title: String, description: String, image: Drawable) : AlertDialog(context) {

    /**
     * Positive and negative Buttons of the dialog
     */
    var buttonPositive: CustomButton? = null
    var buttonNegative: CustomButton? = null

    init {

        this.window?.setBackgroundDrawable(ColorDrawable(Color.TRANSPARENT))
        this.window?.attributes?.windowAnimations = R.style.DialogAnimation

        val inflater = getContext().getSystemService(Context.LAYOUT_INFLATER_SERVICE) as LayoutInflater
        val view = inflater.inflate(R.layout.custom_dialog, null)
        setView(view)

        val imageViewIcon = view.findViewById<ImageView>(R.id.ivCustomDialog)
        imageViewIcon.setImageDrawable(image)

        val textViewTitle = view.findViewById<TextView>(R.id.tvCustomDialogTitle)
        textViewTitle.text = title

        val textViewDescription = view.findViewById<TextView>(R.id.tvCustomDialogDescription)
        textViewDescription.text = description

        buttonPositive = view.findViewById<CustomButton?>(R.id.btnPositiveCustomDialog)
        buttonPositive?.setOnClickListener {
            this.dismiss()
        }

        buttonNegative = view.findViewById<CustomButton?>(R.id.btnNegativeCustomDialog)
        buttonNegative?.setOnClickListener {
            this.dismiss()
        }

    }

    /**
     * Sets the positive Button on click listener
     *
     * @param    func        Lambda to execute when the positive Button is pressed
     */
    fun setPositiveButton(func: () -> Unit){
        buttonPositive?.setOnClickListener {
            this.dismiss()
            func()
        }
    }

    /**
     * Sets the negative Button on click listener
     *
     * @param    func        Lambda to execute when the negative Button is pressed
     */
    fun setNegativeButton(func: () -> Unit){
        buttonNegative?.setOnClickListener {
            this.dismiss()
            func()
        }
    }

    /**
     * Show the dialog - play its Buttons' animations
     */
    override fun show() {
        super.show()
        buttonPositive?.startAppearingAnimation()
        buttonNegative?.startAppearingAnimation()
    }

}