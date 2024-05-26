## CameraX YUV420_888 to H264 buffer

这是一个用于将 CameraX 输出的 YUV420_888 格式的图像数据转换为 H264 编码的组件。通过这个组件，您可以轻松地将 CameraX 的原始图像数据编码为 H264 格式，以便进行存储、传输或流式传输等操作。



```kotlin
val fragment =
    supportFragmentManager.findFragmentById(R.id.fragment) as CameraXPreviewFragment

fragment.setOutputBufferCallback { bytes ->
    // here process video frame buffer
    Log.e("TAG", bytes.joinToString ())
    FileUtil.writeBytesToFile(this, bytes, "test.h264")

}
```


**将摄像头预览图像进行转换并显示的逻辑：**

1. **将 YUV_420_888 转换为 NV21：**


```kotlin
val nv21 = YuvUtil.YUV_420_888toNV21(imageProxy.image)
```


此行将图像数据从 `YUV_420_888` 格式转换为 `NV21` 格式。`NV21` 是摄像头预览和视频编码常用的格式。

2. **旋转 NV21 图像：**


```kotlin
val rotateNV21Right90 =
    if (lensFacing == CameraSelector.LENS_FACING_FRONT)
        YuvUtil.rotateYUV420Degree270(nv21, imageProxy.width, imageProxy.height)
    else
        YuvUtil.rotateYUV420Degree90(nv21, imageProxy.width, imageProxy.height)
```


此代码检查镜头朝向（前置或后置），并将 `NV21` 图像旋转 270 度（如果是前置摄像头）或 90 度（如果是后置摄像头）。这可确保图像在屏幕上正确显示。

3. **将 NV21 转换为 NV12：**


```kotlin
val nv12 = ByteArray(imageProxy.width * imageProxy.height * 3 / 2)
YuvUtil.NV21ToNV12(rotateNV21Right90, nv12, imageProxy.width, imageProxy.height)
```


此代码将旋转后的 `NV21` 图像转换为 `NV12` 格式。`NV12` 是MeidaCodec需要的格式。

4. **从 NV12 图像创建位图：**


```kotlin
val bitmap = BitmapUtils.getBitmap(
    ByteBuffer.wrap(nv12),
    imageProxy.width,
    imageProxy.height,
    imageProxy.imageInfo.rotationDegrees
)
```


此行从 `NV12` 图像数据创建一个 `Bitmap` 对象。`rotationDegrees` 参数指定要应用于位图的旋转角度。

5. **在 ImageView 上显示位图：**


```kotlin
activity?.runOnUiThread {
    binding.myImageView.setImageBitmap(bitmap)
}
```


此代码在 UI 线程中使用创建的位图更新 `ImageView`。


**IDE**: Android Studio Koala Feature Drop | 2024.1.2 Canary 3