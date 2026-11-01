package com.yaz.launcher.data.glance

// The header's date and time, formatted from the locale by Android's own formatters:
// - The Gregorian line: android.text.format.DateFormat.getBestDateTimePattern(locale, "EEEEdMMMM")
//   (https://developer.android.com/reference/android/text/format/DateFormat#getBestDateTimePattern(java.util.Locale,%20java.lang.String)).
// - The Hijri line: android.icu.util.IslamicCalendar in its Umm al-Qura calculation, formatted by android.icu.text.DateFormat.getInstanceForSkeleton
//   with the skeleton "dMMMMy" (no weekday)
//   (https://developer.android.com/reference/android/icu/util/IslamicCalendar and
//   https://developer.android.com/reference/android/icu/text/DateFormat#getInstanceForSkeleton(android.icu.util.Calendar,%20java.lang.String,%20java.util.Locale)).
// - Durations: android.icu.text.MeasureFormat, localised and narrow
//   (https://developer.android.com/reference/android/icu/text/MeasureFormat).
import android.content.Context
import android.icu.text.MeasureFormat
import android.icu.util.Calendar
import android.icu.util.IslamicCalendar
import android.icu.util.Measure
import android.icu.util.MeasureUnit
import android.icu.util.ULocale
import android.text.format.DateFormat
import androidx.annotation.StringRes
import com.yaz.launcher.R
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

/** A day the header names: a string resource and, for Ramadan, the day's number. */
data class SpecialDay(@StringRes val name: Int, val number: Int = 0)

object GlanceDate {

    /** "Tuesday 3 February", the Gregorian line. */
    fun gregorian(now: Long, locale: Locale): String =
        SimpleDateFormat(DateFormat.getBestDateTimePattern(locale, "EEEEdMMMM"), locale).format(Date(now))

    // The Umm al-Qura calendar of the locale, set to [now].
    private fun hijriCalendar(now: Long, locale: Locale): IslamicCalendar =
        IslamicCalendar(ULocale.forLocale(locale)).apply {
            setCalculationType(IslamicCalendar.CalculationType.ISLAMIC_UMALQURA)
            timeInMillis = now
        }

    /** "14 Rajab 1448", the Hijri line, without the weekday. */
    fun hijri(now: Long, locale: Locale): String {
        val calendar = hijriCalendar(now, locale)
        return android.icu.text.DateFormat.getInstanceForSkeleton(calendar, "dMMMMy", locale).format(Date(now))
    }

    /**
     * The special Hijri day, from the same calendar: 1 Muharram the Islamic New Year, 10 Muharram Ashura, 1 Shawwal Eid al-Fitr, 9 Dhu al-Hijjah the
     * Day of Arafah, 10 Dhu al-Hijjah Eid al-Adha, each day of Ramadan, else a Friday is Jumu'ah. Nothing else.
     */
    fun special(now: Long, locale: Locale): SpecialDay? {
        val calendar = hijriCalendar(now, locale)
        val month = calendar.get(Calendar.MONTH)
        val day = calendar.get(Calendar.DAY_OF_MONTH)
        return when {
            month == IslamicCalendar.MUHARRAM && day == 1 -> SpecialDay(R.string.hijri_new_year)
            month == IslamicCalendar.MUHARRAM && day == 10 -> SpecialDay(R.string.hijri_ashura)
            month == IslamicCalendar.SHAWWAL && day == 1 -> SpecialDay(R.string.hijri_eid_fitr)
            month == IslamicCalendar.DHU_AL_HIJJAH && day == 9 -> SpecialDay(R.string.hijri_arafah)
            month == IslamicCalendar.DHU_AL_HIJJAH && day == 10 -> SpecialDay(R.string.hijri_eid_adha)
            month == IslamicCalendar.RAMADAN -> SpecialDay(R.string.hijri_ramadan_day, day)
            calendar.get(Calendar.DAY_OF_WEEK) == Calendar.FRIDAY -> SpecialDay(R.string.hijri_jumuah)
            else -> null
        }
    }

    /** The pattern of the clock for [context]'s 12 or 24 hour setting: getBestDateTimePattern with "Hm" or "hm", the day period left to [dayPeriod]. */
    fun clockPattern(context: Context, locale: Locale): String {
        val pattern = DateFormat.getBestDateTimePattern(locale, if (DateFormat.is24HourFormat(context)) "Hm" else "hm")
        return pattern.replace(Regex("[aBb]+"), "").trim().trim(' ', ' ', ' ')
    }

    /** AM or PM in 12 hour mode, null in 24 hour mode. */
    fun dayPeriod(context: Context, now: Long, locale: Locale): String? =
        if (DateFormat.is24HourFormat(context)) null else SimpleDateFormat("a", locale).format(Date(now))

    /** The hour and minutes of the clock. */
    fun clock(context: Context, now: Long, locale: Locale): String = SimpleDateFormat(clockPattern(context, locale), locale).format(Date(now))

    /** The seconds, ":07". */
    fun seconds(now: Long, locale: Locale): String = String.format(locale, ":%02d", (now / 1000L) % 60L)

    /** A time of day as the alarm shows it ("6:30" or "06:30"), from the phone's 12 or 24 hour setting. */
    fun timeOfDay(context: Context, at: Long, locale: Locale): String {
        val skeleton = if (DateFormat.is24HourFormat(context)) "Hm" else "hm"
        return SimpleDateFormat(DateFormat.getBestDateTimePattern(locale, skeleton), locale).format(Date(at))
    }

    /** "25 min" or "1h 10m": the time left, rounded up to the minute, hours and minutes of MeasureFormat's narrow width. */
    fun duration(ms: Long, locale: Locale): String {
        val minutes = ((ms + 59_999L) / 60_000L).coerceAtLeast(1L)
        val hours = minutes / 60L
        val rest = minutes % 60L
        val format = MeasureFormat.getInstance(locale, MeasureFormat.FormatWidth.NARROW)
        return when {
            hours == 0L -> format.format(Measure(rest, MeasureUnit.MINUTE))
            rest == 0L -> format.format(Measure(hours, MeasureUnit.HOUR))
            else -> format.formatMeasures(Measure(hours, MeasureUnit.HOUR), Measure(rest, MeasureUnit.MINUTE))
        }
    }
}
