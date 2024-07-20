package pan.lib.camera_record.ui

import android.Manifest
import android.annotation.SuppressLint
import android.content.pm.PackageManager
import android.os.Bundle
import android.util.Size
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import androidx.activity.result.contract.ActivityResultContracts
import androidx.annotation.OptIn
import androidx.appcompat.app.AlertDialog
import androidx.camera.core.CameraSelector
import androidx.camera.core.ExperimentalGetImage
import androidx.camera.core.ImageAnalysis
import androidx.camera.core.Preview
import androidx.camera.core.resolutionselector.ResolutionSelector
import androidx.camera.core.resolutionselector.ResolutionStrategy
import androidx.camera.lifecycle.ProcessCameraProvider
import androidx.core.content.ContextCompat
import androidx.fragment.app.Fragment
import androidx.lifecycle.LifecycleOwner
import com.google.common.util.concurrent.ListenableFuture
import pan.lib.camera_record.databinding.FragmentCameraPreviewBinding
import pan.lib.camera_record.media.AudioRecorder
import pan.lib.camera_record.media.BitmapUtils
import pan.lib.camera_record.media.VideoEncoder
import pan.lib.camera_record.media.YuvUtil
import pan.lib.camera_record.test.VideoFileWriter
import java.nio.ByteBuffer
import java.util.concurrent.Executors

/**
 * @author pan qi
 * @since 2024/2/3
 */
class CameraXPreviewFragment : Fragment() {

    private var _binding: FragmentCameraPreviewBinding? = null
    private val binding get() = _binding!!
    private lateinit var cameraProviderFuture: ListenableFuture<ProcessCameraProvider>
    private var lensFacing: Int = CameraSelector.LENS_FACING_BACK

    private lateinit var videoFileWriter: VideoFileWriter
    private var outputBytesCallback: ((ByteArray) -> Unit)? = null

    private var isEncoderInitialized = false
    private val audioRecorder: AudioRecorder by lazy { AudioRecorder() }
    private val videoEncoder = VideoEncoder { byteBuffer ->
        val data: ByteArray
        if (byteBuffer.hasArray()) {
            data = byteBuffer.array()
        } else {
            data = ByteArray(byteBuffer.remaining())
            byteBuffer.get(data)
        }
        outputBytesCallback?.invoke(data)
    }

    fun setOutputBufferCallback(callback: (ByteArray) -> Unit) {
        outputBytesCallback = callback
    }

    override fun onCreateView(
        inflater: LayoutInflater, container: ViewGroup?,
        savedInstanceState: Bundle?
    ): View {
        _binding = FragmentCameraPreviewBinding.inflate(inflater, container, false)
        return binding.root
    }


    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)

        requestPermission()
        videoFileWriter = VideoFileWriter(requireContext().contentResolver)
        binding.cameraSwitchButton.setOnClickListener {
            switchCamera()
        }

        binding.stopButton.setOnClickListener {
            videoEncoder.stop()
            binding.stopButton.postDelayed({
                videoFileWriter.closeOutputStream()

            }, 1000)
        }
    }

    private val requestPermissionLauncher =
        registerForActivityResult(ActivityResultContracts.RequestMultiplePermissions()) { permissions ->
            val allGranted = permissions.all { it.value }
            if (allGranted) {
                binding.root.post {
                    startCameraPreview()
                    startRecording()
                }
            } else {
                val deniedPermissions = permissions.filter { !it.value }.keys.joinToString("\n")
                AlertDialog.Builder(requireContext())
                    .setTitle("以下权限被拒绝")
                    .setMessage(deniedPermissions)
                    .setPositiveButton("确定", null)
                    .show()
            }
        }

    private fun requestPermission() {
        if (ContextCompat.checkSelfPermission(
                requireContext(),
                Manifest.permission.CAMERA
            ) != PackageManager.PERMISSION_GRANTED ||
            ContextCompat.checkSelfPermission(
                requireContext(),
                Manifest.permission.RECORD_AUDIO
            ) != PackageManager.PERMISSION_GRANTED
        ) {
            requestPermissionLauncher.launch(
                arrayOf(
                    Manifest.permission.CAMERA,
                    Manifest.permission.RECORD_AUDIO
                )
            )
        } else {
            binding.root.post {
                startCameraPreview()
                startRecording()
            }
        }
    }

    // 开始录音
    @SuppressLint("MissingPermission")
    private fun startRecording() {
        audioRecorder.startRecording(requireContext())
    }


    private fun switchCamera() {
        lensFacing = if (CameraSelector.LENS_FACING_FRONT == lensFacing) {
            CameraSelector.LENS_FACING_BACK
        } else {
            CameraSelector.LENS_FACING_FRONT
        }
        startCameraPreview()
    }

    private fun bindPreview(cameraProvider: ProcessCameraProvider, imageAnalysis: ImageAnalysis) {
        val preview: Preview = Preview.Builder()
            .build()

        val cameraSelector: CameraSelector = CameraSelector.Builder()
            .requireLensFacing(lensFacing)
            .build()

        preview.surfaceProvider = binding.prewview.getSurfaceProvider()

        cameraProvider.unbindAll()
        cameraProvider.bindToLifecycle(
            this as LifecycleOwner,
            cameraSelector,
            imageAnalysis,
            preview
        )
    }

    @OptIn(ExperimentalGetImage::class)
    private fun startCameraPreview() {
        val rotation = binding.prewview.display.rotation
        val resolutionSelector = ResolutionSelector.Builder().setResolutionStrategy(
            ResolutionStrategy(
                Size(960, 1080),
                ResolutionStrategy.FALLBACK_RULE_CLOSEST_LOWER
            )
        ).build()

        val imageAnalysis = ImageAnalysis.Builder()
            .setOutputImageFormat(ImageAnalysis.OUTPUT_IMAGE_FORMAT_YUV_420_888)
            .setTargetRotation(rotation)
            .setResolutionSelector(resolutionSelector)
            .setBackpressureStrategy(ImageAnalysis.STRATEGY_KEEP_ONLY_LATEST) // 非阻塞式，保留最新的图像
            .build()


        // 创建一个新的线程执行器，并设置为图像分析器的执行器。当有新的图像可用时，分析器的代码将在这个新的线程上执行。
        imageAnalysis.setAnalyzer(Executors.newSingleThreadExecutor()) { imageProxy ->
            if (!isEncoderInitialized) {
                videoEncoder.init(requireContext(), imageProxy.height, imageProxy.width)
                videoEncoder.start()
                isEncoderInitialized = true
            }
            val nv21 = YuvUtil.YUV_420_888toNV21(imageProxy.image)
            val rotateNV21Right90 =
                if (lensFacing == CameraSelector.LENS_FACING_FRONT)
                    YuvUtil.rotateYUV420Degree270(nv21, imageProxy.width, imageProxy.height)
                else
                    YuvUtil.rotateYUV420Degree90(nv21, imageProxy.width, imageProxy.height)

            val nv12 = ByteArray(imageProxy.width * imageProxy.height * 3 / 2)

            YuvUtil.NV21ToNV12(
                rotateNV21Right90,
                nv12,
                imageProxy.width,
                imageProxy.height
            )

            val bitmap = BitmapUtils.getBitmap(
                ByteBuffer.wrap(nv21),
                imageProxy.width,
                imageProxy.height,
                imageProxy.imageInfo.rotationDegrees
            )
            activity?.runOnUiThread {
                binding.myImageView.setImageBitmap(bitmap)
            }

            videoEncoder.encode(nv12)

            imageProxy.close()
        }

        cameraProviderFuture = ProcessCameraProvider.getInstance(requireContext())
        cameraProviderFuture.addListener({
            val cameraProvider = cameraProviderFuture.get()
            bindPreview(cameraProvider, imageAnalysis)
        }, ContextCompat.getMainExecutor(requireContext()))

    }


    override fun onDestroyView() {
        videoEncoder.stop()
        audioRecorder.stopRecording()
        super.onDestroyView()
        _binding = null
    }
}