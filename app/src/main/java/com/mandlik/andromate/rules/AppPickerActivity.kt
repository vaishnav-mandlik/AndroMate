package com.mandlik.andromate.rules

import android.app.Activity
import android.content.Intent
import android.content.pm.ApplicationInfo
import android.content.pm.PackageManager
import android.os.Bundle
import android.text.Editable
import android.text.TextWatcher
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.EditText
import android.widget.ImageView
import android.widget.TextView
import androidx.appcompat.app.AppCompatActivity
import androidx.recyclerview.widget.LinearLayoutManager
import androidx.recyclerview.widget.RecyclerView
import com.mandlik.andromate.R

/**
 * AppPickerActivity — Shows all installed apps for easy selection
 *
 * Instead of typing "com.spotify.music", user sees:
 *   [Spotify icon] Spotify
 *   [WhatsApp icon] WhatsApp
 *   [Chrome icon] Chrome
 *
 * User taps one → we return the package name to AddRuleActivity.
 *
 * Like a contact picker but for apps.
 */
class AppPickerActivity : AppCompatActivity() {

    companion object {
        const val RESULT_PACKAGE_NAME = "package_name"
        const val RESULT_APP_NAME = "app_name"
    }

    private lateinit var adapter: AppAdapter
    private var allApps = listOf<AppInfo>()

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        supportActionBar?.hide()
        setContentView(R.layout.activity_app_picker)

        val recyclerView = findViewById<RecyclerView>(R.id.recycler_apps)
        val searchInput = findViewById<EditText>(R.id.input_search_app)

        // Load all installed apps (on background thread)
        Thread {
            allApps = loadInstalledApps()
            runOnUiThread {
                adapter = AppAdapter(allApps) { app ->
                    // User picked an app — return result
                    val resultIntent = Intent().apply {
                        putExtra(RESULT_PACKAGE_NAME, app.packageName)
                        putExtra(RESULT_APP_NAME, app.appName)
                    }
                    setResult(Activity.RESULT_OK, resultIntent)
                    finish()
                }
                recyclerView.layoutManager = LinearLayoutManager(this)
                recyclerView.adapter = adapter
            }
        }.start()

        // Search/filter as user types
        searchInput.addTextChangedListener(object : TextWatcher {
            override fun beforeTextChanged(s: CharSequence?, start: Int, count: Int, after: Int) {}
            override fun onTextChanged(s: CharSequence?, start: Int, before: Int, count: Int) {}
            override fun afterTextChanged(s: Editable?) {
                val query = s.toString().lowercase()
                val filtered = allApps.filter {
                    it.appName.lowercase().contains(query)
                }
                adapter.updateList(filtered)
            }
        })
    }

    /**
     * Load all user-installed apps (skip system apps like Settings, Phone, etc.)
     * Returns sorted list by app name.
     */
    private fun loadInstalledApps(): List<AppInfo> {
        val pm = packageManager
        val apps = pm.getInstalledApplications(PackageManager.GET_META_DATA)

        return apps
            .filter { app ->
                // Include apps that have a launch intent (user-visible apps)
                pm.getLaunchIntentForPackage(app.packageName) != null
            }
            .map { app ->
                AppInfo(
                    appName = pm.getApplicationLabel(app).toString(),
                    packageName = app.packageName
                )
            }
            .sortedBy { it.appName.lowercase() }
    }
}

/**
 * Simple data class for app info
 */
data class AppInfo(
    val appName: String,
    val packageName: String
)

/**
 * RecyclerView adapter for the app list
 */
class AppAdapter(
    private var apps: List<AppInfo>,
    private val onClick: (AppInfo) -> Unit
) : RecyclerView.Adapter<AppAdapter.ViewHolder>() {

    class ViewHolder(view: View) : RecyclerView.ViewHolder(view) {
        val icon: ImageView = view.findViewById(R.id.img_app_icon)
        val name: TextView = view.findViewById(R.id.text_app_name)
        val pkg: TextView = view.findViewById(R.id.text_app_package)
    }

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): ViewHolder {
        val view = LayoutInflater.from(parent.context)
            .inflate(R.layout.item_app, parent, false)
        return ViewHolder(view)
    }

    override fun onBindViewHolder(holder: ViewHolder, position: Int) {
        val app = apps[position]
        holder.name.text = app.appName
        holder.pkg.text = app.packageName

        // Load app icon
        try {
            val icon = holder.itemView.context.packageManager
                .getApplicationIcon(app.packageName)
            holder.icon.setImageDrawable(icon)
        } catch (e: Exception) {
            // Use a default icon if loading fails
            holder.icon.setImageResource(android.R.drawable.sym_def_app_icon)
        }

        holder.itemView.setOnClickListener { onClick(app) }
    }

    override fun getItemCount() = apps.size

    fun updateList(newList: List<AppInfo>) {
        apps = newList
        notifyDataSetChanged()
    }
}