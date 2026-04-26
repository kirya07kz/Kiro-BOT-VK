package com.vkbot.manager

import android.content.Context
import android.content.Intent
import android.os.Bundle
import android.util.Log
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.Toast
import androidx.core.content.edit
import androidx.fragment.app.Fragment
import androidx.lifecycle.lifecycleScope
import androidx.recyclerview.widget.LinearLayoutManager
import com.google.android.material.dialog.MaterialAlertDialogBuilder
import com.vkbot.manager.databinding.DialogAddBotBinding
import com.vkbot.manager.databinding.FragmentBotsBinding
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import com.vkbot.manager.utils.BotDataManager
import com.vkbot.manager.utils.Bot


/**
 * Фрагмент управления списком ботов.
 * Рефакторинг v2.1.0: View Binding, Scoped Storage migration, KTX.
 */
class BotsFragment : Fragment() {
    
    private var _binding: FragmentBotsBinding? = null
    private val binding get() = _binding!!
    
    private val bots = mutableListOf<Bot>()
    private lateinit var adapter: BotsAdapter
    
    companion object {
        private const val TAG = "BotsFragment"
    }
    
    override fun onCreateView(
        inflater: LayoutInflater,
        container: ViewGroup?,
        savedInstanceState: Bundle?
    ): View {
        _binding = FragmentBotsBinding.inflate(inflater, container, false)
        return binding.root
    }
    
    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)
        
        setupRecyclerView()
        setupListeners()
        
        // Загружаем данные с учетом миграции
        loadBots()
        
        // Запускаем обновление статистики
        startStatsUpdater()
    }
    
    private fun setupRecyclerView() {
        adapter = BotsAdapter(::onEditBot, ::onDeleteBot, ::onToggleBot, ::onClearStats)
        binding.recyclerViewBots.apply {
            layoutManager = LinearLayoutManager(requireContext())
            adapter = this@BotsFragment.adapter
        }
    }
    
    private fun setupListeners() {
        binding.fabAddBot.setOnClickListener {
            if (bots.size >= 5) {
                Toast.makeText(requireContext(), R.string.max_bots_error, Toast.LENGTH_SHORT).show()
            } else {
                showBotDialog(null)
            }
        }
    }
    
    private fun loadBots() {
        lifecycleScope.launch {
            val loadedBots = withContext(Dispatchers.IO) {
                BotDataManager.loadBots(requireContext())
            }
            
            Log.d(TAG, "Loaded ${loadedBots.size} bots")
            bots.clear()
            bots.addAll(loadedBots)
            adapter.updateBots(bots.toList())
            updateUIState()
        }
    }
    
    
    private fun saveBots() {
        val appContext = requireContext().applicationContext
        val botsCopy = bots.toList()
        
        lifecycleScope.launch(Dispatchers.IO) {
            BotDataManager.saveBots(appContext, botsCopy)
        }
    }
    
    private fun updateUIState() {
        val isEmpty = bots.isEmpty()
        binding.emptyStateBots.visibility = if (isEmpty) View.VISIBLE else View.GONE
        binding.recyclerViewBots.visibility = if (isEmpty) View.GONE else View.VISIBLE
        binding.tvBotsCounter.text = getString(R.string.bots_counter_format, bots.size)
    }
    
    private fun showBotDialog(bot: Bot?) {
        val context = requireContext()
        val dialogBinding = DialogAddBotBinding.inflate(layoutInflater)
        
        bot?.let {
            dialogBinding.etBotName.setText(it.name)
            dialogBinding.etBotToken.setText(it.token)
        }
        
        MaterialAlertDialogBuilder(context)
            .setTitle(if (bot == null) R.string.add_bot_title else R.string.edit_bot_title)
            .setView(dialogBinding.root)
            .setPositiveButton(if (bot == null) R.string.add else R.string.save) { _, _ ->
                val name = dialogBinding.etBotName.text.toString().trim()
                val token = dialogBinding.etBotToken.text.toString().trim()
                
                if (name.isEmpty() || token.isEmpty()) {
                    Toast.makeText(context, R.string.fill_all_fields_error, Toast.LENGTH_SHORT).show()
                    return@setPositiveButton
                }
                
                if (bots.any { it.id != bot?.id && it.name.equals(name, ignoreCase = true) }) {
                    Toast.makeText(context, R.string.bot_name_exists_error, Toast.LENGTH_SHORT).show()
                    return@setPositiveButton
                }
                
                if (bot == null) {
                    val newId = (bots.maxOfOrNull { it.id } ?: 0) + 1
                    bots.add(Bot(newId, name, token))
                    Toast.makeText(context, R.string.bot_added_success, Toast.LENGTH_SHORT).show()
                } else {
                    bot.name = name
                    bot.token = token
                    Toast.makeText(context, R.string.bot_updated_success, Toast.LENGTH_SHORT).show()
                }
                
                saveBots()
                adapter.updateBots(bots.toList())
                updateUIState()
            }
            .setNegativeButton(R.string.cancel, null)
            .show()
    }
    
    private fun onEditBot(bot: Bot) = showBotDialog(bot)
    
    private fun onDeleteBot(bot: Bot) {
        MaterialAlertDialogBuilder(requireContext())
            .setTitle(R.string.delete_bot_title)
            .setMessage(getString(R.string.delete_bot_confirm_format, bot.name))
            .setPositiveButton(R.string.delete) { _, _ ->
                bots.remove(bot)
                saveBots()
                adapter.updateBots(bots.toList())
                updateUIState()
                
                // Уведомляем сервис об изменениях
                val intent = Intent(requireContext(), BotService::class.java).apply {
                    action = if (bots.any { it.isRunning }) BotService.ACTION_START else BotService.ACTION_STOP
                }
                try {
                    if (bots.any { it.isRunning }) requireContext().startForegroundService(intent)
                    else requireContext().startService(intent)
                } catch (e: Exception) {
                    Log.e(TAG, "Service sync error", e)
                }
                
                Toast.makeText(requireContext(), R.string.bot_deleted_success, Toast.LENGTH_SHORT).show()
            }
            .setNegativeButton(R.string.cancel, null)
            .show()
    }
    
    private fun onClearStats(bot: Bot) {
        val position = bots.indexOf(bot)
        if (position == -1) return
        
        MaterialAlertDialogBuilder(requireContext())
            .setTitle(R.string.clear_stats_title)
            .setMessage(getString(R.string.clear_stats_confirm_format, bot.name))
            .setPositiveButton(R.string.reset) { _, _ ->
                bot.processedMessages = 0
                bot.answeredMessages = 0
                
                requireContext().getSharedPreferences("vk_bot_settings", Context.MODE_PRIVATE).edit {
                    putLong("bot_${position + 1}_processed", 0)
                    putLong("bot_${position + 1}_answered", 0)
                }
                
                saveBots()
                adapter.updateBots(bots.toList())
                Toast.makeText(requireContext(), R.string.stats_cleared_success, Toast.LENGTH_SHORT).show()
            }
            .setNegativeButton(R.string.cancel, null)
            .show()
    }
    
    private fun onToggleBot(bot: Bot) {
        bot.isRunning = !bot.isRunning
        val status = if (bot.isRunning) R.string.status_auto_start_on else R.string.status_auto_start_off
        Toast.makeText(requireContext(), getString(R.string.bot_status_changed_format, bot.name, getString(status)), Toast.LENGTH_SHORT).show()
        
        saveBots()
        adapter.updateBots(bots.toList())
        
        // Мгновенная синхронизация с сервисом
        val intent = Intent(requireContext(), BotService::class.java).apply {
            action = BotService.ACTION_START 
        }
        try {
            requireContext().startForegroundService(intent)
        } catch (_: Exception) {}
    }
    
    private fun startStatsUpdater() {
        lifecycleScope.launch {
            val prefs = requireContext().getSharedPreferences("vk_bot_settings", Context.MODE_PRIVATE)
            while (isActive) {
                delay(1000)
                if (!isAdded) continue
                
                var changed = false
                bots.forEach { bot ->
                    val keyId = bot.id
                    val processed = prefs.getLong("bot_${keyId}_processed", 0)
                    val answered = prefs.getLong("bot_${keyId}_answered", 0)
                    val isRunning = prefs.getBoolean("bot_${keyId}_running", false)
                    val startTime = prefs.getLong("bot_${keyId}_start_time", 0)
                    
                    if (bot.processedMessages != processed || bot.answeredMessages != answered || 
                        bot.isRunning != isRunning || bot.startTime != startTime) {
                        
                        bot.processedMessages = processed
                        bot.answeredMessages = answered
                        bot.isRunning = isRunning
                        bot.startTime = startTime
                        changed = true
                    }
                }
                
                if (changed) {
                    adapter.updateBots(bots.toList())
                }
            }
        }
    }

    override fun onDestroyView() {
        super.onDestroyView()
        _binding = null
    }
}
