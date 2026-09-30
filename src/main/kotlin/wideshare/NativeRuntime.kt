package wideshare

import java.io.File

/**
 * In the GraalVM native image `java.home` is unset, but Skiko loads `<java.home>/bin/jawt.dll`.
 * Points it at the executable's folder, where the native build puts a `bin` folder with the JDK libraries.
 */
internal fun fixJavaHomeForNativeImage() {
    if (System.getProperty("java.home") != null) return
    val exe = ProcessHandle.current().info().command().orElse(null) ?: return
    File(exe).parentFile?.let { System.setProperty("java.home", it.path) }
}
