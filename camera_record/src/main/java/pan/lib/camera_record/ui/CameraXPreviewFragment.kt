package pan.lib.camera_record.ui

import android.Manifest
import android.content.pm.PackageManager
import android.os.Bundle
import android.util.Log
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import androidx.activity.result.contract.ActivityResultContracts
import androidx.appcompat.app.AlertDialog
import androidx.core.content.ContextCompat
import androidx.fragment.app.Fragment
import pan.lib.camera_record.databinding.FragmentCameraPreviewBinding
import pan.lib.camera_record.media.StreamManager
import pan.lib.camera_record.media.yuv.BitmapUtils
import java.nio.ByteBuffer

/**
 * @author pan qi
 * @since 2024/2/3
 */
class CameraXPreviewFragment : Fragment() {

    private lateinit var binding: FragmentCameraPreviewBinding
    private lateinit var streamManager: StreamManager
    override fun onCreateView(
        inflater: LayoutInflater, container: ViewGroup?,
        savedInstanceState: Bundle?
    ): View {
        binding = FragmentCameraPreviewBinding.inflate(inflater, container, false)
        return binding.root
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)
        requestPermissions()
        streamManager = StreamManager(requireContext(), this, binding.prewview) { nv21, imageProxy ->
            val bitmap = BitmapUtils.getBitmap(
                ByteBuffer.wrap(nv21),
                imageProxy.width,
                imageProxy.height,
                imageProxy.imageInfo.rotationDegrees
            )
            binding.myImageView.post {
                binding.myImageView.setImageBitmap(bitmap)
            }
        }

        binding.cameraSwitchButton.setOnClickListener {
            streamManager.switchCamera()
        }

        binding.stopButton.setOnClickListener {
            streamManager.stop()
        }
    }

    private val requestPermissionLauncher =
        registerForActivityResult(ActivityResultContracts.RequestMultiplePermissions()) { permissions ->
            handlePermissionsResult(permissions)
        }

    private fun requestPermissions() {
        // 分别检查相机和录音权限的状态
        val cameraPermissionStatus = ContextCompat.checkSelfPermission(
            requireContext(),
            Manifest.permission.CAMERA
        )
        val recordAudioPermissionStatus = ContextCompat.checkSelfPermission(
            requireContext(),
            Manifest.permission.RECORD_AUDIO
        )

        // 如果任一权限未被授予，则发起权限请求
        if (cameraPermissionStatus != PackageManager.PERMISSION_GRANTED ||
            recordAudioPermissionStatus != PackageManager.PERMISSION_GRANTED
        ) {
            requestPermissionLauncher.launch(
                arrayOf(
                    Manifest.permission.CAMERA,
                    Manifest.permission.RECORD_AUDIO
                )
            )
        } else {
            // 权限确认后，启动streamManager
            binding.root.post {
                streamManager.start()
            }
        }
    }


    private fun handlePermissionsResult(permissions: Map<String, Boolean>) {
        val allGranted = permissions.all { it.value }
        if (allGranted) {
            binding.root.post {
                streamManager.start()
            }
        } else {
            val deniedPermissions = permissions.filter { !it.value }.keys.joinToString("\n")
            showPermissionDeniedDialog(deniedPermissions)
        }
    }


    private fun showPermissionDeniedDialog(deniedPermissions: String) {
        AlertDialog.Builder(requireContext())
            .setTitle("以下权限被拒绝")
            .setMessage(deniedPermissions)
            .setPositiveButton("确定", null)
            .show()
    }

    override fun onDestroyView() {
        streamManager.stop()
        super.onDestroyView()
    }
}
