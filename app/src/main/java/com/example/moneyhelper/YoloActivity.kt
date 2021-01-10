package com.example.moneyhelper

import android.Manifest
import android.app.Activity
import android.content.Context
import android.content.pm.PackageManager
import android.content.res.AssetManager
import android.os.Build
import android.os.Bundle
import android.util.Log
import android.view.*
import android.widget.*
import androidx.appcompat.widget.SwitchCompat
import androidx.core.app.ActivityCompat
import com.google.android.material.bottomsheet.BottomSheetBehavior
import kotlinx.android.synthetic.main.activity_main.*
import kotlinx.android.synthetic.main.activity_yolo.*
import org.opencv.android.BaseLoaderCallback
import org.opencv.android.CameraBridgeViewBase
import org.opencv.android.LoaderCallbackInterface
import org.opencv.android.OpenCVLoader
import org.opencv.core.Mat
import java.io.BufferedInputStream
import java.io.File
import java.io.FileOutputStream
import java.io.IOException


class YoloActivity : Activity(), CameraBridgeViewBase.CvCameraViewListener2 {

    private lateinit var mOpenCvCameraView: CameraBridgeViewBase
    private var enabled: Boolean = true
//    private var frontCam: Boolean = false
//    lateinit var enBtn: Button
//    lateinit var camBtn: Button
    private lateinit var bottomSheetLayout: LinearLayout
    private lateinit var gestureLayout: LinearLayout
    private lateinit var sheetBehavior: BottomSheetBehavior<LinearLayout>

    protected lateinit var frameValueTextView: TextView
    protected lateinit var cropValueTextView:android.widget.TextView
    protected lateinit var inferenceTimeTextView:android.widget.TextView
    protected lateinit var bottomSheetArrowImageView: ImageView
    private lateinit var plusImageView: ImageView
    private lateinit var minusImageView:android.widget.ImageView
    private lateinit var apiSwitchCompat: SwitchCompat
    private lateinit var threadsTextView: TextView


    private val mLoaderCallback = object : BaseLoaderCallback(this) {
        override fun onManagerConnected(status: Int) {
            when (status) {
                LoaderCallbackInterface.SUCCESS -> {
                    Log.i(TAG, "OpenCV loaded successfully")

                    // Load native library after(!) OpenCV initialization
                    System.loadLibrary("native-lib")

                    mOpenCvCameraView.enableView()
                }
                else -> {
                    super.onManagerConnected(status)
                }
            }
        }
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        Log.i(TAG, "called onCreate")
        super.onCreate(savedInstanceState)
        window.addFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON)

        // Permissions for Android 6+
        ActivityCompat.requestPermissions(
            this@YoloActivity,
            arrayOf(Manifest.permission.CAMERA),
            CAMERA_PERMISSION_REQUEST
        )

        window.addFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON)
        setContentView(R.layout.activity_yolo)
//        enBtn=findViewById(R.id.enBtn)
//        camBtn=findViewById(R.id.camBtn)

        mOpenCvCameraView = findViewById<CameraBridgeViewBase>(R.id.main_surface)

//        mOpenCvCameraView.setMaxFrameSize(480, 640);

        mOpenCvCameraView.setCameraIndex(CameraBridgeViewBase.CAMERA_ID_BACK)

        mOpenCvCameraView.visibility = SurfaceView.VISIBLE

        mOpenCvCameraView.setCvCameraViewListener(this)

        apiSwitchCompat = findViewById(R.id.api_info_switch)
        bottomSheetLayout = findViewById(R.id.bottom_sheet_layout)
        gestureLayout = findViewById(R.id.gesture_layout)
        sheetBehavior = BottomSheetBehavior.from(bottomSheetLayout)
        bottomSheetArrowImageView = findViewById(R.id.bottom_sheet_arrow)

        val vto = gestureLayout.viewTreeObserver
        vto.addOnGlobalLayoutListener(
            object : ViewTreeObserver.OnGlobalLayoutListener {
                override fun onGlobalLayout() {
                    if (Build.VERSION.SDK_INT < Build.VERSION_CODES.JELLY_BEAN) {
                        gestureLayout.viewTreeObserver.removeGlobalOnLayoutListener(this)
                    } else {
                        gestureLayout.viewTreeObserver.removeOnGlobalLayoutListener(this)
                    }
                    //                int width = bottomSheetLayout.getMeasuredWidth();
                    val height = gestureLayout.measuredHeight
                    sheetBehavior.peekHeight = height
                }
            })
        sheetBehavior.isHideable = false

        sheetBehavior.setBottomSheetCallback(
            object : BottomSheetBehavior.BottomSheetCallback() {
                override fun onStateChanged(bottomSheet: View, newState: Int) {
                    when (newState) {
                        BottomSheetBehavior.STATE_HIDDEN -> {
                        }
                        BottomSheetBehavior.STATE_EXPANDED -> {
                            bottomSheetArrowImageView.setImageResource(R.drawable.icn_chevron_down)
                        }
                        BottomSheetBehavior.STATE_COLLAPSED -> {
                            bottomSheetArrowImageView.setImageResource(R.drawable.icn_chevron_up)
                        }
                        BottomSheetBehavior.STATE_DRAGGING -> {
                        }
                        BottomSheetBehavior.STATE_SETTLING -> bottomSheetArrowImageView.setImageResource(
                            R.drawable.icn_chevron_up
                        )
                    }
                }

                override fun onSlide(bottomSheet: View, slideOffset: Float) {}
            })

        frameValueTextView = findViewById(R.id.frame_info)
        cropValueTextView = findViewById(R.id.crop_info)
        inferenceTimeTextView = findViewById(R.id.inference_info)
    }




//    fun enable(view: View){
//        if(enabled){
//            enBtn.setText(R.string.enable)
//            enabled=false
//        } else{
//            enBtn.setText(R.string.disable)
//            enabled=true
//        }
//    }
//
//    fun changeCam(view: View){
//        if(frontCam) {
//            mOpenCvCameraView!!.setCameraIndex(CameraBridgeViewBase.CAMERA_ID_BACK)
//            camBtn.setText(R.string.switch_to_front)
//            frontCam=false
//        } else{
//            mOpenCvCameraView!!.setCameraIndex(CameraBridgeViewBase.CAMERA_ID_FRONT)
//            camBtn.setText(R.string.switch_to_back)
//            frontCam=true
//        }
//
//    }

    override fun onRequestPermissionsResult(
        requestCode: Int,
        permissions: Array<String>,
        grantResults: IntArray
    ) {
        when (requestCode) {
            CAMERA_PERMISSION_REQUEST -> {
                if (grantResults.isNotEmpty() && grantResults[0] == PackageManager.PERMISSION_GRANTED) {
                    mOpenCvCameraView.setCameraPermissionGranted()
                } else {
                    val message = "Camera permission was not granted"
                    Log.e(TAG, message)
                    Toast.makeText(this, message, Toast.LENGTH_LONG).show()
                }
            }
            else -> {
                Log.e(TAG, "Unexpected permission request")
            }
        }
    }

    override fun onPause() {
        super.onPause()
        if (mOpenCvCameraView != null)
            mOpenCvCameraView.disableView()
    }

    override fun onResume() {
        super.onResume()
        if (!OpenCVLoader.initDebug()) {
            Log.d(TAG, "Internal OpenCV library not found. Using OpenCV Manager for initialization")
            OpenCVLoader.initAsync(OpenCVLoader.OPENCV_VERSION, this, mLoaderCallback)
        } else {
            Log.d(TAG, "OpenCV library found inside package. Using it!")
            mLoaderCallback.onManagerConnected(LoaderCallbackInterface.SUCCESS)
        }
    }

    override fun onDestroy() {
        super.onDestroy()
        if (mOpenCvCameraView != null)
            mOpenCvCameraView.disableView()
    }

    override fun onCameraViewStarted(width: Int, height: Int) {
//        val labels=getPath("classes.txt",this)
//        val weights=getPath("myown.onnx",this)
//        val labels=getPath("obj.names",this)
//        val weights=getPath("myown.weights",this)
//        val config=getPath("myown.cfg",this)
//        val labels=getPath("coco.names",this)
//        val weights=getPath("yolov4-tiny.weights",this)
//        val config=getPath("yolov4-tiny.cfg",this)
        val labels=getPath("obj.names", this)
        val weights=getPath("custom_yolov4-tiny_best_107.weights", this)
        val config=getPath("custom_yolov4-tiny.cfg", this)
        initializeNet(labels, weights, config)
    }

    override fun onCameraViewStopped() {}

    override fun onCameraFrame(frame: CameraBridgeViewBase.CvCameraViewFrame): Mat {
        // get current camera frame as OpenCV Mat object
        val mat = frame.rgba()
        val angle=getScreenOrientation()

        
        // native call to process current camera frame
        if(enabled){
            objectDetection(mat.nativeObjAddr, angle)
        } else{
            returnFrame(mat.nativeObjAddr, angle)
        }
        runOnUiThread {
            showFrameInfo(getPrevSize())
            showCropInfo(getDetSize())
            showInference(getInfTime())
        }

        // return processed frame for live preview
        return mat
    }

    private fun getPath(file: String, context: Context): String? {
        val assetManager: AssetManager = context.assets
        lateinit var inputStream: BufferedInputStream
        try { // Read data from assets.
            inputStream = BufferedInputStream(assetManager.open(file))
            val data = ByteArray(inputStream.available())
            inputStream.read(data)
            inputStream.close()
            // Create copy file in storage.
            val outFile = File(context.filesDir, file)
            val os = FileOutputStream(outFile)
            os.write(data)
            os.close()
            // Return a path to file which may be read in common way.
            return outFile.absolutePath
        } catch (ex: IOException) {
            Log.i(TAG, "Failed to upload a file")
        }
        return ""
    }

    protected fun getScreenOrientation(): Int {
        return when (windowManager.defaultDisplay.rotation) {
            Surface.ROTATION_270 -> 270
            Surface.ROTATION_180 -> 180
            Surface.ROTATION_90 -> 90
            else -> 0
        }
    }

    protected fun showFrameInfo(frameInfo: String?) {
        frameValueTextView.text = frameInfo
    }

    protected fun showCropInfo(cropInfo: String?) {
        cropValueTextView.text = cropInfo
    }

    protected fun showInference(inferenceTime: String?) {
        inferenceTimeTextView.text = inferenceTime
    }

    private external fun objectDetection(matAddr: Long, angle: Int)
    private external fun returnFrame(matAddr: Long, angle: Int)
    private external fun initializeNet(names: String?, weights: String?, config: String?)
    private external fun getPrevSize():String
    private external fun getDetSize():String
    private external fun getInfTime():String


    companion object {

        private const val TAG = "YoloActivity"
        private const val CAMERA_PERMISSION_REQUEST = 1
    }
}
