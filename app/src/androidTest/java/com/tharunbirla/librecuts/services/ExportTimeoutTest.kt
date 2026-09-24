package com.tharunbirla.librecuts.services

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.net.Uri
import android.os.Build
import android.os.ParcelFileDescriptor
import androidx.localbroadcastmanager.content.LocalBroadcastManager
import com.antonkarpenko.ffmpegkit.FFmpegKit
import com.antonkarpenko.ffmpegkit.ReturnCode
import androidx.test.core.app.ActivityScenario
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import com.tharunbirla.librecuts.MainActivity
import com.tharunbirla.librecuts.R
import java.io.File
import java.util.concurrent.CountDownLatch
import java.util.concurrent.TimeUnit
import java.util.concurrent.atomic.AtomicReference
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Assume.assumeTrue
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith

/**
 * Si Android corta un export por el límite de los servicios dataSync, el usuario se entera (M-241,
 * `specs/target-sdk-36.md` regla 4). El límite real es de 6 h en 24; `device_config` lo acorta a
 * segundos, como indica la guía oficial de timeouts de foreground services.
 */
@RunWith(AndroidJUnit4::class)
class ExportTimeoutTest {
    private val instrumentation = InstrumentationRegistry.getInstrumentation()
    private val context = instrumentation.targetContext
    private val output = File(context.cacheDir, "export_timeout_test.mp4")

    @Before
    fun onlyWhereTheLimitExists() {
        // El límite llegó en Android 15; en la tablet (Android 13) este test no aplica.
        assumeTrue(Build.VERSION.SDK_INT >= Build.VERSION_CODES.VANILLA_ICE_CREAM)
    }

    @After
    fun restoreTheRealLimit() {
        shell("device_config delete activity_manager data_sync_fgs_timeout_duration")
        output.delete()
    }

    @Test
    fun anExportStoppedByTheSystemTellsTheUserWhy() {
        shell("device_config put activity_manager data_sync_fgs_timeout_duration 3000")
        shell("logcat -c")

        val error = AtomicReference<String?>()
        val failed = CountDownLatch(1)
        val receiver = object : BroadcastReceiver() {
            override fun onReceive(context: Context, intent: Intent) {
                error.set(intent.getStringExtra(ExportService.EXTRA_ERROR))
                failed.countDown()
            }
        }
        val broadcasts = LocalBroadcastManager.getInstance(context)
        broadcasts.registerReceiver(receiver, IntentFilter(ExportService.ACTION_EXPORT_FAILURE))

        // Desde segundo plano Android no deja arrancar un foreground service: hace falta una Activity al frente.
        ActivityScenario.launch(MainActivity::class.java).use {
            // Diez minutos de 1080p en veryslow: tarda mucho más que los 3 s del límite.
            val command = "-f lavfi -i testsrc2=size=1920x1080:rate=30:duration=600 " +
                "-c:v libx264 -preset veryslow -y \"${output.absolutePath}\""
            context.startForegroundService(
                Intent(context, ExportService::class.java)
                    .putExtra(ExportService.EXTRA_COMMAND, command)
                    .putExtra(ExportService.EXTRA_TEMP_OUTPUT_PATH, output.absolutePath)
                    .putExtra(ExportService.EXTRA_TOTAL_DURATION_SECS, 600.0)
            )
        }
        // Con la app al frente el límite no corre (observado en Android 16: 30 s de export sin
        // timeout). Cerrar la Activity deja la app en segundo plano, como cuando el usuario sale.
        assertTrue("el export no avisó que se detuvo", failed.await(30, TimeUnit.SECONDS))
        broadcasts.unregisterReceiver(receiver)

        assertEquals(context.getString(R.string.export_stopped_time_limit), error.get())
        // La cancelación no es un fallo de FFmpeg: no debe dejar un error ni un reporte de diagnóstico.
        Thread.sleep(2_000)
        val errors = shell("logcat -d -s FFmpegRenderEngine:E")
        assertTrue("la cancelación se registró como fallo:\n$errors", !errors.contains("Exception during FFmpeg execution"))
    }

    @Test
    fun aProxyStoppedByTheSystemDoesNotTakeTheAppDown() {
        // Fuente de 180 s en 1080p. Con 60 s el proxy terminaba ~5 s después del corte, dentro del margen
        // que da el sistema, y no se veía el problema; con 180 s tarda ~25 s.
        val source = File(context.cacheDir, "proxy_timeout_source.mp4")
        val made = FFmpegKit.execute(
            "-y -f lavfi -i testsrc2=size=1920x1080:rate=30:duration=180 " +
                "-c:v libx264 -preset ultrafast \"${source.absolutePath}\""
        )
        assertTrue("no se pudo generar la fuente", ReturnCode.isSuccess(made.returnCode))
        val proxies = File(context.cacheDir, "proxies")
        proxies.listFiles()?.forEach { it.delete() }

        shell("device_config put activity_manager data_sync_fgs_timeout_duration 3000")
        ActivityScenario.launch(MainActivity::class.java).use {
            context.startForegroundService(
                Intent(context, ProxyGenerationService::class.java)
                    .putExtra(ProxyGenerationService.EXTRA_SOURCE_URI, Uri.fromFile(source).toString())
                    .putExtra(ProxyGenerationService.EXTRA_DEPENDENCY_ID, "timeout-test")
            )
        }

        // Si el servicio no para a tiempo, el sistema tumba el proceso y el test no llega a la aserción.
        // En Android 16 el crash llega 10 s después del corte (3 s de límite + 10 s de gracia = 13 s).
        Thread.sleep(20_000)
        source.delete()
        assertEquals("quedó un proxy a medias", emptyList<String>(), proxies.list()?.toList() ?: emptyList<String>())
    }

    private fun shell(command: String): String =
        // Leer la salida completa es lo que espera a que el comando termine.
        ParcelFileDescriptor.AutoCloseInputStream(instrumentation.uiAutomation.executeShellCommand(command))
            .use { String(it.readBytes()) }
}
