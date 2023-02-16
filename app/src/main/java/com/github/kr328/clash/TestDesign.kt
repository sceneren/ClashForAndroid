//package com.github.kr328.clash
//
//import android.content.Context
//import android.view.View
//import androidx.appcompat.app.AlertDialog
//import com.github.kr328.clash.core.model.TunnelState
//import com.github.kr328.clash.core.util.trafficTotal
//import com.github.kr328.clash.design.Design
//import com.github.kr328.clash.design.R
//import com.github.kr328.clash.design.databinding.DesignAboutBinding
//import com.github.kr328.clash.design.databinding.DesignMainBinding
//import com.github.kr328.clash.design.util.layoutInflater
//import com.github.kr328.clash.design.util.resolveThemedColor
//import com.github.kr328.clash.design.util.root
//import com.google.android.material.dialog.MaterialAlertDialogBuilder
//import kotlinx.coroutines.Dispatchers
//import kotlinx.coroutines.withContext
//
//
//class TestDesign(context: Context, override val root: View) : Design<TestDesign.Request>(context) {
//    enum class Request {
//        ToggleStatus,
//        OpenProxy,
//        OpenProfiles,
//        OpenProviders,
//        OpenLogs,
//        OpenSettings,
//        OpenHelp,
//        OpenAbout,
//    }
//
//
//
//    suspend fun setProfileName(name: String?) {
//        withContext(Dispatchers.Main) {
//        }
//    }
//
//    suspend fun setClashRunning(running: Boolean) {
//        withContext(Dispatchers.Main) {
//        }
//    }
//
//    suspend fun setForwarded(value: Long) {
//        withContext(Dispatchers.Main) {
//        }
//    }
//
//    suspend fun setMode(mode: TunnelState.Mode) {
//        withContext(Dispatchers.Main) {
//
//        }
//    }
//
//    suspend fun setHasProviders(has: Boolean) {
//        withContext(Dispatchers.Main) {
//        }
//    }
//
//    suspend fun showAbout(versionName: String) {
//        withContext(Dispatchers.Main) {
//            val binding = DesignAboutBinding.inflate(context.layoutInflater).apply {
//                this.versionName = versionName
//            }
//
//            AlertDialog.Builder(context)
//                .setView(binding.root)
//                .show()
//        }
//    }
//
//    suspend fun showUpdatedTips() {
//        withContext(Dispatchers.Main) {
//            MaterialAlertDialogBuilder(context)
//                .setTitle(R.string.version_updated)
//                .setMessage(R.string.version_updated_tips)
//                .setPositiveButton(R.string.ok) { _, _ -> }
//                .show()
//        }
//    }
//
//    init {
//
//    }
//
//    fun request(request: Request) {
//        requests.trySend(request)
//    }
//}