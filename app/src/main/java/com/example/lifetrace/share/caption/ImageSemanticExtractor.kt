package com.example.lifetrace.share.caption

import android.content.Context
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.net.Uri
import android.util.Base64
import android.util.Log
import com.example.lifetrace.api.VolcDoubaoApi
import com.example.lifetrace.service.ImagePreprocessor
import com.example.lifetrace.share.model.CandidateImageItem
import com.example.lifetrace.share.model.EnhancedImageAnalysisResult
import com.example.lifetrace.share.model.VisualTripSummary
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import org.json.JSONObject
import java.io.ByteArrayOutputStream

/**
 * 阶段 C: 图片语义抽取服务
 *
 * 职责：
 * 1. 选择代表图（首/中/尾）
 * 2. 图片预处理（压缩、Base64）
 * 3. 视觉分析（场景/主体/氛围/活动）
 * 4. 多图汇总
 *
 * 日志 TAG: ImageSemanticExtractor
 */
class ImageSemanticExtractor(
    private val context: Context,
    private val api: VolcDoubaoApi
) {
    companion object {
        private const val TAG = "ImageSemanticExtractor"
        private const val MAX_ANALYZE_IMAGES = 3  // 最多分析 3 张图
    }

    private val imagePreprocessor = ImagePreprocessor(context)

    /**
     * 抽取图片语义
     * @param candidateImages 候选图片列表
     * @return 视觉旅程摘要
     */
    suspend fun extract(candidateImages: List<CandidateImageItem>): VisualTripSummary {
        Log.d(TAG, "=== 阶段 C: 图片语义抽取开始 ===")
        Log.d(TAG, "候选图片数量: ${candidateImages.size}")

        if (candidateImages.isEmpty()) {
            Log.w(TAG, "无候选图片，返回空摘要")
            return VisualTripSummary.EMPTY
        }

        try {
            // 1. 选择代表图
            val representativeImages = selectRepresentativeImages(candidateImages)
            Log.d(TAG, "选择代表图: ${representativeImages.size} 张")

            // 2. 分析每张图片
            val analysisResults = mutableListOf<EnhancedImageAnalysisResult>()

            representativeImages.forEachIndexed { index, imageItem ->
                Log.d(TAG, "[C2] 正在分析第 ${index + 1}/${representativeImages.size} 张图片...")
                Log.d(TAG, "  Uri: ${imageItem.uri}")

                val result = analyzeImage(imageItem)
                analysisResults.add(result)

                if (result.analysisSuccess) {
                    Log.d(TAG, "  分析成功: scene=${result.scene}, mood=${result.mood}")
                } else {
                    Log.w(TAG, "  分析失败: ${result.errorMessage}")
                }
            }

            // 3. 汇总多图结果
            val summary = aggregateResults(analysisResults)

            Log.d(TAG, "=== 阶段 C: 图片语义抽取完成 ===")
            Log.d(TAG, "主题句: ${summary.visualThemeSentence}")
            Log.d(TAG, "主要场景: ${summary.mainScenes}")
            Log.d(TAG, "整体氛围: ${summary.overallMood}")

            return summary

        } catch (e: Exception) {
            Log.e(TAG, "阶段 C 图片分析失败", e)
            return VisualTripSummary.EMPTY
        }
    }

    /**
     * 选择代表图（首/中/尾策略）
     */
    private fun selectRepresentativeImages(images: List<CandidateImageItem>): List<CandidateImageItem> {
        if (images.size <= MAX_ANALYZE_IMAGES) {
            // 图片数量 <= 3，全部分析
            return images
        }

        // 选择首/中/尾
        val result = mutableListOf<CandidateImageItem>()
        result.add(images.first())  // 首图

        // 中间图
        val middleIndex = images.size / 2
        result.add(images[middleIndex])

        // 尾图
        result.add(images.last())

        return result
    }

    /**
     * 分析单张图片
     */
    private suspend fun analyzeImage(imageItem: CandidateImageItem): EnhancedImageAnalysisResult {
        return withContext(Dispatchers.IO) {
            try {
                // 1. 预处理图片
                val preprocessResult = imagePreprocessor.preprocessImage(imageItem.uri)

                if (!preprocessResult.success || preprocessResult.base64Data == null) {
                    return@withContext EnhancedImageAnalysisResult(
                        imageId = imageItem.id,
                        analysisSuccess = false,
                        errorMessage = "图片预处理失败: ${preprocessResult.error}"
                    )
                }

                Log.d(TAG, "  预处理成功: ${preprocessResult.fileSize / 1024}KB, ${preprocessResult.width}x${preprocessResult.height}")

                // 2. 调用视觉 API 分析
                val apiResult = api.analyzeImage(preprocessResult.base64Data)

                // 3. 解析结果
                return@withContext EnhancedImageAnalysisResult(
                    imageId = imageItem.id,
                    scene = apiResult.scene,
                    subject = apiResult.subject,
                    mood = apiResult.atmosphere,
                    activity = apiResult.activity,
                    shareableSummary = apiResult.summary,
                    keywords = apiResult.keywords,
                    analysisSuccess = true
                )

            } catch (e: Exception) {
                Log.e(TAG, "图片分析异常: ${imageItem.id}", e)
                EnhancedImageAnalysisResult(
                    imageId = imageItem.id,
                    analysisSuccess = false,
                    errorMessage = "分析异常: ${e.message}"
                )
            }
        }
    }

    /**
     * 汇总多图分析结果
     */
    private fun aggregateResults(results: List<EnhancedImageAnalysisResult>): VisualTripSummary {
        val successResults = results.filter { it.analysisSuccess }

        if (successResults.isEmpty()) {
            return VisualTripSummary.EMPTY
        }

        // 收集所有场景
        val allScenes = successResults.mapNotNull { it.scene.takeIfNotBlank() }.distinct()
        // 收集所有主体
        val allSubjects = successResults.mapNotNull { it.subject.takeIfNotBlank() }.distinct()
        // 收集所有活动
        val allActivities = successResults.mapNotNull { it.activity.takeIfNotBlank() }.distinct()
        // 收集所有氛围
        val allMoods = successResults.mapNotNull { it.mood.takeIfNotBlank() }

        // 计算整体氛围（取最常见的或合并）
        val overallMood = if (allMoods.isNotEmpty()) {
            allMoods.joinToString("、").take(20)
        } else {
            "轻松"
        }

        // 生成主题句
        val themeSentence = buildThemeSentence(allScenes, allActivities, overallMood)

        return VisualTripSummary(
            mainScenes = allScenes.take(5),
            mainSubjects = allSubjects.take(5),
            overallMood = overallMood,
            mainActivities = allActivities.take(5),
            visualThemeSentence = themeSentence,
            analyzedImageCount = successResults.size,
            imageAnalysisResults = successResults
        )
    }

    /**
     * 构建主题句
     */
    private fun buildThemeSentence(
        scenes: List<String>,
        activities: List<String>,
        mood: String
    ): String {
        val sb = StringBuilder()

        // 场景描述
        if (scenes.isNotEmpty()) {
            sb.append(scenes.take(2).joinToString("和"))
        }

        // 活动描述
        if (activities.isNotEmpty()) {
            if (sb.isNotEmpty()) sb.append("，")
            sb.append(activities.take(2).joinToString("和"))
        }

        // 氛围总结
        if (mood.isNotBlank()) {
            if (sb.isNotEmpty()) sb.append("，")
            sb.append("整体感觉${mood}")
        }

        return if (sb.isNotEmpty()) sb.toString() else "一次愉快的旅行"
    }

    /**
     * 辅助扩展函数
     */
    private fun String?.takeIfNotBlank(): String? {
        return if (this.isNullOrBlank()) null else this
    }

    private fun String?.isNullOrBlank(): Boolean = this == null || this.isBlank()
}
