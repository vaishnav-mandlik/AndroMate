package com.mandlik.andromate.rules

import android.app.Activity
import android.content.Intent
import android.os.Bundle
import android.view.View
import android.widget.AdapterView
import android.widget.ArrayAdapter
import android.widget.Button
import android.widget.EditText
import android.widget.Spinner
import android.widget.TextView
import android.widget.TimePicker
import android.widget.Toast
import androidx.activity.result.contract.ActivityResultContracts
import androidx.appcompat.app.AppCompatActivity
import com.mandlik.andromate.R
import com.mandlik.andromate.receiver.RuleScheduler
import com.mandlik.andromate.util.PermissionHelper

/**
 * AddRuleActivity — Create a new automation rule
 *
 * v0.2.1 improvements:
 *   - "Open App" → shows app picker with icons (no more typing package names)
 *   - "Play Local Song" → opens file picker (no more typing file paths)
 *   - "Toggle DND" → auto-checks permission, opens settings if needed
 *   - "Send SMS" → checks SMS permission before saving
 *   - Clear error messages for everything
 */
class AddRuleActivity : AppCompatActivity() {

    private lateinit var inputName: EditText
    private lateinit var timePicker: TimePicker
    private lateinit var spinnerRepeat: Spinner
    private lateinit var spinnerAction: Spinner
    private lateinit var labelActionData: TextView
    private lateinit var inputActionData: EditText
    private lateinit var labelActionExtra: TextView
    private lateinit var inputActionExtra: EditText
    private lateinit var btnPickApp: Button
    private lateinit var btnPickSong: Button

    // Stores the selected package name from app picker
    private var selectedPackageName: String? = null
    // Stores the selected file path from song picker
    private var selectedSongPath: String? = null
    private var selectedSongName: String? = null

    // Action types
    private val actionTypes = listOf(
        "Open App" to "OPEN_APP",
        "Toggle DND" to "TOGGLE_DND",
        "Set Volume" to "SET_VOLUME",
        "Play Spotify" to "PLAY_SPOTIFY",
        "Play Local Song" to "PLAY_LOCAL_SONG",
        "Send SMS" to "SEND_SMS"
    )

    private val repeatModes = listOf("Once", "Daily", "Weekly")

    // App picker result handler
    private val appPickerLauncher = registerForActivityResult(
        ActivityResultContracts.StartActivityForResult()
    ) { result ->
        if (result.resultCode == Activity.RESULT_OK) {
            val data = result.data
            selectedPackageName = data?.getStringExtra(AppPickerActivity.RESULT_PACKAGE_NAME)
            val appName = data?.getStringExtra(AppPickerActivity.RESULT_APP_NAME)
            inputActionData.setText(appName ?: selectedPackageName)
            inputActionData.isEnabled = false // Lock it since we have the selection
        }
    }

    // Song file picker result handler
//    private val songPickerLauncher = registerForActivityResult(
//        ActivityResultContracts.StartActivityForResult()
//    ) { result ->
//        if (result.resultCode == Activity.RESULT_OK) {
//            val uri = result.data?.data
//            if (uri != null) {
//                // Get the real file path from the URI
//                val cursor = contentResolver.query(uri, null, null, null, null)
//                cursor?.use {
//                    if (it.moveToFirst()) {
//                        val pathIndex = it.getColumnIndex("_data")
//                        if (pathIndex >= 0) {
//                            selectedSongPath = it.getString(pathIndex)
//                        }
//                        val nameIndex = it.getColumnIndex("_display_name")
//                        if (nameIndex >= 0) {
//                            selectedSongName = it.getString(nameIndex)
//                        }
//                    }
//                }
//
//                // If _data column didn't work, try to use the URI directly
//                if (selectedSongPath == null) {
//                    selectedSongPath = uri.toString()
//                    selectedSongName = uri.lastPathSegment
//                }
//
//                inputActionData.setText(selectedSongName ?: "Selected song")
//                inputActionData.isEnabled = false
//            }
//        }
//    }
    private val songPickerLauncher = registerForActivityResult(
        ActivityResultContracts.StartActivityForResult()
    ) { result ->
        if (result.resultCode == Activity.RESULT_OK) {
            val uri = result.data?.data
            if (uri != null) {
                // Take persistent permission so we can access this file later
                try {
                    contentResolver.takePersistableUriPermission(
                        uri,
                        Intent.FLAG_GRANT_READ_URI_PERMISSION
                    )
                } catch (e: Exception) {
                    // Some providers don't support persistent permissions
                }

                // Store the URI string (not file path)
                selectedSongPath = uri.toString()

                // Get display name
                val cursor = contentResolver.query(uri, null, null, null, null)
                cursor?.use {
                    if (it.moveToFirst()) {
                        val nameIndex = it.getColumnIndex("_display_name")
                        if (nameIndex >= 0) {
                            selectedSongName = it.getString(nameIndex)
                        }
                    }
                }
                if (selectedSongName == null) {
                    selectedSongName = uri.lastPathSegment ?: "Selected song"
                }

                inputActionData.setText(selectedSongName)
                inputActionData.isEnabled = false
            }
        }
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        supportActionBar?.hide()
        setContentView(R.layout.activity_add_rule)

        // Find views
        inputName = findViewById(R.id.input_rule_name)
        timePicker = findViewById(R.id.time_picker)
        spinnerRepeat = findViewById(R.id.spinner_repeat)
        spinnerAction = findViewById(R.id.spinner_action)
        labelActionData = findViewById(R.id.label_action_data)
        inputActionData = findViewById(R.id.input_action_data)
        labelActionExtra = findViewById(R.id.label_action_extra)
        inputActionExtra = findViewById(R.id.input_action_extra)
        btnPickApp = findViewById(R.id.btn_pick_app)
        btnPickSong = findViewById(R.id.btn_pick_song)

        // Setup repeat spinner
        spinnerRepeat.adapter = ArrayAdapter(
            this,
            android.R.layout.simple_spinner_dropdown_item,
            repeatModes
        )

        // Setup action spinner
        spinnerAction.adapter = ArrayAdapter(
            this,
            android.R.layout.simple_spinner_dropdown_item,
            actionTypes.map { it.first }
        )

        // When action type changes, update the UI
        spinnerAction.onItemSelectedListener = object : AdapterView.OnItemSelectedListener {
            override fun onItemSelected(parent: AdapterView<*>?, view: View?, pos: Int, id: Long) {
                updateActionFields(actionTypes[pos].second)
            }
            override fun onNothingSelected(parent: AdapterView<*>?) {}
        }

        // App picker button
        btnPickApp.setOnClickListener {
            val intent = Intent(this, AppPickerActivity::class.java)
            appPickerLauncher.launch(intent)
        }

        // Song picker button
        btnPickSong.setOnClickListener {
            if (!PermissionHelper.hasStoragePermission(this)) {
                PermissionHelper.requestMissingPermissions(this)
                Toast.makeText(this, "Please grant storage permission first", Toast.LENGTH_SHORT).show()
                return@setOnClickListener
            }
            val intent = Intent(Intent.ACTION_OPEN_DOCUMENT).apply {
                type = "audio/*"
                addCategory(Intent.CATEGORY_OPENABLE)
                addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
                addFlags(Intent.FLAG_GRANT_PERSISTABLE_URI_PERMISSION)
            }
            songPickerLauncher.launch(intent)
        }
        // Save button
        findViewById<Button>(R.id.btn_save_rule).setOnClickListener {
            saveRule()
        }
    }

    /**
     * Update input fields based on selected action type.
     */
    private fun updateActionFields(actionType: String) {
        // Reset state
        inputActionData.isEnabled = true
        inputActionData.setText("")
        selectedPackageName = null
        selectedSongPath = null
        selectedSongName = null

        // Hide all special buttons by default
        btnPickApp.visibility = View.GONE
        btnPickSong.visibility = View.GONE
        labelActionExtra.visibility = View.GONE
        inputActionExtra.visibility = View.GONE
        inputActionData.visibility = View.VISIBLE
        labelActionData.visibility = View.VISIBLE

        when (actionType) {
            "OPEN_APP" -> {
                labelActionData.text = "WHICH APP?"
                inputActionData.hint = "Tap button below to pick"
                inputActionData.isEnabled = false
                btnPickApp.visibility = View.VISIBLE
            }
            "TOGGLE_DND" -> {
                labelActionData.text = "DND STATE"
                inputActionData.visibility = View.GONE
                labelActionData.visibility = View.GONE
                // We'll use a simple spinner instead — but for now,
                // let's just auto-fill ON/OFF based on a toggle
                inputActionData.setText("ON")
                // Check DND permission proactively
                if (!PermissionHelper.isDndPermissionGranted(this)) {
                    PermissionHelper.requestDndPermission(this)
                }
            }
            "SET_VOLUME" -> {
                labelActionData.text = "VOLUME LEVEL (0-100)"
                inputActionData.hint = "e.g. 50"
                inputActionData.inputType = android.text.InputType.TYPE_CLASS_NUMBER
            }
            "PLAY_SPOTIFY" -> {
                labelActionData.text = "SPOTIFY URI (leave empty to just open Spotify)"
                inputActionData.hint = "spotify:playlist:xxxxx"
                inputActionData.inputType = android.text.InputType.TYPE_CLASS_TEXT
            }
            "PLAY_LOCAL_SONG" -> {
                labelActionData.text = "SONG"
                inputActionData.hint = "Tap button below to pick"
                inputActionData.isEnabled = false
                btnPickSong.visibility = View.VISIBLE
            }
            "SEND_SMS" -> {
                labelActionData.text = "PHONE NUMBER"
                inputActionData.hint = "e.g. 5551234567"
                inputActionData.inputType = android.text.InputType.TYPE_CLASS_PHONE
                labelActionExtra.visibility = View.VISIBLE
                inputActionExtra.visibility = View.VISIBLE
                labelActionExtra.text = "MESSAGE"
                inputActionExtra.hint = "e.g. Good morning!"
                // Check SMS permission
                if (!PermissionHelper.hasSmsPermission(this)) {
                    PermissionHelper.requestMissingPermissions(this)
                }
            }
        }
    }

    /**
     * Validate and save the rule.
     */
    private fun saveRule() {
        val name = inputName.text.toString().trim()
        if (name.isEmpty()) {
            inputName.error = "Give your rule a name"
            inputName.requestFocus()
            return
        }

        val hour = timePicker.hour
        val minute = timePicker.minute
        val triggerTime = String.format("%02d:%02d", hour, minute)

        val repeatIndex = spinnerRepeat.selectedItemPosition
        val triggerRepeat = when (repeatIndex) {
            0 -> "ONCE"
            1 -> "DAILY"
            2 -> "WEEKLY"
            else -> "ONCE"
        }

        val actionIndex = spinnerAction.selectedItemPosition
        val actionType = actionTypes[actionIndex].second

        // Build actionData based on type
        val actionData: String?
        val actionExtra: String?

        when (actionType) {
            "OPEN_APP" -> {
                if (selectedPackageName == null) {
                    Toast.makeText(this, "Please pick an app", Toast.LENGTH_SHORT).show()
                    return
                }
                actionData = selectedPackageName
                actionExtra = inputActionData.text.toString() // App name for display
            }
            "TOGGLE_DND" -> {
                if (!PermissionHelper.isDndPermissionGranted(this)) {
                    PermissionHelper.requestDndPermission(this)
                    return
                }
                actionData = "ON"
                actionExtra = null
            }
            "SET_VOLUME" -> {
                val vol = inputActionData.text.toString().trim()
                val volInt = vol.toIntOrNull()
                if (volInt == null || volInt < 0 || volInt > 100) {
                    inputActionData.error = "Enter a number between 0 and 100"
                    inputActionData.requestFocus()
                    return
                }
                actionData = vol
                actionExtra = null
            }
            "PLAY_SPOTIFY" -> {
                val uri = inputActionData.text.toString().trim()
                actionData = uri.ifEmpty { null }
                actionExtra = null
            }
            "PLAY_LOCAL_SONG" -> {
                if (selectedSongPath == null) {
                    Toast.makeText(this, "Please pick a song", Toast.LENGTH_SHORT).show()
                    return
                }
                actionData = selectedSongPath
                actionExtra = selectedSongName
            }
            "SEND_SMS" -> {
                if (!PermissionHelper.hasSmsPermission(this)) {
                    PermissionHelper.requestMissingPermissions(this)
                    Toast.makeText(this, "SMS permission needed", Toast.LENGTH_SHORT).show()
                    return
                }
                val phone = inputActionData.text.toString().trim()
                val message = inputActionExtra.text.toString().trim()
                if (phone.isEmpty()) {
                    inputActionData.error = "Enter a phone number"
                    inputActionData.requestFocus()
                    return
                }
                if (message.isEmpty()) {
                    inputActionExtra.error = "Enter a message"
                    inputActionExtra.requestFocus()
                    return
                }
                actionData = phone
                actionExtra = message
            }
            else -> {
                actionData = inputActionData.text.toString().trim()
                actionExtra = inputActionExtra.text.toString().trim().ifEmpty { null }
            }
        }

        // Check alarm permission
        if (!PermissionHelper.canScheduleExactAlarms(this)) {
            PermissionHelper.requestExactAlarmPermission(this)
            return
        }

        // Create and save the rule
        val rule = Rule(
            name = name,
            enabled = true,
            triggerType = "TIME",
            triggerTime = triggerTime,
            triggerRepeat = triggerRepeat,
            triggerDays = null,
            actionType = actionType,
            actionData = actionData,
            actionExtra = actionExtra
        )

        Thread {
            val db = AndroMateDatabase.getInstance(this)
            val newId = db.ruleDao().insertRule(rule)
            val savedRule = rule.copy(id = newId)
            RuleScheduler.scheduleRule(this, savedRule)

            runOnUiThread {
                Toast.makeText(this, "✅ Rule saved: $name", Toast.LENGTH_SHORT).show()
                finish()
            }
        }.start()
    }
}