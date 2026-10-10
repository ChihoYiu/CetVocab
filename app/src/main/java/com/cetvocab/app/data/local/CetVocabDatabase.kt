package com.cetvocab.app.data.local

import android.content.Context
import androidx.room.Database
import androidx.room.Room
import androidx.room.RoomDatabase
import androidx.room.TypeConverters
import com.cetvocab.app.data.local.dao.DailySummaryDao
import com.cetvocab.app.data.local.dao.ExamDao
import com.cetvocab.app.data.local.dao.ImportHistoryDao
import com.cetvocab.app.data.local.dao.MistakeBookDao
import com.cetvocab.app.data.local.dao.ProgressDao
import com.cetvocab.app.data.local.dao.ReviewEventDao
import com.cetvocab.app.data.local.dao.StudySessionDao
import com.cetvocab.app.data.local.dao.WordDao
import com.cetvocab.app.data.local.dao.WordWithProgressView
import com.cetvocab.app.data.local.entity.DailyStudySummaryEntity
import com.cetvocab.app.data.local.entity.ExamQuestionEntity
import com.cetvocab.app.data.local.entity.ExamSessionEntity
import com.cetvocab.app.data.local.entity.ImportHistoryEntity
import com.cetvocab.app.data.local.entity.ReviewEventEntity
import com.cetvocab.app.data.local.entity.StudySessionEntity
import com.cetvocab.app.data.local.entity.UserWordProgressEntity
import com.cetvocab.app.data.local.entity.WordEntity
import com.cetvocab.app.data.local.entity.WordPackEntity
import com.cetvocab.app.data.local.entity.WrongAnswerEntity

/**
 * The single local database. Everything the app needs lives here, in the app's private
 * storage, which is why the whole product works with no network permission at all.
 *
 * Vocabulary content (`word`, `word_pack`) is separated from user data
 * (`user_word_progress`, `review_event`, `wrong_answer`, sessions, summaries), so
 * re-importing or deleting a word list cannot silently destroy study history.
 */
@Database(
    entities = [
        WordPackEntity::class,
        WordEntity::class,
        UserWordProgressEntity::class,
        ReviewEventEntity::class,
        WrongAnswerEntity::class,
        StudySessionEntity::class,
        ExamSessionEntity::class,
        ExamQuestionEntity::class,
        DailyStudySummaryEntity::class,
        ImportHistoryEntity::class,
    ],
    views = [WordWithProgressView::class],
    // Kept as a literal: the annotation processor requires a compile-time constant, while
    // `Migrations.CURRENT_VERSION` mirrors it and a test asserts the two stay equal.
    version = 1,
    exportSchema = true,
)
@TypeConverters(Converters::class)
abstract class CetVocabDatabase : RoomDatabase() {

    abstract fun wordDao(): WordDao
    abstract fun progressDao(): ProgressDao
    abstract fun mistakeBookDao(): MistakeBookDao
    abstract fun reviewEventDao(): ReviewEventDao
    abstract fun studySessionDao(): StudySessionDao
    abstract fun examDao(): ExamDao
    abstract fun dailySummaryDao(): DailySummaryDao
    abstract fun importHistoryDao(): ImportHistoryDao

    companion object {
        const val NAME = "cet_vocab.db"

        /** Builds the database with the full migration chain attached. */
        fun build(context: Context, databaseName: String = NAME): CetVocabDatabase =
            Room.databaseBuilder(context.applicationContext, CetVocabDatabase::class.java, databaseName)
                .addMigrations(*Migrations.ALL_MIGRATIONS)
                .build()
    }
}
