package com.arpadfodor.android.paw_scanner.view

import android.app.Activity
import android.content.Intent
import android.graphics.Bitmap
import androidx.lifecycle.ViewModelProviders
import android.os.Bundle
import android.provider.MediaStore
import androidx.fragment.app.Fragment
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.ImageView
import android.widget.TextView
import androidx.lifecycle.Observer
import com.arpadfodor.android.paw_scanner.R
import com.arpadfodor.android.paw_scanner.model.Recognition
import com.arpadfodor.android.paw_scanner.viewmodel.MainViewModel
import com.google.android.material.floatingactionbutton.FloatingActionButton

class LoadFragment : Fragment(), View.OnClickListener {

    companion object {
        fun newInstance() = LoadFragment()
        private const val GALLERY_REQUEST_CODE = 1
    }

    private lateinit var viewModel: MainViewModel

    /*
    * Views of the Fragment
    */
    private lateinit var imageView: ImageView
    private lateinit var textView: TextView
    private lateinit var floatingActionButtonUpload: FloatingActionButton

    override fun onCreateView(inflater: LayoutInflater, container: ViewGroup?, savedInstanceState: Bundle?): View? {
        return inflater.inflate(R.layout.load_fragment, container, false)
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {

        super.onViewCreated(view, savedInstanceState)

        imageView = view.findViewById(R.id.ivLoadedImage)
        textView = view.findViewById(R.id.tvRecognitionData)
        floatingActionButtonUpload = view.findViewById(R.id.fabUpload)

        viewModel = ViewModelProviders.of(this).get(MainViewModel::class.java)
        viewModel.loadClassifier()
        subscribeUi()

    }

    override fun onActivityCreated(savedInstanceState: Bundle?) {
        super.onActivityCreated(savedInstanceState)
        floatingActionButtonUpload.setOnClickListener {
            this.onClick(floatingActionButtonUpload)
        }
    }

    private fun subscribeUi() {
        // Create the image observer which updates the UI in case of an image change
        val imageObserver = Observer<Bitmap> { newImage ->
            // Update the UI, in this case, the ImageView
            imageView.setImageBitmap(newImage)
        }
        // Observe the LiveData, passing in this viewLifeCycleOwner as the LifecycleOwner and the observer
        viewModel.loadedImage.observe(viewLifecycleOwner, imageObserver)

        // Create the text observer which updates the UI in case of an image change
        val recognitionObserver = Observer<List<Recognition>> { result ->
            // Update the UI, in this case, the TextView
            var resultText = ""
            for(recognition in result){
                resultText += recognition
                resultText += "\n"
            }
            textView.text = resultText
        }
        // Observe the LiveData, passing in this viewLifeCycleOwner as the LifecycleOwner and the observer
        viewModel.result.observe(viewLifecycleOwner, recognitionObserver)
    }

    private fun loadImage(){
        //Create an Intent with action as ACTION_PICK
        val intent = Intent(Intent.ACTION_PICK)
        // Sets the type as image/*. This ensures only components of type image are selected
        intent.type = "image/*"
        //We pass an extra array with the accepted mime types. This will ensure only components with these MIME types as targeted.
        var mimeTypes = ArrayList<String>()
        mimeTypes.add("image/jpeg")
        mimeTypes.add("image/png")

        intent.putExtra(Intent.EXTRA_MIME_TYPES, mimeTypes)
        // Launching the Intent
        startActivityForResult(intent, GALLERY_REQUEST_CODE)
    }

    override fun onActivityResult(requestCode: Int, resultCode: Int, data: Intent?) {
        // Result code is RESULT_OK only if the user has selected an Image
        if (resultCode == Activity.RESULT_OK)
            when (requestCode) {
                GALLERY_REQUEST_CODE -> {
                    //data.getData returns the content URI for the selected Image
                    val selectedImageUri = data?.data
                    val bitmap = MediaStore.Images.Media.getBitmap(viewModel.app.contentResolver, selectedImageUri)
                    viewModel.loadedImage.value = bitmap
                    viewModel.recognizeLoadedImage()
                }
            }
    }

    override fun onClick(v: View) {
        when(v.id){
            R.id.fabUpload ->{
                loadImage()
            }
        }
    }

}
