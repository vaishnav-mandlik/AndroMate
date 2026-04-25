package com.mandlik.andromate.rules

import android.content.Intent
import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.TextView
import androidx.appcompat.app.AlertDialog
import androidx.appcompat.app.AppCompatActivity
import androidx.appcompat.widget.SwitchCompat
import androidx.recyclerview.widget.LinearLayoutManager
import androidx.recyclerview.widget.RecyclerView
import com.mandlik.andromate.R
import com.mandlik.andromate.receiver.RuleScheduler

/**
 * RulesActivity — Shows all saved rules
 *
 * Like a FlatList in React Native:
 *   - Displays all rules
 *   - Toggle switch to enable/disable
 *   - Long press to delete
 *   - FAB to add new rule
 */
class RulesActivity : AppCompatActivity() {

    private lateinit var recyclerView: RecyclerView
    private lateinit var textEmpty: TextView
    private lateinit var textRuleCount: TextView
    private lateinit var adapter: RuleAdapter
    private var rules = mutableListOf<Rule>()

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        supportActionBar?.hide()
        setContentView(R.layout.activity_rules)

        recyclerView = findViewById(R.id.recycler_rules)
        textEmpty = findViewById(R.id.text_empty)
        textRuleCount = findViewById(R.id.text_rule_count)

        // Setup RecyclerView (like FlatList)
        adapter = RuleAdapter(
            rules = rules,
            onToggle = { rule, enabled -> toggleRule(rule, enabled) },
            onLongPress = { rule -> confirmDeleteRule(rule) }
        )
        recyclerView.layoutManager = LinearLayoutManager(this)
        recyclerView.adapter = adapter

        // Add rule button
        findViewById<View>(R.id.btn_add_rule).setOnClickListener {
            startActivity(Intent(this, AddRuleActivity::class.java))
        }
    }

    override fun onResume() {
        super.onResume()
        loadRules()
    }

    private fun loadRules() {
        Thread {
            val db = AndroMateDatabase.getInstance(this)
            val loadedRules = db.ruleDao().getAllRules()
            runOnUiThread {
                rules.clear()
                rules.addAll(loadedRules)
                adapter.notifyDataSetChanged()
                textRuleCount.text = "${rules.size} rule${if (rules.size != 1) "s" else ""}"
                textEmpty.visibility = if (rules.isEmpty()) View.VISIBLE else View.GONE
                recyclerView.visibility = if (rules.isEmpty()) View.GONE else View.VISIBLE
            }
        }.start()
    }

    private fun toggleRule(rule: Rule, enabled: Boolean) {
        Thread {
            val db = AndroMateDatabase.getInstance(this)
            db.ruleDao().setRuleEnabled(rule.id, enabled)

            if (enabled) {
                // Schedule the alarm
                val updatedRule = rule.copy(enabled = true)
                RuleScheduler.scheduleRule(this, updatedRule)
            } else {
                // Cancel the alarm
                RuleScheduler.cancelRule(this, rule)
            }
        }.start()
    }

    private fun confirmDeleteRule(rule: Rule) {
        AlertDialog.Builder(this)
            .setTitle("Delete Rule")
            .setMessage("Delete \"${rule.name}\"?")
            .setPositiveButton("Delete") { _, _ -> deleteRule(rule) }
            .setNegativeButton("Cancel", null)
            .show()
    }

    private fun deleteRule(rule: Rule) {
        Thread {
            val db = AndroMateDatabase.getInstance(this)
            RuleScheduler.cancelRule(this, rule)
            db.ruleDao().deleteRule(rule)
            runOnUiThread { loadRules() }
        }.start()
    }
}

/**
 * RuleAdapter — RecyclerView adapter for rules list
 * Like renderItem in React Native FlatList
 */
class RuleAdapter(
    private val rules: List<Rule>,
    private val onToggle: (Rule, Boolean) -> Unit,
    private val onLongPress: (Rule) -> Unit
) : RecyclerView.Adapter<RuleAdapter.ViewHolder>() {

    class ViewHolder(view: View) : RecyclerView.ViewHolder(view) {
        val name: TextView = view.findViewById(R.id.text_rule_name)
        val trigger: TextView = view.findViewById(R.id.text_rule_trigger)
        val action: TextView = view.findViewById(R.id.text_rule_action)
        val switch: SwitchCompat = view.findViewById(R.id.switch_rule_enabled)
    }

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): ViewHolder {
        val view = LayoutInflater.from(parent.context)
            .inflate(R.layout.item_rule, parent, false)
        return ViewHolder(view)
    }

    override fun onBindViewHolder(holder: ViewHolder, position: Int) {
        val rule = rules[position]

        holder.name.text = rule.name

        // Format trigger display
        holder.trigger.text = when (rule.triggerType) {
            "TIME" -> {
                val repeat = when (rule.triggerRepeat) {
                    "DAILY" -> "Daily"
                    "WEEKLY" -> "Weekly (${rule.triggerDays})"
                    else -> "Once"
                }
                "⏰ $repeat at ${rule.triggerTime}"
            }
            else -> "⚡ ${rule.triggerType}"
        }

        // Format action display
        holder.action.text = when (rule.actionType) {
            "OPEN_APP" -> "📱 Open ${rule.actionData?.substringAfterLast('.')}"
            "TOGGLE_DND" -> "🔕 DND ${rule.actionData}"
            "SET_VOLUME" -> "🔊 Volume → ${rule.actionData}%"
            "PLAY_SPOTIFY" -> "🎵 Play Spotify"
            "PLAY_LOCAL_SONG" -> "🎵 Play ${rule.actionExtra ?: "local song"}"
            "SEND_SMS" -> "💬 SMS to ${rule.actionData}"
            else -> "⚡ ${rule.actionType}"
        }

        // Toggle switch
        holder.switch.setOnCheckedChangeListener(null) // Prevent recycling triggers
        holder.switch.isChecked = rule.enabled
        holder.switch.setOnCheckedChangeListener { _, checked ->
            onToggle(rule, checked)
        }

        // Long press to delete
        holder.itemView.setOnLongClickListener {
            onLongPress(rule)
            true
        }
    }

    override fun getItemCount() = rules.size
}