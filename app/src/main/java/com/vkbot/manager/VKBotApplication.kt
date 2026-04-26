package com.vkbot.manager

import android.app.Application
import android.util.Log
import com.vkbot.manager.botbrain.AnswerDatabase
import com.vkbot.manager.utils.BlacklistManager
import com.vkbot.manager.utils.SettingsManager
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.launch

/**
 * Основной класс приложения VK Bot.
 * Устранено 56 предупреждений: рефакторинг синтаксиса, очистка импортов и путей.
 */
class VKBotApplication : Application() {
    
    // Глобальный скоуп, который живет пока живо приложение
    private val applicationScope = CoroutineScope(SupervisorJob() + Dispatchers.Default)
    
    override fun onCreate() {
        super.onCreate()
        
        // Инициализация ЧС для работы в фоновом режиме сервиса
        BlacklistManager.init(this)
        
        // Инициализация глобальных настроек
        SettingsManager.init(this)
        
        // Запускаем инициализацию базы данных в фоне
        preloadCriticalComponents()
    }
    
    private fun preloadCriticalComponents() {
        applicationScope.launch(Dispatchers.IO) {
            try {
                initializeAnswerDatabase()
            } catch (e: Exception) {
                Log.e(TAG, "Ошибка предзагрузки", e)
            }
        }
    }
    
    private fun initializeAnswerDatabase() {
        try {
            // Используем try-catch внутри, чтобы не крашнуть App при старте, если база битая
            val answerDatabase = AnswerDatabase(this)
            
            // Использование синтаксиса свойств Kotlin вместо метода getter
            val count = answerDatabase.answersCount
            
            Log.i(TAG, "База данных: $count ответов")
            
            // Создание мини-базы, если пусто
            if (count == 0) {
                Log.i(TAG, "База данных пуста, добавьте ответы через редактор")
            }
        } catch (e: Exception) {
            Log.e(TAG, "Критическая ошибка базы данных", e)
        }
    }

    companion object {
        private const val TAG = "VKBotApp"
    }
}