package com.maxrave.simpmusic

import coil3.ImageLoader
import com.maxrave.logger.Logger
import org.jetbrains.skia.DirectContext
import org.jetbrains.skiko.SkiaLayer
import java.awt.Canvas
import java.awt.Component
import java.awt.Container
import java.awt.Window
import java.lang.ref.WeakReference
import javax.swing.JFrame

/**
 * Manages desktop GPU resources, VRAM allocations and frame rendering lifecycle.
 *
 * Problem addressed:
 *  1. Compose Desktop / Skiko by default allocates a 256MB–512MB Skia GPU resource cache,
 *     which combined with Coil image buffers and OpenGL textures accounts for ~424 MiB of VRAM.
 *  2. When minimized or in background, infinite Compose transitions, shaders and the Skiko redrawer
 *     continue running draw calls and swap-buffers, waking the GPU and causing stutter in games.
 *
 * This manager:
 *  - Caps Skia GPU resource cache to 48 MB by default (sufficient for all visible UI/art).
 *  - Pauses Skia rendering and hides the drawing canvas on minimization (drops GPU compute to 0.0%).
 *  - Purges unlocked textures, resets Skia GPU resources, clears Coil memory cache, and invokes
 *    MemoryTrimmer (malloc_trim) when minimized.
 *  - Seamlessly restores canvas visibility, cache limits and repaints when un-minimized (Alt+Tab).
 */
object DesktopGpuManager {
    private const val TAG = "DesktopGpuManager"

    // Normal active limit: 48 MB (down from Skia default of 256MB-512MB)
    const val NORMAL_CACHE_BYTES = 48L * 1024 * 1024

    // Minimized limit: 8 MB to force purging textures from VRAM
    const val MINIMIZED_CACHE_BYTES = 8L * 1024 * 1024

    private var registeredImageLoader: WeakReference<ImageLoader>? = null
    private var windowRef: WeakReference<Window>? = null
    private var directContextRef: WeakReference<DirectContext>? = null

    @Volatile
    var isMinimized: Boolean = false
        private set

    fun registerImageLoader(imageLoader: ImageLoader) {
        registeredImageLoader = WeakReference(imageLoader)
    }

    fun attach(window: Window) {
        windowRef = WeakReference(window)
        initDirectContext(window)
    }

    fun detach() {
        windowRef = null
        directContextRef = null
    }

    fun initDirectContext(window: Window) {
        val ctx = getDirectContext(window)
        if (ctx != null) {
            directContextRef = WeakReference(ctx)
            runCatching {
                ctx.resourceCacheLimit = NORMAL_CACHE_BYTES
                Logger.d(TAG, "Initialized DirectContext GPU resource cache limit to ${NORMAL_CACHE_BYTES / 1024 / 1024} MB")
            }.onFailure {
                Logger.w(TAG, "Failed to set resource cache limit: ${it.message}")
            }
        }
    }

    fun onMinimized(window: Window) {
        if (isMinimized) return
        isMinimized = true
        Logger.i(TAG, "Desktop window minimized: pausing GPU rendering and freeing VRAM")

        // 1. Hide the content pane, all SkiaLayers and Canvases so Skiko's Redrawer stops completely
        runCatching {
            if (window is JFrame) {
                window.contentPane.isVisible = false
            }
            findSkiaLayers(window).forEach { layer ->
                layer.isVisible = false
            }
            findCanvases(window).forEach { canvas ->
                canvas.isVisible = false
            }
        }

        // 2. Shrink Skia GPU resource cache and purge textures from VRAM
        runCatching {
            val ctx = directContextRef?.get() ?: getDirectContext(window)
            if (ctx != null) {
                ctx.resourceCacheLimit = MINIMIZED_CACHE_BYTES
                ctx.resetAll()
                ctx.submit(true)
                Logger.i(TAG, "Purged Skia GPU resource cache in VRAM")
            }
        }

        // 3. Clear in-memory bitmap cache
        runCatching {
            registeredImageLoader?.get()?.memoryCache?.clear()
        }

        // 4. Trim native memory pages (glibc malloc_trim / Windows HeapOptimizeResources)
        runCatching {
            val trimmerClass = Class.forName("com.simpmusic.media_jvm.memory.MemoryTrimmer")
            trimmerClass.getMethod("trim", String::class.java).invoke(null, "desktop-minimized")
        }

        // 5. Hint JVM GC
        System.gc()
    }

    fun onRestored(window: Window) {
        if (!isMinimized) return
        isMinimized = false
        Logger.i(TAG, "Desktop window restored: resuming GPU rendering")

        // 1. Restore visibility and trigger repaint
        runCatching {
            if (window is JFrame) {
                window.contentPane.isVisible = true
            }
            findSkiaLayers(window).forEach { layer ->
                layer.isVisible = true
                layer.repaint()
            }
            findCanvases(window).forEach { canvas ->
                canvas.isVisible = true
                canvas.repaint()
            }
        }

        // 2. Restore Skia GPU resource cache limit
        runCatching {
            val ctx = directContextRef?.get() ?: getDirectContext(window)
            if (ctx != null) {
                ctx.resourceCacheLimit = NORMAL_CACHE_BYTES
            }
        }

        // 3. Request immediate redraw
        runCatching {
            window.repaint()
        }
    }

    private fun findAllComponents(container: Container): List<Component> {
        val list = mutableListOf<Component>()
        for (comp in container.components) {
            list.add(comp)
            if (comp is Container) {
                list.addAll(findAllComponents(comp))
            }
        }
        return list
    }

    private fun findSkiaLayers(window: Window): List<Component> {
        val all = if (window is JFrame) {
            listOf(window.contentPane) + findAllComponents(window.contentPane)
        } else {
            findAllComponents(window)
        }
        return all.filter { it is SkiaLayer || it.javaClass.name.contains("SkiaLayer") }
    }

    private fun findCanvases(window: Window): List<Canvas> {
        val all = if (window is JFrame) {
            listOf(window.contentPane) + findAllComponents(window.contentPane)
        } else {
            findAllComponents(window)
        }
        return all.filterIsInstance<Canvas>()
    }

    private fun getDirectContext(window: Window): DirectContext? {
        return runCatching {
            val layers = findSkiaLayers(window)
            for (skiaLayer in layers) {
                // Find redrawer field in SkiaLayer
                val redrawerField = skiaLayer.javaClass.declaredFields.firstOrNull {
                    it.name == "redrawer" || it.name.startsWith("redrawer$")
                }?.apply { isAccessible = true }
                val redrawer = redrawerField?.get(skiaLayer) ?: continue

                // Find contextHandler in redrawer hierarchy
                var currClass: Class<*>? = redrawer.javaClass
                var contextHandler: Any? = null
                while (currClass != null && contextHandler == null) {
                    val field = currClass.declaredFields.firstOrNull {
                        it.name == "contextHandler" || it.type.name.contains("ContextHandler")
                    }?.apply { isAccessible = true }
                    if (field != null) {
                        contextHandler = field.get(redrawer)
                    }
                    currClass = currClass.superclass
                }
                if (contextHandler == null) continue

                // Find context in ContextHandler
                var chClass: Class<*>? = contextHandler.javaClass
                var directContext: DirectContext? = null
                while (chClass != null && directContext == null) {
                    val field = chClass.declaredFields.firstOrNull {
                        it.name == "context" && DirectContext::class.java.isAssignableFrom(it.type)
                    }?.apply { isAccessible = true }
                    if (field != null) {
                        directContext = field.get(contextHandler) as? DirectContext
                    }
                    chClass = chClass.superclass
                }
                if (directContext != null) {
                    return directContext
                }
            }
            null
        }.onFailure {
            Logger.w(TAG, "Failed to resolve DirectContext: ${it.message}")
        }.getOrNull()
    }
}
