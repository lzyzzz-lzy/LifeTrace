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
        Log.d(TAG, "=== Step 4: 图片视觉分析开始 ===")
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

            Log.d(TAG, "=== Step 4: 图片视觉分析完成 ===")
            Log.d(TAG, "主题句: ${summary.visualThemeSentence}")
            Log.d(TAG, "主要场景: ${summary.mainScenes}")
            Log.d(TAG, "整体氛围: ${summary.overallMood}")

            return summary

        } catch (e: Exception) {
            Log.e(TAG, "Step 4 图片视觉分析失败", e)
            return VisualTripSummary.EMPTY
        }
    }

    /**
     * 选择代表图（轻量评分策略）
     *
     * 策略：
     * 1. 图片数量 <= 3：全部分析
     * 2. 图片数量 > 3：基于评分选择，同时保证时间线覆盖
     *
     * 评分规则：
     * - +3: 关联记忆点有非空文本
     * - +2: 关联记忆点文本长度 >= 10
     * - +2: 来自有选中图片的记忆点
     * - +1: 位于时间线起点或终点
     */
    private fun selectRepresentativeImages(images: List<CandidateImageItem>): List<CandidateImageItem> {
        if (images.size <= MAX_ANALYZE_IMAGES) {
            // 图片数量 <= 3，全部分析
            Log.d(TAG, "[代表图] 图片数量 <= $MAX_ANALYZE_IMAGES，全部分析")
            return images
        }

        Log.d(TAG, "[代表图] 候选总数=${images.size}，开始评分选择")

        // 计算每张图片的评分
        val scoredImages = images.map { image ->
            val score = scoreImageForRepresentation(image, images.size)
            Log.d(TAG, "  ${image.id}: 评分=$score (text=${image.memoryTextPreview?.take(20)}, fromSelected=${image.fromSelectedMemory}, pos=${image.position})")
            image to score
        }.sortedByDescending { it.second }

        val result = mutableListOf<CandidateImageItem>()
        val selectedIds = mutableSetOf<String>()

        // 1. 固定时间覆盖：首图和尾图（如果评分不是最低的）
        val firstImage = images.first()
        val lastImage = images.last()

        result.add(firstImage)
        selectedIds.add(firstImage.id)
        Log.d(TAG, "[代表图] 添加首图: ${firstImage.id}")

        if (lastImage.id != firstImage.id) {
            result.add(lastImage)
            selectedIds.add(lastImage.id)
            Log.d(TAG, "[代表图] 添加尾图: ${lastImage.id}")
        }

        // 2. 从评分排序中选剩余的代表图（跳过已选的）
        for ((image, score) in scoredImages) {
            if (result.size >= MAX_ANALYZE_IMAGES) break
            if (image.id in selectedIds) continue

            result.add(image)
            selectedIds.add(image.id)
            Log.d(TAG, "[代表图] 添加高评分图: ${image.id} (评分=$score)")
        }

        Log.d(TAG, "[代表图] 最终选中: ${result.map { it.id }}")
        return result.sortedBy { it.sortTime }  // 按时间排序返回
    }

    /**
     * 计算图片代表图评分
     */
    private fun scoreImageForRepresentation(image: CandidateImageItem, totalImages: Int): Float {
        var score = 0f

        // +3: 关联记忆点有非空文本
        if (!image.memoryTextPreview.isNullOrBlank()) {
            score += 3f
        }

        // +2: 关联记忆点文本长度 >= 10
        if ((image.memoryTextPreview?.length ?: 0) >= 10) {
            score += 2f
        }

        // +2: 来自有选中图片的记忆点
        if (image.fromSelectedMemory) {
            score += 2f
        }

        // +1: 位于时间线起点或终点
        if (image.position == 0 || image.position == totalImages - 1) {
            score += 1f
        }

        return score
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
