package com.example.aifloatingball.voice

import android.content.Context
import android.media.MediaRecorder
import android.os.Build
import android.util.Log
import java.io.File
import java.io.IOException

/**
 * 音频录制管理器
 * 支持录音、暂停、继续、转MP3等功能
 */
class AudioRecorderManager(private val context: Context) {
    
    companion object {
        private const val TAG = "AudioRecorderManager"
        private const val RECORDINGS_DIR = "voice_recordings"
    }
    
    private var mediaRecorder: MediaRecorder? = null
    private var outputFile: File? = null
    private var isRecording = false
    private var isPaused = false
    private var recordingStartTime: Long = 0
    private var totalRecordingTime: Long = 0
    
    /**
     * 开始录音
     */
    fun startRecording(): File? {
        if (isRecording) {
            Log.w(TAG, "已经在录音中")
            return null
        }
        
        try {
            // 创建录音目录
            val recordingsDir = File(context.getExternalFilesDir(null), RECORDINGS_DIR)
            if (!recordingsDir.exists()) {
                recordingsDir.mkdirs()
            }
            
            // 生成文件名
            val timestamp = System.currentTimeMillis()
            outputFile = File(recordingsDir, "recording_$timestamp.m4a")
            
            // 创建MediaRecorder
            mediaRecorder = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
                MediaRecorder(context)
            } else {
                @Suppress("DEPRECATION")
                MediaRecorder()
            }
            
            mediaRecorder?.apply {
                setAudioSource(MediaRecorder.AudioSource.MIC)
                setOutputFormat(MediaRecorder.OutputFormat.MPEG_4)
                setAudioEncoder(MediaRecorder.AudioEncoder.AAC)
                setOutputFile(outputFile?.absolutePath)
                setAudioSamplingRate(44100)
                setAudioEncodingBitRate(128000)
                
                prepare()
                start()
            }
            
            isRecording = true
            isPaused = false
            recordingStartTime = System.currentTimeMillis()
            totalRecordingTime = 0
            
            Log.d(TAG, "开始录音: ${outputFile?.absolutePath}")
            return outputFile
        } catch (e: Exception) {
            Log.e(TAG, "开始录音失败", e)
            releaseRecorder()
            return null
        }
    }
    
    /**
     * 暂停录音
     */
    fun pauseRecording(): Boolean {
        if (!isRecording || isPaused) {
            return false
        }
        
        try {
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.N) {
                mediaRecorder?.pause()
                isPaused = true
                totalRecordingTime += System.currentTimeMillis() - recordingStartTime
                Log.d(TAG, "录音已暂停")
                return true
            } else {
                // Android N以下不支持暂停，需要停止并保存当前录音
                Log.w(TAG, "当前Android版本不支持暂停录音")
                return false
            }
        } catch (e: Exception) {
            Log.e(TAG, "暂停录音失败", e)
            return false
        }
    }
    
    /**
     * 继续录音
     */
    fun resumeRecording(): Boolean {
        if (!isRecording || !isPaused) {
            return false
        }
        
        try {
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.N) {
                mediaRecorder?.resume()
                isPaused = false
                recordingStartTime = System.currentTimeMillis()
                Log.d(TAG, "录音已继续")
                return true
            } else {
                Log.w(TAG, "当前Android版本不支持继续录音")
                return false
            }
        } catch (e: Exception) {
            Log.e(TAG, "继续录音失败", e)
            return false
        }
    }
    
    /**
     * 停止录音
     */
    fun stopRecording(): File? {
        if (!isRecording) {
            return null
        }
        
        try {
            mediaRecorder?.apply {
                stop()
                release()
            }
            
            isRecording = false
            isPaused = false
            
            val file = outputFile
            outputFile = null
            mediaRecorder = null
            
            Log.d(TAG, "录音已停止: ${file?.absolutePath}")
            return file
        } catch (e: Exception) {
            Log.e(TAG, "停止录音失败", e)
            releaseRecorder()
            return null
        }
    }
    
    /**
     * 获取当前录音时长（毫秒）
     */
    fun getCurrentDuration(): Long {
        if (!isRecording) {
            return totalRecordingTime
        }
        
        return if (isPaused) {
            totalRecordingTime
        } else {
            totalRecordingTime + (System.currentTimeMillis() - recordingStartTime)
        }
    }
    
    /**
     * 获取录音状态
     */
    fun isRecording(): Boolean = isRecording
    
    fun isPaused(): Boolean = isPaused
    
    /**
     * 获取当前录音文件
     */
    fun getCurrentRecordingFile(): File? = outputFile
    
    /**
     * 获取当前录音音量振幅 (0.0 - 1.0)
     * 用于显示波形动画
     */
    fun getCurrentAmplitude(): Float {
        return try {
            if (isRecording && !isPaused && mediaRecorder != null) {
                val maxAmplitude = mediaRecorder!!.maxAmplitude
                // 将振幅转换为0.0-1.0范围
                // MediaRecorder的maxAmplitude通常在0-32767之间
                (maxAmplitude / 32767.0f).coerceIn(0.1f, 1.0f)
            } else {
                0.1f
            }
        } catch (e: Exception) {
            Log.e(TAG, "获取录音振幅失败", e)
            0.1f
        }
    }
    
    /**
     * 释放资源
     */
    private fun releaseRecorder() {
        try {
            mediaRecorder?.release()
        } catch (e: Exception) {
            Log.e(TAG, "释放MediaRecorder失败", e)
        }
        mediaRecorder = null
        outputFile = null
        isRecording = false
        isPaused = false
    }
    
    /**
     * 清理资源
     */
    fun release() {
        releaseRecorder()
    }
}



