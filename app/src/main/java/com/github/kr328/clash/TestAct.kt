package com.github.kr328.clash

import android.Manifest
import android.annotation.SuppressLint
import android.content.Intent
import android.content.pm.PackageManager
import android.net.Uri
import android.os.Build
import android.os.Bundle
import android.provider.MediaStore
import android.widget.Button
import androidx.activity.result.contract.ActivityResultContract
import androidx.activity.result.contract.ActivityResultContracts
import androidx.appcompat.app.AppCompatActivity
import androidx.core.content.ContextCompat
import androidx.lifecycle.lifecycleScope
import com.blankj.utilcode.util.FileUtils
import com.blankj.utilcode.util.LogUtils
import com.github.kr328.clash.common.log.Log
import com.github.kr328.clash.common.util.intent
import com.github.kr328.clash.common.util.setUUID
import com.github.kr328.clash.core.bridge.ClashException
import com.github.kr328.clash.core.model.TunnelState
import com.github.kr328.clash.remote.Broadcasts
import com.github.kr328.clash.remote.FilesClient
import com.github.kr328.clash.remote.Remote
import com.github.kr328.clash.service.model.Profile
import com.github.kr328.clash.service.util.generateProfileUUID
import com.github.kr328.clash.util.*
import com.hjq.permissions.Permission
import com.hjq.permissions.XXPermissions
import kotlinx.coroutines.*
import kotlinx.coroutines.channels.Channel
import java.io.File
import java.util.*
import java.util.concurrent.atomic.AtomicInteger
import kotlin.coroutines.suspendCoroutine

class TestAct : AppCompatActivity(),
    CoroutineScope by MainScope(),
    Broadcasts.Observer {

    private val events = Channel<BaseActivity.Event>(Channel.UNLIMITED)

    private var activityStarted: Boolean = false
    private val clashRunning: Boolean
        get() = Remote.broadcasts.clashRunning
    private var defer: suspend () -> Unit = {}
    private var deferRunning = false

    private val nextRequestKey = AtomicInteger(0)


    private val requests: Channel<R> = Channel(Channel.UNLIMITED)


    private val fileClient = FilesClient(this)
    private val stack = Stack<String>()


    private lateinit var btnOpen: Button
    private lateinit var btnClose: Button
    private lateinit var btnChoose: Button

    @SuppressLint("MissingInflatedId")
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.act_test)
        btnOpen = findViewById(R.id.btn_open)
        btnClose = findViewById(R.id.btn_close)
        btnChoose = findViewById(R.id.btn_choose)
        fetch()
        XXPermissions.with(this)
            .permission(Permission.MANAGE_EXTERNAL_STORAGE)
            .request { _, _ -> }




        btnOpen.setOnClickListener {
            LogUtils.e("bbbbbbbbb")
            startClash()
            LogUtils.e("eeeeeeeee")
        }

        btnClose.setOnClickListener {
            LogUtils.e("cccccccccc")
            stopClashService()
        }

        btnChoose.setOnClickListener {
            launch {

                val uri: Uri? = startActivityForResult(
                    ActivityResultContracts.GetContent(),
                    "*/*"
                )
                if (uri != null) {
                    contentResolver.takePersistableUriPermission(
                        uri,
                        Intent.FLAG_GRANT_READ_URI_PERMISSION or Intent.FLAG_GRANT_WRITE_URI_PERMISSION
                    )
                    LogUtils.e("====>$uri")
                    chooseConfig(uri)
                }

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
        LogUtils.e("0000")
        launch {
            LogUtils.e("1111")
            val active = withProfile { queryActive() }
            LogUtils.e(active)
            if (active == null || !active.imported) {
                LogUtils.e("3333")
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

    override fun onProfileChanged() {
        events.trySend(BaseActivity.Event.ProfileChanged)
    }

    override fun onProfileLoaded() {
        events.trySend(BaseActivity.Event.ProfileLoaded)
    }

    override fun onServiceRecreated() {
        events.trySend(BaseActivity.Event.ServiceRecreated)
    }

    override fun onStarted() {
        events.trySend(BaseActivity.Event.ClashStart)
    }

    override fun onStopped(cause: String?) {
        events.trySend(BaseActivity.Event.ClashStop)

        if (cause != null && activityStarted) {
            launch {
                Log.e(ClashException(cause).message ?: "异常")
            }
        }
    }

    fun defer(operation: suspend () -> Unit) {
        this.defer = operation
    }


    suspend fun <I, O> startActivityForResult(
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


    override fun onStart() {
        super.onStart()

        activityStarted = true

        Remote.broadcasts.addObserver(this)

        events.trySend(BaseActivity.Event.ActivityStart)
    }

    override fun onStop() {
        super.onStop()

        activityStarted = false

        Remote.broadcasts.removeObserver(this)

        events.trySend(BaseActivity.Event.ActivityStop)
    }

    override fun onDestroy() {
//        design?.cancel()

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

    private fun fetch() {
        lifecycleScope.launch {
            setClashRunning(clashRunning)

            val state = withClash {
                queryTunnelState()
            }
            val providers = withClash {
                queryProviders()
            }

            setMode(state.mode)
            setHasProviders(providers.isNotEmpty())

            withProfile {
                setProfileName(queryActive()?.name)
            }
        }

    }


    suspend fun setProfileName(name: String?) {
        withContext(Dispatchers.Main) {
        }
    }

    suspend fun setClashRunning(running: Boolean) {
        withContext(Dispatchers.Main) {
        }
    }

    suspend fun setForwarded(value: Long) {
        withContext(Dispatchers.Main) {
        }
    }

    suspend fun setMode(mode: TunnelState.Mode) {
        withContext(Dispatchers.Main) {

        }
    }

    suspend fun setHasProviders(has: Boolean) {
        withContext(Dispatchers.Main) {
        }
    }

    @SuppressLint("Range")
    private fun getFileContentUri(file: File): Uri? {
        val volumeName = FileUtils.getFileNameNoExtension(file)
        val filePath = file.absolutePath
        LogUtils.e("xxxxxxxxxxxxxxxx===>$filePath")
        val projection = arrayOf(MediaStore.Files.FileColumns._ID)
        var uri: Uri? = null

        val cursor = contentResolver.query(
            MediaStore.Files.getContentUri(volumeName), projection,
            MediaStore.Images.Media.DATA + "=? ", arrayOf(filePath), null
        )
        if (cursor != null) {
            if (cursor.moveToFirst()) {
                val id = cursor.getLong(cursor.getColumnIndex(MediaStore.Files.FileColumns._ID))
                uri = MediaStore.Files.getContentUri(volumeName, id)
            }
            cursor.close()
        }
        LogUtils.e("++++++++++>$uri")
        return uri
    }

}