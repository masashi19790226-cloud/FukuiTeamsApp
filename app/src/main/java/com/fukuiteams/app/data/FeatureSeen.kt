package com.fukuiteams.app.data

import android.content.Context
import kotlin.random.Random

/**
 * 一面の特集を「開くたびに入れかえる」ための記録(Web版の home.js と同じ考え方)。
 * ・seed:アプリを起動するたびに選び直す数(起動しているあいだは同じ。描き直しで記事が入れかわらないように)
 * ・見た記事:画面に出した記事を、この端末の中だけに記録し、次に起動したときから出さない
 *   (見ていない記事が足りなくなったら、記録をはじめからにする=ひと回りしたらもう一度出る)
 */
object FeatureSeen {
    private const val PREFS = "feature_seen"
    private const val KEY = "seen"
    private const val MAX = 300
    /** 記録の区切り(見出しに改行が入っていても区切りを間違えないよう、ふつう使わない文字にする) */
    private const val SEP = "\u0001"

    /** 起動するたびに変わる数 */
    val seed: Int = Random.nextInt(0, Int.MAX_VALUE)

    /** 起動したときの「見た記事」(起動しているあいだは変えない) */
    private var atOpen: Set<String>? = null
    /** 今回起動してから画面に出した記事 */
    private val shown = LinkedHashSet<String>()
    /** 見ていない記事が足りず、記録をはじめからにしたか */
    private var restart = false

    fun seenAtOpen(context: Context): Set<String> {
        atOpen?.let { return it }
        val list = context.getSharedPreferences(PREFS, Context.MODE_PRIVATE).getString(KEY, "").orEmpty()
            .split(SEP).filter { it.isNotBlank() }
        return list.toSet().also { atOpen = it }
    }

    /** 並べた特集に前に見た記事が入っていたら(見ていない記事が足りなかったら)、記録をはじめからにする */
    fun noteStories(context: Context, stories: List<FeatureStory>) {
        val open = seenAtOpen(context)
        if (!restart && stories.any { featureStoryKey(it) in open }) {
            restart = true
            save(context)
        }
    }

    /** 画面に出した記事を「見た記事」に記録する */
    fun markShown(context: Context, story: FeatureStory) {
        if (shown.add(featureStoryKey(story))) save(context)
    }

    private fun save(context: Context) {
        val base = if (restart) emptyList() else seenAtOpen(context).filterNot { it in shown }
        val next = (base + shown).takeLast(MAX)
        context.getSharedPreferences(PREFS, Context.MODE_PRIVATE).edit().putString(KEY, next.joinToString(SEP)).apply()
    }
}
