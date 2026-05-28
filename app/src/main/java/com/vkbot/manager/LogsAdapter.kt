package com.vkbot.manager

import android.content.ClipData
import android.content.ClipboardManager
import android.content.Context
import android.view.LayoutInflater
import android.view.ViewGroup
import android.widget.Toast
import androidx.core.graphics.toColorInt
import androidx.recyclerview.widget.DiffUtil
import androidx.recyclerview.widget.ListAdapter
import androidx.recyclerview.widget.RecyclerView
import com.vkbot.manager.databinding.ItemLogBinding

/**
 * Модель записи лога.
 * Используется только id и message для отображения.
 */
data class LogEntry(
    val id: Long,
    val message: String
)

/**
 * Адаптер для отображения логов с использованием ListAdapter и DiffUtil.
 * Оптимизирован для отображения большого количества данных.
 */
class LogsAdapter : ListAdapter<LogEntry, LogsAdapter.LogViewHolder>(LogDiffCallback()) {

    companion object {
        // Оптимизация: цвета теперь парсятся один раз через KTX String.toColorInt()
        private val COLOR_ERROR = "#FF5555".toColorInt()
        private val COLOR_SUCCESS = "#50FA7B".toColorInt()
        private val COLOR_WARNING = "#FFB86C".toColorInt()
        private val COLOR_DEFAULT = "#A0A0A0".toColorInt()
    }

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): LogViewHolder {
        val binding = ItemLogBinding.inflate(LayoutInflater.from(parent.context), parent, false)
        return LogViewHolder(binding).apply {
            binding.root.setOnLongClickListener {
                // Использование adapterPosition для совместимости с текущей версией библиотеки
                val pos = adapterPosition
                if (pos != RecyclerView.NO_POSITION) {
                    val logEntry = getItem(pos)
                    val context = parent.context
                    val clipboard = context.getSystemService(Context.CLIPBOARD_SERVICE) as ClipboardManager
                    val clip = ClipData.newPlainText("log", logEntry.message)
                    clipboard.setPrimaryClip(clip)
                    Toast.makeText(context, R.string.log_copied_toast, Toast.LENGTH_SHORT).show()
                }
                true
            }
        }
    }

    override fun onBindViewHolder(holder: LogViewHolder, position: Int) {
        holder.bind(getItem(position))
    }

    class LogViewHolder(private val binding: ItemLogBinding) : RecyclerView.ViewHolder(binding.root) {

        fun bind(logEntry: LogEntry) {
            binding.tvLogText.text = logEntry.message
            
            // Раскраска логов на основе их содержимого
            val color = when {
                isError(logEntry.message) -> COLOR_ERROR
                isSuccess(logEntry.message) -> COLOR_SUCCESS
                isWarning(logEntry.message) -> COLOR_WARNING
                else -> COLOR_DEFAULT
            }
            binding.tvLogText.setTextColor(color)
        }

        private fun isError(msg: String): Boolean {
            return msg.contains("❌") || msg.contains("💥") || 
                   msg.contains("Ошибка", ignoreCase = true) || 
                   msg.contains("Error", ignoreCase = true) ||
                   msg.contains("недействителен", ignoreCase = true) || 
                   msg.contains("Критическая", ignoreCase = true)
        }

        private fun isSuccess(msg: String): Boolean {
            return msg.contains("✅") || msg.contains("🎯") || 
                   msg.contains("успешно", ignoreCase = true) || 
                   msg.contains("готов", ignoreCase = true)
        }

        private fun isWarning(msg: String): Boolean {
            return msg.contains("⚠️") || msg.contains("🔍") || 
                   msg.contains("Проверка", ignoreCase = true)
        }
    }

    private class LogDiffCallback : DiffUtil.ItemCallback<LogEntry>() {
        override fun areItemsTheSame(oldItem: LogEntry, newItem: LogEntry): Boolean = oldItem.id == newItem.id
        override fun areContentsTheSame(oldItem: LogEntry, newItem: LogEntry): Boolean = oldItem == newItem
    }
}