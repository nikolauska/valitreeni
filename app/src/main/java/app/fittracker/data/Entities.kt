package app.fittracker.data

import androidx.room.Entity
import androidx.room.ForeignKey
import androidx.room.Index
import androidx.room.PrimaryKey
import app.fittracker.domain.BodyArea
import app.fittracker.domain.LogSource
import app.fittracker.domain.MoveUnit
import java.time.Instant
import java.time.LocalDate

@Entity(tableName = "exercise", indices = [Index(value = ["builtinKey"], unique = true)])
data class ExerciseEntity(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    /** Set for built-in moves; their name, picture and tutorial come from [MoveCatalog]. */
    val builtinKey: String?,
    /** Set for moves the user added; shown as typed, never translated. */
    val customName: String?,
    val unit: MoveUnit,
    val bodyArea: BodyArea,
    val amountPerSet: Int,
    val enabled: Boolean = true,
    val sortOrder: Int,
)

@Entity(
    tableName = "goal_change",
    foreignKeys = [ForeignKey(ExerciseEntity::class, ["id"], ["exerciseId"], onDelete = ForeignKey.CASCADE)],
    indices = [Index("exerciseId")],
)
data class GoalChangeEntity(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val exerciseId: Long,
    val dailyGoal: Int,
    val effectiveFrom: LocalDate,
)

@Entity(
    tableName = "log_entry",
    foreignKeys = [ForeignKey(ExerciseEntity::class, ["id"], ["exerciseId"], onDelete = ForeignKey.CASCADE)],
    indices = [Index("exerciseId"), Index("localDate")],
)
data class LogEntryEntity(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val exerciseId: Long,
    val amount: Int,
    val timestamp: Instant,
    /** Day in the phone's time zone at log time, so a late-night set stays on its day even after travel. */
    val localDate: LocalDate,
    val source: LogSource,
)

@Entity(
    tableName = "daily_focus",
    primaryKeys = ["localDate", "exerciseId"],
    foreignKeys = [ForeignKey(ExerciseEntity::class, ["id"], ["exerciseId"], onDelete = ForeignKey.CASCADE)],
    indices = [Index("exerciseId")],
)
data class DailyFocusEntity(
    val localDate: LocalDate,
    val exerciseId: Long,
)

data class DailyTotal(val exerciseId: Long, val localDate: LocalDate, val total: Int)

data class LastDone(val exerciseId: Long, val lastDate: LocalDate)
