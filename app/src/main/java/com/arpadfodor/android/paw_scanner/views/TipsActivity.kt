package com.arpadfodor.android.paw_scanner.views

import android.graphics.Typeface
import android.os.Bundle
import android.view.View
import android.view.WindowManager
import android.widget.*
import androidx.appcompat.app.AppCompatActivity
import com.arpadfodor.android.paw_scanner.R
import com.bumptech.glide.Glide
import com.bumptech.glide.request.RequestOptions
import kotlinx.android.synthetic.main.default_app_bar.*

class TipsActivity : AppCompatActivity() {

    companion object{
        const val IMAGE_EXAMPLE_SIZE = 300
        const val IMAGE_CORRECTNESS_SIZE = 60
    }

    override fun onCreate(savedInstanceState: Bundle?) {

        //Remove notification bar
        this.window.setFlags(WindowManager.LayoutParams.FLAG_FULLSCREEN, WindowManager.LayoutParams.FLAG_FULLSCREEN)

        super.onCreate(savedInstanceState)

        window.addFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON)

        setContentView(R.layout.activity_tips)
        setSupportActionBar(toolbarNormal)
        supportActionBar?.setDisplayHomeAsUpEnabled(true)
        supportActionBar?.setDisplayShowHomeEnabled(true)

        toolbarNormal.setNavigationOnClickListener {
            this.finish()
        }

        val tableLayoutTips = buildTableLayout()

    }

    private fun buildTableLayout(): TableLayout{

        val tableLayoutTips = findViewById<TableLayout>(R.id.TableLayoutTips)

        val container1 = buildContainer()
        container1.addView(addTableRowWith3Images(IMAGE_EXAMPLE_SIZE, R.drawable.dog_back, R.drawable.dog_front, R.drawable.dog_full))
        container1.addView(addTableRowWithBadOkPerfectImages())
        container1.addView(addTableRowWithTextView(getString(R.string.tips_full_body)))

        val container2 = buildContainer()
        container2.addView(addTableRowWith2Images(IMAGE_EXAMPLE_SIZE, R.drawable.dog_corner, R.drawable.dog_fullscreen))
        container2.addView(addTableRowWithBadPerfectImages())
        container2.addView(addTableRowWithTextView(getString(R.string.tips_corner_fullscreen)))

        val container3 = buildContainer()
        container3.addView(addTableRowWith2Images(IMAGE_EXAMPLE_SIZE, R.drawable.dog_blurred, R.drawable.dog_sharp))
        container3.addView(addTableRowWithBadPerfectImages())
        container3.addView(addTableRowWithTextView(getString(R.string.tips_blurred_sharp)))

        val container4 = buildContainer()
        container4.addView(addTableRowWith2Images(IMAGE_EXAMPLE_SIZE, R.drawable.dog_dark, R.drawable.dog_clear))
        container4.addView(addTableRowWithBadPerfectImages())
        container4.addView(addTableRowWithTextView(getString(R.string.tips_dark_clear)))

        val container5 = buildContainer()
        container5.addView(addTableRowWith2Images(IMAGE_EXAMPLE_SIZE, R.drawable.dog_vertical, R.drawable.dog_horizontal))
        container5.addView(addTableRowWithBadPerfectImages())
        container5.addView(addTableRowWithTextView(getString(R.string.tips_vertical_horizontal)))

        val container6 = buildContainer()
        container6.addView(addTableRowWith2Images(IMAGE_EXAMPLE_SIZE, R.drawable.dog_many, R.drawable.dog_one))
        container6.addView(addTableRowWithBadPerfectImages())
        container6.addView(addTableRowWithTextView(getString(R.string.tips_many_one)))

        tableLayoutTips.addView(container1)
        tableLayoutTips.addView(container2)
        tableLayoutTips.addView(container3)
        tableLayoutTips.addView(container4)
        tableLayoutTips.addView(container5)
        tableLayoutTips.addView(container6)

        return tableLayoutTips

    }

    private fun addTableRowWith3Images(sizeToUse: Int, resId1: Int, resId2: Int, resId3: Int): LinearLayout{

        val tableRow = LinearLayout(this)
        tableRow.layoutParams = LinearLayout.LayoutParams(LinearLayout.LayoutParams.MATCH_PARENT, LinearLayout.LayoutParams.WRAP_CONTENT)

        val myOptions = RequestOptions().override(sizeToUse, sizeToUse)

        val lp = LinearLayout.LayoutParams(0, LinearLayout.LayoutParams.WRAP_CONTENT, 1f)

        val iv1 = ImageView(this)
        Glide
            .with(applicationContext)
            .asBitmap()
            .apply(myOptions)
            .load(resId1)
            .into(iv1)
        iv1.adjustViewBounds = true
        iv1.layoutParams = lp

        val iv2 = ImageView(this)
        Glide
            .with(applicationContext)
            .asBitmap()
            .apply(myOptions)
            .load(resId2)
            .into(iv2)
        iv2.adjustViewBounds = true
        iv2.layoutParams = lp

        val iv3 = ImageView(this)
        Glide
            .with(applicationContext)
            .asBitmap()
            .apply(myOptions)
            .load(resId3)
            .into(iv3)
        iv3.adjustViewBounds = true
        iv3.layoutParams = lp

        tableRow.addView(iv1)
        tableRow.addView(iv2)
        tableRow.addView(iv3)

        return tableRow

    }

    private fun addTableRowWithBadOkPerfectImages(): LinearLayout{

        val tableRow = LinearLayout(this)
        tableRow.layoutParams = LinearLayout.LayoutParams(LinearLayout.LayoutParams.MATCH_PARENT, LinearLayout.LayoutParams.WRAP_CONTENT)

        val myOptions = RequestOptions().override(IMAGE_CORRECTNESS_SIZE, IMAGE_CORRECTNESS_SIZE)

        val lp = LinearLayout.LayoutParams(LinearLayout.LayoutParams.WRAP_CONTENT, LinearLayout.LayoutParams.WRAP_CONTENT)
        val lpSpace = LinearLayout.LayoutParams(0, 1, 1f)

        val space1 = Space(this)
        space1.layoutParams = lpSpace

        val iv1 = ImageView(this)
        Glide
            .with(applicationContext)
            .asBitmap()
            .apply(myOptions)
            .load(R.drawable.logo_bad)
            .into(iv1)
        iv1.adjustViewBounds = true
        iv1.layoutParams = lp

        val space2 = Space(this)
        space2.layoutParams = lpSpace

        val space3 = Space(this)
        space3.layoutParams = lpSpace

        val iv2 = ImageView(this)
        Glide
            .with(applicationContext)
            .asBitmap()
            .apply(myOptions)
            .load(R.drawable.logo_ok)
            .into(iv2)
        iv2.adjustViewBounds = true
        iv2.layoutParams = lp

        val space4 = Space(this)
        space4.layoutParams = lpSpace

        val space5 = Space(this)
        space5.layoutParams = lpSpace

        val iv3 = ImageView(this)
        Glide
            .with(applicationContext)
            .asBitmap()
            .apply(myOptions)
            .load(R.drawable.logo_perfect)
            .into(iv3)
        iv3.adjustViewBounds = true
        iv3.layoutParams = lp

        val space6 = Space(this)
        space6.layoutParams = lpSpace

        tableRow.addView(space1)
        tableRow.addView(iv1)
        tableRow.addView(space2)
        tableRow.addView(space3)
        tableRow.addView(iv2)
        tableRow.addView(space4)
        tableRow.addView(space5)
        tableRow.addView(iv3)
        tableRow.addView(space6)

        return tableRow

    }

    private fun addTableRowWith2Images(sizeToUse: Int, resId1: Int, resId2: Int): LinearLayout{

        val tableRow = LinearLayout(this)
        tableRow.layoutParams = LinearLayout.LayoutParams(LinearLayout.LayoutParams.MATCH_PARENT, LinearLayout.LayoutParams.WRAP_CONTENT)

        val myOptions = RequestOptions().override(sizeToUse, sizeToUse)

        val lp = LinearLayout.LayoutParams(0, LinearLayout.LayoutParams.WRAP_CONTENT, 1f)

        val iv1 = ImageView(this)
        Glide
            .with(applicationContext)
            .asBitmap()
            .apply(myOptions)
            .load(resId1)
            .into(iv1)
        iv1.adjustViewBounds = true
        iv1.layoutParams = lp

        val iv2 = ImageView(this)
        Glide
            .with(applicationContext)
            .asBitmap()
            .apply(myOptions)
            .load(resId2)
            .into(iv2)
        iv2.adjustViewBounds = true
        iv2.layoutParams = lp

        tableRow.addView(iv1)
        tableRow.addView(iv2)

        return tableRow

    }

    private fun addTableRowWithBadPerfectImages(): LinearLayout{

        val tableRow = LinearLayout(this)
        tableRow.layoutParams = LinearLayout.LayoutParams(LinearLayout.LayoutParams.MATCH_PARENT, LinearLayout.LayoutParams.WRAP_CONTENT)

        val myOptions = RequestOptions().override(IMAGE_CORRECTNESS_SIZE, IMAGE_CORRECTNESS_SIZE)

        val lp = LinearLayout.LayoutParams(LinearLayout.LayoutParams.WRAP_CONTENT, LinearLayout.LayoutParams.WRAP_CONTENT)
        val lpSpace = LinearLayout.LayoutParams(0, 1, 1f)

        val space1 = Space(this)
        space1.layoutParams = lpSpace

        val iv1 = ImageView(this)
        Glide
            .with(applicationContext)
            .asBitmap()
            .apply(myOptions)
            .load(R.drawable.logo_bad)
            .into(iv1)
        iv1.adjustViewBounds = true
        iv1.layoutParams = lp

        val space2 = Space(this)
        space2.layoutParams = lpSpace

        val space3 = Space(this)
        space3.layoutParams = lpSpace

        val iv2 = ImageView(this)
        Glide
            .with(applicationContext)
            .asBitmap()
            .apply(myOptions)
            .load(R.drawable.logo_perfect)
            .into(iv2)
        iv2.adjustViewBounds = true
        iv2.layoutParams = lp

        val space4 = Space(this)
        space4.layoutParams = lpSpace

        tableRow.addView(space1)
        tableRow.addView(iv1)
        tableRow.addView(space2)
        tableRow.addView(space3)
        tableRow.addView(iv2)
        tableRow.addView(space4)

        return tableRow

    }

    private fun addTableRowWithTextView(text: String): LinearLayout{

        val tableRow = LinearLayout(this)
        tableRow.layoutParams = LinearLayout.LayoutParams(LinearLayout.LayoutParams.MATCH_PARENT, LinearLayout.LayoutParams.WRAP_CONTENT)

        val lp = LinearLayout.LayoutParams(0, LinearLayout.LayoutParams.WRAP_CONTENT, 1f)

        val tvText = TextView(this)
        tvText.setTextColor(getColor(R.color.colorText))
        tvText.textAlignment = View.TEXT_ALIGNMENT_CENTER
        tvText.typeface = Typeface.create("body", Typeface.NORMAL)
        tvText.text = text
        tvText.layoutParams = lp

        tableRow.addView(tvText)

        return tableRow

    }

    private fun buildContainer(): LinearLayout{

        val verticalMargin = resources.getDimension(R.dimen.card_vertical_margin).toInt()
        val horizontalMargin = resources.getDimension(R.dimen.card_horizontal_margin).toInt()

        val params = TableLayout.LayoutParams(TableLayout.LayoutParams.MATCH_PARENT, TableLayout.LayoutParams.WRAP_CONTENT)
        params.setMargins(horizontalMargin, verticalMargin, horizontalMargin, 0)

        val layout = LinearLayout(this)
        layout.layoutParams = params
        layout.orientation = LinearLayout.VERTICAL
        layout.background = getDrawable(R.drawable.card_background)

        return layout

    }

}
