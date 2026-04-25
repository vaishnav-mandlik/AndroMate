package com.mandlik.andromate.rules

import androidx.room.Dao
import androidx.room.Delete
import androidx.room.Insert
import androidx.room.Query
import androidx.room.Update

/**
 * RuleDao — Database operations for rules
 *
 * Think of this like an API controller in a Node.js app:
 *   GET    /rules        → getAllRules()
 *   GET    /rules/:id    → getRuleById(id)
 *   POST   /rules        → insertRule(rule)
 *   PUT    /rules/:id    → updateRule(rule)
 *   DELETE /rules/:id    → deleteRule(rule)
 *
 * Room auto-generates the SQL queries from these annotations.
 * You never write raw SQL.
 */
@Dao
interface RuleDao {

    // Get all rules, newest first
    @Query("SELECT * FROM rules ORDER BY createdAt DESC")
    fun getAllRules(): List<Rule>

    // Get only enabled rules
    @Query("SELECT * FROM rules WHERE enabled = 1")
    fun getEnabledRules(): List<Rule>

    // Get a single rule by ID
    @Query("SELECT * FROM rules WHERE id = :id")
    fun getRuleById(id: Long): Rule?

    // Insert a new rule, returns the new ID
    @Insert
    fun insertRule(rule: Rule): Long

    // Update an existing rule
    @Update
    fun updateRule(rule: Rule)

    // Delete a rule
    @Delete
    fun deleteRule(rule: Rule)

    // Quick toggle: enable or disable a rule
    @Query("UPDATE rules SET enabled = :enabled WHERE id = :id")
    fun setRuleEnabled(id: Long, enabled: Boolean)
}