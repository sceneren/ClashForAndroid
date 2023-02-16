package com.github.kr328.clash

import android.Manifest
import android.annotation.SuppressLint
import android.content.pm.PackageManager
import android.net.Uri
import android.os.Build
import android.os.Bundle
import android.widget.Button
import androidx.activity.result.contract.ActivityResultContract
import androidx.activity.result.contract.ActivityResultContracts
import androidx.appcompat.app.AppCompatActivity
import androidx.core.content.ContextCompat
import androidx.documentfile.provider.DocumentFile
import com.blankj.utilcode.util.LogUtils
import com.blankj.utilcode.util.PathUtils
import com.github.kr328.clash.common.log.Log
import com.github.kr328.clash.core.model.ProxySort
import com.github.kr328.clash.remote.FilesClient
import com.github.kr328.clash.service.model.Profile
import com.github.kr328.clash.util.*
import com.hjq.permissions.Permission
import com.hjq.permissions.XXPermissions
import kotlinx.coroutines.*
import java.io.File
import java.util.*
import java.util.concurrent.atomic.AtomicInteger
import kotlin.coroutines.suspendCoroutine

class TestAct : AppCompatActivity(),
    CoroutineScope by MainScope() {

//    private val events = Channel<BaseActivity.Event>(Channel.UNLIMITED)


    private var defer: suspend () -> Unit = {}
    private var deferRunning = false

    private val nextRequestKey = AtomicInteger(0)

    private val fileClient = FilesClient(this)


    private lateinit var btnOpen: Button
    private lateinit var btnClose: Button
    private lateinit var btnChoose: Button
    private lateinit var btnQuery: Button

    @SuppressLint("MissingInflatedId")
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.act_test)
        btnOpen = findViewById(R.id.btn_open)
        btnClose = findViewById(R.id.btn_close)
        btnChoose = findViewById(R.id.btn_choose)
        btnQuery = findViewById(R.id.btn_query)
        XXPermissions.with(this)
            .permission(Permission.MANAGE_EXTERNAL_STORAGE)
            .request { _, _ -> }




        btnOpen.setOnClickListener {
            startClash()
        }

        btnClose.setOnClickListener {
            stopClashService()
        }

        btnChoose.setOnClickListener {
            launch {

                val path = PathUtils.getExternalDownloadsPath() + "/Weixin/clash.yaml"
                val documentFile = DocumentFile.fromFile(File(path))

                val uri = documentFile.uri
                chooseConfig(uri)

            }
        }
        btnQuery.setOnClickListener {

            launch {
                val names = withClash { queryProxyGroupNames(false) }
                names.forEach {
                    val proxy = withClash { queryProxyGroup(it, ProxySort.Default) }
                    proxy.proxies.forEach {
                        LogUtils.e(it.name)
                    }

                }

//                withClash {
//                    val result = queryProxyGroupNames(false)
//                    LogUtils.e("queryProviders=============>$result")
//                }
            }
        }

    }

    private fun chooseConfig(clashConfigUri: Uri) {
        launch {
            withProfile {
                val name = getString(R.string.new_profile)
                val uuid: UUID = create(Profile.Type.File, name)
                val documentId = uuid.toString()
                val config =
                    fileClient.list(documentId).firstOrNull { it.id.endsWith("config.yaml") }
                if (Build.VERSION.SDK_INT >= 23) {
                    val hasPermission = ContextCompat.checkSelfPermission(
                        this@TestAct,
                        Manifest.permission.READ_EXTERNAL_STORAGE
                    ) == PackageManager.PERMISSION_GRANTED

                    if (!hasPermission) {
                        val granted = startActivityForResult(
                            ActivityResultContracts.RequestPermission(),
                            Manifest.permission.READ_EXTERNAL_STORAGE,
                        )

                        if (!granted) {
                            return@withProfile
                        }
                    }
                }
                fileClient.copyDocument(config!!.id, clashConfigUri)

                try {
                    val profile = queryByUUID(uuid)!!
                    withProfile {
                        patch(profile.uuid, profile.name, profile.source, profile.interval)
                        commit(profile.uuid)
                        setActive(profile)
                    }
                } catch (e: Exception) {
                    LogUtils.e(e)
                }

            }
        }
    }


    private fun startClash() {
        launch {
            val active = withProfile { queryActive() }
            if (active == null || !active.imported) {
                return@launch
            }

            val vpnRequest = startClashService()

            try {
                if (vpnRequest != null) {
                    val result = startActivityForResult(
                        ActivityResultContracts.StartActivityForResult(),
                        vpnRequest
                    )

                    if (result.resultCode == RESULT_OK)
                        startClashService()
                }
            } catch (e: Exception) {
                Log.e(getString(R.string.unable_to_start_vpn))
            }

        }
    }


    private suspend fun <I, O> startActivityForResult(
        contracts: ActivityResultContract<I, O>,
        input: I
    ): O = withContext(Dispatchers.Main) {
        val requestKey = nextRequestKey.getAndIncrement().toString()

        ActivityResultLifecycle().use { lifecycle, start ->
            suspendCoroutine { c ->
                activityResultRegistry.register(requestKey, lifecycle, contracts) {
                    c.resumeWith(Result.success(it))
                }.apply { start() }.launch(input)
            }
        }
    }

    override fun onDestroy() {
        cancel()
        super.onDestroy()
    }

    override fun finish() {
        if (deferRunning) {
            return
        }

        deferRunning = true

        launch {
            try {
                defer()
            } finally {
                withContext(NonCancellable) {
                    super.finish()
                }
            }
        }
    }

}