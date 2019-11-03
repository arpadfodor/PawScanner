package com.arpadfodor.android.paw_scanner.views

import android.app.Activity
import android.content.Intent
import androidx.lifecycle.ViewModelProviders
import android.os.Bundle
import android.provider.MediaStore
import androidx.fragment.app.Fragment
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.ImageView
import androidx.lifecycle.Observer
import com.arpadfodor.android.paw_scanner.R
import com.arpadfodor.android.paw_scanner.viewmodels.MainViewModel
import com.bumptech.glide.Glide
import com.google.android.material.floatingactionbutton.FloatingActionButton
import com.arpadfodor.android.paw_scanner.models.BitmapProcessor
import android.graphics.Bitmap

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

    private lateinit var floatingActionButtonUpload: FloatingActionButton
    private lateinit var floatingActionButtonPaw: FloatingActionButton

    override fun onCreateView(inflater: LayoutInflater, container: ViewGroup?, savedInstanceState: Bundle?): View? {
        return inflater.inflate(R.layout.load_fragment, container, false)
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {

        super.onViewCreated(view, savedInstanceState)

        imageView = view.findViewById(R.id.ivLoadedImage)
        floatingActionButtonUpload = view.findViewById(R.id.fabUpload)
        floatingActionButtonPaw = view.findViewById(R.id.fabPawUploaded)

        //due to an Android bug, setting clip to outline cannot be done from XML
        imageView.clipToOutline = true
        Glide
            .with(this)
            .load(R.drawable.load_image_placeholder)
            .into(imageView)

        activity?.let {
            /**
             *  create view model in activity scope
             */
            viewModel = ViewModelProviders.of(it).get(MainViewModel::class.java)
        }
        subscribeToViewModel()

    }

    override fun onActivityCreated(savedInstanceState: Bundle?) {

        super.onActivityCreated(savedInstanceState)

        floatingActionButtonUpload.setOnClickListener {
            this.onClick(floatingActionButtonUpload)
        }

        floatingActionButtonPaw.setOnClickListener {
            this.onClick(floatingActionButtonPaw)
        }

        imageView.setOnClickListener {
            this.onClick(imageView)
        }

    }

    private fun subscribeToViewModel() {

        // Create the image observer which updates the UI in case of an image change
        val imageObserver = Observer<Bitmap> { newImage ->
            // Update the UI, in this case, the ImageView
            imageView.setImageBitmap(newImage)

            Glide
                .with(this)
                .load(newImage)
                .centerCrop()
                .error(R.drawable.load_image_placeholder)
                .placeholder(R.drawable.load_image_placeholder)
                .into(imageView)
        }
        // Observe the LiveData, passing in this viewLifeCycleOwner as the LifecycleOwner and the observer
        viewModel.loadedImage.observe(viewLifecycleOwner, imageObserver)

    }

    private fun loadImage(){

        // Create an Intent with action as ACTION_PICK
        val intent = Intent(Intent.ACTION_PICK)
        // Sets the type as image/*. This ensures only components of type image are selected
        intent.type = "image/*"

        // Pass an extra array with the accepted mime types. This will ensure only components with these MIME types as targeted.
        val mimeTypes = ArrayList<String>()
        mimeTypes.add("image/jpeg")
        mimeTypes.add("image/png")

        intent.putExtra(Intent.EXTRA_MIME_TYPES, mimeTypes)
        // Launch the Intent
        startActivityForResult(intent, GALLERY_REQUEST_CODE)

    }

    override fun onActivityResult(requestCode: Int, resultCode: Int, data: Intent?) {

        // Result code is RESULT_OK only if the user has selected an Image
        if (resultCode == Activity.RESULT_OK) {

            when (requestCode) {
                GALLERY_REQUEST_CODE -> {

                    //data.getData returns the content URI for the selected Image
                    val selectedImageUri = data?.data?:return
                    val sourceBitmap = MediaStore.Images.Media.getBitmap(viewModel.app.contentResolver, selectedImageUri)
                    val croppedBitmap = BitmapProcessor.bitmapToCroppedImage(selectedImageUri, sourceBitmap)

                    viewModel.setLoadedImage(croppedBitmap)

                }
            }

        }

    }

    override fun onClick(v: View) {

        when(v.id){

            R.id.fabUpload ->{
                loadImage()
            }

            R.id.ivLoadedImage ->{
                loadImage()
            }

            R.id.fabPawUploaded ->{
                viewModel.recognitionDetails()
            }

        }

    }

}
