package com.example.moneyhelper

import android.Manifest
import android.app.Activity
import android.content.Context
import android.content.pm.PackageManager
import android.content.res.AssetManager
import android.os.Bundle
import androidx.core.app.ActivityCompat
import android.util.Log
import android.view.SurfaceView
import android.view.View
import android.view.WindowManager
import android.widget.Toast
import kotlinx.android.synthetic.main.activity_main.*
import org.opencv.android.BaseLoaderCallback
import org.opencv.android.CameraBridgeViewBase
import org.opencv.android.LoaderCallbackInterface
import org.opencv.android.OpenCVLoader
import org.opencv.core.Mat
import java.io.BufferedInputStream
import java.io.File
import java.io.FileOutputStream
import java.io.IOException


class MainActivity : Activity(), CameraBridgeViewBase.CvCameraViewListener2 {

    private var mOpenCvCameraView: CameraBridgeViewBase? = null
    private var enabled: Boolean = false
    private var frontCam: Boolean = true

    private val mLoaderCallback = object : BaseLoaderCallback(this) {
        override fun onManagerConnected(status: Int) {
            when (status) {
                LoaderCallbackInterface.SUCCESS -> {
                    Log.i(TAG, "OpenCV loaded successfully")

                    // Load native library after(!) OpenCV initialization
                    System.loadLibrary("native-lib")

                    mOpenCvCameraView!!.enableView()
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
                this@MainActivity,
                arrayOf(Manifest.permission.CAMERA),
                CAMERA_PERMISSION_REQUEST
        )

        window.addFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON)
        setContentView(R.layout.activity_main)

        mOpenCvCameraView = findViewById<CameraBridgeViewBase>(R.id.main_surface)

        mOpenCvCameraView!!.setCameraIndex(CameraBridgeViewBase.CAMERA_ID_FRONT)

        mOpenCvCameraView!!.visibility = SurfaceView.VISIBLE

        mOpenCvCameraView!!.setCvCameraViewListener(this)
    }

    fun enable(view: View){
        if(enabled){
            enBtn.setText(R.string.enable)
            enabled=false
        } else{
            enBtn.setText(R.string.disable)
            enabled=true
        }
    }

    fun changeCam(view: View){
        if(frontCam) {
            mOpenCvCameraView!!.setCameraIndex(CameraBridgeViewBase.CAMERA_ID_BACK)
            camBtn.setText(R.string.switch_to_front)
            frontCam=false
        } else{
            mOpenCvCameraView!!.setCameraIndex(CameraBridgeViewBase.CAMERA_ID_FRONT)
            camBtn.setText(R.string.switch_to_back)
            frontCam=true
        }

    }

    override fun onRequestPermissionsResult(
            requestCode: Int,
            permissions: Array<String>,
            grantResults: IntArray
    ) {
        when (requestCode) {
            CAMERA_PERMISSION_REQUEST -> {
                if (grantResults.isNotEmpty() && grantResults[0] == PackageManager.PERMISSION_GRANTED) {
                    mOpenCvCameraView!!.setCameraPermissionGranted()
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
            mOpenCvCameraView!!.disableView()
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
            mOpenCvCameraView!!.disableView()
    }

    override fun onCameraViewStarted(width: Int, height: Int) {
        val labels=getPath("obj.names",this)
        val weights=getPath("myown.weights",this)
        val config=getPath("myown.cfg",this)
        //val labels=getPath("coco.names",this)
        //val weights=getPath("yolov3-tiny.weights",this)
        //val config=getPath("yolov3-tiny.cfg",this)
        initializeNet(labels,weights,config)
    }

    override fun onCameraViewStopped() {}

    override fun onCameraFrame(frame: CameraBridgeViewBase.CvCameraViewFrame): Mat {
        // get current camera frame as OpenCV Mat object
        val mat = frame.rgba()

        // native call to process current camera frame
        if(enabled){
            objectDetection(mat.nativeObjAddr)
        } else{
            returnFrame(mat.nativeObjAddr)
        }

        // return processed frame for live preview
        return mat
    }

    private fun getPath(file: String, context: Context): String? {
        val assetManager: AssetManager = context.getAssets()
        var inputStream: BufferedInputStream? = null
        try { // Read data from assets.
            inputStream = BufferedInputStream(assetManager.open(file))
            val data = ByteArray(inputStream.available())
            inputStream.read(data)
            inputStream.close()
            // Create copy file in storage.
            val outFile = File(context.getFilesDir(), file)
            val os = FileOutputStream(outFile)
            os.write(data)
            os.close()
            // Return a path to file which may be read in common way.
            return outFile.getAbsolutePath()
        } catch (ex: IOException) {
            Log.i(TAG, "Failed to upload a file")
        }
        return ""
    }

    private external fun objectDetection(matAddr: Long)
    private external fun returnFrame(matAddr: Long)
    private external fun initializeNet(names:String?, weights:String?, config:String?)

    companion object {

        private const val TAG = "MainActivity"
        private const val CAMERA_PERMISSION_REQUEST = 1
    }
}
