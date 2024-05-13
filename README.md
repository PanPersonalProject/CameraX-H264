### CameraX YUV420_888 to H264 buffer

这是一个用于将 CameraX 输出的 YUV420_888 格式的图像数据转换为 H264 编码的组件。通过这个组件，您可以轻松地将 CameraX 的原始图像数据编码为 H264 格式，以便进行存储、传输或流式传输等操作。

This is a component designed to convert YUV420_888 format image data output from CameraX to H264 encoding. With this component, you can easily encode the raw image data from CameraX into H264 format for storage, transmission, or streaming purposes.

```kotlin
        val fragment =
    supportFragmentManager.findFragmentById(R.id.fragment) as CameraXPreviewFragment

fragment.setOutputBufferCallback { bytes ->
    // here process video frame buffer
    Log.e("TAG", bytes.joinToString ())
    FileUtil.writeBytesToFile(this, bytes, "test.h264")

}
```



