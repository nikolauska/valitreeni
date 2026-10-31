package app.fittracker.data

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import androidx.room.Transaction
import androidx.room.Update
import java.time.LocalDate
import kotlinx.coroutines.flow.Flow

@Dao
interface FitDao {
    @Query("SELECT * FROM exercise ORDER BY sortOrder, id")
    fun observeExercises(): Flow<List<ExerciseEntity>>

    @Query("SELECT * FROM exercise ORDER BY sortOrder, id")
    suspend fun exercises(): List<ExerciseEntity>

    @Query("SELECT * FROM exercise WHERE id = :id")
    suspend fun exercise(id: Long): ExerciseEntity?

    @Insert
    suspend fun insertExercise(exercise: ExerciseEntity): Long

    @Update
    suspend fun updateExercise(exercise: ExerciseEntity)

    @Query("SELECT COALESCE(MAX(sortOrder), 0) FROM exercise")
    suspend fun maxSortOrder(): Int

    @Query("SELECT * FROM goal_change")
    fun observeGoalChanges(): Flow<List<GoalChangeEntity>>

    @Query("SELECT * FROM goal_change")
    suspend fun goalChanges(): List<GoalChangeEntity>

    /** Changing a goal twice on the same day keeps only the last value for that day. */
    @Query("DELETE FROM goal_change WHERE exerciseId = :exerciseId AND effectiveFrom = :date")
    suspend fun deleteGoalChange(exerciseId: Long, date: LocalDate)

    @Insert
    suspend fun insertGoalChange(change: GoalChangeEntity)

    @Transaction
    suspend fun setGoal(exerciseId: Long, goal: Int, from: LocalDate) {
        deleteGoalChange(exerciseId, from)
        insertGoalChange(GoalChangeEntity(exerciseId = exerciseId, dailyGoal = goal, effectiveFrom = from))
    }

    @Insert
    suspend fun insertLogs(entries: List<LogEntryEntity>)

    @Query(
        """DELETE FROM log_entry WHERE id = (
            SELECT id FROM log_entry WHERE exerciseId = :exerciseId AND localDate = :date
            ORDER BY timestamp DESC, id DESC LIMIT 1)""",
    )
    suspend fun deleteLatestLog(exerciseId: Long, date: LocalDate)

    @Query(
        """SELECT exerciseId, localDate, SUM(amount) AS total FROM log_entry
           WHERE localDate BETWEEN :from AND :to GROUP BY exerciseId, localDate""",
    )
    fun observeTotals(from: LocalDate, to: LocalDate): Flow<List<DailyTotal>>

    @Query(
        """SELECT exerciseId, localDate, SUM(amount) AS total FROM log_entry
           WHERE localDate BETWEEN :from AND :to GROUP BY exerciseId, localDate""",
    )
    suspend fun totals(from: LocalDate, to: LocalDate): List<DailyTotal>

    @Query("SELECT exerciseId, MAX(localDate) AS lastDate FROM log_entry GROUP BY exerciseId")
    suspend fun lastDoneDates(): List<LastDone>

    @Query("SELECT * FROM daily_focus WHERE localDate = :date")
    suspend fun focus(date: LocalDate): List<DailyFocusEntity>

    @Query("SELECT * FROM daily_focus WHERE localDate BETWEEN :from AND :to")
    fun observeFocus(from: LocalDate, to: LocalDate): Flow<List<DailyFocusEntity>>

    @Insert(onConflict = OnConflictStrategy.IGNORE)
    suspend fun insertFocus(focus: List<DailyFocusEntity>)

    @Query("DELETE FROM daily_focus WHERE localDate = :date AND exerciseId = :exerciseId")
    suspend fun deleteFocus(date: LocalDate, exerciseId: Long)

    /** Days before [date] when the move was in focus; 0 means it is new to the user. */
    @Query("SELECT COUNT(*) FROM daily_focus WHERE exerciseId = :exerciseId AND localDate < :date")
    suspend fun daysInFocusBefore(exerciseId: Long, date: LocalDate): Int
}
